package com.parvez.booker.data.repository

import android.util.Log
import com.parvez.booker.data.model.DriveConnection
import com.parvez.booker.data.model.DriveImportRequest
import com.parvez.booker.data.model.ImportStatus
import com.parvez.booker.data.network.RetrofitClient
import kotlinx.coroutines.delay
import java.io.IOException

/**
 * Repository managing Google Drive connection status, disconnect, and background import job polling.
 */
class DriveImportRepository {

    /**
     * Checks if Google Drive is currently connected for the authenticated account.
     */
    suspend fun getDriveConnection(): DriveConnection {
        return RetrofitClient.bookApi.getDriveConnection()
    }

    /**
     * Disconnects Google Drive integration locally and on server (DELETE /api/integrations/google-drive/connection).
     */
    suspend fun disconnectDrive() {
        val response = RetrofitClient.bookApi.disconnectDrive()
        if (!response.isSuccessful && response.code() != 204) {
            throw IOException("Failed to disconnect Google Drive (HTTP ${response.code()})")
        }
    }

    /**
     * Initiates a Google Drive PDF import for a selected fileId using a durable Idempotency-Key.
     */
    suspend fun initiateDriveImport(
        bookId: Long,
        idempotencyKey: String,
        fileId: String
    ): ImportStatus {
        val request = DriveImportRequest(fileId = fileId.trim())
        return RetrofitClient.bookApi.importDrivePdf(bookId, idempotencyKey, request)
    }

    /**
     * Queries current status of an ongoing Google Drive import job.
     */
    suspend fun getImportStatus(bookId: Long, importId: String): ImportStatus {
        return RetrofitClient.bookApi.getDriveImport(bookId, importId)
    }

    /**
     * Polls active import status with bounded backoff until status is COMPLETED or FAILED.
     *
     * @param onUpdate Callback invoked with each polled ImportStatus.
     */
    suspend fun pollImportStatus(
        bookId: Long,
        importId: String,
        maxAttempts: Int = 10,
        onUpdate: (ImportStatus) -> Unit
    ): ImportStatus {
        var attempts = 0
        var currentDelayMs = INITIAL_POLL_DELAY_MS

        while (attempts < maxAttempts) {
            attempts++
            try {
                val status = getImportStatus(bookId, importId)
                onUpdate(status)

                if (status.status == "COMPLETED" || status.status == "FAILED") {
                    return status
                }
            } catch (e: Exception) {
                Log.w("DriveImportRepository", "Error polling import status (attempt $attempts): ${e.message}")
            }

            delay(currentDelayMs)
            currentDelayMs = (currentDelayMs * 2).coerceAtMost(MAX_POLL_DELAY_MS)
        }

        // Final query attempt
        val finalStatus = getImportStatus(bookId, importId)
        onUpdate(finalStatus)
        return finalStatus
    }

    companion object {
        private const val INITIAL_POLL_DELAY_MS = 2000L // 2s
        private const val MAX_POLL_DELAY_MS = 8000L    // 8s
    }
}
