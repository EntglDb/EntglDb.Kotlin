package com.entgldb.network.discovery

import com.entgldb.network.models.PeerNode

/**
 * No-operation discovery service for server scenarios.
 */
class NoOpDiscoveryService : IDiscoveryService {
    override fun start() {
        // Do nothing
    }

    override fun stop() {
        // Do nothing
    }

    override fun getActivePeers(): List<PeerNode> {
        return emptyList()
    }
}
