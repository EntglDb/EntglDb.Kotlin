package com.entgldb.core.common

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement

object JsonHelpers {
    val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
        encodeDefaults = true
    }

    fun parse(content: String): JsonElement {
        return json.parseToJsonElement(content)
    }
    
    fun stringify(element: JsonElement): String {
        return element.toString()
    }
}
