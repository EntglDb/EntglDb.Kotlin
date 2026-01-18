package com.entgldb.network.sync

import android.util.Log
import com.entgldb.network.security.IPeerHandshakeService
import kotlinx.coroutines.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.ServerSocket
import java.net.Socket

/**
 * TCP server accepting incoming sync connections from peers.
 */
class TcpSyncServer(
    private val nodeId: String,
    private val port: Int,
    private val handshakeService: IPeerHandshakeService?,
    private val store: com.entgldb.core.storage.IPeerStore
) {
    companion object {
        private const val TAG = "TcpSyncServer"
    }

    private var serverSocket: ServerSocket? = null
    private var serverJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    val listeningPort: Int
        get() = serverSocket?.localPort ?: 0

    val listeningEndpoint: String
        get() = "0.0.0.0:$listeningPort"

    /**
     * Starts the TCP server.
     */
    fun start() {
        if (serverSocket != null) return

        serverSocket = ServerSocket(port).also { socket ->
            Log.i(TAG, "TCP Sync Server started on port ${socket.localPort}")
        }

        serverJob = scope.launch {
            try {
                while (isActive) {
                    val clientSocket = serverSocket?.accept() ?: break
                    launch { handleClient(clientSocket) }
                }
            } catch (e: Exception) {
                if (isActive) {
                    Log.e(TAG, "Server accept error", e)
                }
            }
        }
    }

    /**
     * Stops the TCP server.
     */
    fun stop() {
        serverJob?.cancel()
        serverJob = null
        serverSocket?.close()
        serverSocket = null
        scope.cancel()
        Log.i(TAG, "TCP Sync Server stopped")
    }

    private suspend fun handleClient(socket: Socket) {
        withContext(Dispatchers.IO) {
            try {
                socket.use { client ->
                    val input = client.getInputStream()
                    val output = client.getOutputStream()

                    // Perform handshake if security is enabled
                    val cipherState = handshakeService?.performHandshake(input, output, isInitiator = false)

                    if (handshakeService != null && cipherState == null) {
                        Log.w(TAG, "Handshake failed with ${client.inetAddress}")
                        return@use
                    }

                    Log.i(TAG, "Client handshake complete: ${client.inetAddress}")

                    // Establish Secure Channel
                    val channel = SecureChannel(
                        input, 
                        output, 
                        encryptKey = cipherState?.encryptKey,
                        decryptKey = cipherState?.decryptKey
                    )

                    val processor = SyncMessageProcessor(store, nodeId)

                    // Message Loop
                    while (isActive) {
                        try {
                            val (type, payload) = channel.readMessage()
                            
                            val response = processor.process(type, payload)
                            if (response != null) {
                                val (resType, resMsg) = response
                                channel.sendMessage(resType, resMsg as com.google.protobuf.MessageLite)
                            } else {
                                Log.w(TAG, "Processor returned no response for $type")
                            }
                            
                        } catch (e: java.io.EOFException) {
                            Log.i(TAG, "Client disconnected: ${client.inetAddress}")
                            break
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Client handler error", e)
            }
        }
    }
}
