package com.entgldb.network.sync

import com.aayushatharva.brotli4j.Brotli4jLoader
import com.aayushatharva.brotli4j.decoder.BrotliInputStream
import com.aayushatharva.brotli4j.encoder.BrotliOutputStream
import com.aayushatharva.brotli4j.encoder.Encoder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException

object CompressionHelper {
    const val THRESHOLD = 1024
    
    init {
        try {
            Brotli4jLoader.ensureAvailability()
        } catch (e: Throwable) {
            // Log it?
            // e.printStackTrace()
        }
    }

    val isBrotliSupported: Boolean
        get() = try { Brotli4jLoader.isAvailable() } catch (e: Throwable) { false }

    @Throws(IOException::class)
    fun decompress(data: ByteArray): ByteArray {
        if (!isBrotliSupported) throw IOException("Brotli native library not available")
        
        ByteArrayInputStream(data).use { bis ->
            BrotliInputStream(bis).use { brotli ->
                return brotli.readBytes()
            }
        }
    }

    @Throws(IOException::class)
    fun compress(data: ByteArray): ByteArray {
        if (!isBrotliSupported) return data 
        
        val bos = ByteArrayOutputStream()
        // Default quality is -1 (default), can set explicitly
        val params = Encoder.Parameters().setQuality(4) 
        
        BrotliOutputStream(bos, params).use { brotli ->
            brotli.write(data)
        }
        return bos.toByteArray()
    }
}
