package com.parvez.booker.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.parvez.booker.data.model.Book
import com.parvez.booker.data.model.BookRequest
import com.parvez.booker.data.repository.BookRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * ViewModel managing UI state and business logic for the Booker application.
 */
class BookViewModel(
    private val repository: BookRepository = BookRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(BookUiState())
    val uiState: StateFlow<BookUiState> = _uiState.asStateFlow()

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
                        isLoggingIn = false
                    )
                }
            } catch (e: Exception) {
                val errorMsg = if (e is HttpException && e.code() == 401) {
                    "Invalid username or password"
                } else {
                    e.message ?: "Failed to connect to server"
                }
                _uiState.update {
                    it.copy(
                        loginErrorMessage = errorMsg,
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
     * Refreshes book list from the backend API.
     */
    fun refreshBooks() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingBooks = true) }
            try {
                val books = repository.getBooks()
                _uiState.update { it.copy(books = books, isLoadingBooks = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoadingBooks = false) }
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
                        showAddBookDialog = false
                    )
                }
                onSuccess(newBook)
            } catch (e: Exception) {
                onError(e.message ?: "Failed to create book")
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