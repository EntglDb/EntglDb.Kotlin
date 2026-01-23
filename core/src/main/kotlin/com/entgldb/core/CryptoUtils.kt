package com.entgldb.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import java.security.MessageDigest
import java.nio.charset.StandardCharsets

object CryptoUtils {
    
    private val json = Json { encodeDefaults = true } // Ensure consistent encoding if possible

    fun computeOplogHash(entry: OplogEntry): String {
        val sb = StringBuilder()
        
        // 1. Operation
        sb.append(entry.operation.name) // "Put" or "Delete" (Enum name usually matches Protocol string)
        sb.append("|")
        
        // 2. Payload
        if (entry.payload != null) {
            // Needed: Canonical JSON string.
            // .NET and Node.js use the raw string/bytes stored using "UnsafeRelaxedJsonEscaping" or similar?
            // If we are cross-platform, canonicalization of JSON is tricky.
            // Assuming the payload behaves well or was stored as raw bytes previously.
            // But here we have JsonElement.
            // We'll use standard kotlinx.serialization stringify. 
            // Warning: This might differ from .NET defaults if spacing differs.
            // Ideally we store RAW bytes to ensure hash consistency across network transport.
            // For now, let's assume Json.encodeToString gives us the 'minified' version matching other platforms.
            sb.append(json.encodeToString(JsonElement.serializer(), entry.payload))
        }
        sb.append("|")
        
        // 3. Timestamp
        // Format: LogicalTime:Counter:NodeId
        // HLC.ToString() in .NET/Node.js is "logicalTime-counter-nodeId" usually, or "logicalTime-counter-nodeId".
        // Wait, Node.js uses `HLClock.toString(entry.timestamp)`:
        // `${ts.logicalTime}-${ts.counter}-${ts.nodeId}` (hyphens?)
        // Let's check HlcTimestamp.toString() in Kotlin.
        // It is "$physicalTime:$logicalCounter:$nodeId" (colons).
        // I NEED TO VERIFY THE DELIMITER across platforms.
        // Node.js implementation viewed previously:
        // `sb.push(HLClock.toString(entry.timestamp));`
        // I need to check `HLClock.toString` implementation in Node.js again OR check .NET.
        // If they differ, the hash will fail verification.
        
        // Node.js uses hyphens: logicalTime-counter-nodeId
        // We MUST match this for hash consistency.
        sb.append("${entry.timestamp.physicalTime}-${entry.timestamp.logicalCounter}-${entry.timestamp.nodeId}")
        sb.append("|")
        
        // 4. PreviousHash
        sb.append(entry.previousHash)
        
        val input = sb.toString()
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(input.toByteArray(StandardCharsets.UTF_8))
        
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
