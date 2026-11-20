package com.authrisk.domain.user

/** El email no tiene un formato válido. */
class InvalidEmailException : RuntimeException("Email inválido")

/** La contraseña no cumple la política. */
class WeakPasswordException :
    RuntimeException("La contraseña debe tener entre ${PasswordPolicy.MIN_LENGTH} y ${PasswordPolicy.MAX_LENGTH} caracteres")

/** Email o contraseña incorrectos. Mensaje único para no revelar si la cuenta existe. */
class InvalidCredentialsException : RuntimeException("Credenciales inválidas")

/** El usuario autenticado ya no existe. */
class UserNotFoundException : RuntimeException("Usuario no encontrado")
