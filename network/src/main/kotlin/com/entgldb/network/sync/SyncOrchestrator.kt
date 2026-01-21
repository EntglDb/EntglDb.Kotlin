package com.entgldb.network.sync

import android.util.Log
import com.entgldb.network.discovery.UdpDiscoveryService
import com.entgldb.network.models.NodeAddress
import com.entgldb.network.proto.*
import kotlinx.coroutines.*

/**
 * Manages outgoing sync operations with discovered peers.
 * Implements gossip protocol for efficient data propagation.
 */
class SyncOrchestrator(
    private val discovery: com.entgldb.network.discovery.IDiscoveryService, // Use Interface
    private val client: TcpPeerClient,
    private val store: com.entgldb.core.storage.IPeerStore,
    private val nodeId: String,
    private val authToken: String
) : ISyncOrchestrator {
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
                        syncWithPeer(peer.nodeId, peer.address)
                    } catch (e: Exception) {
                        Log.e(TAG, "Sync error with ${peer.nodeId}", e)
                    }
                }
            }
        }
    }

    private suspend fun syncWithPeer(peerNodeId: String, address: String) {
        val nodeAddress = NodeAddress.parse(address)
        Log.i(TAG, "Connecting to peer $peerNodeId at ${nodeAddress.host}:${nodeAddress.port}...")
        
        val channel = runCatching {
            client.connect(nodeAddress)
        }.getOrElse { 
            Log.w(TAG, "Failed to connect to $peerNodeId: ${it.message}")
            return
        }
        
        if (channel == null) {
            Log.w(TAG, "Connection returned null channel for $peerNodeId")
            return
        }

        try {
            // 1. Application Layer Handshake
            Log.d(TAG, "Performing Application Handshake with $peerNodeId...")
            val handshakeReqBuilder = HandshakeRequest.newBuilder()
                .setNodeId(nodeId)
                .setAuthToken(authToken)
            
            if (CompressionHelper.isBrotliSupported) {
                handshakeReqBuilder.addSupportedCompression("brotli")
            }

            channel.sendMessage(MessageType.HandshakeReq, handshakeReqBuilder.build())
            
            val (type, payload) = channel.readMessage()
            if (type != MessageType.HandshakeRes) {
                Log.e(TAG, "Handshake failed. Expected HandshakeRes, got $type")
                return
            }
            
            val handshakeRes = HandshakeResponse.parseFrom(payload)
            if (!handshakeRes.accepted) {
                Log.e(TAG, "Handshake rejected by peer")
                return
            }
            
            if (handshakeRes.selectedCompression == "brotli") {
                channel.useCompression = true
                Log.i(TAG, "Negotatied Brotli compression with $peerNodeId")
            }

            Log.i(TAG, "Handshake successful with $peerNodeId")

            // 2. Get Clock (HLC)
            val clockReq = GetClockRequest.getDefaultInstance()
            channel.sendMessage(MessageType.GetClockReq, clockReq)
            
            val (clockType, clockPayload) = channel.readMessage()
            if (clockType != MessageType.ClockRes) {
                 Log.e(TAG, "Expected ClockRes, got $clockType")
                 return
            }
            
            val clockRes = ClockResponse.parseFrom(clockPayload)
            
            Log.d(TAG, "Peer HLC: Wall=${clockRes.hlcWall}, Logic=${clockRes.hlcLogic}")

            // 3. Pull Changes (From Peer -> Me)
            // Ask for changes since OUR latest timestamp
            val localHlc = store.getLatestTimestamp()
            val pullReq = PullChangesRequest.newBuilder()
                .setSinceWall(localHlc.physicalTime)
                .setSinceLogic(localHlc.logicalCounter)
                .setSinceNode(localHlc.nodeId)
                .build()

            channel.sendMessage(MessageType.PullChangesReq, pullReq)

            val (pullType, pullPayload) = channel.readMessage()
            if (pullType != MessageType.ChangeSetRes) {
                 Log.e(TAG, "Expected ChangeSetRes, got $pullType")
                 return
            }

            val changeSet = ChangeSetResponse.parseFrom(pullPayload)
            if (changeSet.entriesCount > 0) {
                Log.i(TAG, "Received ${changeSet.entriesCount} changes from peer")
                
                val oplogEntries = changeSet.entriesList.map { proto ->
                     com.entgldb.core.OplogEntry(
                         collection = proto.collection,
                         key = proto.key,
                         operation = com.entgldb.core.OperationType.valueOf(proto.operation),
                         payload = if (proto.jsonData.isNotEmpty()) com.entgldb.core.common.JsonHelpers.parse(proto.jsonData) else null,
                         timestamp = com.entgldb.core.HlcTimestamp(proto.hlcWall, proto.hlcLogic, proto.hlcNode)
                     )
                }
                
                store.applyRemoteChanges(oplogEntries)
            } else {
                Log.d(TAG, "No new changes from peer")
            }

            // 4. Push Changes (Me -> Peer)
            // Send changes since THEIR latest timestamp
            val peerHlc = com.entgldb.core.HlcTimestamp(clockRes.hlcWall, clockRes.hlcLogic, clockRes.hlcNode)
            val changesToPush = store.getOplogAfter(peerHlc)
            
            if (changesToPush.isNotEmpty()) {
                Log.i(TAG, "Pushing ${changesToPush.size} changes to peer")
                
                val pushReqBuilder = PushChangesRequest.newBuilder()
                changesToPush.forEach { entry ->
                    pushReqBuilder.addEntries(
                        ProtoOplogEntry.newBuilder()
                            .setCollection(entry.collection)
                            .setKey(entry.key)
                            .setOperation(entry.operation.name)
                            .setJsonData(entry.payload?.toString() ?: "")
                            .setHlcWall(entry.timestamp.physicalTime)
                            .setHlcLogic(entry.timestamp.logicalCounter)
                            .setHlcNode(entry.timestamp.nodeId)
                            .build()
                    )
                }
                
                channel.sendMessage(MessageType.PushChangesReq, pushReqBuilder.build())
                
                val (ackType, ackPayload) = channel.readMessage()
                if (ackType == MessageType.AckRes) {
                    Log.d(TAG, "Push acknowledged")
                } else {
                    Log.w(TAG, "Expected AckRes, got $ackType")
                }
            }

            Log.i(TAG, "Sync cycle completed with ${nodeAddress}")

        } catch (e: Exception) {
             Log.e(TAG, "Sync protocol error with $address", e)
        }
    }
}
