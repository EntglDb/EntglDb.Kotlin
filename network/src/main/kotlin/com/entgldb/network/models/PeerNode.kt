package com.entgldb.network.models

/**
 * Represents a discovered peer node in the network.
 */
data class PeerNode(
    val nodeId: String,
    val address: String, // "host:port"
    val lastSeen: Long // timestamp in milliseconds
)
