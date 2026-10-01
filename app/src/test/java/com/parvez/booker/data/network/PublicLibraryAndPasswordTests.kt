package com.parvez.booker.data.network

import com.google.gson.Gson
import com.parvez.booker.data.model.ChangePasswordRequest
import com.parvez.booker.data.model.ForgotPasswordRequest
import com.parvez.booker.data.model.ResetPasswordRequest
import com.parvez.booker.data.repository.BookRepository
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.HttpException

class PublicLibraryAndPasswordTests {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var repository: BookRepository
    private val gson = Gson()

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val baseUrl = mockWebServer.url("/api/").toString()
        RetrofitClient.baseUrl = baseUrl

        repository = BookRepository(null)
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        RetrofitClient.accessToken = ""
    }

    @Test
    fun testPublicBooksFetchAndDetailLookup(): Unit = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""[{"id":50,"title":"Public Masterpiece","author":"Global Author","publishedDate":"2020","completed":false}]""")
        )

        val publicBooks = repository.getPublicBooks()
        assertEquals(1, publicBooks.size)
        assertEquals(50L, publicBooks[0].id)
        assertEquals("Public Masterpiece", publicBooks[0].title)

        val req1 = mockWebServer.takeRequest()
        assertEquals("/api/public-books", req1.path)

        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":50,"title":"Public Masterpiece","author":"Global Author","publishedDate":"2020","completed":false}""")
        )

        val bookDetail = repository.getPublicBookById(50L)
        assertEquals(50L, bookDetail.id)

        val req2 = mockWebServer.takeRequest()
        assertEquals("/api/public-books/50", req2.path)
    }

    @Test
    fun testChangePassword204Success(): Unit = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(204)
        )

        repository.changePassword("OldPassword123!", "NewPassword456!")

        val req = mockWebServer.takeRequest()
        assertEquals("/api/auth/change-password", req.path)
        assertEquals("POST", req.method)

        val bodyJson = req.body.readUtf8()
        val parsed = gson.fromJson(bodyJson, ChangePasswordRequest::class.java)
        assertEquals("OldPassword123!", parsed.currentPassword)
        assertEquals("NewPassword456!", parsed.newPassword)
    }

    @Test
    fun testForgotPassword202Message(): Unit = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(202)
                .setBody("""{"message":"Password reset instructions emailed if account exists."}""")
        )

        val res = repository.forgotPassword("user@example.com")
        assertNotNull(res)
        assertTrue(res.message!!.contains("emailed"))

        val req = mockWebServer.takeRequest()
        assertEquals("/api/auth/forgot-password", req.path)

        val parsed = gson.fromJson(req.body.readUtf8(), ForgotPasswordRequest::class.java)
        assertEquals("user@example.com", parsed.email)
    }

    @Test
    fun testResetPassword204Success(): Unit = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(204)
        )

        repository.resetPassword("reset_token_123", "NewPassword789!")

        val req = mockWebServer.takeRequest()
        assertEquals("/api/auth/reset-password", req.path)

        val parsed = gson.fromJson(req.body.readUtf8(), ResetPasswordRequest::class.java)
        assertEquals("reset_token_123", parsed.token)
        assertEquals("NewPassword789!", parsed.newPassword)
    }
}
