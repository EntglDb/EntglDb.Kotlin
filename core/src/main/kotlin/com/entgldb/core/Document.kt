package com.entgldb.core

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class Document(
    val collection: String,
    val key: String,
    val content: JsonElement,
    val updatedAt: HlcTimestamp,
    val isDeleted: Boolean
)
