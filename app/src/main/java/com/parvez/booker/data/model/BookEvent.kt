package com.parvez.booker.data.model

/**
 * Data payload received from SSE event stream (e.g., book.created).
 *
 * @property eventId Unique opaque identifier string for this event.
 * @property type Event type (e.g., "book.created").
 * @property schemaVersion Schema version number.
 * @property occurredAt ISO-8601 timestamp string when event occurred.
 * @property book Embedded book entity details.
 */
data class BookEvent(
    val eventId: String? = null,
    val type: String? = null,
    val schemaVersion: Int? = null,
    val occurredAt: String? = null,
    val book: Book? = null
)