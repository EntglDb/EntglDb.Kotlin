package com.entgldb.core.sync

import com.entgldb.core.Document
import com.entgldb.core.HlcTimestamp
import com.entgldb.core.OplogEntry
import com.entgldb.core.OperationType
import kotlinx.serialization.json.*

class RecursiveMergeConflictResolver : IConflictResolver {

    override fun resolve(local: Document?, remote: OplogEntry): ConflictResolutionResult {
        // 1. Handle non-existent local document (Create)
        if (local == null) {
            val content = remote.payload ?: JsonObject(emptyMap())
            val newDoc = Document(
                remote.collection,
                remote.key,
                content,
                remote.timestamp,
                remote.operation == OperationType.Delete
            )
            return ConflictResolutionResult.apply(newDoc)
        }

        // 2. Handle Remote Deletion
        if (remote.operation == OperationType.Delete) {
            // LWW for deletion: if remote is newer, delete.
            if (remote.timestamp > local.updatedAt) {
                val newDoc = local.copy(
                    isDeleted = true,
                    updatedAt = remote.timestamp,
                    content = JsonObject(emptyMap()) // Clear content on delete? Or keep tombstone? .NET clears it to default.
                )
                return ConflictResolutionResult.apply(newDoc)
            }
            return ConflictResolutionResult.ignore()
        }

        // 3. Handle Recursive Merge
        val localJson = local.content
        val remoteJson = remote.payload ?: JsonObject(emptyMap())
        val localTs = local.updatedAt
        val remoteTs = remote.timestamp

        // If types mismatch (e.g. Object vs Array), or one is primitive, LWW at top level
        // But the .NET impl calls MergeJson immediately.
        
        val mergedJson = mergeJson(localJson, localTs, remoteJson, remoteTs)
        
        val maxTs = if (remoteTs > localTs) remoteTs else localTs
        val mergedDoc = local.copy(
            content = mergedJson.jsonObject, // Assuming root is always object for Document? Usually yes.
            updatedAt = maxTs,
            isDeleted = false
        )
        
        return ConflictResolutionResult.apply(mergedDoc)
    }

    private fun mergeJson(
        local: JsonElement, 
        localTs: HlcTimestamp, 
        remote: JsonElement, 
        remoteTs: HlcTimestamp
    ): JsonElement {
        // If types differ, LWW
        if (local::class != remote::class) {
            return if (remoteTs > localTs) remote else local
        }

        return when (local) {
            is JsonObject -> mergeObjects(local, localTs, remote as JsonObject, remoteTs)
            is JsonArray -> mergeArrays(local, localTs, remote as JsonArray, remoteTs)
            else -> {
                // Primitives: LWW
                if (local == remote) local
                else if (remoteTs > localTs) remote else local
            }
        }
    }

    private fun mergeObjects(
        local: JsonObject, 
        localTs: HlcTimestamp, 
        remote: JsonObject, 
        remoteTs: HlcTimestamp
    ): JsonElement {
        val allKeys = local.keys + remote.keys
        
        return buildJsonObject {
            for (key in allKeys) {
                val localVal = local[key]
                val remoteVal = remote[key]

                if (localVal != null && remoteVal != null) {
                    put(key, mergeJson(localVal, localTs, remoteVal, remoteTs))
                } else if (localVal != null) {
                    put(key, localVal)
                } else if (remoteVal != null) {
                    // Start of specific field from remote can be considered "new" info.
                    // But effectively it takes remote value.
                    put(key, remoteVal)
                }
            }
        }
    }

    private fun mergeArrays(
        local: JsonArray, 
        localTs: HlcTimestamp, 
        remote: JsonArray, 
        remoteTs: HlcTimestamp
    ): JsonElement {
        // Heuristic: Check if arrays are object arrays
        val localIsObj = local.all { it is JsonObject }
        val remoteIsObj = remote.all { it is JsonObject }
        
        if (!localIsObj || !remoteIsObj) {
            // Primitives or mixed -> LWW
            return if (remoteTs > localTs) remote else local
        }
        
        // Try ID mapping
        val localMap = mapById(local)
        val remoteMap = mapById(remote)
        
        if (localMap == null || remoteMap == null) {
            // No IDs found -> LWW
            return if (remoteTs > localTs) remote else local
        }
        
        // Merge by ID
        val allIds = localMap.keys + remoteMap.keys
        
        return buildJsonArray {
            allIds.forEach { id ->
                 val localItem = localMap[id]
                 val remoteItem = remoteMap[id]
                 
                 if (localItem != null && remoteItem != null) {
                     add(mergeJson(localItem, localTs, remoteItem, remoteTs))
                 } else if (localItem != null) {
                     add(localItem)
                 } else if (remoteItem != null) {
                     add(remoteItem)
                 }
            }
        }
    }
    
    private fun mapById(array: JsonArray): Map<String, JsonElement>? {
        val map = mutableMapOf<String, JsonElement>()
        for (item in array) {
            if (item !is JsonObject) return null
            
            val id = item["id"]?.jsonPrimitive?.content ?: item["_id"]?.jsonPrimitive?.content
            if (id == null) return null
            if (map.containsKey(id)) return null // Duplicate ID, abort strategy
            
            map[id] = item
        }
        return map
    }
}
