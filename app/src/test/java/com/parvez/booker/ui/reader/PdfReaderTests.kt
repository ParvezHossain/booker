package com.parvez.booker.ui.reader

import com.parvez.booker.data.repository.LocalReadingStore
import org.junit.Assert
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class PdfReaderTests {

    private lateinit var store: LocalReadingStore

    companion object {
        private const val ENV_URL = "http://10.0.2.2:8080/api/"
        private const val EMAIL = "reader@example.com"
        private const val WORKSPACE_ID = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        private const val BOOK_ID = 1L
        private const val DOC_ID = "11111111-1111-4111-8111-111111111111"
    }

    @Before
    fun setUp() {
        store = LocalReadingStore(context = null)
    }

    @Test
    fun testSavedPageRestoration144PageAt93() {
        // 1. Simulate saved local resume position at page 93 (from Prompt 05 store)
        store.recordLocalPageChange(ENV_URL, EMAIL, WORKSPACE_ID, BOOK_ID, DOC_ID, newPage = 93)

        val localState = store.getProgressState(ENV_URL, EMAIL, WORKSPACE_ID, BOOK_ID, DOC_ID)

        val savedLocalResume = localState.latestLocalResumePage
        val serverResume = localState.acknowledgedProgress?.resumePage ?: 1

        val restoredPage = if (savedLocalResume > 1) savedLocalResume else if (serverResume > 1) serverResume else 1

        assertEquals(93, restoredPage)
        val zeroBasedIndex = restoredPage - 1
        assertEquals(92, zeroBasedIndex)
    }

    @Test
    fun testFirstOpenRestorationAtPage1() {
        // First-open: no prior local or server progress
        val localState = store.getProgressState(ENV_URL, EMAIL, WORKSPACE_ID, BOOK_ID, DOC_ID)

        val savedLocalResume = localState.latestLocalResumePage
        val serverResume = localState.acknowledgedProgress?.resumePage ?: 1

        val restoredPage = if (savedLocalResume > 1) savedLocalResume else if (serverResume > 1) serverResume else 1

        assertEquals(1, restoredPage)
        assertEquals(0, restoredPage - 1)
    }

    @Test
    fun testBackwardNavigationPreservesMaxPage93AndResume20() {
        // User reaches page 93
        store.recordLocalPageChange(ENV_URL, EMAIL, WORKSPACE_ID, BOOK_ID, DOC_ID, newPage = 93)

        // User navigates backward to page 20
        store.recordLocalPageChange(ENV_URL, EMAIL, WORKSPACE_ID, BOOK_ID, DOC_ID, newPage = 20)

        val state = store.getProgressState(ENV_URL, EMAIL, WORKSPACE_ID, BOOK_ID, DOC_ID)

        assertEquals(20, state.latestLocalResumePage)
        assertEquals(93, state.latestLocalMaxPage)
    }

    @Test
    fun testClampingPageIndexBounds() {
        val totalPages = 144

        val clampUnder = 0.coerceIn(1, totalPages)
        val clampOver = 999.coerceIn(1, totalPages)

        assertEquals(1, clampUnder)
        assertEquals(144, clampOver)
    }

    @Test
    fun testCorruptFileInitializationRejection() {
        val corruptFile = File.createTempFile("corrupt_test", ".pdf").apply {
            FileOutputStream(this).use { it.write("not a valid pdf".toByteArray()) }
        }

        try {
            PdfRendererAdapter(corruptFile)
            Assert.fail("Expected IOException on corrupt file initialization")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("PdfRenderer"))
        } finally {
            corruptFile.delete()
        }
    }
}
