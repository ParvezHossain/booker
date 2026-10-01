package com.parvez.booker.data.model

/**
 * Combined document metadata and reading progress summary returned by GET /api/books/reading-summaries.
 *
 * @property bookId Numeric ID of the book record.
 * @property document Active PDF document metadata, or null if no document uploaded.
 * @property progress User reading progress metrics, or null if never opened/saved.
 */
data class ReadingSummary(
    val bookId: Long,
    val document: Document? = null,
    val progress: ReadingProgress? = null
)