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
        val resolver = com.entgldb.core.sync.RecursiveMergeConflictResolver()
        peerStore = SqlitePeerStore(this, dbPath, resolver)
        
        // Initialize network components
        val handshakeService = SecureHandshakeService()
        val tcpServer = TcpSyncServer(nodeId, 0, handshakeService, peerStore)
        val discovery = UdpDiscoveryService(this, nodeId, 0, useLocalhost = false)
        val client = TcpPeerClient(handshakeService)
        val orchestrator = SyncOrchestrator(discovery, client, peerStore, nodeId, AUTH_TOKEN)
        
        node = EntglDbNode(tcpServer, discovery, orchestrator)
        node.start()
        
        Log.d(TAG, "EntglDb initialized successfully on ${node.address}")

        // Bind and start Foreground Service to keep node alive
        val intent = android.content.Intent(this, com.entgldb.network.service.EntglDbService::class.java)
        bindService(intent, connection, BIND_AUTO_CREATE)
    }
    
    private val connection = object : android.content.ServiceConnection {
        override fun onServiceConnected(className: android.content.ComponentName, service: android.os.IBinder) {
            val binder = service as com.entgldb.network.service.EntglDbService.LocalBinder
            val entglDbService = binder.getService()
            entglDbService.setNode(node)
            
            // Promote to Foreground Service
            val startIntent = android.content.Intent(this@EntglDbApplication, com.entgldb.network.service.EntglDbService::class.java)
            startIntent.action = com.entgldb.network.service.EntglDbService.ACTION_START
            startService(startIntent)
        }

        override fun onServiceDisconnected(arg0: android.content.ComponentName) {
        }
    }
    
    override fun onTerminate() {
        super.onTerminate()
        node.stop()
        unbindService(connection)
    }
    
    companion object {
        private const val TAG = "EntglDbApplication"
        const val AUTH_TOKEN = "demo-secret-key"
    }
}
