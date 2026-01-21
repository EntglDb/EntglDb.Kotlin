package com.entgldb.network.config

import com.entgldb.network.models.PeerNode

/**
 * Provides configuration for the peer node.
 */
interface IPeerNodeConfigurationProvider {
    data class Configuration(
        val nodeId: String,
        val tcpPort: Int,
        val udpPort: Int = 0 // Optional for server mode
    )

    /**
     * Gets the current configuration.
     */
    fun getConfiguration(): Configuration

    /**
     * Subscribes to configuration changes.
     */
    fun subscribe(listener: (Configuration) -> Unit): () -> Unit
}
