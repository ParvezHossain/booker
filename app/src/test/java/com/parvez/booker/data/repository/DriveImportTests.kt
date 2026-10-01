package com.parvez.booker.data.repository

import com.parvez.booker.data.model.ImportStatus
import com.parvez.booker.data.network.RetrofitClient
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DriveImportTests {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var driveRepository: DriveImportRepository

    companion object {
        private const val BOOK_ID = 1L
        private const val FILE_ID = "drive_file_123"
        private const val IMPORT_ID = "44444444-4444-4444-8444-444444444444"
    }

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val baseUrl = mockWebServer.url("/api/").toString()
        RetrofitClient.baseUrl = baseUrl

        driveRepository = DriveImportRepository()
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        RetrofitClient.accessToken = ""
    }

    @Test
    fun testGetDriveConnectionStatus() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"connected":false}""")
        )

        val connection = driveRepository.getDriveConnection()
        assertFalse(connection.connected)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/api/integrations/google-drive/connection", recordedRequest.path)
    }

    @Test
    fun testDisconnectDrive204NoContent() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(204)
        )

        driveRepository.disconnectDrive()

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/api/integrations/google-drive/connection", recordedRequest.path)
        assertEquals("DELETE", recordedRequest.method)
    }

    @Test
    fun testInitiateDriveImportAndReplaySameKey() = runBlocking {
        val pendingStatusJson = """
            {
              "importId": "$IMPORT_ID",
              "bookId": 1,
              "fileId": "$FILE_ID",
              "status": "PENDING",
              "documentId": null,
              "message": null
            }
        """.trimIndent()

        // 1st initiation 202 Accepted
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(202)
                .setHeader("Location", "/api/books/1/document/imports/$IMPORT_ID")
                .setBody(pendingStatusJson)
        )

        // Replay with same key
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(202)
                .setHeader("Location", "/api/books/1/document/imports/$IMPORT_ID")
                .setBody(pendingStatusJson)
        )

        val idempotencyKey = DocumentRepository.generateIdempotencyKey()

        val status1 = driveRepository.initiateDriveImport(BOOK_ID, idempotencyKey, FILE_ID)
        assertEquals(IMPORT_ID, status1.importId)
        assertEquals("PENDING", status1.status)

        val status2 = driveRepository.initiateDriveImport(BOOK_ID, idempotencyKey, FILE_ID)
        assertEquals(IMPORT_ID, status2.importId)

        val req1 = mockWebServer.takeRequest()
        val req2 = mockWebServer.takeRequest()

        assertEquals(idempotencyKey, req1.getHeader("Idempotency-Key"))
        assertEquals(idempotencyKey, req2.getHeader("Idempotency-Key"))
    }

    @Test
    fun testPollImportStatusUntilCompleted() = runBlocking {
        val pendingJson = """{"importId":"$IMPORT_ID","bookId":1,"fileId":"$FILE_ID","status":"PENDING","documentId":null,"message":null}"""
        val runningJson = """{"importId":"$IMPORT_ID","bookId":1,"fileId":"$FILE_ID","status":"RUNNING","documentId":null,"message":null}"""
        val completedJson = """{"importId":"$IMPORT_ID","bookId":1,"fileId":"$FILE_ID","status":"COMPLETED","documentId":"11111111-1111-4111-8111-111111111111","message":null}"""

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(pendingJson))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(runningJson))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(completedJson))

        val polledStatuses = mutableListOf<ImportStatus>()

        val finalStatus = driveRepository.pollImportStatus(BOOK_ID, IMPORT_ID, maxAttempts = 5) { status ->
            polledStatuses.add(status)
        }

        assertEquals("COMPLETED", finalStatus.status)
        assertEquals("11111111-1111-4111-8111-111111111111", finalStatus.documentId)
        assertEquals(3, polledStatuses.size)
        assertEquals("PENDING", polledStatuses[0].status)
        assertEquals("RUNNING", polledStatuses[1].status)
        assertEquals("COMPLETED", polledStatuses[2].status)
    }

    @Test
    fun testPollImportStatusFailedState() = runBlocking {
        val failedJson = """{"importId":"$IMPORT_ID","bookId":1,"fileId":"$FILE_ID","status":"FAILED","documentId":null,"message":"Google Drive download failed"}"""

        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody(failedJson))

        val finalStatus = driveRepository.pollImportStatus(BOOK_ID, IMPORT_ID, maxAttempts = 3) { _ -> }

        assertEquals("FAILED", finalStatus.status)
        assertEquals("Google Drive download failed", finalStatus.message)
    }
}
