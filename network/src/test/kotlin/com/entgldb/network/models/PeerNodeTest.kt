package com.entgldb.network.models

import org.junit.Assert.*
import org.junit.Test

class PeerNodeTest {

    @Test
    fun testPeerNodeCreation() {
        val now = System.currentTimeMillis()
        val peer = PeerNode(
            nodeId = "test-node-123",
            address = "192.168.1.100:5000",
            lastSeen = now
        )

        assertEquals("test-node-123", peer.nodeId)
        assertEquals("192.168.1.100:5000", peer.address)
        assertEquals(now, peer.lastSeen)
    }

    @Test
    fun testPeerNodeEquality() {
        val now = System.currentTimeMillis()
        val peer1 = PeerNode("node-1", "10.0.0.1:5000", now)
        val peer2 = PeerNode("node-1", "10.0.0.1:5000", now)
        val peer3 = PeerNode("node-2", "10.0.0.1:5000", now)

        assertEquals(peer1, peer2)
        assertNotEquals(peer1, peer3)
    }

    @Test
    fun testPeerNodeCopy() {
        val original = PeerNode("node-1", "10.0.0.1:5000", 123456L)
        val updated = original.copy(lastSeen = 789012L)

        assertEquals(original.nodeId, updated.nodeId)
        assertEquals(original.address, updated.address)
        assertNotEquals(original.lastSeen, updated.lastSeen)
        assertEquals(789012L, updated.lastSeen)
    }
}
