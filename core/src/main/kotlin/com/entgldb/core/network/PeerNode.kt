package com.entgldb.core.network

/**
 * Represents a discovered peer node in the network.
 */
data class PeerNode(
    val nodeId: String,
    val address: String, // "host:port"
    val lastSeen: Long, // timestamp in milliseconds
    val type: PeerType = PeerType.LanDiscovered
) {
    val isPersistent: Boolean
        get() = type != PeerType.LanDiscovered
}
