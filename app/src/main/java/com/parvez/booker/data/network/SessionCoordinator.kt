package com.parvez.booker.data.network

import android.content.Context
import android.util.Log
import com.parvez.booker.data.model.LoginRequest
import com.parvez.booker.data.model.RefreshRequest
import com.parvez.booker.data.model.SignupRequest
import com.parvez.booker.data.model.SignupResponse
import com.parvez.booker.data.model.Workspace
import com.parvez.booker.data.repository.CursorStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException

/**
 * Thread-safe session state representing an active workspace user session.
 */
data class SessionState(
    val isAuthenticated: Boolean = false,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val email: String? = null,
    val workspace: Workspace? = null,
    val sessionGeneration: Long = 0L,
)

/**
 * Thread-safe, lifecycle-aware session coordinator managing JWT authentication,
 * atomic token rotation, single-flight refresh, and session isolation.
 */
class SessionCoordinator(
    context: Context? = null,
    private val cursorStorage: CursorStorage? = CursorStorage(context)
) {

    private val refreshMutex = Mutex()

    private val _sessionState = MutableStateFlow(SessionState())
    val sessionState: StateFlow<SessionState> = _sessionState.asStateFlow()

    /**
     * Currently active session generation counter.
     */
    val currentGeneration: Long
        get() = _sessionState.value.sessionGeneration

    /**
     * Registers a new workspace and owner account via POST /api/auth/signup.
     */
    suspend fun signup(request: SignupRequest): SignupResponse {
        return RetrofitClient.publicAuthApi.signup(request)
    }

    /**
     * Authenticates owner credentials via POST /api/auth/login and initializes workspace session.
     */
    suspend fun login(emailInput: String, passwordInput: String): Workspace {
        val cleanEmail = emailInput.trim().lowercase()
        val loginReq = LoginRequest(email = cleanEmail, password = passwordInput)

        val tokens = RetrofitClient.publicAuthApi.login(loginReq)
        val newGeneration = _sessionState.value.sessionGeneration + 1L

        updateTokensInternal(tokens.accessToken)

        val workspace = try {
            RetrofitClient.bookApi.getWorkspace()
        } catch (e: Exception) {
            Log.w("SessionCoordinator", "Account has no workspace or is Super Admin: ${e.message}")
            null
        }

        _sessionState.update {
            SessionState(
                isAuthenticated = true,
                accessToken = tokens.accessToken,
                refreshToken = tokens.refreshToken,
                email = cleanEmail,
                workspace = workspace,
                sessionGeneration = newGeneration
            )
        }

        Log.d("SessionCoordinator", "Login successful for $cleanEmail (Gen: $newGeneration, Workspace: ${workspace?.name ?: "None/SuperAdmin"})")
        return workspace ?: Workspace(
            id = "super_admin",
            name = "Super Admin",
            plan = "PRO",
            bookLimit = 0,
            booksUsed = 0
        )
    }

    /**
     * Atomically executes a single-flight token refresh for concurrent 401 requests.
     */
    suspend fun performSingleFlightRefresh(failedAccessToken: String?): String? = refreshMutex.withLock {
        val current = _sessionState.value

        // Check if another concurrent request already refreshed the token
        if (!current.accessToken.isNullOrEmpty() && current.accessToken != failedAccessToken) {
            Log.d("SessionCoordinator", "Token already refreshed by concurrent thread")
            return current.accessToken
        }

        val activeRefreshToken = current.refreshToken
        if (activeRefreshToken.isNullOrBlank()) {
            Log.e("SessionCoordinator", "No active refresh token available")
            logoutInternal()
            return null
        }

        return try {
            val refreshReq = RefreshRequest(refreshToken = activeRefreshToken)
            val newTokens = RetrofitClient.publicAuthApi.refresh(refreshReq)

            updateTokensInternal(newTokens.accessToken)

            _sessionState.update { state ->
                state.copy(
                    accessToken = newTokens.accessToken,
                    refreshToken = newTokens.refreshToken
                )
            }

            Log.d("SessionCoordinator", "Token rotation successful")
            newTokens.accessToken
        } catch (e: HttpException) {
            val code = e.code()
            if (code == 400 || code == 401) {
                Log.e("SessionCoordinator", "Refresh token invalid/consumed (HTTP $code). Logging out...")
                logoutInternal()
                null
            } else {
                Log.w("SessionCoordinator", "Transient refresh HTTP error: $code")
                throw e
            }
        } catch (e: IOException) {
            Log.w("SessionCoordinator", "Transient network error during refresh: ${e.message}")
            throw e
        }
    }

    /**
     * Updates workspace metadata in session state.
     */
    fun updateWorkspace(workspace: Workspace) {
        _sessionState.update { it.copy(workspace = workspace) }
    }

    /**
     * Logs out the active user session, attempts POST /api/auth/logout, and clears stored state.
     */
    suspend fun logout() {
        logoutInternal()
    }

    private suspend fun logoutInternal() {
        val current = _sessionState.value
        val activeRefreshToken = current.refreshToken
        val activeEmail = current.email ?: ""

        val nextGeneration = current.sessionGeneration + 1L
        clearSessionInternal(nextGeneration)

        if (!activeEmail.isBlank()) {
            cursorStorage?.clearCursor(activeEmail)
        }

        if (!activeRefreshToken.isNullOrBlank()) {
            try {
                RetrofitClient.publicAuthApi.logout(RefreshRequest(refreshToken = activeRefreshToken))
                Log.d("SessionCoordinator", "Server logout succeeded")
            } catch (e: Exception) {
                Log.w("SessionCoordinator", "Server logout request failed/ignored: ${e.message}")
            }
        }
    }

    private fun updateTokensInternal(access: String) {
        RetrofitClient.accessToken = access
        RetrofitClient.username = ""
        RetrofitClient.password = ""
    }

    private fun clearSessionInternal(generation: Long = _sessionState.value.sessionGeneration + 1L) {
        RetrofitClient.accessToken = ""
        RetrofitClient.username = ""
        RetrofitClient.password = ""

        _sessionState.update {
            SessionState(
                isAuthenticated = false,
                accessToken = null,
                refreshToken = null,
                email = null,
                workspace = null,
                sessionGeneration = generation
            )
        }
        Log.d("SessionCoordinator", "Session state cleared (Gen: $generation)")
    }
}