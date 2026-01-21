package com.entgldb.core.network

/**
 * Defines the type of peer node.
 */
enum class PeerType(val value: Int) {
    LanDiscovered(0),
    StaticRemote(1),
    CloudRemote(2);

    companion object {
        fun fromValue(value: Int): PeerType? = values().find { it.value == value }
    }
}
