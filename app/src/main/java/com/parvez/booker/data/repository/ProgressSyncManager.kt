package com.parvez.booker.data.repository

import android.util.Log
import com.parvez.booker.data.model.ApiError
import com.parvez.booker.data.model.ProgressUpdate
import com.parvez.booker.data.model.ReadingProgress
import com.parvez.booker.data.network.NetworkResult
import com.parvez.booker.data.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID

/**
 * Represents current status of progress sync and conflict resolution.
 */
sealed class SyncConflictState {
    data object None : SyncConflictState()
    data class StaleRevisionConflict(
        val serverProgress: ReadingProgress,
        val localPage: Int,
    ) : SyncConflictState()
    data class ReplacedDocumentConflict(
        val error: ApiError
    ) : SyncConflictState()
}

/**
 * Manager handling debounced reading progress outbox sync, 1.2s coalescing,
 * ordered MAX then RESUME syncing, exact uncertain retries, and 409 CAS conflict resolution.
 */
class ProgressSyncManager(
    private val localReadingStore: LocalReadingStore,
    private val scope: CoroutineScope
) {

    private val syncMutex = Mutex()
    private var debounceJob: Job? = null

    private val _conflictState = MutableStateFlow<SyncConflictState>(SyncConflictState.None)
    val conflictState: StateFlow<SyncConflictState> = _conflictState.asStateFlow()

    /**
     * Schedules a debounced sync (~1.2s delay) for page navigation events.
     */
    fun scheduleDebouncedSync(
        envUrl: String,
        email: String,
        workspaceId: String,
        bookId: Long,
        documentId: String,
        newPage: Int,
        isPublic: Boolean = false
    ) {
        // Record page change in local store immediately
        localReadingStore.recordLocalPageChange(envUrl, email, workspaceId, bookId, documentId, newPage)

        debounceJob?.cancel()
        debounceJob = scope.launch(Dispatchers.IO) {
            delay(DEBOUNCE_DELAY_MS)
            flushSync(envUrl, email, workspaceId, bookId, documentId, isPublic)
        }
    }

    /**
     * Immediately flushes pending progress outbox sync.
     */
    suspend fun flushSync(
        envUrl: String,
        email: String,
        workspaceId: String,
        bookId: Long,
        documentId: String,
        isPublic: Boolean = false
    ) {
        syncMutex.withLock {
            executeOrderedSync(envUrl, email, workspaceId, bookId, documentId, isPublic)
        }
    }

    private suspend fun executeOrderedSync(
        envUrl: String,
        email: String,
        workspaceId: String,
        bookId: Long,
        documentId: String,
        isPublic: Boolean = false
    ) {
        val state = localReadingStore.getProgressState(envUrl, email, workspaceId, bookId, documentId)
        val ack = state.acknowledgedProgress

        val currentVersion = ack?.version ?: 0L
        val serverPagesRead = ack?.pagesRead ?: 0

        // Step 1: Sync MAX page first if local furthest page exceeds server pagesRead
        if (state.latestLocalMaxPage > serverPagesRead) {
            val maxUpdate = state.inFlightOperation ?: ProgressUpdate(
                documentId = documentId,
                currentPage = state.latestLocalMaxPage,
                version = currentVersion,
                operationId = UUID.randomUUID().toString()
            )

            localReadingStore.setInFlightOperation(
                envUrl, email, workspaceId, bookId, documentId,
                maxUpdate, ProgressSyncType.SYNC_MAX
            )

            val outcome = performPutRequest(bookId, maxUpdate, isPublic)
            val continueSync = handleSyncOutcome(envUrl, email, workspaceId, bookId, documentId, maxUpdate, outcome)
            if (!continueSync) return
        }

        // Re-read updated state after MAX sync
        val updatedState = localReadingStore.getProgressState(envUrl, email, workspaceId, bookId, documentId)
        val updatedAck = updatedState.acknowledgedProgress
        val nextVersion = updatedAck?.version ?: currentVersion

        // Step 2: Sync RESUME page if local resume page differs from server currentPage
        if (updatedState.latestLocalResumePage != (updatedAck?.currentPage ?: 0)) {
            val resumeUpdate = ProgressUpdate(
                documentId = documentId,
                currentPage = updatedState.latestLocalResumePage,
                version = nextVersion,
                operationId = UUID.randomUUID().toString()
            )

            localReadingStore.setInFlightOperation(
                envUrl, email, workspaceId, bookId, documentId,
                resumeUpdate, ProgressSyncType.SYNC_RESUME
            )

            val outcome = performPutRequest(bookId, resumeUpdate, isPublic)
            handleSyncOutcome(envUrl, email, workspaceId, bookId, documentId, resumeUpdate, outcome)
        }
    }

    private suspend fun performPutRequest(
        bookId: Long,
        update: ProgressUpdate,
        isPublic: Boolean = false
    ): NetworkResult<ReadingProgress> {
        return try {
            val response = if (isPublic) {
                RetrofitClient.bookApi.updatePublicReadingProgress(bookId, update)
            } else {
                RetrofitClient.bookApi.updateReadingProgress(bookId, update)
            }
            RetrofitClient.parseResponse(response)
        } catch (e: Exception) {
            RetrofitClient.parseHttpException(e)
        }
    }

    private fun handleSyncOutcome(
        envUrl: String,
        email: String,
        workspaceId: String,
        bookId: Long,
        documentId: String,
        sentUpdate: ProgressUpdate,
        outcome: NetworkResult<ReadingProgress>
    ): Boolean {
        return when (outcome) {
            is NetworkResult.Success -> {
                val serverProgress = outcome.data
                localReadingStore.setAcknowledgedProgress(envUrl, email, workspaceId, bookId, documentId, serverProgress)
                localReadingStore.clearInFlightOperation(envUrl, email, workspaceId, bookId, documentId)
                _conflictState.update { SyncConflictState.None }
                true
            }

            is NetworkResult.ProgressConflictResult -> {
                val conflictProgress = outcome.progress
                localReadingStore.setAcknowledgedProgress(envUrl, email, workspaceId, bookId, documentId, conflictProgress)
                localReadingStore.clearInFlightOperation(envUrl, email, workspaceId, bookId, documentId)

                // Stale revision conflict: prompt user or offer resolution
                _conflictState.update {
                    SyncConflictState.StaleRevisionConflict(
                        serverProgress = conflictProgress,
                        localPage = sentUpdate.currentPage
                    )
                }
                false
            }

            is NetworkResult.ApiErrorResult -> {
                val apiError = outcome.error
                localReadingStore.clearInFlightOperation(envUrl, email, workspaceId, bookId, documentId)
                if (outcome.statusCode == 409) {
                    _conflictState.update { SyncConflictState.ReplacedDocumentConflict(apiError) }
                }
                false
            }

            is NetworkResult.UnauthorizedResult -> {
                localReadingStore.clearInFlightOperation(envUrl, email, workspaceId, bookId, documentId)
                false
            }

            is NetworkResult.RetryableErrorResult, is NetworkResult.UnknownErrorResult -> {
                // Keep in-flight operation and exact operationId for retry
                Log.w("ProgressSyncManager", "Uncertain network failure during progress sync; keeping operation for retry.")
                false
            }
        }
    }

    /**
     * Conflict choice resolution: User chooses server page. Discards local intent.
     */
    fun resolveConflictUseServer(
        envUrl: String,
        email: String,
        workspaceId: String,
        bookId: Long,
        documentId: String,
        serverProgress: ReadingProgress
    ) {
        localReadingStore.setAcknowledgedProgress(envUrl, email, workspaceId, bookId, documentId, serverProgress)
        localReadingStore.recordLocalPageChange(envUrl, email, workspaceId, bookId, documentId, serverProgress.currentPage)
        _conflictState.update { SyncConflictState.None }
    }

    /**
     * Conflict choice resolution: User chooses local page. Sends new operation using returned revision.
     */
    fun resolveConflictUseLocal(
        envUrl: String,
        email: String,
        workspaceId: String,
        bookId: Long,
        documentId: String,
        serverRevision: Long,
        localPage: Int
    ) {
        _conflictState.update { SyncConflictState.None }
        scope.launch(Dispatchers.IO) {
            val newUpdate = ProgressUpdate(
                documentId = documentId,
                currentPage = localPage,
                version = serverRevision,
                operationId = UUID.randomUUID().toString()
            )

            localReadingStore.setInFlightOperation(
                envUrl, email, workspaceId, bookId, documentId,
                newUpdate, ProgressSyncType.SYNC_RESUME
            )

            val outcome = performPutRequest(bookId, newUpdate)
            handleSyncOutcome(envUrl, email, workspaceId, bookId, documentId, newUpdate, outcome)
        }
    }

    fun dismissConflict() {
        _conflictState.update { SyncConflictState.None }
    }

    companion object {
        const val DEBOUNCE_DELAY_MS = 1200L
    }
}
