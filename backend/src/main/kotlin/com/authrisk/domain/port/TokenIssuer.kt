package com.authrisk.domain.port

import com.authrisk.domain.token.AccessToken
import com.authrisk.domain.user.User

/** Emite tokens de acceso de corta duración (JWT). */
interface TokenIssuer {
    fun issueAccessToken(user: User): AccessToken
}
