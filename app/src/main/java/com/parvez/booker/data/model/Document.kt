package com.parvez.booker.data.model

/**
 * Data transfer object representing PDF document metadata associated with a book.
 *
 * @property bookId Numeric ID of the associated book record.
 * @property documentId Unique UUID string identifying this document version.
 * @property fileName Sanitized original file name.
 * @property fileSize File size in bytes.
 * @property mimeType MIME content type (always "application/pdf").
 * @property pageCount Total parsed pages count.
 * @property checksum SHA-256 hex checksum string.
 * @property sourceType Origin provider source ("UPLOAD" or "GOOGLE_DRIVE").
 * @property active True if this document is the current active version.
 * @property createdAt ISO-8601 UTC timestamp string when created.
 */
data class Document(
    val bookId: Long,
    val documentId: String,
    val fileName: String,
    val fileSize: Long,
    val mimeType: String = "application/pdf",
    val pageCount: Int,
    val checksum: String,
    val sourceType: String,
    val active: Boolean,
    val createdAt: String
)