package com.authrisk.domain.risk

/** El intento cayó en la zona BLOCK. Mensaje genérico: no revela si el email existe. */
class LoginBlockedException : RuntimeException("Demasiados intentos")

/**
 * Zona CHALLENGE: se exige el segundo factor. Se lanza igual sea la contraseña correcta o no, así
 * la respuesta no la revela. [challengeId] es opaco y de un solo uso; no se incluye en el mensaje.
 */
class ChallengeRequiredException(val challengeId: String) : RuntimeException("Verificación adicional requerida")

/** El almacén de contadores no respondió; el llamador aplica el fail-safe. */
class RiskCountersUnavailableException : RuntimeException("Contadores de riesgo no disponibles")
