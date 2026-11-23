package com.authrisk.infrastructure.config

import com.authrisk.application.admin.AdminQueries
import com.authrisk.application.admin.GetAdminStatsUseCase
import com.authrisk.application.admin.GetRiskConfigUseCase
import com.authrisk.application.admin.ListAdminEventsUseCase
import com.authrisk.application.admin.ListRecentIpsUseCase
import com.authrisk.application.admin.RiskConfigView
import com.authrisk.application.admin.RuleConfig
import com.authrisk.domain.risk.RiskPolicy
import com.authrisk.domain.risk.rules.AccountFailuresRule
import com.authrisk.domain.risk.rules.DistinctUsersPerIpRule
import com.authrisk.domain.risk.rules.GlobalFailureRateRule
import com.authrisk.domain.risk.rules.IpVelocityRule
import com.authrisk.infrastructure.redis.RiskProperties
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.time.Clock

@Configuration
class AdminConfig {

    @Bean
    fun getAdminStatsUseCase(queries: AdminQueries, clock: Clock) = GetAdminStatsUseCase(queries, clock)

    @Bean
    fun listAdminEventsUseCase(queries: AdminQueries) = ListAdminEventsUseCase(queries)

    @Bean
    fun listRecentIpsUseCase(queries: AdminQueries, clock: Clock) = ListRecentIpsUseCase(queries, clock)

    /** Vista de la configuración efectiva, con los mismos ids de regla que se guardan en login_attempts. */
    @Bean
    fun getRiskConfigUseCase(props: RiskProperties, @Value("\${app.demo-mode:false}") demoMode: Boolean): GetRiskConfigUseCase {
        val r = props.rules
        val rules = listOf(
            RuleConfig(IpVelocityRule().id, r.ipVelocity.weight, r.ipVelocity.threshold.toDouble(), null, r.ipVelocity.window.seconds),
            RuleConfig(DistinctUsersPerIpRule().id, r.distinctUsersPerIp.weight, r.distinctUsersPerIp.threshold.toDouble(), null, r.distinctUsersPerIp.window.seconds),
            RuleConfig(AccountFailuresRule().id, r.accountFailures.weight, r.accountFailures.threshold.toDouble(), null, r.accountFailures.window.seconds),
            RuleConfig(GlobalFailureRateRule().id, r.globalFailureRate.weight, r.globalFailureRate.ratio, r.globalFailureRate.minAttempts, r.globalFailureRate.window.seconds),
        )
        val view = RiskConfigView(
            enabled = props.effectiveEnabled(demoMode),
            challengeThreshold = props.thresholds.challenge,
            blockThreshold = props.thresholds.block,
            maxScore = RiskPolicy.MAX_SCORE,
            rules = rules,
        )
        return GetRiskConfigUseCase(view)
    }
}
