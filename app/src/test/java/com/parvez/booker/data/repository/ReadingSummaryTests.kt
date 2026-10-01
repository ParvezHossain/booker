package com.parvez.booker.data.repository

import com.parvez.booker.data.model.Workspace
import com.parvez.booker.data.network.RetrofitClient
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ReadingSummaryTests {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: BookRepository

    companion object {
        private const val SAMPLE_SUMMARIES_JSON = """
            [
              {
                "bookId": 1,
                "document": {
                  "bookId": 1,
                  "documentId": "11111111-1111-4111-8111-111111111111",
                  "fileName": "sample.pdf",
                  "fileSize": 1000,
                  "mimeType": "application/pdf",
                  "pageCount": 144,
                  "checksum": "abc",
                  "sourceType": "UPLOAD",
                  "active": true,
                  "createdAt": "2026-09-29T10:00:00Z"
                },
                "progress": {
                  "bookId": 1,
                  "documentId": "11111111-1111-4111-8111-111111111111",
                  "currentPage": 93,
                  "totalPages": 144,
                  "pagesRead": 93,
                  "progressPercentage": 64.58,
                  "lastReadAt": "2026-09-29T10:00:00Z",
                  "completed": false,
                  "resumePage": 93,
                  "version": 1
                }
              },
              {
                "bookId": 2,
                "document": null,
                "progress": null
              }
            ]
        """
    }

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val baseUrl = mockWebServer.url("/api/").toString()
        RetrofitClient.baseUrl = baseUrl

        repository = BookRepository(context = null)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        RetrofitClient.accessToken = ""
    }

    @Test
    fun testMixedMissingAndPresentDocumentSummaries() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(SAMPLE_SUMMARIES_JSON)
        )

        val summariesMap = repository.fetchReadingSummaries(listOf(1L, 2L))

        assertEquals(2, summariesMap.size)

        val s1 = summariesMap[1L]
        assertNotNull(s1)
        assertNotNull(s1?.document)
        assertNotNull(s1?.progress)
        assertEquals(93, s1?.progress?.currentPage)
        assertEquals(64.58, s1?.progress?.progressPercentage!!, 0.001)

        val s2 = summariesMap[2L]
        assertNotNull(s2)
        assertNull(s2?.document)
        assertNull(s2?.progress)
    }

    @Test
    fun testMoreThan100BookIdsChunking() = runBlocking {
        val bookIds250 = (1L..250L).toList()

        // Enqueue 3 responses for 3 chunks of size 100, 100, 50
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("[]"))
        mockWebServer.enqueue(MockResponse().setResponseCode(200).setBody("[]"))

        repository.fetchReadingSummaries(bookIds250)

        assertEquals(3, mockWebServer.requestCount)

        val req1 = mockWebServer.takeRequest()
        val req2 = mockWebServer.takeRequest()
        val req3 = mockWebServer.takeRequest()

        assertTrue(req1.path!!.contains("bookIds="))
        assertTrue(req2.path!!.contains("bookIds="))
        assertTrue(req3.path!!.contains("bookIds="))
    }

    @Test
    fun testSummaryNetworkErrorIsolationLeavesCatalogueIntact() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(503)
                .setBody("""{"dateTime":"2026-09-29T10:00:00","status":503,"error":"Service Unavailable","message":"Error","path":"/api/books/reading-summaries"}""")
        )

        val summariesMap = repository.fetchReadingSummaries(listOf(1L, 2L))

        // Summary fetch fails gracefully with empty map without throwing exception
        assertTrue(summariesMap.isEmpty())
    }

    @Test
    fun testRemainingBookCapacityCalculation() {
        val workspace = Workspace(
            id = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
            name = "My Library",
            plan = "FREE",
            bookLimit = 100,
            booksUsed = 10
        )

        assertEquals(90, workspace.remainingCapacity)
    }
}
