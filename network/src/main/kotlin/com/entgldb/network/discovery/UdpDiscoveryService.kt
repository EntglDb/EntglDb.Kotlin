package com.entgldb.network.discovery

import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.ConcurrentHashMap

/**
 * Provides UDP-based peer discovery for the EntglDb network.
 * Broadcasts presence beacons and listens for other nodes on the local network.
 */
import com.entgldb.core.network.PeerNode
import kotlinx.serialization.InternalSerializationApi

// ... imports

class UdpDiscoveryService(
    private val context: android.content.Context,
    private val nodeId: String,
    private var tcpPort: Int,
    private val useLocalhost: Boolean = false
) : IDiscoveryService {
    companion object {
        private const val TAG = "UdpDiscovery"
        private val logger = mu.KotlinLogging.logger {}
        private const val DISCOVERY_PORT = 5000
        private const val BROADCAST_INTERVAL_MS = 5000L
        private const val CLEANUP_INTERVAL_MS = 10000L
        private const val PEER_EXPIRY_MS = 15000L
    }

    private val activePeers = ConcurrentHashMap<String, PeerNode>()
    private var discoveryJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    fun setTcpPort(port: Int) {
        tcpPort = port
    }

    /**
     * Starts the discovery service, initiating listener, broadcaster, and cleanup tasks.
     * Can be called to resume after pause().
     */
    override fun start() {
        if (discoveryJob != null && discoveryJob?.isActive == true) return
        
        discoveryJob = scope.launch {
            launch { listenForBeacons() }
            launch { broadcastBeacons() }
            launch { cleanupExpiredPeers() }
        }
        
        logger.info { "UDP Discovery started for node: $nodeId" }
    }

    /**
     * Pauses discovery (stops network activity) but keeps state.
     */
    fun pause() {
        discoveryJob?.cancel()
        discoveryJob = null
        logger.info { "UDP Discovery paused" }
    }

    /**
     * Resumes discovery.
     */
    fun resume() {
        start()
    }

    /**
     * Stops the discovery service permanently.
     */
    override fun stop() {
        pause()
        scope.cancel()
        logger.info { "UDP Discovery stopped" }
    }

    /**
     * Gets the list of currently active peers.
     */
    override fun getActivePeers(): List<PeerNode> = activePeers.values.toList()

    @OptIn(InternalSerializationApi::class)
    private suspend fun listenForBeacons() {
        withContext(Dispatchers.IO) {
            val socket = DatagramSocket(DISCOVERY_PORT).apply {
                reuseAddress = true
            }
            
            
            logger.info { "UDP Discovery listening on port $DISCOVERY_PORT" }
            
            try {
                val buffer = ByteArray(1024)
                while (coroutineContext.isActive) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    
                    val json = String(packet.data, 0, packet.length)
                    logger.debug { "Received beacon raw: $json from ${packet.address}" } 
                    
                    try {
                        val beacon = com.entgldb.core.common.JsonHelpers.json.decodeFromString<DiscoveryBeacon>(json)
                        if (beacon.nodeId != nodeId) {
                            handleBeacon(beacon, packet.address)
                        }
                    } catch (e: Exception) {
                        logger.warn { "Failed to parse beacon from ${packet.address}. Json: $json. Error: ${e.message}" }
                    }
                }
            } catch (e: Exception) {
                if (coroutineContext.isActive) {
                    logger.error(e) { "UDP Listener error" }
                }
            } finally {
                socket.close()
            }
        }
    }

    @OptIn(InternalSerializationApi::class)
    private suspend fun broadcastBeacons() {
        withContext(Dispatchers.IO) {
            val socket = DatagramSocket().apply {
                broadcast = true
            }
            
            val beacon = DiscoveryBeacon(nodeId, tcpPort)
            val json = com.entgldb.core.common.JsonHelpers.json.encodeToString(beacon)
            val bytes = json.toByteArray()
            

            
            logger.info { "UDP Broadcasting started for $nodeId" }
            
            try {
                while (coroutineContext.isActive) {
                     // Recalculate each time in case network changes (though less efficient, safer)
                    val broadcastAddress = getBroadcastAddress() ?: InetAddress.getByName("255.255.255.255")
                    val packet = DatagramPacket(bytes, bytes.size, broadcastAddress, DISCOVERY_PORT)



                    logger.debug { "Broadcasting beacon to ${broadcastAddress.hostAddress}: $json" }
                    socket.send(packet)
                    
                    // Fallback removed as requested implicitly by reverting to "back" state,
                    // but keeping safe broadcastAddress calculation logic.
                    
                    delay(BROADCAST_INTERVAL_MS)
                }
            } catch (e: Exception) {
                if (coroutineContext.isActive) {
                    logger.error(e) { "UDP Broadcast error" }
                }
            } finally {
                socket.close()
            }
        }
    }

    private suspend fun cleanupExpiredPeers() {
        while (coroutineContext.isActive) {
            delay(CLEANUP_INTERVAL_MS)
            
            val now = System.currentTimeMillis()
            val expired = mutableListOf<String>()
            
            activePeers.forEach { (id, peer) ->
                if (now - peer.lastSeen > PEER_EXPIRY_MS) {
                    expired.add(id)
                }
            }
            
            expired.forEach { id ->
                activePeers.remove(id)?.let { peer ->
                    logger.info { "Peer expired: ${peer.nodeId} at ${peer.address}" }
                }
            }
        }
    }

    @OptIn(InternalSerializationApi::class)
    private fun handleBeacon(beacon: DiscoveryBeacon, address: InetAddress) {
        val targetAddress = if (useLocalhost) {
            InetAddress.getLoopbackAddress()
        } else {
            address
        }
        
        val endpoint = "$targetAddress:${beacon.tcpPort}"
        val peer = PeerNode(
            nodeId = beacon.nodeId,
            address = endpoint,
            lastSeen = System.currentTimeMillis()
        )
        
        val wasNew = activePeers.putIfAbsent(beacon.nodeId, peer) == null
        if (!wasNew) {
            activePeers[beacon.nodeId] = peer // Update timestamp
        } else {
            logger.info { "Peer discovered: ${peer.nodeId} at ${peer.address}" }
        }
    }

    @InternalSerializationApi @Serializable
    private data class DiscoveryBeacon(
        @kotlinx.serialization.SerialName("node_id") val nodeId: String,
        @kotlinx.serialization.SerialName("tcp_port") val tcpPort: Int
    )

    private fun getBroadcastAddress(): InetAddress? {
        try {
            val wifiManager = context.getSystemService(android.content.Context.WIFI_SERVICE) as? android.net.wifi.WifiManager
            val dhcp = wifiManager?.dhcpInfo ?: return null
            
            val broadcast = (dhcp.ipAddress and dhcp.netmask) or dhcp.netmask.inv()
            val quizzes = ByteArray(4)
            for (k in 0..3) {
                quizzes[k] = (broadcast shr k * 8 and 0xFF).toByte()
            }
            return InetAddress.getByAddress(quizzes)

        } catch (e: Exception) {
            logger.warn { "Failed to calculate broadcast address: ${e.message}" }
            return null
        }
    }
}
