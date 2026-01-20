package com.entgldb.network.sync

import org.brotli.dec.BrotliInputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

object CompressionHelper {
    const val THRESHOLD = 1024
    
    // Using pure Java Brotli decoder for Android compatibility
    // Note: org.brotli:dec only supports decompression, not compression
    // Setting to false to prevent compression negotiation
    val isBrotliSupported: Boolean = false

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
        // Return uncompressed data since isBrotliSupported is false
        return data 
    }
}
