package com.entgldb.network.security

/**
 * Interface for peer handshake services.
 */
interface IPeerHandshakeService {
    /**
     * Performs handshake as a client.
     * Returns the encryption key if successful, null otherwise.
     */
    suspend fun performClientHandshake(
        send: suspend (ByteArray) -> Unit,
        receive: suspend () -> ByteArray
    ): ByteArray?

    /**
     * Performs handshake as a server.
     * Returns the encryption key if successful, null otherwise.
     */
    suspend fun performServerHandshake(
        send: suspend (ByteArray) -> Unit,
        receive: suspend () -> ByteArray
    ): ByteArray?
}
