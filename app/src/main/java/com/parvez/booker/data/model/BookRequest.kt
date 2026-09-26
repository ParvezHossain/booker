package com.parvez.booker.data.model

/**
 * Payload data class required to create a new book entry via POST request.
 *
 * @property isbn International Standard Book Number (10 or 13 digits).
 * @property title Title of the book.
 * @property author Author of the book.
 * @property publishedDate Publication date string.
 * @property description Optional detailed description.
 * @property completed Initial completion reading status.
 */
data class BookRequest(
    val isbn: String,
    val title: String,
    val author: String,
    val publishedDate: String,
    val description: String?,
    val completed: Boolean
)