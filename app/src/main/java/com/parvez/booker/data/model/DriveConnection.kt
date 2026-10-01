package com.parvez.booker.data.model

/**
 * Status payload returned by GET /api/integrations/google-drive/connection.
 *
 * @property connected True if owner account has an active linked Google Drive connection.
 */
data class DriveConnection(
    val connected: Boolean
)