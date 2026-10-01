package com.parvez.booker.data.model

/**
 * Payload data class required to initiate a Google Drive PDF import.
 *
 * @property fileId Selected Google Drive file ID string.
 */
data class DriveImportRequest(
    val fileId: String
)