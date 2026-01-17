package com.entgldb.network

import android.util.Log
import com.entgldb.network.discovery.UdpDiscoveryService
import com.entgldb.network.models.NodeAddress
import com.entgldb.network.sync.SyncOrchestrator
import com.entgldb.network.sync.TcpSyncServer
import java.net.NetworkInterface

/**
 * Represents a single EntglDb Peer Node.
 * Acts as a facade to orchestrate the lifecycle of Networking, Discovery, and Synchronization components.
 */
class EntglDbNode(
    val server: TcpSyncServer,
    val discovery: UdpDiscoveryService,
    val orchestrator: SyncOrchestrator
) {
    companion object {
        private const val TAG = "EntglDbNode"
    }

    /**
     * Starts all node components (Server, Discovery, Orchestrator).
     */
    fun start() {
        Log.i(TAG, "Starting EntglDb Node...")

        server.start()

        // Ensure Discovery service knows the actual bound port (if configured port was 0)
        discovery.setTcpPort(server.listeningPort)

        discovery.start()
        orchestrator.start()

        Log.i(TAG, "EntglDb Node Started on $address")
    }

    /**
     * Stops all node components.
     */
    fun stop() {
        Log.i(TAG, "Stopping EntglDb Node...")

        orchestrator.stop()
        discovery.stop()
        server.stop()

        Log.i(TAG, "EntglDb Node Stopped")
    }

    /**
     * Gets the address information of this node.
     */
    val address: NodeAddress
        get() {
            val port = server.listeningPort
            val host = getLocalIpAddress()
            return NodeAddress(host, port)
        }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val iface = interfaces.nextElement()
                if (!iface.isUp || iface.isLoopback) continue

                val addresses = iface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val addr = addresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        return addr.hostAddress ?: "127.0.0.1"
                    }
                }
            }
            return "127.0.0.1"
        } catch (e: Exception) {
            Log.w(TAG, "Failed to resolve local IP: ${e.message}. Fallback to localhost.")
            return "127.0.0.1"
        }
    }
}
