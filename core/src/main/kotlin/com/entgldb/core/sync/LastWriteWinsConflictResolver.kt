package com.entgldb.core.sync

import com.entgldb.core.Document
import com.entgldb.core.OplogEntry
import com.entgldb.core.OperationType
import kotlinx.serialization.json.JsonObject

class LastWriteWinsConflictResolver : IConflictResolver {
    override fun resolve(local: Document?, remote: OplogEntry): ConflictResolutionResult {
        // If no local document exists, always apply remote change
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

        // If local exists, compare timestamps
        if (remote.timestamp.compareTo(local.updatedAt) > 0) {
            // Remote is newer, apply it
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

        // Local is newer or equal, ignore remote
        return ConflictResolutionResult.ignore()
    }
}
