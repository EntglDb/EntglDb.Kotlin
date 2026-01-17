package com.entgldb.network.security

import org.junit.Assert.*
import org.junit.Test

class CryptoHelperTest {

    @Test
    fun testEcKeyPairGeneration() {
        val keyPair = CryptoHelper.generateEcKeyPair()
        assertNotNull(keyPair)
        assertNotNull(keyPair.private)
        assertNotNull(keyPair.public)
        assertEquals("EC", keyPair.private.algorithm)
        assertEquals("EC", keyPair.public.algorithm)
    }

    @Test
    fun testEcdhKeyAgreement() {
        // Generate two key pairs
        val aliceKeyPair = CryptoHelper.generateEcKeyPair()
        val bobKeyPair = CryptoHelper.generateEcKeyPair()

        // Derive shared secrets
        val aliceShared = CryptoHelper.deriveSharedSecret(
            aliceKeyPair.private,
            bobKeyPair.public.encoded
        )
        val bobShared = CryptoHelper.deriveSharedSecret(
            bobKeyPair.private,
            aliceKeyPair.public.encoded
        )

        // Both should derive the same shared secret
        assertArrayEquals(aliceShared, bobShared)
    }

    @Test
    fun testAesEncryptionDecryption() {
        val key = CryptoHelper.randomBytes(32) // AES-256
        val plaintext = "Hello, EntglDb!".toByteArray()

        // Encrypt
        val encrypted = CryptoHelper.encryptAesGcm(plaintext, key)
        assertNotNull(encrypted)
        assertTrue(encrypted.size > plaintext.size) // IV + ciphertext + tag

        // Decrypt
        val decrypted = CryptoHelper.decryptAesGcm(encrypted, key)
        assertArrayEquals(plaintext, decrypted)
    }

    @Test
    fun testAesDecryptionWithWrongKey() {
        val key1 = CryptoHelper.randomBytes(32)
        val key2 = CryptoHelper.randomBytes(32)
        val plaintext = "Secret message".toByteArray()

        val encrypted = CryptoHelper.encryptAesGcm(plaintext, key1)

        // Decryption with wrong key should fail
        try {
            CryptoHelper.decryptAesGcm(encrypted, key2)
            fail("Should have thrown exception for wrong key")
        } catch (e: Exception) {
            // Expected
        }
    }

    @Test
    fun testHmacSha256() {
        val key = "test-key".toByteArray()
        val data = "test-data".toByteArray()

        val hmac1 = CryptoHelper.hmacSha256(data, key)
        val hmac2 = CryptoHelper.hmacSha256(data, key)

        // Same input should produce same HMAC
        assertArrayEquals(hmac1, hmac2)
        assertEquals(32, hmac1.size) // SHA-256 produces 32 bytes

        // Different data should produce different HMAC
        val differentData = "different-data".toByteArray()
        val hmac3 = CryptoHelper.hmacSha256(differentData, key)
        assertFalse(hmac1.contentEquals(hmac3))
    }

    @Test
    fun testDeriveAesKey() {
        val sharedSecret = CryptoHelper.randomBytes(32)
        val salt = CryptoHelper.randomBytes(16)

        val key = CryptoHelper.deriveAesKey(sharedSecret, salt)
        assertNotNull(key)
        assertEquals(32, key.size) // AES-256 key

        // Same inputs should produce same key
        val key2 = CryptoHelper.deriveAesKey(sharedSecret, salt)
        assertArrayEquals(key, key2)
    }

    @Test
    fun testRandomBytes() {
        val bytes1 = CryptoHelper.randomBytes(16)
        val bytes2 = CryptoHelper.randomBytes(16)

        assertEquals(16, bytes1.size)
        assertEquals(16, bytes2.size)
        assertFalse(bytes1.contentEquals(bytes2)) // Should be random
    }
}
