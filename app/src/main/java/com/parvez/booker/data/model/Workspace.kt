package com.parvez.booker.data.model

import com.google.gson.annotations.SerializedName

/**
 * Data transfer object representing workspace metadata and quota information returned from GET /api/workspace.
 *
 * @property id Unique UUID string identifying the workspace.
 * @property name Name of the library workspace.
 * @property plan Active subscription plan (e.g. "FREE", "PRO").
 * @property bookLimit Maximum allowed books in this workspace.
 * @property booksUsed Number of books currently used in this workspace.
 */
data class Workspace(
    val id: String? = null,
    val name: String? = null,
    val plan: String? = null,
    @SerializedName("book_limit") val bookLimit: Int = 100,
    @SerializedName("books_used") val booksUsed: Int = 0
) {
    /**
     * Calculates remaining capacity of books that can be added before reaching quota limit.
     */
    val remainingCapacity: Int
        get() = maxOf(0, bookLimit - booksUsed)
}