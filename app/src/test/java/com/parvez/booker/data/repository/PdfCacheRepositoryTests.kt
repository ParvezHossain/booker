package com.parvez.booker.data.repository

import com.parvez.booker.data.model.Document
import com.parvez.booker.data.network.RetrofitClient
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class PdfCacheRepositoryTests {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var pdfCacheRepository: PdfCacheRepository

    companion object {
        private const val ENV_URL = "http://10.0.2.2:8080/api/"
        private const val EMAIL = "reader@example.com"
        private const val WORKSPACE_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        private const val BOOK_ID = 1L
        private const val DOC_ID = "11111111-1111-4111-8111-111111111111"
    }

    private val samplePdfContent = "%PDF-1.4 Sample PDF Content for Booker App"
    private val samplePdfBytes = samplePdfContent.toByteArray()
    private val samplePdfSize = samplePdfBytes.size.toLong()

    private val sampleDocument = Document(
        bookId = BOOK_ID,
        documentId = DOC_ID,
        fileName = "sample.pdf",
        fileSize = samplePdfSize,
        mimeType = "application/pdf",
        pageCount = 10,
        checksum = "abc", // Mock test checksum
        sourceType = "UPLOAD",
        active = true,
        createdAt = "2026-09-29T10:00:00Z"
    )

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val baseUrl = mockWebServer.url("/api/").toString()
        RetrofitClient.baseUrl = baseUrl

        pdfCacheRepository = PdfCacheRepository(context = null)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        pdfCacheRepository.clearAllCachedPdfs(ENV_URL, EMAIL, WORKSPACE_ID)
        RetrofitClient.accessToken = ""
    }

    @Test
    fun testCompleteDownloadAndAtomicPromotion() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/pdf")
                .setHeader("Content-Length", samplePdfSize)
                .setBody(samplePdfContent)
        )

        var reportedProgress = false

        val completeFile = pdfCacheRepository.downloadPdfDocument(
            envUrl = ENV_URL,
            email = EMAIL,
            workspaceId = WORKSPACE_ID,
            bookId = BOOK_ID,
            document = sampleDocument,
            onProgress = { bytesDownloaded, totalBytes, percent ->
                reportedProgress = true
                assertTrue(bytesDownloaded > 0)
                assertEquals(samplePdfSize, totalBytes)
            }
        )

        assertTrue(reportedProgress)
        assertTrue(completeFile.exists())
        assertEquals(samplePdfSize, completeFile.length())
        assertEquals(samplePdfContent, completeFile.readText())

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/api/books/1/document/content?documentId=11111111-1111-4111-8111-111111111111", recordedRequest.path)
    }

    @Test
    fun testResumableDownload206ContentRangeAppend() = runBlocking {
        val halfLen = samplePdfBytes.size / 2
        val firstHalf = String(samplePdfBytes, 0, halfLen)
        val secondHalf = String(samplePdfBytes, halfLen, samplePdfBytes.size - halfLen)

        // Pre-create partial file with first half
        val partialFile = pdfCacheRepository.getPartialPdfFile(ENV_URL, EMAIL, WORKSPACE_ID, BOOK_ID, DOC_ID)
        FileOutputStream(partialFile).use { it.write(firstHalf.toByteArray()) }
        assertEquals(halfLen.toLong(), partialFile.length())

        // Enqueue 206 Partial Content response for remaining bytes
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(206)
                .setHeader("Content-Type", "application/pdf")
                .setHeader("Content-Range", "bytes $halfLen-${samplePdfBytes.size - 1}/$samplePdfSize")
                .setBody(secondHalf)
        )

        val completeFile = pdfCacheRepository.downloadPdfDocument(
            envUrl = ENV_URL,
            email = EMAIL,
            workspaceId = WORKSPACE_ID,
            bookId = BOOK_ID,
            document = sampleDocument,
            onProgress = { _, _, _ -> }
        )

        assertTrue(completeFile.exists())
        assertEquals(samplePdfSize, completeFile.length())
        assertEquals(samplePdfContent, completeFile.readText())

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("bytes=$halfLen-", recordedRequest.getHeader("Range"))
    }

    @Test
    fun testRangeIgnored200OKTruncatesPartialFile() = runBlocking {
        // Pre-create partial file with corrupt bytes
        val partialFile = pdfCacheRepository.getPartialPdfFile(ENV_URL, EMAIL, WORKSPACE_ID, BOOK_ID, DOC_ID)
        FileOutputStream(partialFile).use { it.write("corrupt header".toByteArray()) }

        // Server ignores Range header and returns 200 OK full file
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/pdf")
                .setBody(samplePdfContent)
        )

        val completeFile = pdfCacheRepository.downloadPdfDocument(
            envUrl = ENV_URL,
            email = EMAIL,
            workspaceId = WORKSPACE_ID,
            bookId = BOOK_ID,
            document = sampleDocument,
            onProgress = { _, _, _ -> }
        )

        assertTrue(completeFile.exists())
        assertEquals(samplePdfSize, completeFile.length())
        assertEquals(samplePdfContent, completeFile.readText())
    }

    @Test
    fun testCorruptDownloadedFileIsDeleted() = runBlocking {
        // Return incomplete / truncated body from server
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setHeader("Content-Type", "application/pdf")
                .setBody("too short")
        )

        val completeFile = pdfCacheRepository.getCompletePdfFile(ENV_URL, EMAIL, WORKSPACE_ID, BOOK_ID, DOC_ID)
        val partialFile = pdfCacheRepository.getPartialPdfFile(ENV_URL, EMAIL, WORKSPACE_ID, BOOK_ID, DOC_ID)

        try {
            pdfCacheRepository.downloadPdfDocument(
                envUrl = ENV_URL,
                email = EMAIL,
                workspaceId = WORKSPACE_ID,
                bookId = BOOK_ID,
                document = sampleDocument,
                onProgress = { _, _, _ -> }
            )
            fail("Expected IOException on size mismatch")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("size mismatch"))
        }

        assertFalse(completeFile.exists())
        assertFalse(partialFile.exists())
    }

    @Test
    fun test409ConflictTriggersReplacementError() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(409)
                .setBody("""{"dateTime":"2026-09-29T10:00:00","status":409,"error":"Conflict","message":"The PDF was replaced; reopen the book","path":"/api/books/1/document/content"}""")
        )

        try {
            pdfCacheRepository.downloadPdfDocument(
                envUrl = ENV_URL,
                email = EMAIL,
                workspaceId = WORKSPACE_ID,
                bookId = BOOK_ID,
                document = sampleDocument,
                onProgress = { _, _, _ -> }
            )
            fail("Expected IllegalStateException on 409 Conflict")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("PDF document was replaced"))
        }
    }

    @Test
    fun testOfflineReopenAndAccountIsolation() {
        val completeFile = pdfCacheRepository.getCompletePdfFile(ENV_URL, EMAIL, WORKSPACE_ID, BOOK_ID, DOC_ID)
        FileOutputStream(completeFile).use { it.write(samplePdfBytes) }

        // Matching account and workspace retrieves cached file
        val cachedFile = pdfCacheRepository.getValidCachedPdf(
            envUrl = ENV_URL,
            email = EMAIL,
            workspaceId = WORKSPACE_ID,
            bookId = BOOK_ID,
            document = sampleDocument
        )
        assertNotNull(cachedFile)
        assertEquals(samplePdfSize, cachedFile?.length())

        // Different user account in same workspace CANNOT access cached PDF file
        val wrongUserCached = pdfCacheRepository.getValidCachedPdf(
            envUrl = ENV_URL,
            email = "other_user@example.com",
            workspaceId = WORKSPACE_ID,
            bookId = BOOK_ID,
            document = sampleDocument
        )
        assertNull(wrongUserCached)
    }
}
