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
     * Returns the connected socket if successful, null otherwise.
     */
    suspend fun connect(address: NodeAddress): Socket? {
        return withContext(Dispatchers.IO) {
            try {
                val socket = Socket()
                socket.connect(
                    java.net.InetSocketAddress(address.host, address.port),
                    CONNECT_TIMEOUT_MS
                )

                val input = DataInputStream(socket.getInputStream())
                val output = DataOutputStream(socket.getOutputStream())

                // Perform handshake if security is enabled
                val encryptionKey = if (handshakeService != null) {
                    handshakeService.performClientHandshake(
                        send = { data ->
                            output.writeInt(data.size)
                            output.write(data)
                            output.flush()
                        },
                        receive = {
                            val len = input.readInt()
                            val buffer = ByteArray(len)
                            input.readFully(buffer)
                            buffer
                        }
                    )
                } else {
                    null
                }

                if (handshakeService != null && encryptionKey == null) {
                    Log.w(TAG, "Handshake failed with $address")
                    socket.close()
                    return@withContext null
                }

                Log.i(TAG, "Connected to peer at $address")
                socket

            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect to $address", e)
                null
            }
        }
    }

    /**
     * Sends data to a peer.
     */
    suspend fun send(socket: Socket, data: ByteArray) {
        withContext(Dispatchers.IO) {
            try {
                val output = DataOutputStream(socket.getOutputStream())
                output.writeInt(data.size)
                output.write(data)
                output.flush()
            } catch (e: Exception) {
                Log.e(TAG, "Send error", e)
                throw e
            }
        }
    }

    /**
     * Receives data from a peer.
     */
    suspend fun receive(socket: Socket): ByteArray {
        return withContext(Dispatchers.IO) {
            try {
                val input = DataInputStream(socket.getInputStream())
                val len = input.readInt()
                val buffer = ByteArray(len)
                input.readFully(buffer)
                buffer
            } catch (e: Exception) {
                Log.e(TAG, "Receive error", e)
                throw e
            }
        }
    }
}
