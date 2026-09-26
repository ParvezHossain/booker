package com.parvez.booker.data.repository

import android.content.Context
import android.content.SharedPreferences

/**
 * Persists and manages SSE stream cursors per authenticated account.
 */
class CursorStorage(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /**
     * Gets stored last successfully handled SSE event ID for the account.
     */
    fun getCursor(username: String): String? {
        val key = getKey(username)
        return prefs.getString(key, null)
    }

    /**
     * Persists last successfully handled SSE event ID for the account.
     */
    fun saveCursor(username: String, cursor: String) {
        val key = getKey(username)
        prefs.edit().putString(key, cursor).apply()
    }

    /**
     * Clears stored SSE event cursor for the account.
     */
    fun clearCursor(username: String) {
        val key = getKey(username)
        prefs.edit().remove(key).apply()
    }

    private fun getKey(username: String): String {
        val normalized = username.trim().ifEmpty { "default_user" }
        return "sse_cursor_$normalized"
    }

    companion object {
        private const val PREFS_NAME = "booker_sse_cursors"
    }
}