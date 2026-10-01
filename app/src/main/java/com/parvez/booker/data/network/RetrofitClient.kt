package com.parvez.booker.data.network

import com.google.gson.Gson
import com.parvez.booker.data.model.ApiError
import com.parvez.booker.data.model.ReadingProgress
import kotlinx.coroutines.runBlocking
import okhttp3.Authenticator
import okhttp3.Credentials
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.HttpException
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * Singleton managing public and authenticated Retrofit API clients, automatic single-flight token refresh,
 * and typed error body parsing.
 */
object RetrofitClient {

    /**
     * Default base URL pointing to host server IP (http://192.168.0.122:8080/api/).
     */
    const val DEFAULT_BASE_URL = "http://192.168.0.122:8080/api/"

    val gson: Gson = Gson()

    /**
     * Configurable API base URL per environment (normalized with trailing slash once).
     */
    var baseUrl: String = DEFAULT_BASE_URL
        set(value) {
            val formatted = if (!value.endsWith("/")) "$value/" else value
            field = formatted
            rebuildClients()
        }

    /**
     * Active Bearer access token string.
     */
    var accessToken: String = ""

    /**
     * Legacy/fallback Basic auth username.
     */
    var username: String = ""

    /**
     * Legacy/fallback Basic auth password.
     */
    var password: String = ""

    /**
     * Reference to SessionCoordinator for single-flight token refresh handling.
     */
    var sessionCoordinatorRef: SessionCoordinator? = null

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.NONE
    }

    /**
     * Interceptor attaching Bearer access token or legacy Basic Auth credentials to protected requests.
     */
    private val authInterceptor = Interceptor { chain ->
        val requestBuilder = chain.request().newBuilder()

        when {
            accessToken.isNotEmpty() -> {
                requestBuilder.header("Authorization", "Bearer $accessToken")
            }
            username.isNotEmpty() || password.isNotEmpty() -> {
                requestBuilder.header("Authorization", Credentials.basic(username, password))
            }
        }

        chain.proceed(requestBuilder.build())
    }

    /**
     * OkHttp Authenticator handling single-flight token refresh on 401 Unauthorized responses.
     */
    private val tokenAuthenticator = object : Authenticator {
        override fun authenticate(route: Route?, response: Response): Request? {
            val request = response.request

            // Attach Bearer token only if targeting the configured API origin
            if (!request.url.toString().startsWith(baseUrl)) {
                return null
            }

            // Cap retries to prevent infinite refresh loops
            if (responseCount(response) >= 2) {
                return null
            }

            val failedToken = accessToken
            val coordinator = sessionCoordinatorRef ?: return null

            return try {
                runBlocking {
                    val newToken = coordinator.performSingleFlightRefresh(failedToken)
                    if (!newToken.isNullOrBlank()) {
                        request.newBuilder()
                            .header("Authorization", "Bearer $newToken")
                            .build()
                    } else {
                        null
                    }
                }
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun responseCount(response: Response): Int {
        var result = 1
        var prior = response.priorResponse
        while (prior != null) {
            result++
            prior = prior.priorResponse
        }
        return result
    }

    /**
     * Public OkHttpClient without authorization headers (used for signup, login, refresh, logout).
     */
    val publicOkHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(loggingInterceptor)
        .build()

    /**
     * Authenticated OkHttpClient injecting dynamic Bearer/Basic headers and handling token refresh.
     */
    val authenticatedOkHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(authInterceptor)
        .authenticator(tokenAuthenticator)
        .addInterceptor(loggingInterceptor)
        .build()

    private var _publicAuthApi: BookApi = createPublicApiInstance()
    private var _authenticatedBookApi: BookApi = createAuthenticatedApiInstance()

    /**
     * Public Retrofit client for unauthenticated routes.
     */
    val publicAuthApi: BookApi
        get() = _publicAuthApi

    /**
     * Authenticated Retrofit client for protected API endpoints.
     */
    val bookApi: BookApi
        get() = _authenticatedBookApi

    private fun rebuildClients() {
        _publicAuthApi = createPublicApiInstance()
        _authenticatedBookApi = createAuthenticatedApiInstance()
    }

    private fun createPublicApiInstance(): BookApi {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(publicOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(BookApi::class.java)
    }

    private fun createAuthenticatedApiInstance(): BookApi {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(authenticatedOkHttpClient)
            .addConverterFactory(GsonConverterFactory.create(gson))
            .build()
            .create(BookApi::class.java)
    }

    /**
     * Decodes and routes network responses or error bodies into typed [NetworkResult] outcomes.
     */
    fun <T> parseResponse(response: retrofit2.Response<T>): NetworkResult<T> {
        val statusCode = response.code()

        if (response.isSuccessful) {
            val body = response.body()
            return if (body != null) {
                NetworkResult.Success(body)
            } else {
                @Suppress("UNCHECKED_CAST")
                NetworkResult.Success(Unit as T)
            }
        }

        val errorBodyStr = response.errorBody()?.string()

        if (statusCode == 409 && !errorBodyStr.isNullOrBlank()) {
            try {
                val progress = gson.fromJson(errorBodyStr, ReadingProgress::class.java)
                if (progress != null && progress.bookId > 0 && progress.documentId.isNotBlank()) {
                    return NetworkResult.ProgressConflictResult(progress)
                }
            } catch (ignored: Exception) {
            }
        }

        val apiError = if (!errorBodyStr.isNullOrBlank()) {
            try {
                gson.fromJson(errorBodyStr, ApiError::class.java)
            } catch (e: Exception) {
                null
            }
        } else null

        val effectiveError = apiError ?: ApiError(
            status = statusCode,
            error = "HTTP $statusCode",
            message = "HTTP status $statusCode returned from server",
            path = response.raw().request.url.encodedPath
        )

        return when (statusCode) {
            401 -> NetworkResult.UnauthorizedResult(effectiveError)
            503 -> NetworkResult.RetryableErrorResult(
                effectiveError.message ?: "Service temporarily unavailable",
                503
            )
            else -> NetworkResult.ApiErrorResult(effectiveError, statusCode)
        }
    }

    /**
     * Parses a [HttpException] into a typed [NetworkResult] outcome.
     */
    fun parseHttpException(throwable: Throwable): NetworkResult<Nothing> {
        if (throwable !is HttpException) {
            return NetworkResult.UnknownErrorResult(throwable)
        }

        val statusCode = throwable.code()
        val errorBodyStr = throwable.response()?.errorBody()?.string()

        if (statusCode == 409 && !errorBodyStr.isNullOrBlank()) {
            try {
                val progress = gson.fromJson(errorBodyStr, ReadingProgress::class.java)
                if (progress != null && progress.bookId > 0 && progress.documentId.isNotBlank()) {
                    return NetworkResult.ProgressConflictResult(progress)
                }
            } catch (ignored: Exception) {
            }
        }

        val apiError = if (!errorBodyStr.isNullOrBlank()) {
            try {
                gson.fromJson(errorBodyStr, ApiError::class.java)
            } catch (e: Exception) {
                null
            }
        } else null

        val effectiveError = apiError ?: ApiError(
            status = statusCode,
            error = "HTTP $statusCode",
            message = throwable.message(),
            path = throwable.response()?.raw()?.request?.url?.encodedPath ?: ""
        )

        return when (statusCode) {
            401 -> NetworkResult.UnauthorizedResult(effectiveError)
            503 -> NetworkResult.RetryableErrorResult(
                effectiveError.message ?: "Service temporarily unavailable",
                503
            )
            else -> NetworkResult.ApiErrorResult(effectiveError, statusCode)
        }
    }
}