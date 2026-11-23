package com.authrisk.application.auth

import com.authrisk.application.port.TransactionRunner
import com.authrisk.application.twofactor.LoginChallengeIssuer
import com.authrisk.application.twofactor.VerifyLoginChallengeUseCase
import com.authrisk.domain.port.ChallengeIdGenerator
import com.authrisk.domain.port.LoginChallengeRepository
import com.authrisk.domain.totp.LoginChallenge
import com.authrisk.domain.totp.Totp
import com.authrisk.domain.port.LoginAttemptRepository
import com.authrisk.domain.port.PasswordHasher
import com.authrisk.domain.port.RiskCounters
import com.authrisk.domain.risk.LoginAttempt
import com.authrisk.domain.risk.LoginOutcome
import com.authrisk.domain.risk.RiskCountersUnavailableException
import com.authrisk.domain.risk.RiskEngine
import com.authrisk.domain.risk.RiskPolicy
import com.authrisk.domain.risk.RiskSignals
import com.authrisk.domain.risk.rules.AccountFailuresRule
import com.authrisk.domain.risk.rules.DistinctUsersPerIpRule
import com.authrisk.domain.risk.rules.GlobalFailureRateRule
import com.authrisk.domain.risk.rules.IpVelocityRule
import com.authrisk.domain.port.RefreshTokenGenerator
import com.authrisk.domain.port.RefreshTokenRepository
import com.authrisk.domain.port.TokenIssuer
import com.authrisk.domain.port.UserRepository
import com.authrisk.domain.port.TotpSecretCipher
import com.authrisk.domain.port.UserTotpRepository
import com.authrisk.domain.totp.UserTotp
import com.authrisk.domain.token.AccessToken
import com.authrisk.domain.token.RefreshToken
import com.authrisk.domain.user.Email
import com.authrisk.domain.user.User
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Base64
import java.util.UUID

// Dobles de prueba en memoria para los puertos: evitan mocks y mantienen las pruebas legibles.

class InMemoryUserRepository : UserRepository {
    val users = mutableMapOf<UUID, User>()

    override fun insertIfAbsent(user: User): Boolean {
        if (users.values.any { it.email == user.email }) return false
        users[user.id] = user
        return true
    }

    override fun findByEmail(email: Email) = users.values.firstOrNull { it.email == email }
    override fun findById(id: UUID) = users[id]
}

class InMemoryRefreshTokenRepository : RefreshTokenRepository {
    val tokens = linkedMapOf<UUID, RefreshToken>()

    override fun save(token: RefreshToken) {
        tokens[token.id] = token
    }

    override fun findByHashForUpdate(tokenHash: String) = tokens.values.firstOrNull { it.tokenHash == tokenHash }

    override fun update(token: RefreshToken) {
        check(tokens.containsKey(token.id))
        tokens[token.id] = token
    }

    override fun revokeFamily(familyId: UUID, now: Instant) {
        tokens.replaceAll { _, t -> if (t.familyId == familyId && t.revokedAt == null) t.copy(revokedAt = now) else t }
    }

    fun family(familyId: UUID) = tokens.values.filter { it.familyId == familyId }
}

/** Hash reversible trivial; cuenta las verificaciones para comprobar el hash ficticio del login. */
class FakePasswordHasher : PasswordHasher {
    var hashCalls = 0
    var matchCalls = 0

    override fun hash(rawPassword: String): String {
        hashCalls++
        return "hashed:$rawPassword"
    }

    override fun matches(rawPassword: String, passwordHash: String): Boolean {
        matchCalls++
        return passwordHash == "hashed:$rawPassword"
    }
}

class FakeTokenIssuer(private val clock: Clock) : TokenIssuer {
    override fun issueAccessToken(user: User) =
        AccessToken("jwt-for-${user.id}", clock.instant().plus(Duration.ofMinutes(15)))
}

class SequentialRefreshTokenGenerator : RefreshTokenGenerator {
    private var counter = 0
    override fun generate() = "refresh-${++counter}"
    override fun hash(rawToken: String) = "sha:$rawToken"
}

class DirectTransactionRunner : TransactionRunner {
    override fun <T> inTransaction(block: () -> T): T = block()
}

/** Reloj controlable para probar expiraciones. */
class MutableClock(var now: Instant = Instant.parse("2026-01-01T00:00:00Z")) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = now
    fun advance(d: Duration) {
        now = now.plus(d)
    }
}

/** Arma todos los casos de uso sobre los mismos fakes. */
class AuthFixture(riskEnabled: Boolean = true) {
    val clock = MutableClock()
    val users = InMemoryUserRepository()
    val refreshTokens = InMemoryRefreshTokenRepository()
    val hasher = FakePasswordHasher()
    val generator = SequentialRefreshTokenGenerator()
    val tx = DirectTransactionRunner()
    val refreshTtl: Duration = Duration.ofDays(7)
    val sessions = SessionTokenFactory(FakeTokenIssuer(clock), generator, refreshTtl)

    val riskCounters = FakeRiskCounters()
    val loginAttempts = InMemoryLoginAttemptRepository()
    val riskEngine = RiskEngine(
        listOf(IpVelocityRule(), DistinctUsersPerIpRule(), AccountFailuresRule(), GlobalFailureRateRule()),
        RiskPolicy(),
    )

    val challenges = InMemoryLoginChallengeRepository()
    val challengeIds = SequentialChallengeIdGenerator()
    val totpRepo = InMemoryUserTotpRepository()
    val cipher = FakeTotpSecretCipher()
    val totp = Totp()
    val challengeTtl: Duration = Duration.ofMinutes(5)
    val maxVerifyAttempts = 5
    val challengeIssuer = LoginChallengeIssuer(challenges, challengeIds, tx, clock, challengeTtl)

    val register = RegisterUseCase(users, hasher, tx, clock)
    val login = LoginUseCase(
        users, refreshTokens, hasher, sessions, riskEngine, riskCounters, loginAttempts, challengeIssuer, tx, clock, riskEnabled,
    )
    val verify = VerifyLoginChallengeUseCase(
        challenges, challengeIds, totpRepo, cipher, totp, users, refreshTokens, sessions, loginAttempts, tx, clock, maxVerifyAttempts,
    )
    val refresh = RefreshUseCase(users, refreshTokens, generator, sessions, tx, clock)
    val logout = LogoutUseCase(refreshTokens, generator, tx, clock)
    val currentUser = GetCurrentUserUseCase(users)

    fun registerAndLogin(email: String = "ana@example.com", password: String = PASSWORD): AuthTokens {
        register.execute(email, password)
        return login.execute(email, password, IP)
    }

    fun recordOf(rawToken: String) = refreshTokens.findByHashForUpdate(generator.hash(rawToken))!!

    companion object {
        const val PASSWORD = "contrasena-larga-1"
    }
}

/** IP de prueba para los casos de uso. */
const val IP = "203.0.113.10"

/** Contadores controlables: devuelve [signals] y anota cada registro; con [failing] simula Redis caído. */
class FakeRiskCounters : RiskCounters {
    var signals = RiskSignals()
    var failing = false
    val recorded = mutableListOf<Triple<String, String, LoginOutcome>>()
    var reads = 0

    override fun read(ip: String, email: String): RiskSignals {
        reads++
        if (failing) throw RiskCountersUnavailableException()
        return signals
    }

    override fun record(ip: String, email: String, outcome: LoginOutcome) {
        if (failing) throw RiskCountersUnavailableException()
        recorded += Triple(ip, email, outcome)
    }
}

class InMemoryLoginAttemptRepository : LoginAttemptRepository {
    val attempts = mutableListOf<LoginAttempt>()
    override fun save(attempt: LoginAttempt) {
        attempts += attempt
    }
}

class InMemoryUserTotpRepository : UserTotpRepository {
    val rows = mutableMapOf<UUID, UserTotp>()

    override fun findByUserId(userId: UUID) = rows[userId]

    override fun savePending(userId: UUID, encryptedSecret: String, now: Instant): Boolean {
        if (rows[userId]?.enabled == true) return false
        rows[userId] = UserTotp(userId, encryptedSecret, false, null, null, now)
        return true
    }

    override fun markStepUsed(userId: UUID, step: Long): Boolean {
        val row = rows[userId] ?: return false
        if (row.lastUsedStep != null && row.lastUsedStep >= step) return false
        rows[userId] = row.copy(lastUsedStep = step)
        return true
    }

    override fun enable(userId: UUID, now: Instant): Boolean {
        val row = rows[userId] ?: return false
        if (row.enabled) return false
        rows[userId] = row.copy(enabled = true, confirmedAt = now)
        return true
    }
}

/** "Cifrado" reversible ligado al usuario: suficiente para probar los casos de uso sin AES. */
class FakeTotpSecretCipher : TotpSecretCipher {
    override fun encrypt(userId: UUID, secret: ByteArray) = "$userId:${Base64.getEncoder().encodeToString(secret)}"

    override fun decrypt(userId: UUID, encrypted: String): ByteArray {
        val (owner, data) = encrypted.split(":")
        check(owner == userId.toString()) { "secreto de otro usuario" }
        return Base64.getDecoder().decode(data)
    }
}

class InMemoryLoginChallengeRepository : LoginChallengeRepository {
    val rows = linkedMapOf<UUID, LoginChallenge>()
    /** Simula un fallo del almacenamiento al guardar. */
    var failing = false

    override fun save(challenge: LoginChallenge) {
        if (failing) throw IllegalStateException("BD caída")
        rows[challenge.id] = challenge
    }

    override fun findByHashForUpdate(idHash: String) = rows.values.firstOrNull { it.idHash == idHash }

    override fun updateAttempts(idHash: String, attempts: Int, consumedAt: Instant?): Int {
        val row = rows.values.firstOrNull { it.idHash == idHash } ?: return 0
        rows[row.id] = row.copy(attempts = attempts, consumedAt = consumedAt)
        return 1
    }

    override fun deleteClosed(now: Instant): Int {
        val closed = rows.values.filter { !now.isBefore(it.expiresAt) || it.consumedAt != null }.map { it.id }
        closed.forEach(rows::remove)
        return closed.size
    }
}

class SequentialChallengeIdGenerator : ChallengeIdGenerator {
    private var counter = 0
    override fun generate() = "challenge-${++counter}"
    override fun hash(rawToken: String) = "sha:$rawToken"
}
