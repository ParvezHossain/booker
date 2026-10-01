package com.parvez.booker.data.model

/**
 * Payload data class for token rotation and logout requests via POST /api/auth/refresh and POST /api/auth/logout.
 *
 * @property refreshToken Currently active refresh token string.
 */
data class RefreshRequest(
    val refreshToken: String
)