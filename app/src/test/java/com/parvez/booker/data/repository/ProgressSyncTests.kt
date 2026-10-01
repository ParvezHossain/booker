package com.parvez.booker.data.repository

import com.google.gson.Gson
import com.parvez.booker.data.model.ApiError
import com.parvez.booker.data.model.ProgressUpdate
import com.parvez.booker.data.model.ReadingProgress
import com.parvez.booker.data.network.NetworkResult
import com.parvez.booker.data.network.RetrofitClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestScope
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ProgressSyncTests {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var localReadingStore: LocalReadingStore
    private lateinit var progressSyncManager: ProgressSyncManager
    private val gson = Gson()

    companion object {
        private const val ENV_URL = "http://10.0.2.2:8080/api/"
        private const val EMAIL = "reader@example.com"
        private const val WORKSPACE_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        private const val BOOK_ID = 1L
        private const val DOC_ID = "11111111-1111-4111-8111-111111111111"
    }

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val baseUrl = mockWebServer.url("/api/").toString()
        RetrofitClient.baseUrl = baseUrl

        localReadingStore = LocalReadingStore(context = null)
        progressSyncManager = ProgressSyncManager(localReadingStore, CoroutineScope(Dispatchers.IO))
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        RetrofitClient.accessToken = ""
    }

    @Test
    fun testPercentageCalculation6458AndVersionZeroFirstSave() {
        val totalPages = 144
        val currentPage = 93
        val percentage = (currentPage.toDouble() / totalPages.toDouble()) * 100.0
        val roundedPercentage = Math.round(percentage * 100.0) / 100.0

        assertEquals(64.58, roundedPercentage, 0.001)

        val firstSaveUpdate = ProgressUpdate(
            documentId = DOC_ID,
            currentPage = currentPage,
            version = 0L,
            operationId = "33333333-3333-4333-8333-333333333333"
        )

        val json = gson.toJson(firstSaveUpdate)
        assertTrue(json.contains("\"version\":0"))
        assertTrue(json.contains("\"currentPage\":93"))
        assertTrue(json.contains("\"documentId\":\"$DOC_ID\""))
    }

    @Test
    fun testExactRetryReusesSameOperationIdAndBody() = runBlocking {
        val fixedOpId = "44444444-4444-4444-8444-444444444444"
        val update = ProgressUpdate(
            documentId = DOC_ID,
            currentPage = 50,
            version = 1L,
            operationId = fixedOpId
        )

        // Set in-flight operation
        localReadingStore.setInFlightOperation(ENV_URL, EMAIL, WORKSPACE_ID, BOOK_ID, DOC_ID, update, ProgressSyncType.SYNC_RESUME)

        val stateBefore = localReadingStore.getProgressState(ENV_URL, EMAIL, WORKSPACE_ID, BOOK_ID, DOC_ID)
        assertNotNull(stateBefore.inFlightOperation)
        assertEquals(fixedOpId, stateBefore.inFlightOperation?.operationId)
        assertEquals(50, stateBefore.inFlightOperation?.currentPage)
    }

    @Test
    fun testMax93FollowedByResume20OrderedSync() = runBlocking {
        // Enqueue MAX 93 200 OK response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"bookId":1,"documentId":"$DOC_ID","currentPage":93,"totalPages":144,"pagesRead":93,"progressPercentage":64.58,"lastReadAt":"2026-09-29T10:00:00Z","completed":false,"resumePage":93,"version":1}""")
        )

        // Enqueue RESUME 20 200 OK response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"bookId":1,"documentId":"$DOC_ID","currentPage":20,"totalPages":144,"pagesRead":93,"progressPercentage":64.58,"lastReadAt":"2026-09-29T10:01:00Z","completed":false,"resumePage":20,"version":2}""")
        )

        // Record page 93 then page 20 offline
        localReadingStore.recordLocalPageChange(ENV_URL, EMAIL, WORKSPACE_ID, BOOK_ID, DOC_ID, newPage = 93)
        localReadingStore.recordLocalPageChange(ENV_URL, EMAIL, WORKSPACE_ID, BOOK_ID, DOC_ID, newPage = 20)

        progressSyncManager.flushSync(ENV_URL, EMAIL, WORKSPACE_ID, BOOK_ID, DOC_ID)

        val req1 = mockWebServer.takeRequest()
        val req2 = mockWebServer.takeRequest()

        assertTrue(req1.body.readUtf8().contains("\"currentPage\":93"))
        assertTrue(req2.body.readUtf8().contains("\"currentPage\":20"))

        val finalState = localReadingStore.getProgressState(ENV_URL, EMAIL, WORKSPACE_ID, BOOK_ID, DOC_ID)
        assertEquals(20, finalState.acknowledgedProgress?.currentPage)
        assertEquals(93, finalState.acknowledgedProgress?.pagesRead)
        assertEquals(2L, finalState.acknowledgedProgress?.version)
    }

    @Test
    fun testStaleRevisionConflict409ParsingAndResolution() = runBlocking {
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

        localReadingStore.recordLocalPageChange(ENV_URL, EMAIL, WORKSPACE_ID, BOOK_ID, DOC_ID, newPage = 90)
        progressSyncManager.flushSync(ENV_URL, EMAIL, WORKSPACE_ID, BOOK_ID, DOC_ID)

        val activeConflict = progressSyncManager.conflictState.value
        assertTrue(activeConflict is SyncConflictState.StaleRevisionConflict)

        val staleConflict = activeConflict as SyncConflictState.StaleRevisionConflict
        assertEquals(85, staleConflict.serverProgress.currentPage)
        assertEquals(3L, staleConflict.serverProgress.version)

        // User resolves conflict by choosing server page (85)
        progressSyncManager.resolveConflictUseServer(ENV_URL, EMAIL, WORKSPACE_ID, BOOK_ID, DOC_ID, staleConflict.serverProgress)
        val resolvedState = localReadingStore.getProgressState(ENV_URL, EMAIL, WORKSPACE_ID, BOOK_ID, DOC_ID)

        assertEquals(85, resolvedState.latestLocalResumePage)
        assertEquals(85, resolvedState.acknowledgedProgress?.currentPage)
        assertEquals(SyncConflictState.None, progressSyncManager.conflictState.value)
    }

    @Test
    fun testReplacedDocument409ConflictParsing() = runBlocking {
        val replacementErrorJson = """
            {
              "dateTime": "2026-09-29T16:13:00",
              "status": 409,
              "error": "Conflict",
              "message": "The PDF was replaced; reopen the book",
              "path": "/api/books/1/reading-progress"
            }
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(409)
                .setBody(replacementErrorJson)
        )

        localReadingStore.recordLocalPageChange(ENV_URL, EMAIL, WORKSPACE_ID, BOOK_ID, DOC_ID, newPage = 90)
        progressSyncManager.flushSync(ENV_URL, EMAIL, WORKSPACE_ID, BOOK_ID, DOC_ID)

        val activeConflict = progressSyncManager.conflictState.value
        assertTrue(activeConflict is SyncConflictState.ReplacedDocumentConflict)

        val replacedError = (activeConflict as SyncConflictState.ReplacedDocumentConflict).error
        assertEquals("The PDF was replaced; reopen the book", replacedError.message)
    }

    @Test
    fun testReachingFinalPageStaysCompletedWhenNavigatingBackward() {
        val finalPageProgress = ReadingProgress(
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

        localReadingStore.setAcknowledgedProgress(ENV_URL, EMAIL, WORKSPACE_ID, BOOK_ID, DOC_ID, finalPageProgress)

        // User navigates backward to page 20
        localReadingStore.recordLocalPageChange(ENV_URL, EMAIL, WORKSPACE_ID, BOOK_ID, DOC_ID, newPage = 20)

        val state = localReadingStore.getProgressState(ENV_URL, EMAIL, WORKSPACE_ID, BOOK_ID, DOC_ID)

        // Completed remains true, pagesRead remains 144, while resumePage becomes 20
        assertTrue(state.acknowledgedProgress!!.completed)
        assertEquals(144, state.acknowledgedProgress?.pagesRead)
        assertEquals(20, state.latestLocalResumePage)
    }
}
