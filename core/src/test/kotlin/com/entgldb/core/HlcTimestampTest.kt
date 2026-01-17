package com.entgldb.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class HlcTimestampTest {

    @Test
    fun testComparison() {
        val t1 = HlcTimestamp(100, 0, "A")
        val t2 = HlcTimestamp(100, 1, "A")
        val t3 = HlcTimestamp(101, 0, "A")
        val t4 = HlcTimestamp(100, 0, "B")

        assertTrue(t1 < t2, "Logical counter should order same physical time")
        assertTrue(t2 < t3, "Physical time should dominate")
        assertTrue(t1 < t4, "Node ID comparison (lexicographical) for tie breaking")
    }

    @Test
    fun testToString() {
        val t = HlcTimestamp(123456789, 42, "node-1")
        assertEquals("123456789:42:node-1", t.toString())
    }
}
