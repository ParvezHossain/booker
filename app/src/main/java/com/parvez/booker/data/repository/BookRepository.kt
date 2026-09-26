package com.parvez.booker.data.repository

import android.content.Context
import com.parvez.booker.data.model.Book
import com.parvez.booker.data.model.BookRequest
import com.parvez.booker.data.network.BookSseEvent
import com.parvez.booker.data.network.BookSseManager
import com.parvez.booker.data.network.RetrofitClient
import kotlinx.coroutines.flow.SharedFlow

/**
 * Repository layer providing a clean data abstraction for book operations and live SSE notifications.
 */
class BookRepository(context: Context) {

    private val sseManager = BookSseManager(context)

    val sseEvents: SharedFlow<BookSseEvent> = sseManager.eventsFlow

    /**
     * Sets active basic auth credentials in the network client and starts SSE manager.
     */
    fun setCredentials(usernameInput: String, passwordInput: String) {
        RetrofitClient.username = usernameInput
        RetrofitClient.password = passwordInput
        sseManager.start(usernameInput, passwordInput)
    }

    /**
     * Pauses live SSE connection (e.g. app backgrounded).
     */
    fun pauseSse() {
        sseManager.stop()
    }

    /**
     * Resumes live SSE connection using stored credentials and cursor.
     */
    fun resumeSse() {
        if (RetrofitClient.username.isNotEmpty() && RetrofitClient.password.isNotEmpty()) {
            sseManager.start(RetrofitClient.username, RetrofitClient.password)
        }
    }

    /**
     * Clears stored credentials and closes SSE connection on logout.
     */
    fun clearCredentials() {
        sseManager.logout()
        RetrofitClient.username = ""
        RetrofitClient.password = ""
    }

    /**
     * Fetches complete book list or filters by title/author from server.
     */
    suspend fun getBooks(title: String? = null, author: String? = null): List<Book> {
        return RetrofitClient.bookApi.getBooks(title = title, author = author)
    }

    /**
     * Fetches book by exact ISBN number from server.
     */
    suspend fun getBookByIsbn(isbn: String): Book {
        return RetrofitClient.bookApi.getBookByIsbn(isbn)
    }

    /**
     * Creates a new book entry on server.
     */
    suspend fun createBook(request: BookRequest): Book {
        return RetrofitClient.bookApi.createBook(request)
    }

    /**
     * Updates an existing book entry on server.
     */
    suspend fun updateBook(id: Long, request: BookRequest): Book {
        return RetrofitClient.bookApi.updateBook(id, request)
    }
}