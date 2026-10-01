package com.parvez.booker.ui.reader

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import kotlin.math.min

/**
 * Thread-safe adapter wrapping platform [PdfRenderer] to render PDF pages into bounded [Bitmap] objects.
 * Handles 1-based API page conversions, bitmap dimension capping, and descriptor resource cleanup.
 *
 * @param pdfFile Verified complete local PDF file.
 */
class PdfRendererAdapter(
    val pdfFile: File
) : AutoCloseable {

    private val fileDescriptor: ParcelFileDescriptor = ParcelFileDescriptor.open(
        pdfFile,
        ParcelFileDescriptor.MODE_READ_ONLY
    )

    private val pdfRenderer: PdfRenderer = try {
        PdfRenderer(fileDescriptor)
    } catch (e: Exception) {
        fileDescriptor.close()
        throw IOException("Failed to initialize PdfRenderer: ${e.message}", e)
    }

    private val renderMutex = Mutex()

    /**
     * Total number of pages in the PDF document.
     */
    val pageCount: Int
        get() = pdfRenderer.pageCount

    /**
     * Renders specified 1-based page into a bounded [Bitmap] off the main thread.
     *
     * @param pageNumber 1-based page number (1..pageCount).
     * @param displayWidth Target viewport width in pixels.
     * @param displayHeight Target viewport height in pixels.
     * @param zoomScale Target zoom multiplier (capped to prevent OOM).
     */
    suspend fun renderPage(
        pageNumber: Int,
        displayWidth: Int = 1080,
        displayHeight: Int = 1920,
        zoomScale: Float = 1.0f
    ): Bitmap = withContext(Dispatchers.Default) {
        require(pageNumber in 1..pageCount) {
            "Page number $pageNumber out of bounds (1..$pageCount)"
        }

        val pageIndex = pageNumber - 1

        renderMutex.withLock {
            var page: PdfRenderer.Page? = null
            try {
                page = pdfRenderer.openPage(pageIndex)

                val originalWidth = page.width
                val originalHeight = page.height

                // Calculate aspect ratio scale
                val baseScale = min(
                    displayWidth.toFloat() / originalWidth.toFloat(),
                    displayHeight.toFloat() / originalHeight.toFloat()
                ).coerceAtLeast(0.1f)

                val effectiveScale = (baseScale * zoomScale).coerceIn(0.1f, MAX_ZOOM_SCALE)

                val targetWidth = (originalWidth * effectiveScale).toInt().coerceIn(100, MAX_BITMAP_DIMENSION)
                val targetHeight = (originalHeight * effectiveScale).toInt().coerceIn(100, MAX_BITMAP_DIMENSION)

                val bitmap = Bitmap.createBitmap(targetWidth, targetHeight, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)

                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            } catch (e: Exception) {
                Log.e("PdfRendererAdapter", "Error rendering page $pageNumber: ${e.message}")
                throw IOException("Error rendering PDF page $pageNumber: ${e.message}", e)
            } finally {
                try {
                    page?.close()
                } catch (e: Exception) {
                    Log.w("PdfRendererAdapter", "Error closing page $pageIndex: ${e.message}")
                }
            }
        }
    }

    override fun close() {
        try {
            pdfRenderer.close()
        } catch (e: Exception) {
            Log.w("PdfRendererAdapter", "Error closing PdfRenderer: ${e.message}")
        } finally {
            try {
                fileDescriptor.close()
            } catch (e: Exception) {
                Log.w("PdfRendererAdapter", "Error closing ParcelFileDescriptor: ${e.message}")
            }
        }
    }

    companion object {
        private const val MAX_BITMAP_DIMENSION = 2048
        private const val MAX_ZOOM_SCALE = 3.0f
    }
}
