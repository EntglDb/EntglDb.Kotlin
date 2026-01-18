package com.entgldb.network.sync

import android.util.Log
import com.entgldb.core.HlcTimestamp
import com.entgldb.core.OplogEntry
import com.entgldb.core.OperationType
import com.entgldb.core.common.JsonHelpers
import com.entgldb.core.storage.IPeerStore
import com.entgldb.network.proto.*
import kotlinx.coroutines.flow.first

class SyncMessageProcessor(
    private val store: IPeerStore,
    private val nodeId: String
) {
    companion object {
        private const val TAG = "SyncMessageProcessor"
    }

    suspend fun process(type: MessageType, payload: ByteArray): Pair<MessageType, Any>? {
        return when (type) {
            MessageType.HandshakeReq -> {
                // Handshake is handled by Server directly, but if it leaks here:
                Log.w(TAG, "Unexpected HandshakeReq in processor")
                null
            }
            MessageType.GetClockReq -> {
                val latest = store.getLatestTimestamp()
                val res = ClockResponse.newBuilder()
                    .setHlcWall(latest.physicalTime)
                    .setHlcLogic(latest.logicalCounter)
                    .setHlcNode(latest.nodeId)
                    .build()
                Pair(MessageType.ClockRes, res)
            }
            MessageType.PullChangesReq -> {
                val req = PullChangesRequest.parseFrom(payload)
                val since = HlcTimestamp(req.sinceWall, req.sinceLogic, req.sinceNode)
                
                Log.d(TAG, "Processing PullChanges since $since")
                
                val entries = store.getOplogAfter(since)
                
                val resBuilder = ChangeSetResponse.newBuilder()
                entries.forEach { entry ->
                    resBuilder.addEntries(
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
                
                // Also sending current max clock? Not explicitly in proto but implicit in entries.
                Pair(MessageType.ChangeSetRes, resBuilder.build())
            }
            MessageType.PushChangesReq -> {
                val req = PushChangesRequest.parseFrom(payload)
                Log.d(TAG, "Processing PushChanges with ${req.entriesCount} entries")
                
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
                
                val res = AckResponse.newBuilder().setSuccess(true).build()
                Pair(MessageType.AckRes, res)
            }
            else -> {
                Log.w(TAG, "Unknown message type: $type")
                null
            }
        }
    }
}
