package com.parvez.booker.ui.viewmodel

import com.parvez.booker.data.model.Book

/**
 * Search mode options for querying the library API or local list.
 */
enum class SearchType {
    LOCAL,
    TITLE_AUTHOR,
    ISBN
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
 *
 * @property books Total list of books fetched from server.
 * @property isAuthenticated True if credentials have been submitted successfully.
 * @property showLoginDialog True if the authentication popup should be visible.
 * @property showAddBookDialog True if the new book creation popup should be visible.
 * @property isLoggingIn True during login network request execution.
 * @property isLoadingBooks True during background book list refreshing.
 * @property loginErrorMessage Error message string if login attempt fails.
 * @property userFeedbackMessage Informational or error banner message for user feedback.
 * @property newBookNotification Newly created book entity triggering an in-app notification banner.
 * @property searchQuery Current query typed in the search bar.
 * @property searchType Current active search category (LOCAL, TITLE_AUTHOR, ISBN).
 * @property selectedFilter Currently active book filter option.
 */
data class BookUiState(
    val books: List<Book> = emptyList(),
    val isAuthenticated: Boolean = false,
    val showLoginDialog: Boolean = true,
    val showAddBookDialog: Boolean = false,
    val isLoggingIn: Boolean = false,
    val isLoadingBooks: Boolean = false,
    val loginErrorMessage: String? = null,
    val userFeedbackMessage: String? = null,
    val newBookNotification: Book? = null,
    val searchQuery: String = "",
    val searchType: SearchType = SearchType.LOCAL,
    val selectedFilter: BookFilterOption = BookFilterOption.ALL
) {
    /**
     * Filtered list based on [searchQuery] and [selectedFilter].
     */
    val filteredBooks: List<Book>
        get() = books.filter { book ->
            val matchesSearch = searchQuery.isBlank() || searchType != SearchType.LOCAL ||
                    (book.title?.contains(searchQuery, ignoreCase = true) == true) ||
                    (book.author?.contains(searchQuery, ignoreCase = true) == true) ||
                    (book.isbn?.contains(searchQuery, ignoreCase = true) == true)

            val matchesFilter = when (selectedFilter) {
                BookFilterOption.ALL -> true
                BookFilterOption.COMPLETED -> book.completed
                BookFilterOption.IN_PROGRESS -> !book.completed
            }

            matchesSearch && matchesFilter
        }

    val totalCount: Int get() = books.size
    val completedCount: Int get() = books.count { it.completed }
    val inProgressCount: Int get() = totalCount - completedCount
}