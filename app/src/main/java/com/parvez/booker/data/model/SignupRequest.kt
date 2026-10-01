package com.parvez.booker.data.model

/**
 * Payload data class required for workspace owner sign up via POST /api/auth/signup.
 *
 * @property workspaceName Non-blank workspace name (max 100 characters).
 * @property email Valid email address (max 254 characters).
 * @property password Owner account password (12–64 characters).
 */
data class SignupRequest(
    val workspaceName: String,
    val email: String,
    val password: String
)