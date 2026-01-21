package com.entgldb.core

import com.entgldb.core.query.QueryBuilder
import com.entgldb.core.query.QueryNode
import com.entgldb.core.query.query
import com.entgldb.core.storage.IPeerStore

class PeerCollection(
    private val store: IPeerStore,
    val name: String,
    private val db: PeerDatabase
) {

    suspend fun find(query: QueryNode): List<Document> {
        return store.queryDocuments(collection = name, filter = query)
    }

    suspend fun find(init: QueryBuilder.() -> Unit): List<Document> {
        val queryNode = query(init)
        return find(queryNode)
    }

    suspend fun put(key: String, content: kotlinx.serialization.json.JsonElement) {
        val timestamp = db.tick()
        // content is already JsonElement
        
        val document = Document(
            collection = name,
            key = key,
            content = content,
            updatedAt = timestamp,
            isDeleted = false
        )
        
        val oplog = OplogEntry(
            collection = name,
            key = key,
            operation = com.entgldb.core.OperationType.Put,
            payload = content, 
            timestamp = timestamp
        )
        
        store.applyBatch(listOf(document), listOf(oplog))
    }
    
    suspend fun get(key: String): Document? {
        return store.getDocument(name, key)
    }
    
    suspend fun delete(key: String) {
        val timestamp = db.tick()
        // For deleted document, content might need to be empty object or null?
        // Document definition says content is JsonElement (non-null).
        // Let's use empty JsonObject.
        val emptyContent = kotlinx.serialization.json.JsonObject(emptyMap())

         val document = Document(
            collection = name,
            key = key,
            content = emptyContent, 
            updatedAt = timestamp,
            isDeleted = true
        )
         val oplog = OplogEntry(
            collection = name,
            key = key,
            operation = com.entgldb.core.OperationType.Delete,
            payload = null,
            timestamp = timestamp
        )
        store.applyBatch(listOf(document), listOf(oplog))
    }
}
