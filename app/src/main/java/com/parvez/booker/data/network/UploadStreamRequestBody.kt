package com.parvez.booker.data.network

import okhttp3.MediaType
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody
import okio.Buffer
import okio.BufferedSink
import okio.source
import java.io.File

/**
 * Custom OkHttp RequestBody that streams a file in bounded chunks off the main thread
 * and invokes progress callbacks with total and transferred Long byte counters.
 *
 * @property file App-private snapshot File to stream.
 * @property contentType Media type string (default application/pdf).
 * @property onProgress Callback reporting bytesSent and totalBytes.
 */
class UploadStreamRequestBody(
    private val file: File,
    private val contentType: MediaType = "application/pdf".toMediaType(),
    private val onProgress: (bytesSent: Long, totalBytes: Long) -> Unit
) : RequestBody() {

    override fun contentType(): MediaType = contentType

    override fun contentLength(): Long = file.length()

    override fun writeTo(sink: BufferedSink) {
        val totalLength = file.length()
        var bytesSent = 0L

        file.source().use { source ->
            val buffer = Buffer()
            var readCount: Long

            while (source.read(buffer, BUFFER_SIZE_BYTES).also { readCount = it } != -1L) {
                sink.write(buffer, readCount)
                bytesSent += readCount
                onProgress(bytesSent, totalLength)
            }
        }
    }

    companion object {
        private const val BUFFER_SIZE_BYTES = 16L * 1024L // 16 KB bounded buffer
    }
}
