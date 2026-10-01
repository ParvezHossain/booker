package com.parvez.booker.data.network

import com.parvez.booker.data.model.ApiError
import com.parvez.booker.data.model.ReadingProgress

/**
 * Typed outcome hierarchy representing network call results and server error responses.
 */
sealed class NetworkResult<out T> {

    /**
     * Successful API response containing typed payload [data].
     */
    data class Success<out T>(val data: T) : NetworkResult<T>()

    /**
     * Standard backend error response containing parsed [error] DTO and HTTP [statusCode].
     */
    data class ApiErrorResult(
        val error: ApiError,
        val statusCode: Int
    ) : NetworkResult<Nothing>()

    /**
     * Progress revision conflict (HTTP 409) returning current server [progress] state.
     */
    data class ProgressConflictResult(
        val progress: ReadingProgress
    ) : NetworkResult<Nothing>()

    /**
     * Authentication failure (HTTP 401) requiring re-login or refresh.
     */
    data class UnauthorizedResult(
        val error: ApiError?
    ) : NetworkResult<Nothing>()

    /**
     * Transient or retryable server/network failure (HTTP 503, connection timeout).
     */
    data class RetryableErrorResult(
        val errorMessage: String,
        val statusCode: Int
    ) : NetworkResult<Nothing>()

    /**
     * Unexpected exception or malformed payload error.
     */
    data class UnknownErrorResult(
        val throwable: Throwable
    ) : NetworkResult<Nothing>()
}