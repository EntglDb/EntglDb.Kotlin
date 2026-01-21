package com.entgldb.network.discovery

import com.entgldb.core.network.PeerNode

interface IDiscoveryService {
    fun start()
    fun stop()
    fun getActivePeers(): List<PeerNode>
}
