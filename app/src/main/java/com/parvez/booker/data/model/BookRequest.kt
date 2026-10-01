package com.parvez.booker.data.model

/**
 * Payload data class required to create a new book entry via POST request.
 *
 * @property title Title of the book (1–255 chars).
 * @property author Author of the book (1–255 chars).
 * @property publishedDate Publication date string (max 20 chars).
 * @property description Optional detailed description (max 5000 chars).
 * @property completed Initial completion reading status.
 */
data class BookRequest(
    val title: String,
    val author: String,
    val publishedDate: String,
    val description: String?,
    val completed: Boolean
)
