package com.entgldb.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VectorClockTest {

    @Test
    fun testVectorClockComparison() {
        val vc1 = VectorClock(mutableMapOf(
            "A" to HlcTimestamp(10, 0, "A"),
            "B" to HlcTimestamp(5, 0, "B")
        ))

        val vc2 = VectorClock(mutableMapOf(
            "A" to HlcTimestamp(10, 0, "A"),
            "B" to HlcTimestamp(5, 0, "B")
        ))
        
        assertEquals(CausalityRelation.Equal, vc1.compareTo(vc2))

        val vc3 = VectorClock(mutableMapOf(
            "A" to HlcTimestamp(11, 0, "A"),
            "B" to HlcTimestamp(5, 0, "B")
        ))

        // vc3 > vc1 because A:11 > A:10
        assertEquals(CausalityRelation.StrictlyAhead, vc3.compareTo(vc1))
        assertEquals(CausalityRelation.StrictlyBehind, vc1.compareTo(vc3))

        val vc4 = VectorClock(mutableMapOf(
            "A" to HlcTimestamp(10, 0, "A"),
            "B" to HlcTimestamp(6, 0, "B")
        ))

        // vc3 (A:11, B:5) vs vc4 (A:10, B:6) -> Concurrent
        assertEquals(CausalityRelation.Concurrent, vc3.compareTo(vc4))
    }

    @Test
    fun testMerge() {
        val vc1 = VectorClock(mutableMapOf(
            "A" to HlcTimestamp(10, 0, "A"),
            "B" to HlcTimestamp(5, 0, "B")
        ))

        val vc2 = VectorClock(mutableMapOf(
            "A" to HlcTimestamp(12, 0, "A"),
            "C" to HlcTimestamp(1, 0, "C")
        ))

        vc1.merge(vc2)
        
        assertEquals(12, vc1.clock["A"]?.physicalTime)
        assertEquals(5, vc1.clock["B"]?.physicalTime)
        assertEquals(1, vc1.clock["C"]?.physicalTime)
    }

    @Test
    fun testNodesToPull() {
        // Local state
        val local = VectorClock(mutableMapOf(
            "A" to HlcTimestamp(10, 0, "A")
        ))
        // Remote has newer A and new B
        val remote = VectorClock(mutableMapOf(
            "A" to HlcTimestamp(20, 0, "A"),
            "B" to HlcTimestamp(5, 0, "B")
        ))

        val toPull = local.getNodesWithUpdates(remote)
        assertTrue(toPull.contains("A"))
        assertTrue(toPull.contains("B"))
    }

    @Test
    fun testCryptoUtilsHash() {
        val ts = HlcTimestamp(1234567890L, 1, "node1")
        val entry = OplogEntry(
            collection = "users",
            key = "user1",
            operation = OperationType.Put,
            payload = com.entgldb.core.common.JsonHelpers.parse("{\"name\":\"Alice\"}"),
            timestamp = ts,
            previousHash = "prevHash123"
        )

        // Compute hash
        val hash = CryptoUtils.computeOplogHash(entry)
        
        // Expected format: Op|Collection|Key|Payload|Timestamp|PrevHash
        // Put|users|user1|{"name":"Alice"}|1234567890-1-node1|prevHash123
        // SHA256 of that string.
        
        assertTrue(hash.isNotEmpty())
        assertEquals(64, hash.length) // SHA-256 hex length
        
        // Ensure consistency
        val hash2 = CryptoUtils.computeOplogHash(entry)
        assertEquals(hash, hash2)
    }
}
