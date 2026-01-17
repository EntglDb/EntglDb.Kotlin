package com.entgldb.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

enum class OperationType {
    Put,
    Delete
}

@Serializable
data class OplogEntry(
    val collection: String,
    val key: String,
    val operation: OperationType,
    val payload: JsonElement?,
    val timestamp: HlcTimestamp
)
