package com.entgldb.network.security

import android.util.Log
import java.security.*
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.Mac
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.random.Random

/**
 * Utility class for cryptographic operations.
 * Provides ECDH key agreement, AES-256-GCM encryption, and HMAC.
 */
object CryptoHelper {
    private const val TAG = "CryptoHelper"
    private const val EC_CURVE = "secp256r1"
    private const val GCM_TAG_LENGTH = 128
    private const val GCM_IV_LENGTH = 12

    /**
     * Generates an EC key pair for ECDH.
     */
    fun generateEcKeyPair(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(256) // P-256 curve
        return keyGen.generateKeyPair()
    }

    /**
     * Performs ECDH key agreement to derive a shared secret.
     */
    fun deriveSharedSecret(privateKey: PrivateKey, publicKeyBytes: ByteArray): ByteArray {
        val keyFactory = KeyFactory.getInstance("EC")
        val publicKeySpec = X509EncodedKeySpec(publicKeyBytes)
        val publicKey = keyFactory.generatePublic(publicKeySpec)

        val keyAgreement = KeyAgreement.getInstance("ECDH")
        keyAgreement.init(privateKey)
        keyAgreement.doPhase(publicKey, true)
        
        return keyAgreement.generateSecret()
    }

    /**
     * Derives an AES key from shared secret using HKDF-SHA256.
     */
    fun deriveAesKey(sharedSecret: ByteArray, salt: ByteArray = ByteArray(0)): ByteArray {
        // Simple HKDF: HMAC-SHA256(salt, sharedSecret)
        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(if (salt.isEmpty()) ByteArray(32) else salt, "HmacSHA256")
        mac.init(keySpec)
        val prk = mac.doFinal(sharedSecret)
        
        // Expand to 32 bytes for AES-256
        mac.init(SecretKeySpec(prk, "HmacSHA256"))
        return mac.doFinal(byteArrayOf(1)).copyOf(32)
    }

    /**
     * Encrypts data using AES-256-GCM.
     * Returns: IV (12 bytes) + ciphertext + tag
     */
    fun encryptAesGcm(plaintext: ByteArray, key: ByteArray): ByteArray {
        require(key.size == 32) { "Key must be 32 bytes for AES-256" }
        
        val iv = Random.nextBytes(GCM_IV_LENGTH)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        val secretKey = SecretKeySpec(key, "AES")
        
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, spec)
        val ciphertext = cipher.doFinal(plaintext)
        
        // Return IV + ciphertext (includes tag)
        return iv + ciphertext
    }

    /**
     * Decrypts data using AES-256-GCM.
     * Input: IV (12 bytes) + ciphertext + tag
     */
    fun decryptAesGcm(encrypted: ByteArray, key: ByteArray): ByteArray {
        require(key.size == 32) { "Key must be 32 bytes for AES-256" }
        require(encrypted.size > GCM_IV_LENGTH) { "Encrypted data too short" }
        
        val iv = encrypted.copyOfRange(0, GCM_IV_LENGTH)
        val ciphertext = encrypted.copyOfRange(GCM_IV_LENGTH, encrypted.size)
        
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        val spec = GCMParameterSpec(GCM_TAG_LENGTH, iv)
        val secretKey = SecretKeySpec(key, "AES")
        
        cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
        return cipher.doFinal(ciphertext)
    }

    /**
     * Computes HMAC-SHA256.
     */
    fun hmacSha256(data: ByteArray, key: ByteArray): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        val keySpec = SecretKeySpec(key, "HmacSHA256")
        mac.init(keySpec)
        return mac.doFinal(data)
    }

    /**
     * Generates random bytes.
     */
    fun randomBytes(size: Int): ByteArray = Random.nextBytes(size)
}
