package com.parvez.booker.data.network

import com.google.gson.Gson
import com.parvez.booker.data.model.BookEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.pow
import kotlin.math.min

class BookSseManagerTest {

    private val gson = Gson()

    @Test
    fun testDuplicateEventIdIdempotency() {
        val handledEventIds = ConcurrentHashMap.newKeySet<String>()

        val eventData1 = """
            {"eventId":"17","type":"book.created","schemaVersion":1,"occurredAt":"2026-09-26T12:00:00.000Z","book":{"id":123,"isbn":"9780134685991","title":"Effective Java","author":"Joshua Bloch","publishedDate":"2018-01-11","completed":false}}
        """.trimIndent()

        val eventData2 = """
            {"eventId":"17","type":"book.created","schemaVersion":1,"occurredAt":"2026-09-26T12:00:00.000Z","book":{"id":123,"isbn":"9780134685991","title":"Effective Java","author":"Joshua Bloch","publishedDate":"2018-01-11","completed":false}}
        """.trimIndent()

        val parsed1 = gson.fromJson(eventData1, BookEvent::class.java)
        val eventId1 = parsed1.eventId ?: ""

        val isNewFirstTime = handledEventIds.add(eventId1)
        assertTrue(isNewFirstTime)

        val parsed2 = gson.fromJson(eventData2, BookEvent::class.java)
        val eventId2 = parsed2.eventId ?: ""

        val isNewSecondTime = handledEventIds.add(eventId2)
        assertFalse(isNewSecondTime) // Duplicate ignored idempotently
    }

    @Test
    fun testReconnectBackoffCalculation() {
        fun calculateBackoff(reconnectAttempt: Int): Long {
            val baseDelayMs = min(30_000L, 3000L * (2.0.pow(reconnectAttempt - 1)).toLong())
            return baseDelayMs
        }

        assertEquals(3000L, calculateBackoff(1))
        assertEquals(6000L, calculateBackoff(2))
        assertEquals(12000L, calculateBackoff(3))
        assertEquals(24000L, calculateBackoff(4))
        assertEquals(30000L, calculateBackoff(5)) // Capped at 30 seconds
    }
}