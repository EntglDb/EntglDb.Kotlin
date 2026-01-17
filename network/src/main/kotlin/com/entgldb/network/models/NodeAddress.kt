package com.entgldb.network.models

/**
 * Represents the network address of a node.
 */
data class NodeAddress(
    val host: String,
    val port: Int
) {
    override fun toString(): String = "$host:$port"
    
    companion object {
        fun parse(address: String): NodeAddress {
            val parts = address.split(":")
            require(parts.size == 2) { "Invalid address format: $address" }
            return NodeAddress(parts[0], parts[1].toInt())
        }
    }
}
