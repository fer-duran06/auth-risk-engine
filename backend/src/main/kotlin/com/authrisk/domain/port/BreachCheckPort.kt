package com.authrisk.domain.port

/**
 * Consulta de brechas de seguridad conocidas en servicios gratuitos de terceros (Pwned Passwords
 * y XposedOrNot). Ninguno de los dos métodos lanza: un fallo de red, un timeout o el tercero caído
 * se traducen en `null` ("no se pudo determinar"), que el punto de uso siempre trata como
 * "no filtrado" — un servicio externo caído nunca debe bloquear el registro ni el login.
 */
interface BreachCheckPort {
    /**
     * true si la contraseña aparece en la base de contraseñas filtradas (Pwned Passwords).
     * La contraseña nunca sale del servidor: solo se envían los primeros 5 caracteres de su hash
     * SHA-1 (k-anonimato) y la comparación del resto se hace en memoria con la respuesta.
     */
    fun isPasswordPwned(rawPassword: String): Boolean?

    /** Número de brechas conocidas en las que aparece el email, o null si no se pudo consultar. */
    fun breachesForEmail(email: String): Int?
}
