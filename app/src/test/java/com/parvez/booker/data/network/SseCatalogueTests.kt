package com.parvez.booker.data.network

import com.parvez.booker.data.repository.BookRepository
import com.parvez.booker.data.repository.CursorStorage
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Set

class SseCatalogueTests {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var cursorStorage: CursorStorage
    private lateinit var sseManager: BookSseManager

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val baseUrl = mockWebServer.url("/api/").toString()
        RetrofitClient.baseUrl = baseUrl

        cursorStorage = CursorStorage(null)
        sseManager = BookSseManager(null, cursorStorage)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        sseManager.stop()
        RetrofitClient.accessToken = ""
    }

    @Test
    fun testOmitBlankAuthorAndTitleFiltersInGetBooks() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[{"id":1,"title":"Effective Java","author":"Joshua Bloch","publishedDate":"2018","description":null,"completed":false}]""")
        )

        val repository = BookRepository(null)
        val books = repository.getBooks(title = "   ", author = "  ")

        assertEquals(1, books.size)
        assertEquals("Effective Java", books[0].title)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/api/books", recordedRequest.path)
        assertFalse(recordedRequest.path!!.contains("title="))
        assertFalse(recordedRequest.path!!.contains("author="))
    }

    @Test
    fun testExactAuthorAndTitleQueryParameters() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[{"id":1,"title":"Effective Java","author":"Joshua Bloch","publishedDate":"2018","description":null,"completed":false}]""")
        )

        val repository = BookRepository(null)
        val books = repository.getBooks(title = "Effective Java", author = "Joshua Bloch")

        assertEquals(1, books.size)

        val recordedRequest = mockWebServer.takeRequest()
        assertTrue(recordedRequest.path!!.contains("title=Effective%20Java") || recordedRequest.path!!.contains("title=Effective+Java"))
        assertTrue(recordedRequest.path!!.contains("author=Joshua%20Bloch") || recordedRequest.path!!.contains("author=Joshua+Bloch"))
    }

    @Test
    fun testMarkLocallyCreatedBookSuppressesDuplicateNotificationFlag() = runBlocking {
        sseManager.markLocallyCreatedBook(123L, "Effective Java")

        val field = BookSseManager::class.java.getDeclaredField("locallyCreatedBookKeys")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val set = field.get(sseManager) as Set<String>

        assertTrue(set.contains("id_123"))
        assertTrue(set.contains("title_effective java"))
    }

    @Test
    fun testBadCursor400ClearsCursorAndTriggersResync() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(400)
                .setBody("""{"dateTime":"2026-09-29T10:00:00","status":400,"error":"Bad Request","message":"Invalid or future cursor","path":"/api/books/events"}""")
        )

        sseManager.startToken("reader@example.com", "workspace_123")

        val event = sseManager.eventsFlow.first()
        assertTrue(event is BookSseEvent.ResyncRequired)
    }

    @Test
    fun testPermanentAuthFailureStopsReconnect() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"dateTime":"2026-09-29T10:00:00","status":401,"error":"Unauthorized","message":"Authentication required","path":"/api/books/events"}""")
        )

        sseManager.startToken("reader@example.com", "workspace_123")

        val event = sseManager.eventsFlow.first()
        assertTrue(event is BookSseEvent.AuthError)
    }

    @Test
    fun testAccountSwitchCancelsStreamAndClearsDedupe() = runBlocking {
        sseManager.startToken("user1@example.com", "ws_1")
        sseManager.logout()

        val field = BookSseManager::class.java.getDeclaredField("handledEventIds")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val handled = field.get(sseManager) as Set<String>

        assertTrue(handled.isEmpty())
    }
}
