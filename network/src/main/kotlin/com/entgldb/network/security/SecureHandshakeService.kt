package com.entgldb.network.security

import android.util.Base64
import android.util.Log
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.security.KeyPair

/**
 * Secure handshake service implementing ECDH key exchange with authentication.
 */
class SecureHandshakeService(
    private val authToken: String
) : IPeerHandshakeService {
    
    companion object {
        private const val TAG = "SecureHandshake"
    }

    override suspend fun performClientHandshake(
        send: suspend (ByteArray) -> Unit,
        receive: suspend () -> ByteArray
    ): ByteArray? {
        try {
            // Generate EC key pair
            val keyPair = CryptoHelper.generateEcKeyPair()
            val challenge = CryptoHelper.randomBytes(32)
            
            // Send HELLO
            val hello = HandshakeMessage(
                type = "HELLO",
                publicKey = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP),
                challenge = Base64.encodeToString(challenge, Base64.NO_WRAP)
            )
            send(Json.encodeToString(hello).toByteArray())
            
            // Receive WELCOME
            val welcomeBytes = receive()
            val welcome = Json.decodeFromString<HandshakeMessage>(String(welcomeBytes))
            
            if (welcome.type != "WELCOME") {
                Log.e(TAG, "Expected WELCOME, got ${welcome.type}")
                return null
            }
            
            // Derive shared secret
            val peerPublicKey = Base64.decode(welcome.publicKey, Base64.NO_WRAP)
            val sharedSecret = CryptoHelper.deriveSharedSecret(keyPair.private, peerPublicKey)
            val aesKey = CryptoHelper.deriveAesKey(sharedSecret, authToken.toByteArray())
            
            // Verify challenge response
            val expectedResponse = CryptoHelper.hmacSha256(challenge, aesKey)
            val actualResponse = Base64.decode(welcome.challengeResponse, Base64.NO_WRAP)
            
            if (!expectedResponse.contentEquals(actualResponse)) {
                Log.e(TAG, "Challenge response verification failed")
                return null
            }
            
            Log.i(TAG, "Client handshake successful")
            return aesKey
            
        } catch (e: Exception) {
            Log.e(TAG, "Client handshake failed", e)
            return null
        }
    }

    override suspend fun performServerHandshake(
        send: suspend (ByteArray) -> Unit,
        receive: suspend () -> ByteArray
    ): ByteArray? {
        try {
            // Receive HELLO
            val helloBytes = receive()
            val hello = Json.decodeFromString<HandshakeMessage>(String(helloBytes))
            
            if (hello.type != "HELLO") {
                Log.e(TAG, "Expected HELLO, got ${hello.type}")
                return null
            }
            
            // Generate EC key pair
            val keyPair = CryptoHelper.generateEcKeyPair()
            
            // Derive shared secret
            val peerPublicKey = Base64.decode(hello.publicKey, Base64.NO_WRAP)
            val sharedSecret = CryptoHelper.deriveSharedSecret(keyPair.private, peerPublicKey)
            val aesKey = CryptoHelper.deriveAesKey(sharedSecret, authToken.toByteArray())
            
            // Compute challenge response
            val challenge = Base64.decode(hello.challenge, Base64.NO_WRAP)
            val challengeResponse = CryptoHelper.hmacSha256(challenge, aesKey)
            
            // Send WELCOME
            val welcome = HandshakeMessage(
                type = "WELCOME",
                publicKey = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP),
                challengeResponse = Base64.encodeToString(challengeResponse, Base64.NO_WRAP)
            )
            send(Json.encodeToString(welcome).toByteArray())
            
            Log.i(TAG, "Server handshake successful")
            return aesKey
            
        } catch (e: Exception) {
            Log.e(TAG, "Server handshake failed", e)
            return null
        }
    }

    @Serializable
    private data class HandshakeMessage(
        val type: String,
        val publicKey: String = "",
        val challenge: String = "",
        val challengeResponse: String = ""
    )
}
