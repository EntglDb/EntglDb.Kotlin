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

    suspend fun applyBatch(documents: List<Document>, oplogEntries: List<OplogEntry>)

    suspend fun queryDocuments(
        collection: String, 
        filterJson: String? = null,
        skip: Int? = null,
        take: Int? = null,
        orderBy: String? = null,
        ascending: Boolean = true
    ): List<Document>

    suspend fun countDocuments(collection: String, filterJson: String? = null): Int

    suspend fun getCollections(): List<String>
    suspend fun ensureIndex(collection: String, propertyPath: String)
}
