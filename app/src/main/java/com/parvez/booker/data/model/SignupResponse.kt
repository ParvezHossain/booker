package com.parvez.booker.data.model

/**
 * Data object returned upon successful workspace owner registration (HTTP 201).
 *
 * @property workspaceId Generated UUID string for the workspace.
 * @property workspaceName Name of the newly created workspace.
 * @property email Email of the registered workspace owner.
 * @property plan Initial plan assigned to the workspace (e.g. "FREE").
 */
data class SignupResponse(
    val workspaceId: String? = null,
    val workspaceName: String? = null,
    val email: String? = null,
    val plan: String? = null
)