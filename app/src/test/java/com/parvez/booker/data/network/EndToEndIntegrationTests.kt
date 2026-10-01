package com.parvez.booker.data.network

import com.google.gson.Gson
import com.parvez.booker.data.model.BookRequest
import com.parvez.booker.data.model.Document
import com.parvez.booker.data.model.ReadingProgress
import com.parvez.booker.data.model.SignupRequest
import com.parvez.booker.data.repository.BookRepository
import com.parvez.booker.data.repository.LocalReadingStore
import com.parvez.booker.data.repository.PdfCacheRepository
import com.parvez.booker.data.repository.ProgressSyncManager
import com.parvez.booker.data.repository.SyncConflictState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalCoroutinesApi::class)
class EndToEndIntegrationTests {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: BookRepository
    private lateinit var localReadingStore: LocalReadingStore
    private lateinit var pdfCacheRepository: PdfCacheRepository
    private lateinit var progressSyncManager: ProgressSyncManager
    private val gson = Gson()

    companion object {
        private const val ENV_URL = "http://10.0.2.2:8080/api/"
        private const val EMAIL_A = "usera@example.com"
        private const val EMAIL_B = "userb@example.com"
        private const val WORKSPACE_1 = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        private const val WORKSPACE_2 = "bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb"
        private const val BOOK_ID = 1L
        private const val DOC_ID = "11111111-1111-4111-8111-111111111111"
    }

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val baseUrl = mockWebServer.url("/api/").toString()
        RetrofitClient.baseUrl = baseUrl

        repository = BookRepository(context = null)
        localReadingStore = repository.localReadingStore
        pdfCacheRepository = repository.pdfCacheRepository
        progressSyncManager = repository.progressSyncManager
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        pdfCacheRepository.clearAllCachedPdfs(ENV_URL, EMAIL_A, WORKSPACE_1)
        pdfCacheRepository.clearAllCachedPdfs(ENV_URL, EMAIL_B, WORKSPACE_1)
        RetrofitClient.accessToken = ""
    }

    @Test
    fun testSignupLoginRefreshLogoutMatrix() = runBlocking {
        // 1. Signup 201
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"workspaceId":"$WORKSPACE_1","workspaceName":"My Library","email":"$EMAIL_A","plan":"FREE"}""")
        )

        // 2. Login 200
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"accessToken":"access_jwt_123","refreshToken":"refresh_jwt_123","tokenType":"Bearer","expiresIn":900,"refreshExpiresIn":604800}""")
        )

        // 3. Workspace 200
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":"$WORKSPACE_1","name":"My Library","plan":"FREE","book_limit":100,"books_used":1}""")
        )

        val signupRes = repository.signup(SignupRequest("My Library", EMAIL_A, "password-123456"))
        assertEquals("My Library", signupRes.workspaceName)

        val workspace = repository.login(EMAIL_A, "password-123456")
        assertEquals(WORKSPACE_1, workspace.id)
        assertTrue(repository.sessionCoordinator.sessionState.value.isAuthenticated)

        // 4. Logout 204
        mockWebServer.enqueue(MockResponse().setResponseCode(204))
        repository.logout()

        assertFalse(repository.sessionCoordinator.sessionState.value.isAuthenticated)
    }

    @Test
    fun testBookQuotaAndDuplicateHandling(): Unit = runBlocking {
        // Quota 403
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("""{"dateTime":"2026-09-29T10:00:00","status":403,"error":"Forbidden","message":"Workspace book limit reached","path":"/api/books"}""")
        )

        val req = BookRequest("Effective Java", "Joshua Bloch", "2018", null, false)

        try {
            repository.createBook(req)
        } catch (e: HttpException) {
            assertEquals(403, e.code())
        }

        // Duplicate Author/Title pair 409
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(409)
                .setBody("""{"dateTime":"2026-09-29T10:00:00","status":409,"error":"Conflict","message":"Book with title 'Effective Java' and author 'Joshua Bloch' already exists","path":"/api/books"}""")
        )

        try {
            repository.createBook(req)
        } catch (e: HttpException) {
            assertEquals(409, e.code())
        }
    }

    @Test
    fun testRangeDownloadAndIntegrityCheck() = runBlocking {
        val pdfContent = "%PDF-1.4 E2E Sample PDF File Content"
        val pdfBytes = pdfContent.toByteArray()
        val doc = Document(
            bookId = BOOK_ID,
            documentId = DOC_ID,
            fileName = "sample.pdf",
            fileSize = pdfBytes.size.toLong(),
            mimeType = "application/pdf",
            pageCount = 10,
            checksum = "abc",
            sourceType = "UPLOAD",
            active = true,
            createdAt = "2026-09-29T10:00:00Z"
        )

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/pdf")
                .setHeader("Content-Length", pdfBytes.size.toLong())
                .setBody(pdfContent)
        )

        val downloadedFile = pdfCacheRepository.downloadPdfDocument(
            envUrl = ENV_URL,
            email = EMAIL_A,
            workspaceId = WORKSPACE_1,
            bookId = BOOK_ID,
            document = doc,
            onProgress = { _, _, _ -> }
        )

        assertTrue(downloadedFile.exists())
        assertEquals(pdfBytes.size.toLong(), downloadedFile.length())
        assertEquals(pdfContent, downloadedFile.readText())
    }

    @Test
    fun testSavedPageRestorationAndBackwardNavigationCompletion() = runBlocking {
        // User reaches page 144 (100% completion)
        val finalProgress = ReadingProgress(
            bookId = BOOK_ID,
            documentId = DOC_ID,
            currentPage = 144,
            totalPages = 144,
            pagesRead = 144,
            progressPercentage = 100.0,
            lastReadAt = "2026-09-29T10:00:00Z",
            completed = true,
            resumePage = 144,
            version = 1L
        )

        localReadingStore.setAcknowledgedProgress(ENV_URL, EMAIL_A, WORKSPACE_1, BOOK_ID, DOC_ID, finalProgress)

        // User navigates backward to page 20
        localReadingStore.recordLocalPageChange(ENV_URL, EMAIL_A, WORKSPACE_1, BOOK_ID, DOC_ID, newPage = 20)

        val state = localReadingStore.getProgressState(ENV_URL, EMAIL_A, WORKSPACE_1, BOOK_ID, DOC_ID)

        assertEquals(20, state.latestLocalResumePage)
        assertEquals(144, state.latestLocalMaxPage)
        assertTrue(state.acknowledgedProgress!!.completed)
        assertEquals(144, state.acknowledgedProgress?.pagesRead)
    }

    @Test
    fun testMultiDeviceConflictResolutionChoice() = runBlocking {
        val staleConflictJson = """
            {
              "bookId": 1,
              "documentId": "$DOC_ID",
              "currentPage": 85,
              "totalPages": 144,
              "pagesRead": 93,
              "progressPercentage": 64.58,
              "lastReadAt": "2026-09-29T10:12:00Z",
              "completed": false,
              "resumePage": 85,
              "version": 3
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(409)
                .setBody(staleConflictJson)
        )

        localReadingStore.recordLocalPageChange(ENV_URL, EMAIL_A, WORKSPACE_1, BOOK_ID, DOC_ID, newPage = 90)
        progressSyncManager.flushSync(ENV_URL, EMAIL_A, WORKSPACE_1, BOOK_ID, DOC_ID)

        val activeConflict = progressSyncManager.conflictState.value
        assertTrue(activeConflict is SyncConflictState.StaleRevisionConflict)

        val staleConflict = activeConflict as SyncConflictState.StaleRevisionConflict
        assertEquals(85, staleConflict.serverProgress.currentPage)

        // Resolve conflict by choosing server page (85)
        progressSyncManager.resolveConflictUseServer(ENV_URL, EMAIL_A, WORKSPACE_1, BOOK_ID, DOC_ID, staleConflict.serverProgress)
        val resolvedState = localReadingStore.getProgressState(ENV_URL, EMAIL_A, WORKSPACE_1, BOOK_ID, DOC_ID)

        assertEquals(85, resolvedState.latestLocalResumePage)
        assertEquals(SyncConflictState.None, progressSyncManager.conflictState.value)
    }

    @Test
    fun testCrossAccountAndWorkspaceIsolation() {
        // User A in Workspace 1
        localReadingStore.recordLocalPageChange(ENV_URL, EMAIL_A, WORKSPACE_1, BOOK_ID, DOC_ID, newPage = 93)

        // User B in Workspace 1 (same book)
        localReadingStore.recordLocalPageChange(ENV_URL, EMAIL_B, WORKSPACE_1, BOOK_ID, DOC_ID, newPage = 12)

        // User A in Workspace 2 (same book)
        localReadingStore.recordLocalPageChange(ENV_URL, EMAIL_A, WORKSPACE_2, BOOK_ID, DOC_ID, newPage = 50)

        val stateA1 = localReadingStore.getProgressState(ENV_URL, EMAIL_A, WORKSPACE_1, BOOK_ID, DOC_ID)
        val stateB1 = localReadingStore.getProgressState(ENV_URL, EMAIL_B, WORKSPACE_1, BOOK_ID, DOC_ID)
        val stateA2 = localReadingStore.getProgressState(ENV_URL, EMAIL_A, WORKSPACE_2, BOOK_ID, DOC_ID)

        assertEquals(93, stateA1.latestLocalResumePage)
        assertEquals(12, stateB1.latestLocalResumePage)
        assertEquals(50, stateA2.latestLocalResumePage)
    }
}
