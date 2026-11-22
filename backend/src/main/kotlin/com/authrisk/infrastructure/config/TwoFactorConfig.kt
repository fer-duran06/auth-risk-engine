package com.authrisk.infrastructure.config

import com.authrisk.application.port.TransactionRunner
import com.authrisk.application.twofactor.EnableTotpUseCase
import com.authrisk.application.twofactor.GetTotpStatusUseCase
import com.authrisk.application.twofactor.SetupTotpUseCase
import com.authrisk.domain.port.TotpSecretCipher
import com.authrisk.domain.port.UserRepository
import com.authrisk.domain.port.UserTotpRepository
import com.authrisk.domain.totp.Totp
import com.authrisk.infrastructure.security.AesGcmTotpSecretCipher
import com.authrisk.infrastructure.security.TwoFactorProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

/** Beans del 2FA. El cifrador valida TOTP_ENCRYPTION_KEY al arrancar. */
@Configuration
class TwoFactorConfig {

    @Bean
    fun totp(): Totp = Totp(digits = 6, periodSeconds = 30, window = 1)

    @Bean
    fun totpSecretCipher(props: TwoFactorProperties): TotpSecretCipher = AesGcmTotpSecretCipher(props.encryptionKey)

    @Bean
    fun setupTotpUseCase(
        users: UserRepository,
        totpRepo: UserTotpRepository,
        cipher: TotpSecretCipher,
        totp: Totp,
        tx: TransactionRunner,
        clock: Clock,
        props: TwoFactorProperties,
    ) = SetupTotpUseCase(users, totpRepo, cipher, totp, tx, clock, props.issuer)

    @Bean
    fun enableTotpUseCase(totpRepo: UserTotpRepository, cipher: TotpSecretCipher, totp: Totp, tx: TransactionRunner, clock: Clock) =
        EnableTotpUseCase(totpRepo, cipher, totp, tx, clock)

    @Bean
    fun getTotpStatusUseCase(totpRepo: UserTotpRepository) = GetTotpStatusUseCase(totpRepo)
}
