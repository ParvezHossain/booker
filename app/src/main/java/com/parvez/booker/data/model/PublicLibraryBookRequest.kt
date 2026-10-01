package com.parvez.booker.data.model

/**
 * Data transfer object representing a Public Library Book Request.
 *
 * @property id Unique UUID of the request.
 * @property title Title of the requested book.
 * @property authorName Author of the requested book.
 * @property workspaceId Workspace UUID where request originated.
 * @property requesterEmail Email address of submitter.
 * @property status Current review status: "PENDING", "ACCEPTED", "REJECTED".
 * @property bookId Associated public book numeric ID if accepted.
 * @property reviewedBy Email/Username of administrator who reviewed request.
 * @property createdAt UTC Instant timestamp when request was submitted.
 * @property reviewedAt UTC Instant timestamp when request was reviewed.
 */
data class PublicLibraryBookRequest(
    val id: String? = null,
    val title: String? = null,
    val authorName: String? = null,
    val workspaceId: String? = null,
    val requesterEmail: String? = null,
    val status: String = "PENDING",
    val bookId: Long? = null,
    val reviewedBy: String? = null,
    val createdAt: String? = null,
    val reviewedAt: String? = null
)

/**
 * Request payload for POST /api/public-book-requests.
 */
data class CreatePublicBookRequestPayload(
    val title: String,
    val authorName: String
)
