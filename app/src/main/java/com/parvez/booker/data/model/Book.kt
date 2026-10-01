package com.parvez.booker.data.model

/**
 * Data transfer object representing a Book returned from the backend API.
 *
 * @property id Unique numeric identifier of the book record.
 * @property title Title of the book.
 * @property author Author of the book.
 * @property publishedDate Publication date string (e.g., "2018").
 * @property description Detailed description/summary of the book.
 * @property completed Flag indicating whether the book reading is completed.
 */
data class Book(
    val id: Long? = null,
    val title: String? = null,
    val author: String? = null,
    val publishedDate: String? = null,
    val description: String? = null,
    val completed: Boolean = false
)
