package com.entgldb.network.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.entgldb.network.EntglDbNode

/**
 * Foreground Service to keep EntglDbNode alive.
 * 
 * Usage from Native:
 * 1. bindService to get LocalBinder and set the Node instance.
 * 2. startService(Intent(ACTION_START)) to promote to Foreground.
 * 
 * Usage from React Native (simplified):
 * - The RN bridge should create the Node, then start this service.
 * - This service simply holds the process alive.
 */
class EntglDbService : Service() {

    companion object {
        const val ACTION_START = "com.entgldb.action.START"
        const val ACTION_STOP = "com.entgldb.action.STOP"
        const val NOTIFICATION_ID = 1001
        const val CHANNEL_ID = "entgldb_sync_channel"
        private const val TAG = "EntglDbService"
    }

    // Node reference held by the service to prevent GC and manage lifecycle if desired.
    // In this robust design, we assume the Application or RN Bridge initializes the Node
    // and passes it here, OR this Service manages it.
    // For simplicity and flexibility, we act as a container.
    private var node: EntglDbNode? = null
    
    private val binder = LocalBinder()

    inner class LocalBinder : Binder() {
        fun getService(): EntglDbService = this@EntglDbService
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    fun setNode(node: EntglDbNode) {
        this.node = node
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerNetworkCallback()
    }
    
    private val networkCallback = object : android.net.ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: android.net.Network) {
            Log.i(TAG, "Network available - Resuming Discovery")
            node?.discovery?.resume()
        }

        override fun onLost(network: android.net.Network) {
            Log.i(TAG, "Network lost - Pausing Discovery")
            node?.discovery?.pause()
        }
    }
    
    private fun registerNetworkCallback() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val request = android.net.NetworkRequest.Builder()
            .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, networkCallback)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                Log.i(TAG, "Starting EntglDb Foreground Service")
                val notification = createNotification()
                startForeground(NOTIFICATION_ID, notification)
                
                // Ensure node is started if we hold it
                node?.start()
            }
            ACTION_STOP -> {
                Log.i(TAG, "Stopping EntglDb Foreground Service")
                stopForeground(true)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        cm.unregisterNetworkCallback(networkCallback)
        
        node?.stop()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "EntglDb Sync Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("EntglDb Sync Active")
            .setContentText("Synchronizing data with peers...")
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }
}
