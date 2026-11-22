package com.authrisk.infrastructure.scheduling

import com.authrisk.application.twofactor.PurgeLoginChallengesUseCase
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

/** Habilita las tareas programadas (se puede desactivar con app.scheduling.enabled=false). */
@Configuration
@EnableScheduling
@ConditionalOnProperty(prefix = "app.scheduling", name = ["enabled"], havingValue = "true", matchIfMissing = true)
class SchedulingConfig

/**
 * Purga periódica de desafíos vencidos o cerrados: un flujo sostenido de logins en zona
 * CHALLENGE (p. ej. un ataque) no debe llenar la tabla login_challenges.
 */
@Component
class LoginChallengeCleanup(private val purge: PurgeLoginChallengesUseCase) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(
        fixedDelayString = "\${app.two-factor.cleanup-interval:5m}",
        initialDelayString = "\${app.two-factor.cleanup-interval:5m}",
    )
    fun run() {
        try {
            val deleted = purge.execute()
            if (deleted > 0) log.debug("Desafíos de login purgados: {}", deleted)
        } catch (ex: RuntimeException) {
            // Se reintenta en la próxima ejecución; solo el tipo, sin datos.
            log.warn("Falló la purga de desafíos de login: {}", ex.javaClass.name)
        }
    }
}
