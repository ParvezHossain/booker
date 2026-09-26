package com.parvez.booker.data.repository

import com.parvez.booker.data.model.Book
import com.parvez.booker.data.model.BookRequest
import com.parvez.booker.data.network.RetrofitClient

/**
 * Repository layer providing a clean data abstraction for book operations.
 */
class BookRepository {

    /**
     * Sets active basic auth credentials in the network client.
     */
    fun setCredentials(usernameInput: String, passwordInput: String) {
        RetrofitClient.username = usernameInput
        RetrofitClient.password = passwordInput
    }

    /**
     * Clears stored credentials.
     */
    fun clearCredentials() {
        RetrofitClient.username = ""
        RetrofitClient.password = ""
    }

    /**
     * Fetches book list from server.
     */
    suspend fun getBooks(): List<Book> {
        return RetrofitClient.bookApi.getBooks()
    }

    /**
     * Creates a new book entry on server.
     */
    suspend fun createBook(request: BookRequest): Book {
        return RetrofitClient.bookApi.createBook(request)
    }
}