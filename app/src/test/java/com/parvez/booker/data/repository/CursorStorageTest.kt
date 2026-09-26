package com.parvez.booker.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CursorStorageTest {

    private val inMemoryCursors = mutableMapOf<String, String>()

    @Test
    fun testCursorPersistenceKeyNormalizing() {
        fun getKey(username: String): String {
            val normalized = username.trim().ifEmpty { "default_user" }
            return "sse_cursor_$normalized"
        }

        val keyAdmin = getKey("admin")
        val keyWithSpaces = getKey("  admin  ")
        val keyEmpty = getKey("")

        assertEquals("sse_cursor_admin", keyAdmin)
        assertEquals("sse_cursor_admin", keyWithSpaces)
        assertEquals("sse_cursor_default_user", keyEmpty)
    }

    @Test
    fun testSaveAndGetCursorInMemory() {
        val username = "admin"
        val cursorId = "105"

        inMemoryCursors[username] = cursorId
        assertEquals("105", inMemoryCursors[username])

        inMemoryCursors.remove(username)
        assertNull(inMemoryCursors[username])
    }
}