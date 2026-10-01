package com.parvez.booker.data.model

/**
 * Authentication tokens payload returned upon successful login or token refresh.
 *
 * @property accessToken Short-lived JWT access token string.
 * @property refreshToken Long-lived refresh token string.
 * @property tokenType Authorization header scheme prefix (e.g. "Bearer").
 * @property expiresIn Access token lifetime duration in seconds.
 * @property refreshExpiresIn Refresh token lifetime duration in seconds.
 */
data class Tokens(
    val accessToken: String,
    val refreshToken: String,
    val tokenType: String = "Bearer",
    val expiresIn: Long,
    val refreshExpiresIn: Long
)