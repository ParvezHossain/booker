package com.parvez.booker.data.repository

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.ConcurrentHashMap

/**
 * Persists and manages SSE stream cursors scoped by API environment, normalized account email, and workspace ID.
 */
class CursorStorage(context: Context? = null) {

    private val prefs: SharedPreferences? = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val inMemoryFallback = ConcurrentHashMap<String, String>()

    /**
     * Gets stored last successfully handled SSE event ID for the account and workspace scope.
     */
    fun getScopedCursor(envUrl: String, email: String, workspaceId: String?): String? {
        val key = getScopedKey(envUrl, email, workspaceId)
        return prefs?.getString(key, null) ?: inMemoryFallback[key]
    }

    /**
     * Persists last successfully handled SSE event ID for the account and workspace scope.
     */
    fun saveScopedCursor(envUrl: String, email: String, workspaceId: String?, cursor: String) {
        val key = getScopedKey(envUrl, email, workspaceId)
        if (prefs != null) {
            prefs.edit().putString(key, cursor).apply()
        } else {
            inMemoryFallback[key] = cursor
        }
    }

    /**
     * Clears stored SSE event cursor for the account and workspace scope.
     */
    fun clearScopedCursor(envUrl: String, email: String, workspaceId: String?) {
        val key = getScopedKey(envUrl, email, workspaceId)
        if (prefs != null) {
            prefs.edit().remove(key).apply()
        } else {
            inMemoryFallback.remove(key)
        }
    }

    /**
     * Legacy single-key cursor access for basic auth fallback.
     */
    fun getCursor(username: String): String? {
        val key = getKey(username)
        return prefs?.getString(key, null) ?: inMemoryFallback[key]
    }

    fun saveCursor(username: String, cursor: String) {
        val key = getKey(username)
        if (prefs != null) {
            prefs.edit().putString(key, cursor).apply()
        } else {
            inMemoryFallback[key] = cursor
        }
    }

    fun clearCursor(username: String) {
        val key = getKey(username)
        if (prefs != null) {
            prefs.edit().remove(key).apply()
        } else {
            inMemoryFallback.remove(key)
        }
    }

    private fun getScopedKey(envUrl: String, email: String, workspaceId: String?): String {
        val normEnv = envUrl.trim().trimEnd('/')
        val normEmail = email.trim().lowercase().ifEmpty { "anonymous" }
        val normWorkspace = workspaceId?.trim()?.ifEmpty { "default_workspace" } ?: "default_workspace"
        return "sse_cursor_${normEnv.hashCode()}_${normEmail.hashCode()}_$normWorkspace"
    }

    private fun getKey(username: String): String {
        val normalized = username.trim().lowercase().ifEmpty { "default_user" }
        return "sse_cursor_$normalized"
    }

    companion object {
        private const val PREFS_NAME = "booker_sse_cursors"
    }
}