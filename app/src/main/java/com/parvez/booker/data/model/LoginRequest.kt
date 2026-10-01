package com.parvez.booker.data.model

/**
 * Payload data class required for JWT authentication via POST /api/auth/login.
 *
 * @property email Registered account email address.
 * @property password Owner account password.
 */
data class LoginRequest(
    val email: String,
    val password: String
)