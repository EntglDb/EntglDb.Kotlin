package com.entgldb.core

import com.entgldb.core.query.QueryBuilder
import com.entgldb.core.query.QueryNode
import com.entgldb.core.query.query
import com.entgldb.core.storage.IPeerStore

class PeerCollection(
    private val store: IPeerStore,
    val name: String
) {

    suspend fun find(query: QueryNode): List<Document> {
        return store.queryDocuments(collection = name, filter = query)
    }

    suspend fun find(init: QueryBuilder.() -> Unit): List<Document> {
        val queryNode = query(init)
        return find(queryNode)
    }

    suspend fun save(document: Document) {
        if (document.collection != name) throw IllegalArgumentException("Document collection mismatch")
        store.saveDocument(document)
    }

    suspend fun get(key: String): Document? {
        return store.getDocument(name, key)
    }
}
