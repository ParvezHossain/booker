package com.parvez.booker.data.network

import com.parvez.booker.data.model.SignupRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
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

class SessionCoordinatorTests {

    private lateinit var mockWebServer: MockWebServer
    private lateinit var sessionCoordinator: SessionCoordinator

    @Before
    fun setUp() {
        mockWebServer = MockWebServer()
        mockWebServer.start()

        val baseUrl = mockWebServer.url("/api/").toString()
        RetrofitClient.baseUrl = baseUrl

        sessionCoordinator = SessionCoordinator(cursorStorage = null)
        RetrofitClient.sessionCoordinatorRef = sessionCoordinator
    }

    @After
    fun tearDown() {
        mockWebServer.shutdown()
        RetrofitClient.sessionCoordinatorRef = null
        RetrofitClient.accessToken = ""
    }

    @Test
    fun testSignupFollowedByLogin() = runBlocking {
        // Enqueue Signup 201
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(201)
                .setBody("""{"workspaceId":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","workspaceName":"My Library","email":"reader@example.com","plan":"FREE"}""")
        )

        // Enqueue Login 200
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"accessToken":"access_jwt_123","refreshToken":"refresh_jwt_123","tokenType":"Bearer","expiresIn":900,"refreshExpiresIn":604800}""")
        )

        // Enqueue Workspace 200
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"id":"aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa","name":"My Library","plan":"FREE","book_limit":100,"books_used":1}""")
        )

        val signupResponse = sessionCoordinator.signup(
            SignupRequest("My Library", "reader@example.com", "replace-this-password")
        )
        assertEquals("My Library", signupResponse.workspaceName)

        val workspace = sessionCoordinator.login("reader@example.com", "replace-this-password")
        assertEquals("My Library", workspace.name)
        assertTrue(sessionCoordinator.sessionState.value.isAuthenticated)
        assertEquals("access_jwt_123", RetrofitClient.accessToken)

        val r1 = mockWebServer.takeRequest()
        assertEquals("/api/auth/signup", r1.path)

        val r2 = mockWebServer.takeRequest()
        assertEquals("/api/auth/login", r2.path)

        val r3 = mockWebServer.takeRequest()
        assertEquals("/api/workspace", r3.path)
        assertEquals("Bearer access_jwt_123", r3.getHeader("Authorization"))
    }

    @Test
    fun testInvalidCredentialsLoginFailure() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"dateTime":"2026-09-29T10:00:00","status":401,"error":"Unauthorized","message":"Invalid credentials","path":"/api/auth/login"}""")
        )

        var caughtException: Exception? = null
        try {
            sessionCoordinator.login("reader@example.com", "wrong-password")
        } catch (e: Exception) {
            caughtException = e
        }

        assertNotNull(caughtException)
        assertTrue(caughtException is HttpException)
        assertEquals(401, (caughtException as HttpException).code())
        assertFalse(sessionCoordinator.sessionState.value.isAuthenticated)
        assertEquals("", RetrofitClient.accessToken)
    }

    @Test
    fun testTenSimultaneous401sSingleRefresh() = runBlocking {
        // Enqueue 1 Refresh response
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"accessToken":"new_access_token_999","refreshToken":"new_refresh_token_999","tokenType":"Bearer","expiresIn":900,"refreshExpiresIn":604800}""")
        )

        // Pre-set active session state
        sessionCoordinator.loginStateForTest("old_access_token", "old_refresh_token", "reader@example.com")

        // Execute 10 concurrent single flight refresh attempts with the old access token
        val deferreds = (1..10).map {
            async(Dispatchers.IO) {
                sessionCoordinator.performSingleFlightRefresh("old_access_token")
            }
        }

        val results = deferreds.awaitAll()

        // All 10 callers receive the same new access token
        assertEquals(10, results.size)
        results.forEach { newToken ->
            assertEquals("new_access_token_999", newToken)
        }

        // Only ONE single request to /api/auth/refresh was sent to the server
        assertEquals(1, mockWebServer.requestCount)
        val recordedReq = mockWebServer.takeRequest()
        assertEquals("/api/auth/refresh", recordedReq.path)
        assertEquals("new_access_token_999", RetrofitClient.accessToken)
        assertEquals("new_refresh_token_999", sessionCoordinator.sessionState.value.refreshToken)
    }

    @Test
    fun testTokenRotationPersistence() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody("""{"accessToken":"rotated_access_token","refreshToken":"rotated_refresh_token","tokenType":"Bearer","expiresIn":900,"refreshExpiresIn":604800}""")
        )

        sessionCoordinator.loginStateForTest("token_1", "refresh_1", "reader@example.com")

        val newToken = sessionCoordinator.performSingleFlightRefresh("token_1")
        assertEquals("rotated_access_token", newToken)
        assertEquals("rotated_access_token", RetrofitClient.accessToken)
        assertEquals("rotated_refresh_token", sessionCoordinator.sessionState.value.refreshToken)
    }

    @Test
    fun testConsumedRefreshTokenLogoutsSession() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("""{"dateTime":"2026-09-29T10:00:00","status":401,"error":"Unauthorized","message":"Refresh token expired or consumed","path":"/api/auth/refresh"}""")
        )

        sessionCoordinator.loginStateForTest("token_1", "consumed_refresh_token", "reader@example.com")

        val result = sessionCoordinator.performSingleFlightRefresh("token_1")
        assertNull(result)
        assertFalse(sessionCoordinator.sessionState.value.isAuthenticated)
        assertEquals("", RetrofitClient.accessToken)
    }

    @Test
    fun testLogoutClearsSessionAndBlocksLateCallbacks() = runBlocking {
        mockWebServer.enqueue(
            MockResponse()
                .setResponseCode(204)
        )

        sessionCoordinator.loginStateForTest("active_access_token", "active_refresh_token", "reader@example.com")
        val initialGen = sessionCoordinator.currentGeneration

        sessionCoordinator.logout()

        assertFalse(sessionCoordinator.sessionState.value.isAuthenticated)
        assertNull(sessionCoordinator.sessionState.value.accessToken)
        assertEquals("", RetrofitClient.accessToken)
        assertTrue(sessionCoordinator.currentGeneration > initialGen)

        val recordedRequest = mockWebServer.takeRequest()
        assertEquals("/api/auth/logout", recordedRequest.path)
    }

    private fun SessionCoordinator.loginStateForTest(access: String, refresh: String, email: String) {
        RetrofitClient.accessToken = access
        val field = SessionCoordinator::class.java.getDeclaredField("_sessionState")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val flow = field.get(this) as MutableStateFlow<SessionState>
        flow.value = SessionState(
            isAuthenticated = true,
            accessToken = access,
            refreshToken = refresh,
            email = email,
            sessionGeneration = 1L
        )
    }
}