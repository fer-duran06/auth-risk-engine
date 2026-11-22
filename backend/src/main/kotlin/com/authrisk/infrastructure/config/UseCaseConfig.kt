package com.authrisk.infrastructure.config

import com.authrisk.application.auth.GetCurrentUserUseCase
import com.authrisk.application.auth.LoginUseCase
import com.authrisk.application.auth.LogoutUseCase
import com.authrisk.application.auth.RefreshUseCase
import com.authrisk.application.auth.RegisterUseCase
import com.authrisk.application.auth.SessionTokenFactory
import com.authrisk.application.port.TransactionRunner
import com.authrisk.domain.port.LoginAttemptRepository
import com.authrisk.domain.port.PasswordHasher
import com.authrisk.domain.port.RiskCounters
import com.authrisk.domain.risk.RiskEngine
import com.authrisk.infrastructure.redis.RiskProperties
import com.authrisk.domain.port.RefreshTokenGenerator
import com.authrisk.domain.port.RefreshTokenRepository
import com.authrisk.domain.port.TokenIssuer
import com.authrisk.domain.port.UserRepository
import com.authrisk.infrastructure.security.AuthProperties
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** Registra los casos de uso (Kotlin puro) como beans de Spring. */
@Configuration
class UseCaseConfig {
    private val log = LoggerFactory.getLogger(javaClass)

    @Bean
    fun clock(): Clock = Clock.systemUTC()

    @Bean
    fun sessionTokenFactory(issuer: TokenIssuer, generator: RefreshTokenGenerator, props: AuthProperties) =
        SessionTokenFactory(issuer, generator, props.refreshTokenTtl)

    @Bean
    fun registerUseCase(users: UserRepository, hasher: PasswordHasher, tx: TransactionRunner, clock: Clock) =
        RegisterUseCase(users, hasher, tx, clock)

    @Bean
    fun riskEngine(props: RiskProperties): RiskEngine = props.toEngine()

    @Bean
    fun loginUseCase(
        users: UserRepository,
        refreshTokens: RefreshTokenRepository,
        hasher: PasswordHasher,
        sessionTokens: SessionTokenFactory,
        riskEngine: RiskEngine,
        riskCounters: RiskCounters,
        loginAttempts: LoginAttemptRepository,
        tx: TransactionRunner,
        clock: Clock,
        riskProps: RiskProperties,
        @Value("\${app.demo-mode:false}") demoMode: Boolean,
    ): LoginUseCase {
        val riskEnabled = riskProps.effectiveEnabled(demoMode)
        if (!riskEnabled) log.warn("MOTOR DE RIESGO DESACTIVADO (app.risk.enabled=false): todo login es ALLOW. Solo para demos.")
        return LoginUseCase(users, refreshTokens, hasher, sessionTokens, riskEngine, riskCounters, loginAttempts, tx, clock, riskEnabled)
    }

    @Bean
    fun refreshUseCase(
        users: UserRepository,
        refreshTokens: RefreshTokenRepository,
        generator: RefreshTokenGenerator,
        sessionTokens: SessionTokenFactory,
        tx: TransactionRunner,
        clock: Clock,
    ) = RefreshUseCase(users, refreshTokens, generator, sessionTokens, tx, clock)

    @Bean
    fun logoutUseCase(
        refreshTokens: RefreshTokenRepository,
        generator: RefreshTokenGenerator,
        tx: TransactionRunner,
        clock: Clock,
    ) = LogoutUseCase(refreshTokens, generator, tx, clock)

    @Bean
    fun getCurrentUserUseCase(users: UserRepository) = GetCurrentUserUseCase(users)
}
