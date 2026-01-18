package com.entgldb.network.security

/**
 * Interface for peer handshake services.
 */
interface IPeerHandshakeService {
    /**
     * Performs a secure handshake over the given input and output streams.
     * @param input Stream to read from.
     * @param output Stream to write to.
     * @param isInitiator True if this node initiated the connection (Client), False otherwise (Server).
     * @return CipherState containing the derived session keys.
     */
    suspend fun performHandshake(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        isInitiator: Boolean
    ): CipherState
}
