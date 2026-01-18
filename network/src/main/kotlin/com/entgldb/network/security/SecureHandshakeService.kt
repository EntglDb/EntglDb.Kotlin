package com.entgldb.network.security

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.io.OutputStream
import com.entgldb.network.sync.readFully

/**
 * Secure handshake service implementing ECDH key exchange compatible with EntglDb.Net.
 */
class SecureHandshakeService : IPeerHandshakeService {
    
    companion object {
        private const val TAG = "SecureHandshake"
    }

    override suspend fun performHandshake(
        input: InputStream,
        output: OutputStream,
        isInitiator: Boolean
    ): CipherState {
        return withContext(Dispatchers.IO) {
            Log.d(TAG, "Starting Secure Handshake. Initiator: $isInitiator")
            
            // 1. Generate Ephemeral Keys
            val keyPair = CryptoHelper.generateEcdhKeyPair()
            val myPublicKey = CryptoHelper.encodePublicKey(keyPair.public) // 65 bytes
            
            // 2. Exchange Public Keys
            Log.d(TAG, "Sending my Public Key (${myPublicKey.size} bytes)")
            
            // Write my key
            output.write(myPublicKey)
            output.flush()
            
            // Read peer key
            val peerPublicKeyBytes = ByteArray(65)
            Log.d(TAG, "Waiting for Peer Public Key...")
            try {
                input.readFully(peerPublicKeyBytes)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to read Peer Public Key", e)
                throw e
            }
            Log.d(TAG, "Received Peer Public Key")
            
            val peerPublicKey = CryptoHelper.decodePublicKey(peerPublicKeyBytes)
            
            // 3. Compute Shared Secret
            val sharedSecret = CryptoHelper.computeSharedSecret(keyPair.private, peerPublicKey)
            Log.d(TAG, "Shared Secret computed (${sharedSecret.size} bytes)")
            
            // 4. Derive Keys (HKDF-ish)
            // .NET Impl uses simple SHA256 expansion:
            // Initiator Encrypts with Key derived from 0x00, Decrypts with 0x01
            // Responder Encrypts with Key derived from 0x01, Decrypts with 0x00
            
            val encryptInfo = if (isInitiator) 0x00.toByte() else 0x01.toByte()
            val decryptInfo = if (isInitiator) 0x01.toByte() else 0x00.toByte()
            
            val encryptKey = CryptoHelper.deriveKey(sharedSecret, encryptInfo)
            val decryptKey = CryptoHelper.deriveKey(sharedSecret, decryptInfo)
            
            Log.i(TAG, "Handshake Completed. Keys derived.")
            Log.v(TAG, "EncryptKey: ${encryptKey.joinToString("") { "%02x".format(it).take(4) }}...") // Log partial key for debug
            
            CipherState(encryptKey, decryptKey)
        }
    }
}
