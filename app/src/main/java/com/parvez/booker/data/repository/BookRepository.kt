package com.parvez.booker.data.repository

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.gson.Gson
import com.parvez.booker.data.model.Book
import com.parvez.booker.data.model.BookRequest
import com.parvez.booker.data.model.ChangePasswordRequest
import com.parvez.booker.data.model.CreatePublicBookRequestPayload
import com.parvez.booker.data.model.ForgotPasswordRequest
import com.parvez.booker.data.model.MessageResponse
import com.parvez.booker.data.model.PublicLibraryBookRequest
import com.parvez.booker.data.model.ReadingSummary
import com.parvez.booker.data.model.ResetPasswordRequest
import com.parvez.booker.data.model.SignupRequest
import com.parvez.booker.data.model.SignupResponse
import com.parvez.booker.data.model.Workspace
import com.parvez.booker.data.network.BookSseEvent
import com.parvez.booker.data.network.BookSseManager
import com.parvez.booker.data.network.RetrofitClient
import com.parvez.booker.data.network.SessionCoordinator
import com.parvez.booker.data.network.UploadStreamRequestBody
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharedFlow
import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

/**
 * Repository layer providing a clean data abstraction for workspace, JWT session lifecycle,
 * book operations, public library, password management, and SSE.
 */
class BookRepository(context: Context? = null) {

    private val sseManager = BookSseManager(context)
    val sessionCoordinator = SessionCoordinator(context)
    val localReadingStore = LocalReadingStore(context)
    val documentRepository = DocumentRepository(context)
    val pdfCacheRepository = PdfCacheRepository(context)
    val progressSyncManager = ProgressSyncManager(localReadingStore, CoroutineScope(Dispatchers.IO))
    val driveImportRepository = DriveImportRepository()

    init {
        RetrofitClient.sessionCoordinatorRef = sessionCoordinator
    }

    val sseEvents: SharedFlow<BookSseEvent> = sseManager.eventsFlow

    // --- Auth & Session ---

    suspend fun signup(request: SignupRequest): SignupResponse {
        return sessionCoordinator.signup(request)
    }

    suspend fun login(emailInput: String, passwordInput: String): Workspace {
        val workspace = sessionCoordinator.login(emailInput, passwordInput)
        sseManager.startToken(emailInput.trim().lowercase(), workspace.id)
        return workspace
    }

    suspend fun getWorkspace(): Workspace {
        val workspace = RetrofitClient.bookApi.getWorkspace()
        sessionCoordinator.updateWorkspace(workspace)
        return workspace
    }

    fun pauseSse() {
        sseManager.stop()
    }

    fun resumeSse() {
        val email = sessionCoordinator.sessionState.value.email
        val workspaceId = sessionCoordinator.sessionState.value.workspace?.id
        if (!email.isNullOrBlank()) {
            sseManager.startToken(email, workspaceId)
        }
    }

    suspend fun logout() {
        sseManager.logout()
        sessionCoordinator.logout()
    }

    fun setCredentials(usernameInput: String, passwordInput: String) {
        RetrofitClient.username = usernameInput
        RetrofitClient.password = passwordInput
        sseManager.start(usernameInput, passwordInput)
    }

    fun clearCredentials() {
        sseManager.logout()
        RetrofitClient.username = ""
        RetrofitClient.password = ""
    }

    // --- Password Management (Steps 36–38) ---

    suspend fun changePassword(currentPass: String, newPass: String) {
        val response = RetrofitClient.bookApi.changePassword(ChangePasswordRequest(currentPass, newPass))
        if (!response.isSuccessful) {
            throw HttpException(response)
        }
    }

    suspend fun forgotPassword(email: String): MessageResponse {
        return RetrofitClient.publicAuthApi.forgotPassword(ForgotPasswordRequest(email.trim().lowercase()))
    }

    suspend fun resetPassword(token: String, newPass: String) {
        val response = RetrofitClient.publicAuthApi.resetPassword(ResetPasswordRequest(token.trim(), newPass))
        if (!response.isSuccessful) {
            throw HttpException(response)
        }
    }

    // --- Private Books & Catalogue ---

    suspend fun getBooks(title: String? = null, author: String? = null): List<Book> {
        val cleanTitle = title?.trim()?.ifBlank { null }
        val cleanAuthor = author?.trim()?.ifBlank { null }
        val activeEmail = sessionCoordinator.sessionState.value.email
        val activeWorkspaceId = sessionCoordinator.sessionState.value.workspace?.id

        return try {
            val books = RetrofitClient.bookApi.getBooks(title = cleanTitle, author = cleanAuthor)
            if (!activeEmail.isNullOrBlank() && !activeWorkspaceId.isNullOrBlank() && cleanTitle == null && cleanAuthor == null) {
                localReadingStore.saveCatalogueCache(RetrofitClient.baseUrl, activeEmail, activeWorkspaceId, books)
            }
            books
        } catch (e: Exception) {
            if (!activeEmail.isNullOrBlank() && !activeWorkspaceId.isNullOrBlank()) {
                val cached = localReadingStore.getCatalogueCache(RetrofitClient.baseUrl, activeEmail, activeWorkspaceId)
                if (cached.isNotEmpty()) {
                    return cached.filter { book ->
                        val matchesTitle = cleanTitle == null || (book.title?.contains(cleanTitle, ignoreCase = true) == true)
                        val matchesAuthor = cleanAuthor == null || (book.author?.contains(cleanAuthor, ignoreCase = true) == true)
                        matchesTitle && matchesAuthor
                    }
                }
            }
            throw e
        }
    }

    suspend fun getBookById(bookId: Long): Book {
        return RetrofitClient.bookApi.getBookById(bookId)
    }

    suspend fun createBook(request: BookRequest): Book {
        val createdBook = RetrofitClient.bookApi.createBook(request)
        sseManager.markLocallyCreatedBook(createdBook.id, createdBook.title)
        return createdBook
    }

    suspend fun fetchReadingSummaries(bookIds: List<Long>): Map<Long, ReadingSummary> {
        val uniqueIds = bookIds.distinct().filter { it > 0L }
        if (uniqueIds.isEmpty()) return emptyMap()

        val chunks = uniqueIds.chunked(100)
        val resultMap = mutableMapOf<Long, ReadingSummary>()

        for (chunk in chunks) {
            try {
                val queryParam = chunk.joinToString(",")
                val summaries = RetrofitClient.bookApi.getReadingSummaries(queryParam)
                for (summary in summaries) {
                    resultMap[summary.bookId] = summary
                }
            } catch (e: Exception) {
                Log.w("BookRepository", "Failed to fetch summary chunk: ${e.message}")
            }
        }

        return resultMap
    }

    // --- Public Library (Steps 24–35) ---

    suspend fun getPublicBooks(title: String? = null, author: String? = null): List<Book> {
        val cleanTitle = title?.trim()?.ifBlank { null }
        val cleanAuthor = author?.trim()?.ifBlank { null }
        return RetrofitClient.bookApi.getPublicBooks(title = cleanTitle, author = cleanAuthor)
    }

    suspend fun getPublicBookById(bookId: Long): Book {
        return RetrofitClient.bookApi.getPublicBookById(bookId)
    }

    suspend fun fetchPublicReadingSummaries(bookIds: List<Long>): Map<Long, ReadingSummary> {
        val uniqueIds = bookIds.distinct().filter { it > 0L }
        if (uniqueIds.isEmpty()) return emptyMap()

        val chunks = uniqueIds.chunked(100)
        val resultMap = mutableMapOf<Long, ReadingSummary>()

        for (chunk in chunks) {
            try {
                val queryParam = chunk.joinToString(",")
                val summaries = RetrofitClient.bookApi.getPublicReadingSummaries(queryParam)
                for (summary in summaries) {
                    resultMap[summary.bookId] = summary
                }
            } catch (e: Exception) {
                Log.w("BookRepository", "Failed to fetch public summary chunk: ${e.message}")
            }
        }

        return resultMap
    }

    // --- Public Library Book Requests ---

    suspend fun submitPublicBookRequest(title: String, authorName: String): PublicLibraryBookRequest {
        val payload = CreatePublicBookRequestPayload(
            title = title.trim(),
            authorName = authorName.trim()
        )
        return RetrofitClient.bookApi.createPublicBookRequest(payload)
    }

    suspend fun getPublicBookRequests(): List<PublicLibraryBookRequest> {
        return RetrofitClient.bookApi.getPublicBookRequests()
    }

    suspend fun getAdminPublicBookRequests(status: String? = null): List<PublicLibraryBookRequest> {
        return RetrofitClient.bookApi.getAdminPublicBookRequests(status)
    }

    suspend fun rejectPublicBookRequest(requestId: String): PublicLibraryBookRequest {
        return RetrofitClient.bookApi.rejectPublicBookRequest(requestId)
    }

    suspend fun acceptPublicBookRequest(
        requestId: String,
        context: Context,
        uri: Uri,
        publishedDate: String,
        description: String?,
        completed: Boolean,
        onProgress: (Long, Long) -> Unit = { _, _ -> }
    ): PublicLibraryBookRequest {
        val uploadId = DocumentRepository.generateIdempotencyKey()
        val snapshotFile = documentRepository.stagePrivateSnapshot(context, uri, uploadId)
        try {
            val gson = Gson()
            val metadataObj = mapOf(
                "publishedDate" to publishedDate.trim(),
                "description" to description?.trim()?.ifBlank { null },
                "completed" to completed
            )
            val metadataJson = gson.toJson(metadataObj)
            val metadataBody = metadataJson.toRequestBody("application/json".toMediaType())

            val metadata = documentRepository.queryUriMetadata(context, uri)
            val pdfMediaType = "application/pdf".toMediaType()
            val streamBody = UploadStreamRequestBody(
                file = snapshotFile,
                contentType = pdfMediaType,
                onProgress = onProgress
            )
            val filePart = MultipartBody.Part.createFormData("file", metadata.fileName, streamBody)

            return RetrofitClient.bookApi.acceptPublicBookRequest(requestId, filePart, metadataBody)
        } finally {
            documentRepository.cleanupSnapshot(context, uploadId)
        }
    }
}
