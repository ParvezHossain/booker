package com.parvez.booker.data.model

/**
 * Payload data class required to update reading progress position via PUT /api/books/{bookId}/reading-progress.
 *
 * @property documentId UUID string of the active document version.
 * @property currentPage Page number to save (1..totalPages).
 * @property version Expected revision counter (send 0 for initial save).
 * @property operationId Unique UUID string for request idempotency and deduplication.
 */
data class ProgressUpdate(
    val documentId: String,
    val currentPage: Int,
    val version: Long,
    val operationId: String
)