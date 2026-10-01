package com.parvez.booker.data.model

/**
 * Standard backend error response DTO returned for application exceptions.
 *
 * @property dateTime Server timestamp string (server-local without timezone).
 * @property status HTTP status code number (e.g., 400, 401, 403, 409).
 * @property error Error type name (e.g., "Conflict", "Forbidden").
 * @property message Detailed error explanation message from the backend.
 * @property path Requested URL endpoint path.
 */
data class ApiError(
    val dateTime: String? = null,
    val status: Int? = null,
    val error: String? = null,
    val message: String? = null,
    val path: String? = null
)