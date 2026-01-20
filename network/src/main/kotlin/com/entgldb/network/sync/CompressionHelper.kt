package com.entgldb.network.sync

import org.brotli.dec.BrotliInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

object CompressionHelper {
    const val THRESHOLD = 1024
    
    // Using pure Java Brotli decoder for Android compatibility
    // Note: org.brotli:dec only supports decompression
    val isBrotliSupported: Boolean = true

    @Throws(IOException::class)
    fun decompress(data: ByteArray): ByteArray {
        ByteArrayInputStream(data).use { bis ->
            BrotliInputStream(bis).use { brotli ->
                return brotli.readBytes()
            }
        }
    }

    @Throws(IOException::class)
    fun compress(data: ByteArray): ByteArray {
        // org.brotli:dec doesn't support compression
        // For now, return uncompressed data
        // TODO: Consider adding a compression library that supports both Android and compression
        return data 
    }
}
