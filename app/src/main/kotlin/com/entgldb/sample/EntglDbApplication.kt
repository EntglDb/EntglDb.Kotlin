package com.entgldb.sample

import android.app.Application
import android.util.Log
import com.entgldb.network.EntglDbNode
import com.entgldb.network.discovery.UdpDiscoveryService
import com.entgldb.network.security.SecureHandshakeService
import com.entgldb.network.sync.SyncOrchestrator
import com.entgldb.network.sync.TcpPeerClient
import com.entgldb.network.sync.TcpSyncServer
import com.entgldb.persistence.sqlite.SqlitePeerStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.util.UUID

class EntglDbApplication : Application() {
    
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    
    lateinit var peerStore: SqlitePeerStore
        private set
    
    lateinit var node: EntglDbNode
        private set
    
    val nodeId: String by lazy {
        val prefs = getSharedPreferences("entgldb", MODE_PRIVATE)
        prefs.getString("node_id", null) ?: run {
            val newId = "android-node-${UUID.randomUUID().toString().substring(0, 8)}"
            prefs.edit().putString("node_id", newId).apply()
            newId
        }
    }
    
    override fun onCreate() {
        super.onCreate()
        
        Log.d(TAG, "Initializing EntglDb with nodeId: $nodeId")
        
        // Initialize peer store
        val dbPath = getDatabasePath("entgldb-android.db").absolutePath
        peerStore = SqlitePeerStore(this, dbPath)
        
        // Initialize network components
        val handshakeService = SecureHandshakeService(AUTH_TOKEN)
        val tcpServer = TcpSyncServer(nodeId, 0, handshakeService)
        val discovery = UdpDiscoveryService(nodeId, 0, useLocalhost = false)
        val client = TcpPeerClient(handshakeService)
        val orchestrator = SyncOrchestrator(discovery, client)
        
        node = EntglDbNode(tcpServer, discovery, orchestrator)
        node.start()
        
        Log.d(TAG, "EntglDb initialized successfully on ${node.address}")
    }
    
    override fun onTerminate() {
        super.onTerminate()
        node.stop()
    }
    
    companion object {
        private const val TAG = "EntglDbApplication"
        const val AUTH_TOKEN = "demo-secret-key"
    }
}
