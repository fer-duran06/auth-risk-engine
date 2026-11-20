package com.authrisk.domain.risk

/** Umbrales de zona: ALLOW < [challengeThreshold] <= CHALLENGE < [blockThreshold] <= BLOCK. */
data class RiskPolicy(val challengeThreshold: Int = 30, val blockThreshold: Int = 70) {
    init {
        require(challengeThreshold in 1 until blockThreshold && blockThreshold <= MAX_SCORE) {
            "Umbrales inválidos: se requiere 0 < challenge < block <= $MAX_SCORE"
        }
    }

    fun zoneFor(score: Int): RiskZone = when {
        score >= blockThreshold -> RiskZone.BLOCK
        score >= challengeThreshold -> RiskZone.CHALLENGE
        else -> RiskZone.ALLOW
    }

    companion object {
        const val MAX_SCORE = 100
    }
}
