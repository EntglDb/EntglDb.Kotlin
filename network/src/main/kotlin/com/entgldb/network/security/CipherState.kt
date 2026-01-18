package com.entgldb.network.security

data class CipherState(
    val encryptKey: ByteArray,
    val decryptKey: ByteArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as CipherState

        if (!encryptKey.contentEquals(other.encryptKey)) return false
        if (!decryptKey.contentEquals(other.decryptKey)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = encryptKey.contentHashCode()
        result = 31 * result + decryptKey.contentHashCode()
        return result
    }
}
