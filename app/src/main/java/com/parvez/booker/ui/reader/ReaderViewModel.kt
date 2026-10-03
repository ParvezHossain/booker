package com.parvez.booker.ui.reader

import android.app.Application
import android.graphics.Bitmap
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.parvez.booker.data.model.Document
import com.parvez.booker.data.model.ReadingProgress
import com.parvez.booker.data.network.RetrofitClient
import com.parvez.booker.data.repository.BookRepository
import com.parvez.booker.data.repository.LocalProgressState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * UI State holding PDF reader viewport, navigation position, zoom, and restoration status.
 */
data class ReaderUiState(
    val isLoading: Boolean = true,
    val bookId: Long = 0L,
    val documentId: String = "",
    val bookTitle: String = "Reading PDF",
    val fileName: String = "document.pdf",
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val currentBitmap: Bitmap? = null,
    val zoomScale: Float = 1.0f,
    val isRestored: Boolean = false,
    val isOffline: Boolean = false,
    val isUnsynced: Boolean = false,
    val isPublic: Boolean = false,
    val errorMessage: String? = null
)

/**
 * ViewModel managing PDF renderer lifecycle, saved-page restoration, off-main-thread bitmap rendering,
 * and immediate page persistence.
 */
class ReaderViewModel(
    application: Application,
    private val repository: BookRepository
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        repository = BookRepository(application)
    )

    private val _uiState = MutableStateFlow(ReaderUiState())
    val uiState: StateFlow<ReaderUiState> = _uiState.asStateFlow()

    private var pdfAdapter: PdfRendererAdapter? = null

    /**
     * Initializes PDF renderer, performs saved-page restoration, and renders initial restored page.
     */
    fun loadDocument(bookId: Long, documentId: String, initialTitle: String? = null, isPublic: Boolean = false) {
        val sessionState = repository.sessionCoordinator.sessionState.value
        val email = sessionState.email ?: "anonymous"
        val workspaceId = sessionState.workspace?.id ?: "default_workspace"
        val envUrl = RetrofitClient.baseUrl

        _uiState.update {
            it.copy(
                isLoading = true,
                bookId = bookId,
                documentId = documentId,
                bookTitle = initialTitle ?: "Reading PDF",
                isPublic = isPublic,
                errorMessage = null
            )
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Fetch fresh server reading progress for active session first
                val serverProgress: ReadingProgress? = try {
                    if (isPublic) {
                        RetrofitClient.bookApi.getPublicReadingProgress(bookId)
                    } else {
                        RetrofitClient.bookApi.getReadingProgress(bookId)
                    }
                } catch (e: Exception) {
                    null
                }

                if (serverProgress != null) {
                    repository.localReadingStore.setAcknowledgedProgress(
                        envUrl = envUrl,
                        email = email,
                        workspaceId = workspaceId,
                        bookId = bookId,
                        documentId = documentId,
                        acknowledged = serverProgress
                    )
                }

                // Query local reading state from LocalReadingStore
                val localState: LocalProgressState = repository.localReadingStore.getProgressState(
                    envUrl = envUrl,
                    email = email,
                    workspaceId = workspaceId,
                    bookId = bookId,
                    documentId = documentId
                )

                // Saved-page restoration logic
                val savedLocalResume = localState.latestLocalResumePage
                val serverResume = localState.acknowledgedProgress?.resumePage ?: 1
                val restoredPage = if (savedLocalResume > 1) {
                    savedLocalResume
                } else if (serverResume > 1) {
                    serverResume
                } else {
                    1
                }

                // Check or download verified complete cached PDF file
                var cachedPdfFile = repository.pdfCacheRepository.getCompletePdfFile(
                    envUrl = envUrl,
                    email = email,
                    workspaceId = workspaceId,
                    bookId = bookId,
                    documentId = documentId
                )

                var isOffline = false
                if (!cachedPdfFile.exists() || cachedPdfFile.length() == 0L) {
                    try {
                        val activeDoc: Document = repository.documentRepository.getActiveDocument(bookId, isPublic)
                        cachedPdfFile = repository.pdfCacheRepository.downloadPdfDocument(
                            envUrl = envUrl,
                            email = email,
                            workspaceId = workspaceId,
                            bookId = bookId,
                            document = activeDoc,
                            isPublic = isPublic,
                            onProgress = { _, _, _ -> }
                        )
                    } catch (e: Exception) {
                        isOffline = true
                        if (!cachedPdfFile.exists()) {
                            throw e
                        }
                    }
                }

                // Mark document as open to prevent cache eviction during reading
                repository.pdfCacheRepository.markDocumentOpen(documentId)

                // Instantiate PdfRendererAdapter
                val adapter = PdfRendererAdapter(cachedPdfFile)
                pdfAdapter = adapter

                val pageCount = adapter.pageCount
                val clampedPage = restoredPage.coerceIn(1, pageCount)

                // Render initial restored page
                val bitmap = adapter.renderPage(clampedPage, zoomScale = 1.0f)

                // Persist restored position immediately to local store
                repository.localReadingStore.recordLocalPageChange(
                    envUrl = envUrl,
                    email = email,
                    workspaceId = workspaceId,
                    bookId = bookId,
                    documentId = documentId,
                    newPage = clampedPage
                )

                val isUnsynced = clampedPage != (localState.acknowledgedProgress?.currentPage ?: 0)

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        currentPage = clampedPage,
                        totalPages = pageCount,
                        currentBitmap = bitmap,
                        isRestored = true,
                        isOffline = isOffline,
                        isUnsynced = isUnsynced
                    )
                }
            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Failed to open PDF document: ${e.message}")
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "Could not open PDF reader: ${e.localizedMessage ?: "File missing or corrupt."}"
                    )
                }
            }
        }
    }

    /**
     * Navigates to a specific 1-based page number and updates rendered bitmap and saved state.
     */
    fun goToPage(newPage: Int) {
        val adapter = pdfAdapter ?: return
        val current = _uiState.value
        val clampedPage = newPage.coerceIn(1, current.totalPages)

        if (clampedPage == current.currentPage && current.currentBitmap != null) {
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val bitmap = adapter.renderPage(clampedPage, zoomScale = current.zoomScale)

                val sessionState = repository.sessionCoordinator.sessionState.value
                val email = sessionState.email ?: "anonymous"
                val workspaceId = sessionState.workspace?.id ?: "default_workspace"

                // Persist local page change immediately and schedule debounced outbox sync (~1.2s)
                repository.progressSyncManager.scheduleDebouncedSync(
                    envUrl = RetrofitClient.baseUrl,
                    email = email,
                    workspaceId = workspaceId,
                    bookId = current.bookId,
                    documentId = current.documentId,
                    newPage = clampedPage,
                    isPublic = current.isPublic
                )

                _uiState.update {
                    it.copy(
                        currentPage = clampedPage,
                        currentBitmap = bitmap,
                        isUnsynced = true
                    )
                }
            } catch (e: Exception) {
                Log.e("ReaderViewModel", "Error navigating to page $clampedPage: ${e.message}")
            }
        }
    }

    fun nextPage() {
        goToPage(_uiState.value.currentPage + 1)
    }

    fun previousPage() {
        goToPage(_uiState.value.currentPage - 1)
    }

    val conflictState = repository.progressSyncManager.conflictState

    fun setZoomScale(scale: Float) {
        val clampedScale = scale.coerceIn(0.5f, 3.0f)
        if (clampedScale == _uiState.value.zoomScale) return

        _uiState.update { it.copy(zoomScale = clampedScale) }
        goToPage(_uiState.value.currentPage)
    }

    fun resolveConflictUseServer(serverProgress: ReadingProgress) {
        val current = _uiState.value
        val sessionState = repository.sessionCoordinator.sessionState.value
        val email = sessionState.email ?: "anonymous"
        val workspaceId = sessionState.workspace?.id ?: "default_workspace"

        repository.progressSyncManager.resolveConflictUseServer(
            envUrl = RetrofitClient.baseUrl,
            email = email,
            workspaceId = workspaceId,
            bookId = current.bookId,
            documentId = current.documentId,
            serverProgress = serverProgress
        )
        goToPage(serverProgress.currentPage)
    }

    fun resolveConflictUseLocal(serverRevision: Long, localPage: Int) {
        val current = _uiState.value
        val sessionState = repository.sessionCoordinator.sessionState.value
        val email = sessionState.email ?: "anonymous"
        val workspaceId = sessionState.workspace?.id ?: "default_workspace"

        repository.progressSyncManager.resolveConflictUseLocal(
            envUrl = RetrofitClient.baseUrl,
            email = email,
            workspaceId = workspaceId,
            bookId = current.bookId,
            documentId = current.documentId,
            serverRevision = serverRevision,
            localPage = localPage
        )
    }

    /**
     * Closes renderer handles, flushes pending progress outbox, and unmarks active open document ID.
     */
    fun closeReader(onClosed: (() -> Unit)? = null) {
        val current = _uiState.value
        val sessionState = repository.sessionCoordinator.sessionState.value
        val email = sessionState.email ?: "anonymous"
        val workspaceId = sessionState.workspace?.id ?: "default_workspace"

        viewModelScope.launch(Dispatchers.IO) {
            if (current.bookId > 0L && current.documentId.isNotBlank()) {
                try {
                    repository.progressSyncManager.flushSync(
                        envUrl = RetrofitClient.baseUrl,
                        email = email,
                        workspaceId = workspaceId,
                        bookId = current.bookId,
                        documentId = current.documentId,
                        isPublic = current.isPublic
                    )
                } catch (e: Exception) {
                    Log.w("ReaderViewModel", "Failed to flush sync on close: ${e.message}")
                }
                repository.pdfCacheRepository.markDocumentClosed(current.documentId)
            }
            pdfAdapter?.close()
            pdfAdapter = null
            withContext(Dispatchers.Main) {
                _uiState.update { ReaderUiState() }
                onClosed?.invoke()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        closeReader()
    }
}
