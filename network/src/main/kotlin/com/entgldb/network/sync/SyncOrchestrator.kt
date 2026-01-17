package com.entgldb.network.sync

import android.util.Log
import com.entgldb.network.discovery.UdpDiscoveryService
import com.entgldb.network.models.NodeAddress
import kotlinx.coroutines.*

/**
 * Manages outgoing sync operations with discovered peers.
 * Implements gossip protocol for efficient data propagation.
 */
class SyncOrchestrator(
    private val discovery: UdpDiscoveryService,
    private val client: TcpPeerClient
) {
    companion object {
        private const val TAG = "SyncOrchestrator"
        private const val SYNC_INTERVAL_MS = 10000L
    }

    private var syncJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Starts the sync orchestrator.
     */
    fun start() {
        if (syncJob != null) return

        syncJob = scope.launch {
            while (isActive) {
                try {
                    syncWithPeers()
                } catch (e: Exception) {
                    Log.e(TAG, "Sync cycle error", e)
                }
                delay(SYNC_INTERVAL_MS)
            }
        }

        Log.i(TAG, "Sync Orchestrator started")
    }

    /**
     * Stops the sync orchestrator.
     */
    fun stop() {
        syncJob?.cancel()
        syncJob = null
        scope.cancel()
        Log.i(TAG, "Sync Orchestrator stopped")
    }

    private suspend fun syncWithPeers() {
        val peers = discovery.getActivePeers()
        
        if (peers.isEmpty()) {
            Log.d(TAG, "No active peers to sync with")
            return
        }

        Log.d(TAG, "Syncing with ${peers.size} peer(s)")

        // Sync with random subset (gossip)
        val peersToSync = peers.shuffled().take(3)

        coroutineScope {
            peersToSync.forEach { peer ->
                launch {
                    try {
                        syncWithPeer(peer.address)
                    } catch (e: Exception) {
                        Log.e(TAG, "Sync error with ${peer.nodeId}", e)
                    }
                }
            }
        }
    }

    private suspend fun syncWithPeer(address: String) {
        val nodeAddress = NodeAddress.parse(address)
        val socket = client.connect(nodeAddress) ?: return

        socket.use {
            // TODO: Implement sync protocol
            // 1. Send our latest timestamp
            // 2. Receive oplog entries
            // 3. Apply to local store
            
            Log.d(TAG, "Sync completed with $address")
        }
    }
}
