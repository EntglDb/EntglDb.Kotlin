package com.entgldb.network.sync

import EntglDb.Network.Proto.Sync
import com.entgldb.network.discovery.UdpDiscoveryService
import com.entgldb.network.models.NodeAddress
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
        private val logger = mu.KotlinLogging.logger {}
        private const val SYNC_INTERVAL_MS = 10000L
    }

    private var syncJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    /**
     * Starts the sync orchestrator.
     */
    override fun start() {
        if (syncJob != null) return

        syncJob = scope.launch {
            while (isActive) {
                try {
                    syncWithPeers()
                } catch (e: Exception) {
                    logger.error(e) { "Sync cycle error" }
                }
                delay(SYNC_INTERVAL_MS)
            }
        }

        logger.info { "Sync Orchestrator started" }
    }

    /**
     * Stops the sync orchestrator.
     */
    override fun stop() {
        syncJob?.cancel()
        syncJob = null
        scope.cancel()
        logger.info { "Sync Orchestrator stopped" }
    }

    private suspend fun syncWithPeers() {
        val peers = discovery.getActivePeers()
        
        if (peers.isEmpty()) {
            logger.debug { "No active peers to sync with" }
            return
        }

        logger.debug { "Syncing with ${peers.size} peer(s)" }

        // Sync with random subset (gossip)
        val peersToSync = peers.shuffled().take(3)

        coroutineScope {
            peersToSync.forEach { peer ->
                launch {
                    try {
                        syncWithPeer(peer.nodeId, peer.address)
                    } catch (e: Exception) {
                        logger.error(e) { "Sync error with ${peer.nodeId}" }
                    }
                }
            }
        }
    }

    private suspend fun syncWithPeer(peerNodeId: String, address: String) {
        val nodeAddress = NodeAddress.parse(address)
        logger.info { "Connecting to peer $peerNodeId at ${nodeAddress.host}:${nodeAddress.port}..." }
        
        val channel = runCatching {
            client.connect(nodeAddress)
        }.getOrElse { 
            logger.warn { "Failed to connect to $peerNodeId: ${it.message}" }
            return
        }
        
        if (channel == null) {
            logger.warn { "Connection returned null channel for $peerNodeId" }
            return
        }

        try {
            // 1. Application Layer Handshake
            logger.debug { "Performing Application Handshake with $peerNodeId..." }
            val handshakeReqBuilder = Sync.HandshakeRequest.newBuilder()
                .setNodeId(nodeId)
                .setAuthToken(authToken)
            
            if (CompressionHelper.isBrotliSupported) {
                handshakeReqBuilder.addSupportedCompression("brotli")
            }

            channel.sendMessage(Sync.MessageType.HandshakeReq, handshakeReqBuilder.build())
            
            val (type, payload) = channel.readMessage()
            if (type != Sync.MessageType.HandshakeRes) {
                logger.error { "Handshake failed. Expected HandshakeRes, got $type" }
                return
            }
            
            val handshakeRes = Sync.HandshakeResponse.parseFrom(payload)
            if (!handshakeRes.accepted) {
                logger.error { "Handshake rejected by peer" }
                return
            }
            
            if (handshakeRes.selectedCompression == "brotli") {
                channel.useCompression = true
                logger.info { "Negotatied Brotli compression with $peerNodeId" }
            }

            logger.info { "Handshake successful with $peerNodeId" }

            // 2. Exchange Vector Clocks
            logger.debug { "Exchanging Vector Clocks..." }
            channel.sendMessage(Sync.MessageType.GetVectorClockReq, Sync.GetVectorClockRequest.getDefaultInstance())

            val (vcType, vcPayload) = channel.readMessage()
            if (vcType != Sync.MessageType.VectorClockRes) {
                 logger.error { "Expected VectorClockRes, got $vcType" }
                 return
            }

            val vcRes = Sync.VectorClockResponse.parseFrom(vcPayload)
            val remoteVCMap = vcRes.entriesList.associate { it ->
                it.nodeId to com.entgldb.core.HlcTimestamp(it.hlcWall, it.hlcLogic, it.nodeId)
            }
            val remoteVC = com.entgldb.core.VectorClock(remoteVCMap)
            val localVC = store.getVectorClock()

            // 3. Determine Sync Actions
            // Pull: Nodes where Remote is Ahead of Local
            val nodesToPull = localVC.getNodesWithUpdates(remoteVC)
            
            // Push: Nodes where Local is Ahead of Remote
            val nodesToPush = localVC.getNodesToPush(remoteVC)

            logger.info { "Sync Check with $peerNodeId: Pull=${nodesToPull.size} nodes, Push=${nodesToPush.size} nodes" }

            // 4. Pull Changes
            for (targetNodeId in nodesToPull) {
                val sinceTs = localVC.getTimestamp(targetNodeId) 
                    ?: com.entgldb.core.HlcTimestamp(0, 0, targetNodeId) // Default if missing
                
                logger.debug { "Pulling changes for $targetNodeId since $sinceTs" }

                val pullReq = Sync.PullChangesRequest.newBuilder()
                    .setSinceWall(sinceTs.physicalTime)
                    .setSinceLogic(sinceTs.logicalCounter)
                    .setSinceNode(sinceTs.nodeId) // Asking for this node's timeline
                    .build()

                channel.sendMessage(Sync.MessageType.PullChangesReq, pullReq)

                val (respType, respPayload) = channel.readMessage()
                if (respType != Sync.MessageType.ChangeSetRes) {
                    logger.warn { "Expected ChangeSetRes for $targetNodeId, got $respType" }
                    continue
                }

                val changeSet = Sync.ChangeSetResponse.parseFrom(respPayload)
                if (changeSet.entriesCount > 0) {
                     val mappedEntries = changeSet.entriesList.map { proto -> mapProtoToDomain(proto) }
                     processInboundBatch(targetNodeId, mappedEntries, channel)
                }
            }

            // 5. Push Changes
            for (targetNodeId in nodesToPush) {
                val remoteSinceTs = remoteVC.getTimestamp(targetNodeId)
                    ?: com.entgldb.core.HlcTimestamp(0, 0, targetNodeId)

                // Get changes from OUR store originating from targetNodeId that are newer than remoteSinceTs
                val changes = store.getOplogForNodeAfter(targetNodeId, remoteSinceTs)

                if (changes.isNotEmpty()) {
                    logger.debug { "Pushing ${changes.size} changes for $targetNodeId to peer" }
                    val pushReq = Sync.PushChangesRequest.newBuilder()
                        .addAllEntries(changes.map { mapDomainToProto(it) })
                        .build()

                    channel.sendMessage(Sync.MessageType.PushChangesReq, pushReq)
                    
                    val (ackType, _) = channel.readMessage()
                    if (ackType != Sync.MessageType.AckRes) {
                        logger.warn { "Push failed for $targetNodeId, expected AckRes got $ackType" }
                    }
                }
            }



            logger.info { "Sync cycle completed with ${nodeAddress}" }

        } catch (e: Exception) {
             logger.error(e) { "Sync protocol error with $address" }
        }
    }

    private suspend fun processInboundBatch(nodeId: String, entries: List<com.entgldb.core.OplogEntry>, channel: PeerChannel) {
        // Validate Hash Chain
        // We process sequentially.
        // For each entry:
        //   Calculate Hash
        //   Check PreviousHash == LastHash (from store or previous in batch)
        
        // This is complex because we might need to "recover" a gap.
        // Simplified Logic:
        // Try to apply. If store rejects due to hash mismatch? 
        // Or check manually here?

        // Let's check manually here against Store's last hash.
        // But store might be updated by other syncs? Protocol assumes single writer per node-lane usually,
        // or we lock.

        var validBatch = mutableListOf<com.entgldb.core.OplogEntry>()
        // We need the last hash for this node from the store
        var lastKnownHash = store.getLastEntryHash(nodeId)

        for (entry in entries) {
            // 1. Verify Entry Integrity (Hash matches content)
            val computedHash = com.entgldb.core.CryptoUtils.computeOplogHash(entry)
            
            if (computedHash != entry.hash) {
                logger.error { "Hash Integrity Check Failed for ${entry.key}. Computed=$computedHash, Declared=${entry.hash}" }
                // Stop processing batch? or Skip? Stop to be safe.
                break  
            }

            // 2. Verify Chain (PreviousHash matches LastHash)
            if (entry.previousHash != lastKnownHash) {
                logger.warn { "Hash Chain Gap Detected for $nodeId! EntryPrev=${entry.previousHash}, LocalLast=$lastKnownHash" }
                
                // Gap Recovery
                if (recoverGap(nodeId, lastKnownHash, channel)) {
                    // re-fetch last hash after recovery
                    lastKnownHash = store.getLastEntryHash(nodeId)
                    // Re-check this entry
                    if (entry.previousHash == lastKnownHash) {
                        validBatch.add(entry)
                        lastKnownHash = entry.hash
                    } else {
                        logger.error { "Gap recovery failed to align chain. Stopping batch." }
                        break
                    }
                } else {
                     break
                }
            } else {
                // Chain valid
                validBatch.add(entry)
                lastKnownHash = entry.hash
            }
        }
        
        if (validBatch.isNotEmpty()) {
            store.applyRemoteChanges(validBatch)
        }
    }
    
    private suspend fun recoverGap(nodeId: String, localLastHash: String, channel: PeerChannel): Boolean {
         logger.info { "Attempting Gap Recovery for $nodeId starting from hash $localLastHash" }
         // We need to find where we are.
         // Actually, we ask the peer: "Give me the missing link".
         // Use getChainRange? 
         // Strategy: Ask for defaults, or ask for "Chain since my last timestamp".
         // Actually, PullReq already asked "Since my last timestamp".
         // If we got a gap, it means:
         // A) We are missing intermediate entries that were NOT sent? (Should not happen if peer is honest and has history)
         // B) Our last hash is WRONG (corruption)?
         // C) Peer sent entries in wrong order? (They sort by timestamp usually).
         
         // Assuming Peer is honest.
         // If Peer sent [Entry N+2], and we have [Entry N], we need [Entry N+1].
         // PullReq asked for "Since T_N". Peer should have sent N+1.
         // Maybe N+1 is missing on Peer? Then Peer has a gap too.
         
         // Let's try to ask for a range of generic entries using GetChainRange if supported?
         // Actually, if we just pull again with correct timestamp?
         // Maybe our "LastTimestamp" doesn't match "LastHash" (fork)?
         
         // For now, log and fail. Implementing full iteractive recovery is complex.
         return false
    }

    private fun mapProtoToDomain(proto: Sync.ProtoOplogEntry): com.entgldb.core.OplogEntry {
        return com.entgldb.core.OplogEntry(
            collection = proto.collection,
            key = proto.key,
            operation = com.entgldb.core.OperationType.valueOf(proto.operation),
            payload = if (proto.jsonData.isNotEmpty()) com.entgldb.core.common.JsonHelpers.parse(proto.jsonData) else null,
            timestamp = com.entgldb.core.HlcTimestamp(proto.hlcWall, proto.hlcLogic, proto.hlcNode),
            hash = proto.hash,
            previousHash = proto.previousHash
        )
    }

    private fun mapDomainToProto(domain: com.entgldb.core.OplogEntry): Sync.ProtoOplogEntry {
        return Sync.ProtoOplogEntry.newBuilder()
            .setCollection(domain.collection)
            .setKey(domain.key)
            .setOperation(domain.operation.name)
            .setJsonData(domain.payload?.toString() ?: "")
            .setHlcWall(domain.timestamp.physicalTime)
            .setHlcLogic(domain.timestamp.logicalCounter)
            .setHlcNode(domain.timestamp.nodeId)
            .setHash(domain.hash)
            .setPreviousHash(domain.previousHash)
            .build()
    }
}
