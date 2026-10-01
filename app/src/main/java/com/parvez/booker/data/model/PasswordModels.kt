package com.parvez.booker.data.model

/**
 * Request payload for POST /api/auth/change-password.
 */
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

/**
 * Request payload for POST /api/auth/forgot-password.
 */
data class ForgotPasswordRequest(
    val email: String
)

/**
 * Request payload for POST /api/auth/reset-password.
 */
data class ResetPasswordRequest(
    val token: String,
    val newPassword: String
)

/**
 * Generic message response DTO returned by endpoints like forgot-password.
 */
data class MessageResponse(
    val message: String? = null
)
