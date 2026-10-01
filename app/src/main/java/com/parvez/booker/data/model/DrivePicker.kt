package com.parvez.booker.data.model

/**
 * Credentials payload returned by GET /api/integrations/google-drive/picker for web picker authorization.
 *
 * @property accessToken Short-lived Google access token string.
 * @property apiKey Public restricted Google API key string.
 * @property appId Google Developer Cloud Project Number string.
 */
data class DrivePicker(
    val accessToken: String,
    val apiKey: String,
    val appId: String
)