package com.parvez.booker.data.network

import com.google.gson.Gson
import com.parvez.booker.data.model.ApiError
import com.parvez.booker.data.model.Document
import com.parvez.booker.data.model.LoginRequest
import com.parvez.booker.data.model.ProgressUpdate
import com.parvez.booker.data.model.ReadingProgress
import com.parvez.booker.data.model.ReadingSummary
import com.parvez.booker.data.model.RefreshRequest
import com.parvez.booker.data.model.SignupRequest
import com.parvez.booker.data.model.Tokens
import com.parvez.booker.data.model.Workspace
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody
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
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ContractTests {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var api: BookApi
    private val gson = Gson()

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val baseUrl = mockWebServer.url("/api/").toString()
        RetrofitClient.baseUrl = baseUrl

        api = Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(BookApi::class.java)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
    }

    @Test
    fun testExplicitVersionZeroSerializationInProgressUpdate() {
        val update = ProgressUpdate(
            documentId = "11111111-1111-4111-8111-111111111111",
            currentPage = 1,
            version = 0L,
            operationId = "33333333-3333-4333-8333-333333333333"
        )

        val json = gson.toJson(update)
        assertTrue(json.contains("\"version\":0"))
        assertTrue(json.contains("\"currentPage\":1"))
        assertTrue(json.contains("\"documentId\":\"11111111-1111-4111-8111-111111111111\""))
        assertTrue(json.contains("\"operationId\":\"33333333-3333-4333-8333-333333333333\""))
    }

    @Test
    fun testJsonFieldNamesNullsAndLongValues() {
        val documentJson = """
            {
              "bookId": 9876543210,
              "documentId": "11111111-1111-4111-8111-111111111111",
              "fileName": "effective_java.pdf",
              "fileSize": 12345678901,
              "mimeType": "application/pdf",
              "pageCount": 144,
              "checksum": "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
              "sourceType": "UPLOAD",
              "active": true,
              "createdAt": "2026-09-29T10:00:00Z"
            }
        """.trimIndent()

        val doc = gson.fromJson(documentJson, Document::class.java)
        assertEquals(9876543210L, doc.bookId)
        assertEquals(12345678901L, doc.fileSize)
        assertEquals("11111111-1111-4111-8111-111111111111", doc.documentId)
        assertEquals("effective_java.pdf", doc.fileName)
        assertTrue(doc.active)

        val progressJson = """
            {
              "bookId": 9876543210,
              "documentId": "11111111-1111-4111-8111-111111111111",
              "currentPage": 93,
              "totalPages": 144,
              "pagesRead": 93,
              "progressPercentage": 64.58,
              "lastReadAt": null,
              "completed": false,
              "resumePage": 93,
              "version": 0
            }
        """.trimIndent()

        val progress = gson.fromJson(progressJson, ReadingProgress::class.java)
        assertEquals(9876543210L, progress.bookId)
        assertEquals(0L, progress.version)
        assertNull(progress.lastReadAt)
        assertEquals(64.58, progress.progressPercentage, 0.001)
        assertFalse(progress.completed)
    }

    @Test
    fun testCorrectUrlResolutionNoDoubleApi() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","name":"My Library","plan":"FREE","book_limit":100,"books_used":1}""")
        )

        val workspace = api.getWorkspace()
        assertEquals("My Library", workspace.name)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/api/workspace", recordedRequest.path)
        assertFalse(recordedRequest.path!!.contains("/api/api/"))
    }

    @Test
    fun testMultipartFileUploadHeaderAndPart() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"bookId":1,"documentId":"11111111-1111-4111-8111-111111111111","fileName":"book.pdf","fileSize":5,"mimeType":"application/pdf","pageCount":1,"checksum":"abc","sourceType":"UPLOAD","active":true,"createdAt":"2026-09-29T10:00:00Z"}""")
        )

        val pdfBytes = "%PDF-".toByteArray()
        val filePart = MultipartBody.Part.createFormData(
            "file",
            "book.pdf",
            pdfBytes.toRequestBody("application/pdf".toMediaType())
        )

        val doc = api.uploadDocument(1L, "22222222-2222-4222-8222-222222222222", filePart)
        assertEquals(1L, doc.bookId)
        assertEquals("book.pdf", doc.fileName)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/api/books/1/document", recordedRequest.path)
        assertEquals("22222222-2222-4222-8222-222222222222", recordedRequest.getHeader("Idempotency-Key"))
        assertTrue(recordedRequest.getHeader("Content-Type")!!.startsWith("multipart/form-data"))
    }

    @Test
    fun testBinaryStreamingAnd206RangeHeader() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", "application/pdf")
                .setHeader("Content-Range", "bytes 0-4/100")
                .setBody("%PDF-")
        )

        val response = api.getDocumentContent(1L, "11111111-1111-4111-8111-111111111111", null, "bytes=0-4")
        assertEquals(206, response.code())
        assertNotNull(response.body())
        assertEquals("%PDF-", response.body()!!.string())

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/api/books/1/document/content?documentId=11111111-1111-4111-8111-111111111111", recordedRequest.path)
        assertEquals("bytes=0-4", recordedRequest.getHeader("Range"))
    }

    @Test
    fun testEmpty204NoContentResponse() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(204)
        )

        val response = api.logout(RefreshRequest("refresh_token_123"))
        assertEquals(204, response.code())
        assertNull(response.body())

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/api/auth/logout", recordedRequest.path)
    }

    @Test
    fun testBoth409ResponseShapesOnProgressUpdate() = runBlocking {
        val staleConflictJson = """
            {
              "bookId": 1,
              "documentId": "11111111-1111-4111-8111-111111111111",
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

        val updateReq = ProgressUpdate(
            documentId = "11111111-1111-4111-8111-111111111111",
            currentPage = 93,
            version = 1L,
            operationId = "33333333-3333-4333-8333-333333333333"
        )

        val response1 = api.updateReadingProgress(1L, updateReq)
        assertEquals(409, response1.code())

        val outcome1 = RetrofitClient.parseResponse(response1)
        assertTrue(outcome1 is NetworkResult.ProgressConflictResult)
        val progressConflict = (outcome1 as NetworkResult.ProgressConflictResult).progress
        assertEquals(85, progressConflict.currentPage)
        assertEquals(3L, progressConflict.version)

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

        val response2 = api.updateReadingProgress(1L, updateReq)
        assertEquals(409, response2.code())

        val outcome2 = RetrofitClient.parseResponse(response2)
        assertTrue(outcome2 is NetworkResult.ApiErrorResult)
        val apiError = (outcome2 as NetworkResult.ApiErrorResult).error
        assertEquals("The PDF was replaced; reopen the book", apiError.message)
    }

    @Test
    fun testBatchedReadingSummariesParsing() = runBlocking {
        val summariesJson = """
            [
              {
                "bookId": 1,
                "document": {
                  "bookId": 1,
                  "documentId": "11111111-1111-4111-8111-111111111111",
                  "fileName": "book.pdf",
                  "fileSize": 1000,
                  "mimeType": "application/pdf",
                  "pageCount": 100,
                  "checksum": "abc",
                  "sourceType": "UPLOAD",
                  "active": true,
                  "createdAt": "2026-09-29T10:00:00Z"
                },
                "progress": {
                  "bookId": 1,
                  "documentId": "11111111-1111-4111-8111-111111111111",
                  "currentPage": 10,
                  "totalPages": 100,
                  "pagesRead": 10,
                  "progressPercentage": 10.0,
                  "lastReadAt": null,
                  "completed": false,
                  "resumePage": 10,
                  "version": 1
                }
              },
              {
                "bookId": 2,
                "document": null,
                "progress": null
              }
            ]
        """.trimIndent()

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(summariesJson)
        )

        val summaries = api.getReadingSummaries("1,2")
        assertEquals(2, summaries.size)

        val s1 = summaries.first { it.bookId == 1L }
        assertNotNull(s1.document)
        assertNotNull(s1.progress)

        val s2 = summaries.first { it.bookId == 2L }
        assertNull(s2.document)
        assertNull(s2.progress)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/api/books/reading-summaries?bookIds=1%2C2", recordedRequest.path)
    }
}