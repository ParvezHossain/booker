package com.parvez.booker.data.model

/**
 * Import status payload returned when initiating or polling a Google Drive document import job.
 *
 * @property importId Unique operation UUID string for the import job.
 * @property bookId Associated book numeric ID.
 * @property fileId Google Drive file ID string.
 * @property status Current job lifecycle state ("PENDING", "RUNNING", "COMPLETED", "FAILED").
 * @property documentId Document UUID string when completed, or null if pending/failed.
 * @property message Error or informational status message, or null if empty.
 */
data class ImportStatus(
    val importId: String,
    val bookId: Long,
    val fileId: String,
    val status: String,
    val documentId: String? = null,
    val message: String? = null
)