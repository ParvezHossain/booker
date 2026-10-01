package com.parvez.booker.data.repository

import com.parvez.booker.data.model.Book
import com.parvez.booker.data.model.Document
import com.parvez.booker.data.model.ProgressUpdate
import com.parvez.booker.data.model.ReadingProgress
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import java.io.IOException

class LocalReadingStoreTests {

    private lateinit var store: LocalReadingStore

    companion object {
        private const val ENV_DEBUG = "http://10.0.2.2:8080/api/"
        private const val ENV_PROD = "https://booker.example.com/api/"
        private const val USER_A = "usera@example.com"
        private const val USER_B = "userb@example.com"
        private const val WORKSPACE_1 = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"
        private const val BOOK_ID = 101L
        private const val DOC_ID = "11111111-1111-4111-8111-111111111111"
    }

    @Before
    fun setUp() {
        store = LocalReadingStore(context = null)
    }

    @Test
    fun testAtomicRecoveryAndProcessRecreation() {
        store.recordLocalPageChange(ENV_DEBUG, USER_A, WORKSPACE_1, BOOK_ID, DOC_ID, newPage = 45)

        val serverProgress = ReadingProgress(
            bookId = BOOK_ID,
            documentId = DOC_ID,
            currentPage = 45,
            totalPages = 200,
            pagesRead = 45,
            progressPercentage = 22.5,
            lastReadAt = "2026-09-29T10:00:00Z",
            completed = false,
            resumePage = 45,
            version = 1L
        )
        store.setAcknowledgedProgress(ENV_DEBUG, USER_A, WORKSPACE_1, BOOK_ID, DOC_ID, serverProgress)

        val recreatedStore = LocalReadingStore(context = null)
        val fieldState = LocalReadingStore::class.java.getDeclaredField("inMemoryState")
        fieldState.isAccessible = true
        val origMap = fieldState.get(store)
        fieldState.set(recreatedStore, origMap)

        val recoveredState = recreatedStore.getProgressState(ENV_DEBUG, USER_A, WORKSPACE_1, BOOK_ID, DOC_ID)
        assertNotNull(recoveredState.acknowledgedProgress)
        assertEquals(45, recoveredState.acknowledgedProgress?.currentPage)
        assertEquals(45, recoveredState.latestLocalResumePage)
        assertEquals(45, recoveredState.latestLocalMaxPage)
        assertEquals(1L, recoveredState.acknowledgedProgress?.version)
    }

    @Test
    fun testTwoAccountsInOneWorkspaceIsolation() {
        store.recordLocalPageChange(ENV_DEBUG, USER_A, WORKSPACE_1, BOOK_ID, DOC_ID, newPage = 93)
        store.recordLocalPageChange(ENV_DEBUG, USER_B, WORKSPACE_1, BOOK_ID, DOC_ID, newPage = 12)

        val stateA = store.getProgressState(ENV_DEBUG, USER_A, WORKSPACE_1, BOOK_ID, DOC_ID)
        val stateB = store.getProgressState(ENV_DEBUG, USER_B, WORKSPACE_1, BOOK_ID, DOC_ID)

        assertEquals(93, stateA.latestLocalResumePage)
        assertEquals(93, stateA.latestLocalMaxPage)

        assertEquals(12, stateB.latestLocalResumePage)
        assertEquals(12, stateB.latestLocalMaxPage)
    }

    @Test
    fun testTwoEnvironmentsWithSameBookIdIsolation() {
        store.recordLocalPageChange(ENV_DEBUG, USER_A, WORKSPACE_1, BOOK_ID, DOC_ID, newPage = 50)
        store.recordLocalPageChange(ENV_PROD, USER_A, WORKSPACE_1, BOOK_ID, DOC_ID, newPage = 150)

        val debugState = store.getProgressState(ENV_DEBUG, USER_A, WORKSPACE_1, BOOK_ID, DOC_ID)
        val prodState = store.getProgressState(ENV_PROD, USER_A, WORKSPACE_1, BOOK_ID, DOC_ID)

        assertEquals(50, debugState.latestLocalResumePage)
        assertEquals(150, prodState.latestLocalResumePage)
    }

    @Test
    fun testLatestLocalIntentSurvivingOldResponses() {
        store.recordLocalPageChange(ENV_DEBUG, USER_A, WORKSPACE_1, BOOK_ID, DOC_ID, newPage = 50)

        val oldServerProgress = ReadingProgress(
            bookId = BOOK_ID,
            documentId = DOC_ID,
            currentPage = 20,
            totalPages = 100,
            pagesRead = 20,
            progressPercentage = 20.0,
            lastReadAt = "2026-09-29T09:00:00Z",
            completed = false,
            resumePage = 20,
            version = 1L
        )

        store.setAcknowledgedProgress(ENV_DEBUG, USER_A, WORKSPACE_1, BOOK_ID, DOC_ID, oldServerProgress)

        val updatedState = store.getProgressState(ENV_DEBUG, USER_A, WORKSPACE_1, BOOK_ID, DOC_ID)

        assertEquals(50, updatedState.latestLocalResumePage)
        assertEquals(50, updatedState.latestLocalMaxPage)
        assertEquals(20, updatedState.acknowledgedProgress?.currentPage)
    }

    @Test
    fun testMax93FollowedByResume20Offline() {
        store.recordLocalPageChange(ENV_DEBUG, USER_A, WORKSPACE_1, BOOK_ID, DOC_ID, newPage = 93)
        store.recordLocalPageChange(ENV_DEBUG, USER_A, WORKSPACE_1, BOOK_ID, DOC_ID, newPage = 20)

        val state = store.getProgressState(ENV_DEBUG, USER_A, WORKSPACE_1, BOOK_ID, DOC_ID)

        assertEquals(93, state.latestLocalMaxPage)
        assertEquals(20, state.latestLocalResumePage)
    }

    @Test
    fun testDiskWriteFailure() {
        store.simulateDiskFailure = true

        try {
            store.recordLocalPageChange(ENV_DEBUG, USER_A, WORKSPACE_1, BOOK_ID, DOC_ID, newPage = 10)
            fail("Expected IOException on simulated disk write failure")
        } catch (e: IOException) {
            assertTrue(e.message!!.contains("disk write failure"))
        }
    }

    @Test
    fun testInFlightOperationIsolation() {
        val update = ProgressUpdate(
            documentId = DOC_ID,
            currentPage = 93,
            version = 1L,
            operationId = "33333333-3333-4333-8333-333333333333"
        )

        store.setInFlightOperation(ENV_DEBUG, USER_A, WORKSPACE_1, BOOK_ID, DOC_ID, update, ProgressSyncType.SYNC_MAX)

        var state = store.getProgressState(ENV_DEBUG, USER_A, WORKSPACE_1, BOOK_ID, DOC_ID)
        assertNotNull(state.inFlightOperation)
        assertEquals(93, state.inFlightOperation?.currentPage)
        assertEquals(ProgressSyncType.SYNC_MAX, state.inFlightType)

        store.clearInFlightOperation(ENV_DEBUG, USER_A, WORKSPACE_1, BOOK_ID, DOC_ID)
        state = store.getProgressState(ENV_DEBUG, USER_A, WORKSPACE_1, BOOK_ID, DOC_ID)
        assertNull(state.inFlightOperation)
        assertNull(state.inFlightType)
    }

    @Test
    fun testCatalogueAndDocumentCaching() {
        val books = listOf(
            Book(id = 1L, title = "Effective Java", author = "Joshua Bloch", publishedDate = "2018", completed = false)
        )

        store.saveCatalogueCache(ENV_DEBUG, USER_A, WORKSPACE_1, books)
        val cached = store.getCatalogueCache(ENV_DEBUG, USER_A, WORKSPACE_1)

        assertEquals(1, cached.size)
        assertEquals("Effective Java", cached[0].title)

        val doc = Document(
            bookId = 1L,
            documentId = DOC_ID,
            fileName = "effective_java.pdf",
            fileSize = 1000L,
            mimeType = "application/pdf",
            pageCount = 100,
            checksum = "abc",
            sourceType = "UPLOAD",
            active = true,
            createdAt = "2026-09-29T10:00:00Z"
        )

        store.saveDocumentCache(ENV_DEBUG, USER_A, WORKSPACE_1, 1L, doc)
        val cachedDoc = store.getDocumentCache(ENV_DEBUG, USER_A, WORKSPACE_1, 1L)

        assertNotNull(cachedDoc)
        assertEquals("effective_java.pdf", cachedDoc?.fileName)
    }
}
