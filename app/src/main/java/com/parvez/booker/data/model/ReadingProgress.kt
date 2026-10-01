package com.parvez.booker.data.model

/**
 * Data transfer object representing a user's reading position and progress metrics for a book document.
 *
 * @property bookId Numeric ID of the associated book record.
 * @property documentId UUID string of the document version.
 * @property currentPage Last viewed page number (0 if never opened, 1..totalPages when read).
 * @property totalPages Total pages count in the active PDF document.
 * @property pagesRead Maximum page number reached.
 * @property progressPercentage Server-calculated completion percentage (0.0..100.0).
 * @property lastReadAt UTC ISO timestamp string when last read, or null if never saved.
 * @property completed True if maximum page reached equals total pages.
 * @property resumePage Next page to resume reading (1..totalPages).
 * @property version Server revision counter for optimistic concurrency control.
 */
data class ReadingProgress(
    val bookId: Long,
    val documentId: String,
    val currentPage: Int,
    val totalPages: Int,
    val pagesRead: Int,
    val progressPercentage: Double,
    val lastReadAt: String? = null,
    val completed: Boolean,
    val resumePage: Int,
    val version: Long
)