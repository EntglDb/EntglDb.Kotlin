package com.entgldb.network.sync

import android.util.Log
import com.entgldb.network.models.NodeAddress
import com.entgldb.network.security.IPeerHandshakeService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.Socket

/**
 * TCP client for connecting to peer nodes.
 */
class TcpPeerClient(
    private val handshakeService: IPeerHandshakeService?
) {
    companion object {
        private const val TAG = "TcpPeerClient"
        private const val CONNECT_TIMEOUT_MS = 5000
    }

    /**
     * Connects to a peer and performs handshake.
     * Returns the established SecureChannel if successful, null otherwise.
     */
    suspend fun connect(address: NodeAddress): SecureChannel? {
        return withContext(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(
                    java.net.InetSocketAddress(address.host, address.port),
                    CONNECT_TIMEOUT_MS
                )

                val input = socket.getInputStream()
                val output = socket.getOutputStream()

                // Perform handshake
                val cipherState = handshakeService?.performHandshake(input, output, isInitiator = true)

                if (handshakeService != null && cipherState == null) {
                    Log.w(TAG, "Handshake failed with $address")
                    socket.close()
                    return@withContext null
                }

                Log.i(TAG, "Connected to peer at $address")
                
                SecureChannel(
                    input, 
                    output, 
                    encryptKey = cipherState?.encryptKey, 
                    decryptKey = cipherState?.decryptKey
                )

            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to $address", e)
                null
            }
        }
    }
}
