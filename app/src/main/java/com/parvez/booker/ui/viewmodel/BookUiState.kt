package com.parvez.booker.ui.viewmodel

import com.parvez.booker.data.model.Book
import com.parvez.booker.data.model.ImportStatus
import com.parvez.booker.data.model.PublicLibraryBookRequest
import com.parvez.booker.data.model.ReadingSummary
import com.parvez.booker.data.model.Workspace
import com.parvez.booker.data.repository.PdfDownloadState
import com.parvez.booker.data.repository.UploadState

/**
 * Authentication dialog active tab mode.
 */
enum class AuthMode {
    SIGN_IN,
    SIGN_UP
}

/**
 * Tab selection for switching between Private Workspace Books and Global Public Library.
 */
enum class LibraryTab {
    PRIVATE_WORKSPACE,
    PUBLIC_LIBRARY
}

/**
 * Search mode options for querying the library API or local list.
 */
enum class SearchType {
    LOCAL,
    TITLE_AUTHOR,
    BOOK_ID
}

/**
 * Filter options for the library book list.
 */
enum class BookFilterOption {
    ALL,
    COMPLETED,
    IN_PROGRESS
}

/**
 * UI State data class holding screen state for Booker app.
 */
data class BookUiState(
    val books: List<Book> = emptyList(),
    val publicBooks: List<Book> = emptyList(),
    val workspace: Workspace? = null,
    val isAuthenticated: Boolean = false,
    val showLoginDialog: Boolean = true,
    val authMode: AuthMode = AuthMode.SIGN_IN,
    val selectedLibraryTab: LibraryTab = LibraryTab.PRIVATE_WORKSPACE,
    val showAddBookDialog: Boolean = false,
    val showPasswordChangeDialog: Boolean = false,
    val showForgotPasswordDialog: Boolean = false,
    val isLoggingIn: Boolean = false,
    val isSigningUp: Boolean = false,
    val isLoadingBooks: Boolean = false,
    val isLoadingPublicBooks: Boolean = false,
    val isPasswordActionInProgress: Boolean = false,
    val loginErrorMessage: String? = null,
    val signupErrorMessage: String? = null,
    val passwordActionFeedback: String? = null,
    val userFeedbackMessage: String? = null,
    val newBookNotification: Book? = null,
    val searchQuery: String = "",
    val searchType: SearchType = SearchType.LOCAL,
    val selectedFilter: BookFilterOption = BookFilterOption.ALL,
    val uploadBookId: Long? = null,
    val uploadState: UploadState = UploadState.Idle,
    val downloadBookId: Long? = null,
    val downloadState: PdfDownloadState = PdfDownloadState.Idle,
    val readingSummaries: Map<Long, ReadingSummary> = emptyMap(),
    val publicReadingSummaries: Map<Long, ReadingSummary> = emptyMap(),
    val publicBookRequests: List<PublicLibraryBookRequest> = emptyList(),
    val adminPublicBookRequests: List<PublicLibraryBookRequest> = emptyList(),
    val isLoadingBookRequests: Boolean = false,
    val isDriveConnected: Boolean = false,
    val activeDriveImportStatus: ImportStatus? = null
) {
    /**
     * True if currently logged in session is Super Admin.
     */
    val isSuperAdmin: Boolean
        get() = workspace?.id == "super_admin" || workspace?.plan == "SUPER_ADMIN"

    /**
     * Active list of books according to selected [selectedLibraryTab].
     */
    val activeBooks: List<Book>
        get() = if (selectedLibraryTab == LibraryTab.PUBLIC_LIBRARY) publicBooks else books

    /**
     * Active reading summaries map according to [selectedLibraryTab].
     */
    val activeSummaries: Map<Long, ReadingSummary>
        get() = if (selectedLibraryTab == LibraryTab.PUBLIC_LIBRARY) publicReadingSummaries else readingSummaries

    /**
     * Filtered list based on [searchQuery] and [selectedFilter].
     */
    val filteredBooks: List<Book>
        get() = activeBooks.filter { book ->
            val matchesSearch = searchQuery.isBlank() || searchType != SearchType.LOCAL ||
                    (book.title?.contains(searchQuery, ignoreCase = true) == true) ||
                    (book.author?.contains(searchQuery, ignoreCase = true) == true) ||
                    (book.id?.toString()?.contains(searchQuery) == true)

            val matchesFilter = when (selectedFilter) {
                BookFilterOption.ALL -> true
                BookFilterOption.COMPLETED -> book.completed
                BookFilterOption.IN_PROGRESS -> !book.completed
            }

            matchesSearch && matchesFilter
        }

    val totalCount: Int get() = activeBooks.size
    val completedCount: Int get() = activeBooks.count { it.completed }
    val inProgressCount: Int get() = totalCount - completedCount
}
