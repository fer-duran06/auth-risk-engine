package com.authrisk.domain.port

/** Genera refresh tokens opacos y calcula el hash con el que se almacenan. */
interface RefreshTokenGenerator {
    fun generate(): String
    fun hash(rawToken: String): String
}
