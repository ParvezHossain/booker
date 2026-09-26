package com.parvez.booker.data.network

import com.parvez.booker.data.model.Book
import com.parvez.booker.data.model.BookRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST

/**
 * Retrofit interface defining backend endpoints for managing books.
 */
interface BookApi {

    /**
     * Fetches the complete list of books from the API.
     */
    @GET("books")
    suspend fun getBooks(): List<Book>

    /**
     * Creates a new book entry on the backend.
     *
     * @param request Payload containing book creation attributes.
     * @return The newly created [Book] entity from the server.
     */
    @POST("books")
    suspend fun createBook(@Body request: BookRequest): Book
}