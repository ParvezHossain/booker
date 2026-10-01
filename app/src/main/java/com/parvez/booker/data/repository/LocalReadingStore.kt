package com.parvez.booker.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.parvez.booker.data.model.Book
import com.parvez.booker.data.model.Document
import com.parvez.booker.data.model.ProgressUpdate
import com.parvez.booker.data.model.ReadingProgress
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max

/**
 * Type of sync operation pending for reading progress outbox.
 */
enum class ProgressSyncType {
    SYNC_MAX,
    SYNC_RESUME
}

/**
 * Encapsulates the complete local reading state for a specific book and document.
 *
 * @property acknowledgedProgress Server-acknowledged ReadingProgress object.
 * @property latestLocalResumePage Furthest user-selected page position for resume.
 * @property latestLocalMaxPage Furthest page ever reached locally.
 * @property inFlightOperation Currently active immutable ProgressUpdate request.
 * @property inFlightType Type of in-flight operation (SYNC_MAX or SYNC_RESUME).
 * @property nextUnsentIntent Pending ProgressUpdate waiting for next network flush.
 */
data class LocalProgressState(
    val acknowledgedProgress: ReadingProgress? = null,
    val latestLocalResumePage: Int = 1,
    val latestLocalMaxPage: Int = 1,
    val inFlightOperation: ProgressUpdate? = null,
    val inFlightType: ProgressSyncType? = null,
    val nextUnsentIntent: ProgressUpdate? = null,
)

/**
 * Thread-safe, account-isolated local store for reading progress, document metadata,
 * and catalogue cache. Uses SharedPreferences with synchronous commit() for durability,
 * backed by an in-memory fallback for unit testing.
 */
class LocalReadingStore(
    context: Context? = null,
    private val gson: Gson = Gson()
) {

    private val prefs: SharedPreferences? = context?.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val inMemoryState = ConcurrentHashMap<String, String>()
    private val inMemoryCatalogue = ConcurrentHashMap<String, String>()
    private val inMemoryDocuments = ConcurrentHashMap<String, String>()

    /**
     * Simulated disk failure flag for unit testing.
     */
    var simulateDiskFailure: Boolean = false

    // --- Scoped Key Generation ---

    fun buildScopedKey(
        envUrl: String,
        email: String,
        workspaceId: String,
        bookId: Long,
        documentId: String
    ): String {
        val normEnv = envUrl.trim().trimEnd('/')
        val normEmail = email.trim().lowercase().ifEmpty { "anonymous" }
        val normWorkspace = workspaceId.trim().ifEmpty { "default_workspace" }
        val normDoc = documentId.trim()
        return "progress_${normEnv.hashCode()}_${normEmail.hashCode()}_${normWorkspace}_${bookId}_$normDoc"
    }

    fun buildCatalogueKey(envUrl: String, email: String, workspaceId: String): String {
        val normEnv = envUrl.trim().trimEnd('/')
        val normEmail = email.trim().lowercase().ifEmpty { "anonymous" }
        val normWorkspace = workspaceId.trim().ifEmpty { "default_workspace" }
        return "catalogue_${normEnv.hashCode()}_${normEmail.hashCode()}_$normWorkspace"
    }

    fun buildDocumentKey(envUrl: String, email: String, workspaceId: String, bookId: Long): String {
        val normEnv = envUrl.trim().trimEnd('/')
        val normEmail = email.trim().lowercase().ifEmpty { "anonymous" }
        val normWorkspace = workspaceId.trim().ifEmpty { "default_workspace" }
        return "doc_${normEnv.hashCode()}_${normEmail.hashCode()}_${normWorkspace}_$bookId"
    }

    // --- Reading Progress Operations ---

    /**
     * Retrieves stored LocalProgressState for specified scope.
     */
    @Synchronized
    fun getProgressState(
        envUrl: String,
        email: String,
        workspaceId: String,
        bookId: Long,
        documentId: String
    ): LocalProgressState {
        val key = buildScopedKey(envUrl, email, workspaceId, bookId, documentId)
        val json = prefs?.getString(key, null) ?: inMemoryState[key]
        return if (!json.isNullOrBlank()) {
            try {
                gson.fromJson(json, LocalProgressState::class.java) ?: LocalProgressState()
            } catch (e: Exception) {
                LocalProgressState()
            }
        } else {
            LocalProgressState()
        }
    }

    /**
     * Atomically records a local page navigation event.
     */
    @Synchronized
    fun recordLocalPageChange(
        envUrl: String,
        email: String,
        workspaceId: String,
        bookId: Long,
        documentId: String,
        newPage: Int
    ): LocalProgressState {
        if (simulateDiskFailure) {
            throw IOException("Simulated disk write failure")
        }

        val currentState = getProgressState(envUrl, email, workspaceId, bookId, documentId)
        val newMax = max(currentState.latestLocalMaxPage, newPage)

        val updatedState = currentState.copy(
            latestLocalResumePage = newPage,
            latestLocalMaxPage = newMax
        )

        saveProgressStateInternal(envUrl, email, workspaceId, bookId, documentId, updatedState)
        return updatedState
    }

    /**
     * Atomically sets or updates acknowledged server reading progress.
     */
    @Synchronized
    fun setAcknowledgedProgress(
        envUrl: String,
        email: String,
        workspaceId: String,
        bookId: Long,
        documentId: String,
        acknowledged: ReadingProgress
    ): LocalProgressState {
        if (simulateDiskFailure) {
            throw IOException("Simulated disk write failure")
        }

        val current = getProgressState(envUrl, email, workspaceId, bookId, documentId)

        // Determine resume and max pages taking server values into account
        val effectiveResume = if (current.latestLocalResumePage <= 1 && acknowledged.resumePage > 1) {
            acknowledged.resumePage
        } else {
            current.latestLocalResumePage
        }

        val effectiveMax = max(current.latestLocalMaxPage, acknowledged.pagesRead)

        val updated = current.copy(
            acknowledgedProgress = acknowledged,
            latestLocalResumePage = effectiveResume,
            latestLocalMaxPage = effectiveMax,
            inFlightOperation = null,
            inFlightType = null
        )

        saveProgressStateInternal(envUrl, email, workspaceId, bookId, documentId, updated)
        return updated
    }

    /**
     * Marks a ProgressUpdate as in-flight for an account and book.
     */
    @Synchronized
    fun setInFlightOperation(
        envUrl: String,
        email: String,
        workspaceId: String,
        bookId: Long,
        documentId: String,
        update: ProgressUpdate,
        type: ProgressSyncType
    ): LocalProgressState {
        if (simulateDiskFailure) {
            throw IOException("Simulated disk write failure")
        }

        val current = getProgressState(envUrl, email, workspaceId, bookId, documentId)
        val updated = current.copy(
            inFlightOperation = update,
            inFlightType = type
        )

        saveProgressStateInternal(envUrl, email, workspaceId, bookId, documentId, updated)
        return updated
    }

    /**
     * Clears in-flight operation state upon completion or cancellation.
     */
    @Synchronized
    fun clearInFlightOperation(
        envUrl: String,
        email: String,
        workspaceId: String,
        bookId: Long,
        documentId: String
    ): LocalProgressState {
        if (simulateDiskFailure) {
            throw IOException("Simulated disk write failure")
        }

        val current = getProgressState(envUrl, email, workspaceId, bookId, documentId)
        val updated = current.copy(
            inFlightOperation = null,
            inFlightType = null
        )

        saveProgressStateInternal(envUrl, email, workspaceId, bookId, documentId, updated)
        return updated
    }

    private fun saveProgressStateInternal(
        envUrl: String,
        email: String,
        workspaceId: String,
        bookId: Long,
        documentId: String,
        state: LocalProgressState
    ) {
        val key = buildScopedKey(envUrl, email, workspaceId, bookId, documentId)
        val json = gson.toJson(state)

        if (prefs != null) {
            val success = prefs.edit().putString(key, json).commit()
            if (!success && !simulateDiskFailure) {
                // Fallback to memory if commit returns false
                inMemoryState[key] = json
            }
        } else {
            inMemoryState[key] = json
        }
    }

    // --- Catalogue & Document Cache Operations ---

    @Synchronized
    fun saveCatalogueCache(envUrl: String, email: String, workspaceId: String, books: List<Book>) {
        if (simulateDiskFailure) throw IOException("Simulated disk write failure")
        val key = buildCatalogueKey(envUrl, email, workspaceId)
        val json = gson.toJson(books)
        if (prefs != null) {
            prefs.edit().putString(key, json).commit()
        } else {
            inMemoryCatalogue[key] = json
        }
    }

    @Synchronized
    fun getCatalogueCache(envUrl: String, email: String, workspaceId: String): List<Book> {
        val key = buildCatalogueKey(envUrl, email, workspaceId)
        val json = prefs?.getString(key, null) ?: inMemoryCatalogue[key]
        return if (!json.isNullOrBlank()) {
            try {
                gson.fromJson(json, Array<Book>::class.java).toList()
            } catch (e: Exception) {
                emptyList()
            }
        } else {
            emptyList()
        }
    }

    @Synchronized
    fun saveDocumentCache(envUrl: String, email: String, workspaceId: String, bookId: Long, document: Document) {
        if (simulateDiskFailure) throw IOException("Simulated disk write failure")
        val key = buildDocumentKey(envUrl, email, workspaceId, bookId)
        val json = gson.toJson(document)
        if (prefs != null) {
            prefs.edit().putString(key, json).commit()
        } else {
            inMemoryDocuments[key] = json
        }
    }

    @Synchronized
    fun getDocumentCache(envUrl: String, email: String, workspaceId: String, bookId: Long): Document? {
        val key = buildDocumentKey(envUrl, email, workspaceId, bookId)
        val json = prefs?.getString(key, null) ?: inMemoryDocuments[key]
        return if (!json.isNullOrBlank()) {
            try {
                gson.fromJson(json, Document::class.java)
            } catch (e: Exception) {
                null
            }
        } else null
    }

    // --- Account Switch & Data Deletion ---

    /**
     * Explicit account data purge (e.g. user requests full local clear).
     */
    @Synchronized
    fun clearAccountData(envUrl: String, email: String, workspaceId: String) {
        val catKey = buildCatalogueKey(envUrl, email, workspaceId)
        if (prefs != null) {
            val editor = prefs.edit()
            editor.remove(catKey)
            // Remove matching keys
            val allKeys = prefs.all.keys
            for (k in allKeys) {
                if (k.startsWith("progress_") || k.startsWith("doc_")) {
                    val normEmail = email.trim().lowercase()
                    if (k.contains("_${normEmail.hashCode()}_")) {
                        editor.remove(k)
                    }
                }
            }
            editor.commit()
        } else {
            inMemoryCatalogue.remove(catKey)
            val normEmail = email.trim().lowercase()
            inMemoryState.keys.removeIf { it.contains("_${normEmail.hashCode()}_") }
            inMemoryDocuments.keys.removeIf { it.contains("_${normEmail.hashCode()}_") }
        }
    }

    companion object {
        private const val PREFS_NAME = "booker_local_reading_store"
    }
}
