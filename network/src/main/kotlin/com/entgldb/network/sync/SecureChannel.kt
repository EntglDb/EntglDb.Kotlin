package com.entgldb.network.sync

import com.entgldb.network.proto.SecureEnvelope
import com.entgldb.network.proto.MessageType
import com.entgldb.network.security.CryptoHelper
import com.google.protobuf.ByteString
import com.google.protobuf.MessageLite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Handles the wire protocol framing and encryption.
 * Wire format: [Length (4 bytes LE)] [Type (1 byte)] [Payload (Length bytes)]
 */
class SecureChannel(
    private val input: InputStream,
    private val output: OutputStream,
    private val encryptKey: ByteArray? = null,
    private val decryptKey: ByteArray? = null
) {
    /**
     * Sends a message with proper framing and encryption.
     */
    suspend fun sendMessage(type: MessageType, message: MessageLite) {
        withContext(Dispatchers.IO) {
            var finalType = type.number
            var payloadBytes = message.toByteArray()

            if (encryptKey != null) {
                // Encrypt payload: [Type (1 byte)] + [Original Payload]
                val dataToEncrypt = ByteArray(1 + payloadBytes.size)
                dataToEncrypt[0] = finalType.toByte()
                System.arraycopy(payloadBytes, 0, dataToEncrypt, 1, payloadBytes.size)

                val encryptedData = CryptoHelper.encryptAesGcm(dataToEncrypt, encryptKey)
                
                // Parse encrypted data into (IV + Ciphertext + Tag)
                // CryptoHelper.encryptAesGcm returns [IV (12)] + [Ciphertext + Tag]
                // My Kotlin CryptoHelper.encryptAesGcm returns IV + Ciphertext(with tag).
                // I need to split the Kotlin result to populate SecureEnvelope correctly.
                
                val ivLength = 12
                // Java GCM tag length is usually 128 bit (16 bytes).
                val tagLength = 16 
                
                // encryptedData = [IV (12)] + [Ciphertext] + [Tag (16)] (Java standard default)
                
                val iv = encryptedData.copyOfRange(0, ivLength)
                val cipherWithTag = encryptedData.copyOfRange(ivLength, encryptedData.size)
                
                // .NET expects Tag separately?
                // Let's check .NET's CryptoHelper.Encrypt logic if possible.
                // Assuming standard AES-GCM.
                // If .NET uses BouncyCastle/standard, Tag is often separate.
                // I need to extract Tag from Java's ciphertext.
                
                val tag = cipherWithTag.copyOfRange(cipherWithTag.size - tagLength, cipherWithTag.size)
                val ciphertext = cipherWithTag.copyOfRange(0, cipherWithTag.size - tagLength)

                val env = SecureEnvelope.newBuilder()
                    .setCiphertext(ByteString.copyFrom(ciphertext))
                    .setNonce(ByteString.copyFrom(iv))
                    .setAuthTag(ByteString.copyFrom(tag))
                    .build()

                payloadBytes = env.toByteArray()
                finalType = MessageType.SecureEnv.number
            }

            // Write Length (Little Endian)
            val lengthBuffer = ByteBuffer.allocate(4).order(ByteOrder.LITTLE_ENDIAN)
            lengthBuffer.putInt(payloadBytes.size)
            output.write(lengthBuffer.array())

            // Write Type
            output.write(finalType)

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
            
            // Read Payload
            val payload = ByteArray(length)
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
                
                // Decrypted: [Type (1 byte)] + [Original Payload]
                if (decrypted.isEmpty()) throw IllegalStateException("Decrypted payload too short")
                
                val innerType = decrypted[0].toInt()
                msgType = MessageType.forNumber(innerType) ?: MessageType.Unknown
                
                val innerPayload = ByteArray(decrypted.size - 1)
                System.arraycopy(decrypted, 1, innerPayload, 0, innerPayload.size)
                
                return@withContext Pair(msgType, innerPayload)
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
