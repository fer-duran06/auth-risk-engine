package com.authrisk.infrastructure.web

import com.authrisk.domain.risk.ChallengeRequiredException
import com.authrisk.domain.risk.LoginBlockedException
import com.authrisk.domain.token.InvalidRefreshTokenException
import com.authrisk.domain.totp.InvalidTotpCodeException
import com.authrisk.domain.totp.TotpAlreadyEnabledException
import com.authrisk.infrastructure.web.auth.CodedErrorResponse
import com.authrisk.domain.user.InvalidCredentialsException
import com.authrisk.domain.user.InvalidEmailException
import com.authrisk.domain.user.UserNotFoundException
import com.authrisk.domain.user.WeakPasswordException
import com.authrisk.infrastructure.web.auth.ErrorResponse
import com.authrisk.infrastructure.web.auth.RefreshCookieFactory
import org.slf4j.LoggerFactory
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.converter.HttpMessageNotReadableException
import org.springframework.web.bind.MethodArgumentNotValidException
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice

/**
 * Traduce errores a respuestas JSON genéricas. Nunca se incluyen valores enviados por el cliente
 * (contraseñas, tokens) ni trazas en la respuesta o en los logs.
 */
@RestControllerAdvice
class ApiExceptionHandler(private val cookies: RefreshCookieFactory) {
    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(InvalidCredentialsException::class, UserNotFoundException::class)
    fun unauthorized(): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse(INVALID_CREDENTIALS))

    // Sin Retry-After ni detalles: no se revela la ventana, el score ni si el email existe.
    @ExceptionHandler(LoginBlockedException::class)
    fun blocked(): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).body(ErrorResponse(TOO_MANY_ATTEMPTS))

    @ExceptionHandler(ChallengeRequiredException::class)
    fun challenge(): ResponseEntity<CodedErrorResponse> =
        ResponseEntity.status(HttpStatus.FORBIDDEN).body(CodedErrorResponse(CHALLENGE_REQUIRED_MESSAGE, CHALLENGE_REQUIRED))

    @ExceptionHandler(InvalidRefreshTokenException::class)
    fun invalidRefresh(ex: InvalidRefreshTokenException): ResponseEntity<ErrorResponse> {
        if (ex.reuseDetected) log.warn("Reutilización de refresh token detectada: familia revocada")
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
            .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
            .body(ErrorResponse(INVALID_SESSION))
    }

    @ExceptionHandler(InvalidTotpCodeException::class)
    fun invalidTotp(): ResponseEntity<ErrorResponse> = badRequest(emptyList(), INVALID_CODE)

    @ExceptionHandler(TotpAlreadyEnabledException::class)
    fun totpAlreadyEnabled(): ResponseEntity<ErrorResponse> =
        ResponseEntity.status(HttpStatus.CONFLICT).body(ErrorResponse(TOTP_ALREADY_ENABLED))

    @ExceptionHandler(InvalidEmailException::class, WeakPasswordException::class)
    fun domainValidation(ex: RuntimeException): ResponseEntity<ErrorResponse> =
        badRequest(listOfNotNull(ex.message))

    @ExceptionHandler(MethodArgumentNotValidException::class)
    fun beanValidation(ex: MethodArgumentNotValidException): ResponseEntity<ErrorResponse> =
        badRequest(ex.bindingResult.fieldErrors.map { "${it.field}: ${it.defaultMessage}" }.sorted())

    @ExceptionHandler(HttpMessageNotReadableException::class)
    fun unreadable(): ResponseEntity<ErrorResponse> = badRequest(emptyList())

    @ExceptionHandler(Exception::class)
    fun unexpected(ex: Exception): ResponseEntity<ErrorResponse> {
        // Excepciones estándar de Spring MVC (405, 415, 404...): se respeta su código con cuerpo genérico.
        if (ex is org.springframework.web.ErrorResponse) {
            return ResponseEntity.status(ex.statusCode).body(ErrorResponse("Solicitud no válida para este recurso"))
        }
        // Solo el tipo: el mensaje podría contener datos de la petición.
        log.error("Error no controlado: {}", ex.javaClass.name)
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ErrorResponse("Error interno"))
    }

    private fun badRequest(details: List<String>, message: String = "Solicitud inválida") =
        ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ErrorResponse(message, details))

    companion object {
        const val INVALID_CREDENTIALS = "Credenciales inválidas"
        const val INVALID_SESSION = "Sesión inválida o expirada"
        const val TOO_MANY_ATTEMPTS = "Demasiados intentos. Intenta más tarde."
        const val CHALLENGE_REQUIRED_MESSAGE = "Verificación adicional requerida"
        const val CHALLENGE_REQUIRED = "CHALLENGE_REQUIRED"
        const val INVALID_CODE = "Código inválido"
        const val TOTP_ALREADY_ENABLED = "La verificación en dos pasos ya está activa"
    }
}
