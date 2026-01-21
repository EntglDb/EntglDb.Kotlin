package com.entgldb.core.storage

import com.entgldb.core.Document
import com.entgldb.core.HlcTimestamp
import com.entgldb.core.OplogEntry
import kotlinx.coroutines.flow.Flow

interface IPeerStore {
    val changesApplied: Flow<List<String>> // List of modified collections or keys

    suspend fun saveDocument(document: Document)
    suspend fun getDocument(collection: String, key: String): Document?

    suspend fun appendOplogEntry(entry: OplogEntry)
    suspend fun getOplogAfter(timestamp: HlcTimestamp): List<OplogEntry>
    suspend fun getLatestTimestamp(): HlcTimestamp
    suspend fun applyRemoteChanges(changes: List<OplogEntry>)

    suspend fun applyBatch(documents: List<Document>, oplogEntries: List<OplogEntry>)

    suspend fun queryDocuments(
        collection: String, 
        filter: com.entgldb.core.query.QueryNode? = null,
        skip: Int? = null,
        take: Int? = null,
        orderBy: String? = null,
        ascending: Boolean = true
    ): List<Document>

    suspend fun countDocuments(collection: String, filter: com.entgldb.core.query.QueryNode? = null): Int

    suspend fun getCollections(): List<String>
    suspend fun ensureIndex(collection: String, propertyPath: String)

    suspend fun getRemotePeers(): List<com.entgldb.core.network.PeerNode>
    suspend fun saveRemotePeer(peer: com.entgldb.core.network.PeerNode)
    suspend fun removeRemotePeer(nodeId: String)
}
