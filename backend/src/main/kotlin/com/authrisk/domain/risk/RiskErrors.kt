package com.authrisk.domain.risk

/** El intento cayó en la zona BLOCK. Mensaje genérico: no revela si el email existe. */
class LoginBlockedException : RuntimeException("Demasiados intentos")

/** Contraseña correcta, pero el riesgo exige verificación adicional antes de emitir tokens. */
class ChallengeRequiredException : RuntimeException("Verificación adicional requerida")

/** El almacén de contadores no respondió; el llamador aplica el fail-safe. */
class RiskCountersUnavailableException : RuntimeException("Contadores de riesgo no disponibles")
