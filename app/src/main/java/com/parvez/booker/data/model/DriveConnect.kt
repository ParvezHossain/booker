package com.parvez.booker.data.model

/**
 * Authorization URL payload returned by POST /api/integrations/google-drive/connect.
 *
 * @property authorizationUrl Browser OAuth authorization URL string.
 */
data class DriveConnect(
    val authorizationUrl: String
)