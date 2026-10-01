package com.parvez.booker.data.repository

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import com.parvez.booker.data.model.Document
import com.parvez.booker.data.network.RetrofitClient
import com.parvez.booker.data.network.UploadStreamRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.util.UUID

/**
 * Metadata retrieved from a content provider URI.
 */
data class SelectedDocumentMetadata(
    val uri: Uri,
    val fileName: String,
    val fileSize: Long?
)

/**
 * Represents current state of a PDF document upload operation.
 */
sealed class UploadState {
    data object Idle : UploadState()
    data object Staging : UploadState()
    data class Transferring(val bytesSent: Long, val totalBytes: Long, val percent: Float) : UploadState()
    data object Validating : UploadState()
    data class Success(val document: Document, val isReplay: Boolean = false) : UploadState()
    data class Error(val message: String, val statusCode: Int? = null) : UploadState()
}

/**
 * Repository providing secure document selection, private snapshot staging,
 * streamed multipart PDF uploads, and idempotency key lifecycle management.
 */
class DocumentRepository(
    private val context: Context? = null
) {

    /**
     * Safely queries display name and file size from a content URI via ContentResolver.
     */
    fun queryUriMetadata(context: Context, uri: Uri): SelectedDocumentMetadata {
        var fileName = "document.pdf"
        var fileSize: Long? = null

        // Attempt persistable permission grant if supported by flags
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: Exception) {
            Log.d("DocumentRepository", "Persistable URI permission not granted or unsupported: ${e.message}")
        }

        try {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIdx != -1) {
                        val name = cursor.getString(nameIdx)
                        if (!name.isNullOrBlank()) {
                            fileName = name
                        }
                    }

                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (sizeIdx != -1 && !cursor.isNull(sizeIdx)) {
                        fileSize = cursor.getLong(sizeIdx)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("DocumentRepository", "Failed to query URI metadata: ${e.message}")
        }

        return SelectedDocumentMetadata(
            uri = uri,
            fileName = sanitizeFileName(fileName),
            fileSize = fileSize
        )
    }

    /**
     * Stages an app-private snapshot file from Uri content stream to guarantee byte reproducibility across retries.
     */
    fun stagePrivateSnapshot(context: Context, uri: Uri, uploadId: String): File {
        val snapshotsDir = File(context.cacheDir, "upload_snapshots").apply {
            if (!exists()) mkdirs()
        }

        val snapshotFile = File(snapshotsDir, "$uploadId.pdf")
        val inputStream: InputStream = context.contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open input stream for URI: $uri")

        inputStream.use { input ->
            FileOutputStream(snapshotFile).use { output ->
                val buffer = ByteArray(BUFFER_SIZE_BYTES)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
                output.flush()
            }
        }

        if (snapshotFile.length() == 0L) {
            snapshotFile.delete()
            throw IllegalArgumentException("Selected PDF file is empty (0 bytes).")
        }

        return snapshotFile
    }

    /**
     * Executes streamed multipart upload via POST /api/books/{bookId}/document with Idempotency-Key.
     */
    suspend fun uploadDocument(
        bookId: Long,
        idempotencyKey: String,
        snapshotFile: File,
        fileName: String,
        onProgress: (bytesSent: Long, totalBytes: Long) -> Unit
    ): Document {
        val cleanName = sanitizeFileName(fileName)

        val streamBody = UploadStreamRequestBody(
            file = snapshotFile,
            contentType = "application/pdf".toMediaType(),
            onProgress = onProgress
        )

        val filePart = MultipartBody.Part.createFormData("file", cleanName, streamBody)

        // Upload file with idempotency key
        val uploadResponseDoc = RetrofitClient.bookApi.uploadDocument(bookId, idempotencyKey, filePart)

        // Always re-fetch active document to verify active state (replayed key might return historical doc)
        return try {
            val activeDoc = RetrofitClient.bookApi.getDocument(bookId)
            if (activeDoc.documentId == uploadResponseDoc.documentId) {
                activeDoc
            } else {
                uploadResponseDoc
            }
        } catch (e: Exception) {
            uploadResponseDoc
        }
    }

    /**
     * Fetches currently active document metadata for a book via GET /api/books/{bookId}/document.
     */
    suspend fun getActiveDocument(bookId: Long): Document {
        return RetrofitClient.bookApi.getDocument(bookId)
    }

    /**
     * Deletes staged private snapshot file for an upload operation ID.
     */
    fun cleanupSnapshot(context: Context, uploadId: String) {
        try {
            val snapshotFile = File(File(context.cacheDir, "upload_snapshots"), "$uploadId.pdf")
            if (snapshotFile.exists()) {
                snapshotFile.delete()
            }
        } catch (e: Exception) {
            Log.w("DocumentRepository", "Failed to cleanup snapshot for $uploadId: ${e.message}")
        }
    }

    private fun sanitizeFileName(fileName: String): String {
        val trimmed = fileName.trim()
        val safeName = trimmed.replace(Regex("[^a-zA-Z0-9._-]"), "_")
        return if (!safeName.lowercase().endsWith(".pdf")) {
            "$safeName.pdf"
        } else {
            safeName
        }
    }

    companion object {
        private const val BUFFER_SIZE_BYTES = 16 * 1024
        fun generateIdempotencyKey(): String = UUID.randomUUID().toString()
    }
}
