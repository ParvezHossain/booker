package com.parvez.booker.data.network

import com.parvez.booker.data.model.Book
import com.parvez.booker.data.model.BookRequest
import com.parvez.booker.data.model.ChangePasswordRequest
import com.parvez.booker.data.model.CreatePublicBookRequestPayload
import com.parvez.booker.data.model.Document
import com.parvez.booker.data.model.DriveConnect
import com.parvez.booker.data.model.DriveConnection
import com.parvez.booker.data.model.DriveImportRequest
import com.parvez.booker.data.model.DrivePicker
import com.parvez.booker.data.model.ForgotPasswordRequest
import com.parvez.booker.data.model.ImportStatus
import com.parvez.booker.data.model.LoginRequest
import com.parvez.booker.data.model.MessageResponse
import com.parvez.booker.data.model.ProgressUpdate
import com.parvez.booker.data.model.PublicLibraryBookRequest
import com.parvez.booker.data.model.ReadingProgress
import com.parvez.booker.data.model.ReadingSummary
import com.parvez.booker.data.model.RefreshRequest
import com.parvez.booker.data.model.ResetPasswordRequest
import com.parvez.booker.data.model.SignupRequest
import com.parvez.booker.data.model.SignupResponse
import com.parvez.booker.data.model.Tokens
import com.parvez.booker.data.model.Workspace
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.HEAD
import retrofit2.http.Header
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Part
import retrofit2.http.Path
import retrofit2.http.Query
import retrofit2.http.Streaming

/**
 * Retrofit interface defining all 38 Booker API routes matching the updated backend guidelines.
 */
interface BookApi {

    // --- Authentication Routes ---

    @POST("auth/signup")
    suspend fun signup(
        @Body request: SignupRequest
    ): SignupResponse

    @POST("auth/login")
    suspend fun login(
        @Body request: LoginRequest
    ): Tokens

    @POST("auth/refresh")
    suspend fun refresh(
        @Body request: RefreshRequest
    ): Tokens

    @POST("auth/logout")
    suspend fun logout(
        @Body request: RefreshRequest
    ): Response<Unit>

    // --- Password Management Routes (Steps 36–38) ---

    @POST("auth/change-password")
    suspend fun changePassword(
        @Body request: ChangePasswordRequest
    ): Response<Unit>

    @POST("auth/forgot-password")
    suspend fun forgotPassword(
        @Body request: ForgotPasswordRequest
    ): MessageResponse

    @POST("auth/reset-password")
    suspend fun resetPassword(
        @Body request: ResetPasswordRequest
    ): Response<Unit>

    // --- Workspace & Private Catalogue Routes ---

    @GET("workspace")
    suspend fun getWorkspace(): Workspace

    @GET("books")
    suspend fun getBooks(
        @Query("author") author: String? = null,
        @Query("title") title: String? = null
    ): List<Book>

    @POST("books")
    suspend fun createBook(
        @Body request: BookRequest
    ): Book

    @GET("books/{bookId}")
    suspend fun getBookById(
        @Path("bookId") bookId: Long
    ): Book

    // --- Private Document Routes ---

    @Multipart
    @POST("books/{bookId}/document")
    suspend fun uploadDocument(
        @Path("bookId") bookId: Long,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Part file: MultipartBody.Part
    ): Document

    @GET("books/{bookId}/document")
    suspend fun getDocument(
        @Path("bookId") bookId: Long
    ): Document

    @Streaming
    @GET("books/{bookId}/document/content")
    suspend fun getDocumentContent(
        @Path("bookId") bookId: Long,
        @Query("documentId") documentId: String? = null,
        @Query("download") download: Boolean? = null,
        @Header("Range") range: String? = null
    ): Response<ResponseBody>

    @HEAD("books/{bookId}/document/content")
    suspend fun headDocumentContent(
        @Path("bookId") bookId: Long,
        @Query("documentId") documentId: String? = null,
        @Query("download") download: Boolean? = null
    ): Response<Void>

    // --- Private Reading Progress & Summaries Routes ---

    @GET("books/{bookId}/reading-progress")
    suspend fun getReadingProgress(
        @Path("bookId") bookId: Long
    ): ReadingProgress

    @PUT("books/{bookId}/reading-progress")
    suspend fun updateReadingProgress(
        @Path("bookId") bookId: Long,
        @Body request: ProgressUpdate
    ): Response<ReadingProgress>

    @GET("books/reading-summaries")
    suspend fun getReadingSummaries(
        @Query("bookIds") bookIds: String
    ): List<ReadingSummary>

    // --- Public Library Routes (Steps 24–35) ---

    @GET("public-books")
    suspend fun getPublicBooks(
        @Query("author") author: String? = null,
        @Query("title") title: String? = null
    ): List<Book>

    @GET("public-books/{bookId}")
    suspend fun getPublicBookById(
        @Path("bookId") bookId: Long
    ): Book

    @POST("public-books")
    suspend fun createPublicBook(
        @Body request: BookRequest
    ): Book

    @PUT("public-books/{bookId}")
    suspend fun updatePublicBook(
        @Path("bookId") bookId: Long,
        @Body request: BookRequest
    ): Book

    @DELETE("public-books/{bookId}")
    suspend fun deletePublicBook(
        @Path("bookId") bookId: Long
    ): Response<Unit>

    @Multipart
    @POST("public-books/{bookId}/document")
    suspend fun uploadPublicDocument(
        @Path("bookId") bookId: Long,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Part file: MultipartBody.Part
    ): Document

    @GET("public-books/{bookId}/document")
    suspend fun getPublicDocument(
        @Path("bookId") bookId: Long
    ): Document

    @Streaming
    @GET("public-books/{bookId}/document/content")
    suspend fun getPublicDocumentContent(
        @Path("bookId") bookId: Long,
        @Query("documentId") documentId: String? = null,
        @Query("download") download: Boolean? = null,
        @Header("Range") range: String? = null
    ): Response<ResponseBody>

    @HEAD("public-books/{bookId}/document/content")
    suspend fun headPublicDocumentContent(
        @Path("bookId") bookId: Long,
        @Query("documentId") documentId: String? = null,
        @Query("download") download: Boolean? = null
    ): Response<Void>

    @GET("public-books/{bookId}/reading-progress")
    suspend fun getPublicReadingProgress(
        @Path("bookId") bookId: Long
    ): ReadingProgress

    @PUT("public-books/{bookId}/reading-progress")
    suspend fun updatePublicReadingProgress(
        @Path("bookId") bookId: Long,
        @Body request: ProgressUpdate
    ): Response<ReadingProgress>

    @GET("public-books/reading-summaries")
    suspend fun getPublicReadingSummaries(
        @Query("bookIds") bookIds: String
    ): List<ReadingSummary>

    // --- Public Library Book Requests Routes (43 Total Operations) ---

    @POST("public-book-requests")
    suspend fun createPublicBookRequest(
        @Body request: CreatePublicBookRequestPayload
    ): PublicLibraryBookRequest

    @GET("public-book-requests")
    suspend fun getPublicBookRequests(): List<PublicLibraryBookRequest>

    @GET("admin/public-book-requests")
    suspend fun getAdminPublicBookRequests(
        @Query("status") status: String? = null
    ): List<PublicLibraryBookRequest>

    @POST("admin/public-book-requests/{requestId}/reject")
    suspend fun rejectPublicBookRequest(
        @Path("requestId") requestId: String
    ): PublicLibraryBookRequest

    @Multipart
    @POST("admin/public-book-requests/{requestId}/accept")
    suspend fun acceptPublicBookRequest(
        @Path("requestId") requestId: String,
        @Part file: MultipartBody.Part,
        @Part("metadata") metadata: RequestBody
    ): PublicLibraryBookRequest

    // --- Google Drive Integration Routes ---

    @POST("integrations/google-drive/connect")
    suspend fun connectDrive(): DriveConnect

    @GET("integrations/google-drive/connection")
    suspend fun getDriveConnection(): DriveConnection

    @DELETE("integrations/google-drive/connection")
    suspend fun disconnectDrive(): Response<Unit>

    @GET("integrations/google-drive/picker")
    suspend fun getDrivePicker(): DrivePicker

    @POST("books/{bookId}/document/imports/google-drive")
    suspend fun importDrivePdf(
        @Path("bookId") bookId: Long,
        @Header("Idempotency-Key") idempotencyKey: String,
        @Body request: DriveImportRequest
    ): ImportStatus

    @GET("books/{bookId}/document/imports/{importId}")
    suspend fun getDriveImport(
        @Path("bookId") bookId: Long,
        @Path("importId") importId: String
    ): ImportStatus
}
