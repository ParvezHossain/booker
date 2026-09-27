package com.parvez.booker.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.parvez.booker.data.model.Book
import com.parvez.booker.data.model.BookRequest
import com.parvez.booker.data.network.BookSseEvent
import com.parvez.booker.data.repository.BookRepository
import com.parvez.booker.ui.notification.SystemNotificationHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

/**
 * AndroidViewModel managing UI state, live SSE notifications, system notifications, and business logic for Booker.
 */
class BookViewModel(
    application: Application,
    private val repository: BookRepository
) : AndroidViewModel(application) {

    /**
     * Primary single-argument constructor required for ViewModelProvider.AndroidViewModelFactory.
     */
    constructor(application: Application) : this(
        application = application,
        repository = BookRepository(application)
    )

    private val systemNotificationHelper = SystemNotificationHelper(application)

    private val _uiState = MutableStateFlow(BookUiState())
    val uiState: StateFlow<BookUiState> = _uiState.asStateFlow()

    init {
        observeSseEvents()
    }

    private fun observeSseEvents() {
        viewModelScope.launch {
            repository.sseEvents.collect { event ->
                when (event) {
                    is BookSseEvent.BookCreated -> {
                        val newBook = event.book
                        systemNotificationHelper.showNewBookNotification(newBook)

                        _uiState.update { current ->
                            val alreadyExists = current.books.any {
                                (it.id != null && it.id == newBook.id) || (!newBook.isbn.isNullOrBlank() && it.isbn == newBook.isbn)
                            }
                            val updatedList = if (alreadyExists) {
                                current.books.map {
                                    if ((it.id != null && it.id == newBook.id) || (!newBook.isbn.isNullOrBlank() && it.isbn == newBook.isbn)) newBook else it
                                }
                            } else {
                                current.books + newBook
                            }
                            current.copy(
                                books = updatedList,
                                newBookNotification = newBook,
                                userFeedbackMessage = "New book added: ${newBook.title ?: "Untitled"}"
                            )
                        }
                    }
                    is BookSseEvent.ResyncRequired -> {
                        refreshBooks()
                    }
                    is BookSseEvent.AuthError -> {
                        logout()
                    }
                }
            }
        }
    }

    /**
     * Called when lifecycle transitions to foreground.
     */
    fun onAppForegrounded() {
        if (_uiState.value.isAuthenticated) {
            repository.resumeSse()
        }
    }

    /**
     * Called when lifecycle transitions to background.
     */
    fun onAppBackgrounded() {
        repository.pauseSse()
    }

    /**
     * Dismisses active notification banner.
     */
    fun dismissNotification() {
        _uiState.update { it.copy(newBookNotification = null) }
    }

    private fun formatErrorMessage(throwable: Throwable): String {
        return when (throwable) {
            is HttpException -> {
                when (throwable.code()) {
                    401 -> "Invalid username or password. Please try again."
                    404 -> "No matching book found on the server."
                    400 -> "Invalid request data. Please verify your input."
                    500 -> "Server internal error. Please try again later."
                    else -> "HTTP ${throwable.code()}: ${throwable.message()}"
                }
            }
            is ConnectException, is UnknownHostException -> {
                "Cannot connect to API (http://192.168.0.122:8080/api/). Check network."
            }
            is SocketTimeoutException -> {
                "Server connection timed out. Please try again."
            }
            else -> throwable.localizedMessage ?: "An unexpected error occurred."
        }
    }

    /**
     * Authenticates user credentials with basic auth against the server.
     */
    fun login(usernameInput: String, passwordInput: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoggingIn = true, loginErrorMessage = null) }
            try {
                repository.setCredentials(usernameInput, passwordInput)
                val books = repository.getBooks()
                _uiState.update {
                    it.copy(
                        books = books,
                        isAuthenticated = true,
                        showLoginDialog = false,
                        isLoggingIn = false,
                        userFeedbackMessage = null
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        loginErrorMessage = formatErrorMessage(e),
                        isLoggingIn = false
                    )
                }
            }
        }
    }

    /**
     * Logs out the user and resets stored session state.
     */
    fun logout() {
        repository.clearCredentials()
        _uiState.update {
            BookUiState(
                books = emptyList(),
                isAuthenticated = false,
                showLoginDialog = true
            )
        }
    }

    /**
     * Refreshes complete book list from the backend API.
     */
    fun refreshBooks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingBooks = true, userFeedbackMessage = null) }
            try {
                val books = repository.getBooks()
                _uiState.update { it.copy(books = books, isLoadingBooks = false, searchQuery = "") }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingBooks = false, userFeedbackMessage = formatErrorMessage(e)) }
            }
        }
    }

    /**
     * Performs an API search by title, author, or ISBN endpoint.
     */
    fun performApiSearch() {
        val query = _uiState.value.searchQuery.trim()
        val searchType = _uiState.value.searchType

        if (query.isBlank()) {
            refreshBooks()
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingBooks = true, userFeedbackMessage = null) }
            try {
                val results = when (searchType) {
                    SearchType.ISBN -> {
                        try {
                            val book = repository.getBookByIsbn(query)
                            listOf(book)
                        } catch (e: Exception) {
                            emptyList()
                        }
                    }
                    SearchType.TITLE_AUTHOR, SearchType.LOCAL -> {
                        repository.getBooks(title = query, author = query)
                    }
                }
                val msg = if (results.isEmpty()) "No books found on server for '$query'." else null
                _uiState.update { it.copy(books = results, isLoadingBooks = false, userFeedbackMessage = msg) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingBooks = false, userFeedbackMessage = formatErrorMessage(e)) }
            }
        }
    }

    /**
     * Toggles book reading completion status and updates backend/local state.
     */
    fun toggleBookCompletion(book: Book) {
        val bookId = book.id
        val newStatus = !book.completed
        val updatedRequest = BookRequest(
            isbn = book.isbn.orEmpty(),
            title = book.title.orEmpty(),
            author = book.author.orEmpty(),
            publishedDate = book.publishedDate.orEmpty(),
            description = book.description,
            completed = newStatus
        )

        // Optimistically update local UI list
        _uiState.update { state ->
            val updatedBooks = state.books.map { item ->
                if ((bookId != null && item.id == bookId) || (!book.isbn.isNullOrBlank() && item.isbn == book.isbn)) {
                    item.copy(completed = newStatus)
                } else item
            }
            state.copy(books = updatedBooks)
        }

        viewModelScope.launch {
            if (bookId != null) {
                try {
                    val syncedBook = repository.updateBook(bookId, updatedRequest)
                    _uiState.update { state ->
                        val syncedBooks = state.books.map { item ->
                            if (item.id == bookId) syncedBook else item
                        }
                        state.copy(books = syncedBooks, userFeedbackMessage = "Updated '${book.title}' status.")
                    }
                } catch (e: Exception) {
                    _uiState.update { state ->
                        state.copy(userFeedbackMessage = "Status updated locally (${if (newStatus) "Completed" else "In Progress"}).")
                    }
                }
            } else {
                _uiState.update { state ->
                    state.copy(userFeedbackMessage = "Status updated locally (${if (newStatus) "Completed" else "In Progress"}).")
                }
            }
        }
    }

    /**
     * Creates a new book on the backend API.
     */
    fun createBook(request: BookRequest, onSuccess: (Book) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val newBook = repository.createBook(request)
                _uiState.update { current ->
                    current.copy(
                        books = current.books + newBook,
                        showAddBookDialog = false,
                        userFeedbackMessage = "Book '${newBook.title}' created successfully!"
                    )
                }
                onSuccess(newBook)
            } catch (e: Exception) {
                onError(formatErrorMessage(e))
            }
        }
    }

    /**
     * Updates text search filter query.
     */
    fun updateSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
    }

    /**
     * Updates active search mode type (LOCAL, TITLE_AUTHOR, ISBN).
     */
    fun updateSearchType(type: SearchType) {
        _uiState.update { it.copy(searchType = type) }
    }

    /**
     * Updates selected status filter tab.
     */
    fun updateFilter(filter: BookFilterOption) {
        _uiState.update { it.copy(selectedFilter = filter) }
    }

    /**
     * Toggles visibility of Add Book Dialog.
     */
    fun setAddBookDialogVisible(visible: Boolean) {
        _uiState.update { it.copy(showAddBookDialog = visible) }
    }
}