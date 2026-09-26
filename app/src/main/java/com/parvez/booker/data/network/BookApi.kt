package com.parvez.booker.data.network

import com.parvez.booker.data.model.Book
import com.parvez.booker.data.model.BookRequest
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path
import retrofit2.http.Query

/**
 * Retrofit interface defining backend endpoints for managing books.
 */
interface BookApi {

    /**
     * Fetches books list from the API with optional title and author filter parameters.
     */
    @GET("books")
    suspend fun getBooks(
        @Query("title") title: String? = null,
        @Query("author") author: String? = null
    ): List<Book>

    /**
     * Fetches a specific book entity by its ISBN string.
     */
    @GET("books/isbn/{isbn}")
    suspend fun getBookByIsbn(
        @Path("isbn") isbn: String
    ): Book

    /**
     * Creates a new book entry on the backend.
     *
     * @param request Payload containing book creation attributes.
     * @return The newly created [Book] entity from the server.
     */
    @POST("books")
    suspend fun createBook(@Body request: BookRequest): Book

    /**
     * Updates an existing book entity on the backend by ID.
     */
    @PUT("books/{id}")
    suspend fun updateBook(
        @Path("id") id: Long,
        @Body request: BookRequest
    ): Book
}