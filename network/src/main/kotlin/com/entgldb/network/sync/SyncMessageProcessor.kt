package com.entgldb.network.sync

import EntglDb.Network.Proto.Sync
import com.entgldb.core.HlcTimestamp
import com.entgldb.core.OplogEntry
import com.entgldb.core.OperationType
import com.entgldb.core.common.JsonHelpers
import com.entgldb.core.storage.IPeerStore
import kotlinx.coroutines.flow.first

class SyncMessageProcessor(
    private val store: IPeerStore,
    private val nodeId: String
) {
    companion object {
        private const val TAG = "SyncMessageProcessor"
        private val logger = mu.KotlinLogging.logger {}
    }

    suspend fun process(type: Sync.MessageType, payload: ByteArray): Pair<Sync.MessageType, Any>? {
        return when (type) {
            Sync.MessageType.HandshakeReq -> {
                // Handshake is handled by Server directly, but if it leaks here:
                logger.warn { "Unexpected HandshakeReq in processor" }
                null
            }
            Sync. MessageType.GetClockReq -> {
                val latest = store.getLatestTimestamp()
                val res = Sync.ClockResponse.newBuilder()
                    .setHlcWall(latest.physicalTime)
                    .setHlcLogic(latest.logicalCounter)
                    .setHlcNode(latest.nodeId)
                    .build()
                Pair(Sync.MessageType.ClockRes, res)
            }
            Sync.MessageType.PullChangesReq -> {
                val req = Sync.PullChangesRequest.parseFrom(payload)
                val since = HlcTimestamp(req.sinceWall, req.sinceLogic, req.sinceNode)
                

                
                logger.debug { "Processing PullChanges since $since" }
                
                val entries = store.getOplogAfter(since)
                

                
                val resBuilder = Sync.ChangeSetResponse.newBuilder()
                entries.forEach { entry ->
                    resBuilder.addEntries(
                        Sync.ProtoOplogEntry.newBuilder()
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
                
                // Also sending current max clock? Not explicitly in proto but implicit in entries.
                Pair(Sync.MessageType.ChangeSetRes, resBuilder.build())
            }
            Sync.MessageType.PushChangesReq -> {
                val req = Sync.PushChangesRequest.parseFrom(payload)
                logger.debug { "Processing PushChanges with ${req.entriesCount} entries" }
                
                val entries = req.entriesList.map { proto ->
                    OplogEntry(
                        collection = proto.collection,
                        key = proto.key,
                        operation = OperationType.valueOf(proto.operation),
                        payload = if (proto.jsonData.isNotEmpty()) JsonHelpers.parse(proto.jsonData) else null,
                        timestamp = HlcTimestamp(proto.hlcWall, proto.hlcLogic, proto.hlcNode)
                    )
                }
                
                store.applyRemoteChanges(entries)
                
                val res = Sync.AckResponse.newBuilder().setSuccess(true).build()
                Pair(Sync.MessageType.AckRes, res)
            }
            else -> {
                logger.warn { "Unknown message type: $type" }
                null
            }
        }
    }
}
