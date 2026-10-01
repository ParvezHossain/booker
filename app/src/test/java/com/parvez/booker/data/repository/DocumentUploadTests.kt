package com.parvez.booker.data.repository

import com.google.gson.Gson
import com.parvez.booker.data.network.RetrofitClient
import com.parvez.booker.data.network.UploadStreamRequestBody
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import retrofit2.HttpException

class DocumentUploadTests {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var documentRepository: DocumentRepository
    private lateinit var tempFile: File
    private val gson = Gson()

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val baseUrl = mockWebServer.url("/api/").toString()
        RetrofitClient.baseUrl = baseUrl

        documentRepository = DocumentRepository(context = null)

        // Create temporary PDF file for tests
        tempFile = File.createTempFile("test_sample", ".pdf").apply {
            FileOutputStream(this).use { out ->
                out.write("%PDF-1.4 Test PDF binary content".toByteArray())
            }
        }
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        if (tempFile.exists()) {
            tempFile.delete()
        }
        RetrofitClient.accessToken = ""
    }

    @Test
    fun testSuccessfulUploadAndActiveDocumentFetch() = runBlocking {
        val docJson = """
            {
              "bookId": 1,
              "documentId": "11111111-1111-4111-8111-111111111111",
              "fileName": "sample.pdf",
              "fileSize": 33,
              "mimeType": "application/pdf",
              "pageCount": 10,
              "checksum": "aaaaa",
              "sourceType": "UPLOAD",
              "active": true,
              "createdAt": "2026-09-29T10:00:00Z"
            }
        """.trimIndent()

        // 1. POST upload response (201)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody(docJson)
        )

        // 2. GET active document response (200)
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(docJson)
        )

        val idempotencyKey = DocumentRepository.generateIdempotencyKey()
        var progressCalled = false

        val resultDoc = documentRepository.uploadDocument(
            bookId = 1L,
            idempotencyKey = idempotencyKey,
            snapshotFile = tempFile,
            fileName = "sample.pdf",
            onProgress = { sent, total ->
                progressCalled = true
                assertTrue(sent > 0)
                assertEquals(tempFile.length(), total)
            }
        )

        assertTrue(progressCalled)
        assertEquals(1L, resultDoc.bookId)
        assertEquals("11111111-1111-4111-8111-111111111111", resultDoc.documentId)
        assertEquals("sample.pdf", resultDoc.fileName)

        val req1 = mockWebServer.takeRequest()
        assertEquals("/api/books/1/document", req1.path)
        assertEquals(idempotencyKey, req1.getHeader("Idempotency-Key"))

        val req2 = mockWebServer.takeRequest()
        assertEquals("/api/books/1/document", req2.path)
    }

    @Test
    fun testExactRetryReusesSameIdempotencyKeyAndFile() = runBlocking {
        val docJson = """{"bookId":1,"documentId":"11111111-1111-4111-8111-111111111111","fileName":"sample.pdf","fileSize":33,"mimeType":"application/pdf","pageCount":10,"checksum":"a","sourceType":"UPLOAD","active":true,"createdAt":"2026-09-29T10:00:00Z"}"""

        // First attempt 503 Transient error
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setBody("""{"dateTime":"2026-09-29T10:00:00","status":503,"error":"Service Unavailable","message":"Temporary overload","path":"/api/books/1/document"}""")
        )

        // Second attempt 201 Success
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody(docJson)
        )
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(docJson)
        )

        val fixedKey = DocumentRepository.generateIdempotencyKey()

        try {
            documentRepository.uploadDocument(1L, fixedKey, tempFile, "sample.pdf") { _, _ -> }
            fail("Expected HttpException 503 on first attempt")
        } catch (e: HttpException) {
            assertEquals(503, e.code())
        }

        // Retry with EXACT SAME key and file
        val retryResult = documentRepository.uploadDocument(1L, fixedKey, tempFile, "sample.pdf") { _, _ -> }
        assertEquals("11111111-1111-4111-8111-111111111111", retryResult.documentId)

        val req1 = mockWebServer.takeRequest()
        val req2 = mockWebServer.takeRequest()

        assertEquals(fixedKey, req1.getHeader("Idempotency-Key"))
        assertEquals(fixedKey, req2.getHeader("Idempotency-Key"))
    }

    @Test
    fun testNewFileGeneratesNewIdempotencyKey() {
        val key1 = DocumentRepository.generateIdempotencyKey()
        val key2 = DocumentRepository.generateIdempotencyKey()

        assertNotNull(key1)
        assertNotNull(key2)
        assertNotEquals(key1, key2)
    }

    @Test
    fun testServerLimitErrorResponses413And415And403() = runBlocking {
        // 413 File too large
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(413)
                .setBody("""{"dateTime":"2026-09-29T10:00:00","status":413,"error":"Payload Too Large","message":"File exceeds 200MB limit","path":"/api/books/1/document"}""")
        )

        try {
            documentRepository.uploadDocument(1L, "key_413", tempFile, "large.pdf") { _, _ -> }
            fail("Expected 413")
        } catch (e: HttpException) {
            assertEquals(413, e.code())
        }

        // 415 Invalid PDF signature
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(415)
                .setBody("""{"dateTime":"2026-09-29T10:00:00","status":415,"error":"Unsupported Media Type","message":"Invalid or encrypted PDF header","path":"/api/books/1/document"}""")
        )

        try {
            documentRepository.uploadDocument(1L, "key_415", tempFile, "invalid.pdf") { _, _ -> }
            fail("Expected 415")
        } catch (e: HttpException) {
            assertEquals(415, e.code())
        }

        // 403 Storage quota exceeded
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(403)
                .setBody("""{"dateTime":"2026-09-29T10:00:00","status":403,"error":"Forbidden","message":"Workspace storage quota exceeded","path":"/api/books/1/document"}""")
        )

        try {
            documentRepository.uploadDocument(1L, "key_403", tempFile, "quota.pdf") { _, _ -> }
            fail("Expected 403")
        } catch (e: HttpException) {
            assertEquals(403, e.code())
        }
    }

    @Test
    fun testUploadStreamRequestBodyCountersWithoutMemoryInflation() {
        var progressCount = 0
        var totalBytesReported = 0L
        var bytesSentReported = 0L

        val streamBody = UploadStreamRequestBody(
            file = tempFile,
            contentType = "application/pdf".toMediaType(),
            onProgress = { sent, total ->
                progressCount++
                bytesSentReported = sent
                totalBytesReported = total
            }
        )

        assertEquals(tempFile.length(), streamBody.contentLength())

        val sink = Buffer()
        streamBody.writeTo(sink)

        assertTrue(progressCount > 0)
        assertEquals(tempFile.length(), totalBytesReported)
        assertEquals(tempFile.length(), bytesSentReported)
        assertEquals(tempFile.length(), sink.size)
    }

    @Test
    fun testSanitizeFileNameExtension() {
        val method = DocumentRepository::class.java.getDeclaredMethod("sanitizeFileName", String::class.java)
        method.isAccessible = true

        val s1 = method.invoke(documentRepository, "my_book_document") as String
        val s2 = method.invoke(documentRepository, "special#name@file.pdf") as String

        assertEquals("my_book_document.pdf", s1)
        assertEquals("special_name_file.pdf", s2)
        assertTrue(s1.endsWith(".pdf"))
        assertTrue(s2.endsWith(".pdf"))
    }
}
