package com.parvez.booker.data.model

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test

class BookEventTest {

    private val gson = Gson()

    @Test
    fun testParseBookCreatedEvent() {
        val jsonPayload = """
            {
              "eventId": "17",
              "type": "book.created",
              "schemaVersion": 1,
              "occurredAt": "2026-09-26T12:00:00.000Z",
              "book": {
                "id": 123,
                "isbn": "9780134685991",
                "title": "Effective Java",
                "author": "Joshua Bloch",
                "publishedDate": "2018-01-11",
                "description": null,
                "completed": false
              }
            }
        """.trimIndent()

        val event = gson.fromJson(jsonPayload, BookEvent::class.java)

        assertNotNull(event)
        assertEquals("17", event.eventId)
        assertEquals("book.created", event.type)
        assertEquals(1, event.schemaVersion)
        assertEquals("2026-09-26T12:00:00.000Z", event.occurredAt)

        val book = event.book
        assertNotNull(book)
        assertEquals(123L, book?.id)
        assertEquals("9780134685991", book?.isbn)
        assertEquals("Effective Java", book?.title)
        assertEquals("Joshua Bloch", book?.author)
        assertEquals("2018-01-11", book?.publishedDate)
        assertFalse(book?.completed ?: true)
    }
}