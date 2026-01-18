package com.entgldb.network.sync

import com.entgldb.network.proto.SecureEnvelope
import com.entgldb.network.proto.MessageType
import com.entgldb.network.security.CryptoHelper
import com.google.protobuf.ByteString
import com.google.protobuf.MessageLite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Handles the wire protocol framing and encryption.
 * Wire format: [Length (4 bytes LE)] [Type (1 byte)] [Comp (1 byte)] [Payload (Length bytes)]
 * Note: Payload length excludes Length header, but includes Type & Comp.
 * Wait, actually Length covers (Type + Comp + Payload)?
 * In .NET: "length = BitConverter.GetBytes(payloadBytes.Length);"
 * And payloadBytes was just payload.
 * Then "WriteAsync(length); WriteByte(type); WriteByte(flag); WriteAsync(payloadBytes)".
 * So "Length" only covers "PayloadBytes".
 * The reader reads Length, then Type, then Flag, then Payload(Length).
 * So Length matches Payload size exactly. Correct.
 */
class SecureChannel(
    private val input: InputStream,
    private val output: OutputStream,
    private val encryptKey: ByteArray? = null,
    private val decryptKey: ByteArray? = null
) {
    var useCompression: Boolean = false

    /**
     * Sends a message with proper framing and encryption.
     */
    suspend fun sendMessage(type: MessageType, message: MessageLite) {
        withContext(Dispatchers.IO) {
            var finalType = type.number
            var payloadBytes = message.toByteArray()
            var compressionFlag: Byte = 0x00

            // 1. Compress (if enabled & large & not invalid type)
            if (useCompression && payloadBytes.size > CompressionHelper.THRESHOLD) {
                val compressed = CompressionHelper.compress(payloadBytes)
                if (compressed.size < payloadBytes.size) { 
                     payloadBytes = compressed
                     compressionFlag = 0x01
                }
            }

            if (encryptKey != null) {
                // Encrypt payload: [Type (1 byte)] + [Compression (1 byte)] + [Original/Compressed Payload]
                val dataToEncrypt = ByteArray(2 + payloadBytes.size)
                dataToEncrypt[0] = finalType.toByte()
                dataToEncrypt[1] = compressionFlag
                System.arraycopy(payloadBytes, 0, dataToEncrypt, 2, payloadBytes.size)

                val encryptedData = CryptoHelper.encryptAesGcm(dataToEncrypt, encryptKey)
                
                // Parse encrypted data into (IV + Ciphertext + Tag)
                val ivLength = 12
                val tagLength = 16 
                
                val iv = encryptedData.copyOfRange(0, ivLength)
                val cipherWithTag = encryptedData.copyOfRange(ivLength, encryptedData.size)
                val tag = cipherWithTag.copyOfRange(cipherWithTag.size - tagLength, cipherWithTag.size)
                val ciphertext = cipherWithTag.copyOfRange(0, cipherWithTag.size - tagLength)

                val env = SecureEnvelope.newBuilder()
                    .setCiphertext(ByteString.copyFrom(ciphertext))
                    .setNonce(ByteString.copyFrom(iv))
                    .setAuthTag(ByteString.copyFrom(tag))
                    .build()

                payloadBytes = env.toByteArray()
                finalType = MessageType.SecureEnv.number
                compressionFlag = 0x00 // Outer envelope is not compressed
            }

            // Write Length (Little Endian)
            val lengthBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            lengthBuffer.putInt(payloadBytes.size)
            output.write(lengthBuffer.array())

            // Write Type
            output.write(finalType)

            // Write Compression Flag (v0.7.0)
            output.write(compressionFlag.toInt())

            // Write Payload
            output.write(payloadBytes)
            output.flush()
        }
    }

    /**
     * Reads a message, decrypts if necessary.
     */
    suspend fun readMessage(): Pair<MessageType, ByteArray> {
        return withContext(Dispatchers.IO) {
            // Read Length (LE)
            val lenBuf = ByteArray(4)
            readFully(lenBuf)
            val length = ByteBuffer.wrap(lenBuf).order(ByteOrder.LITTLE_ENDIAN).int

            // Read Type
            val typeByte = input.read()
            if (typeByte == -1) throw java.io.EOFException("Connection closed")

            // Read Compression Flag
            val compByte = input.read()
            if (compByte == -1) throw java.io.EOFException("Connection closed (missing comp flag)")
            
            // Read Payload
            var payload = ByteArray(length)
            readFully(payload)

            var msgType = MessageType.forNumber(typeByte) ?: MessageType.Unknown

            if (msgType == MessageType.SecureEnv) {
                if (decryptKey == null) throw IllegalStateException("Received encrypted message but no keys established")

                val env = SecureEnvelope.parseFrom(payload)
                
                // Reconstruct for Java Decrypt: [IV] + [Ciphertext] + [Tag]
                val iv = env.nonce.toByteArray()
                val ciphertext = env.ciphertext.toByteArray()
                val tag = env.authTag.toByteArray()
                
                val encryptedData = ByteArray(iv.size + ciphertext.size + tag.size)
                System.arraycopy(iv, 0, encryptedData, 0, iv.size)
                System.arraycopy(ciphertext, 0, encryptedData, iv.size, ciphertext.size)
                System.arraycopy(tag, 0, encryptedData, iv.size + ciphertext.size, tag.size)
                
                val decrypted = CryptoHelper.decryptAesGcm(encryptedData, decryptKey)
                
                // Decrypted: [Type (1 byte)] + [Compression (1 byte)] + [Payload]
                if (decrypted.size < 2) throw IllegalStateException("Decrypted payload too short")
                
                val innerType = decrypted[0].toInt()
                val innerComp = decrypted[1].toInt()
                msgType = MessageType.forNumber(innerType) ?: MessageType.Unknown
                
                val innerPayload = ByteArray(decrypted.size - 2)
                System.arraycopy(decrypted, 2, innerPayload, 0, innerPayload.size)
                
                if (innerComp == 0x01) {
                    val decompressed = CompressionHelper.decompress(innerPayload)
                    return@withContext Pair(msgType, decompressed)
                }
                
                return@withContext Pair(msgType, innerPayload)
            }

            if (compByte == 0x01) {
                payload = CompressionHelper.decompress(payload)
            }

            Pair(msgType, payload)
        }
    }

    private fun readFully(buffer: ByteArray) {
        var total = 0
        while (total < buffer.size) {
            val read = input.read(buffer, total, buffer.size - total)
            if (read == -1) throw java.io.EOFException("Connection closed")
            total += read
        }
    }
}
