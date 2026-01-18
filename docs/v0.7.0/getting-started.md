---
layout: default
title: Getting Started v0.7.0
---

# Getting Started with EntglDb Kotlin (v0.7.0)

This guide will help you integrate EntglDb into your Android application.

## 1. Installation

EntglDb is available via Maven Central. Add the following dependencies to your module's `build.gradle.kts`:

```kotlin
dependencies {
    // Core functionality (Required)
    implementation("com.entgldb:core:0.7.0")
    
    // Networking support (Recommended for sync)
    // Brotli compression (Brotli4j) included
    implementation("com.entgldb:network:0.7.0")
    implementation("com.aayushatharva.brotli4j:brotli4j:1.16.0")
    
    // SQLite storage (Recommended for persistence)
    implementation("com.entgldb:persistence-sqlite-android:0.7.0")
}
```

### New in v0.7.0
- **Brotli Compression**: Network traffic is compressed if both peers support it.
- **Secure Handshake**: Improved security with ECDH key exchange.

## 2. Platform Setup

### Android Permissions

Add the following permissions to your `AndroidManifest.xml` for discovery and sync:

```xml
<manifest ...>
    <!-- Required for network communication -->
    <uses-permission android:name="android.permission.INTERNET" />
    <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
    
    <!-- Required for UDP Discovery -->
    <uses-permission android:name="android.permission.CHANGE_WIFI_MULTICAST_STATE" />
    
    <application ...>
        ...
    </application>
</manifest>
```

## 3. Basic Usage

### Initialize the Database

Initialize `SqlitePeerStore` in your Application class or Dependency Injection module.

```kotlin
import com.entgldb.persistence.sqlite.SqlitePeerStore

// In your context (Activity or Application)
val dbPath = context.getDatabasePath("entgldb.db").absolutePath
val peerStore = SqlitePeerStore(context, dbPath)
```

### Configure Networking

Set up the `EntglDbNode` to enable peer discovery and synchronization.

```kotlin
import com.entgldb.network.*
import com.entgldb.network.discovery.UdpDiscoveryService
import com.entgldb.network.sync.*
import com.entgldb.network.security.SecureHandshakeService

// 1. Unique Node ID
val nodeId = "android-node-${UUID.randomUUID()}"

// 2. Security (Must match other peers)
val handshakeService = SecureHandshakeService("your-secret-auth-key")

// 3. Components
val tcpServer = TcpSyncServer(nodeId, 0, handshakeService) // 0 = auto-assign port
val discovery = UdpDiscoveryService(nodeId, 0)
val client = TcpPeerClient(handshakeService)
val orchestrator = SyncOrchestrator(discovery, client)

// 4. Start Node
val node = EntglDbNode(tcpServer, discovery, orchestrator)
node.start()

// Access the allocated port
println("Listening on port: ${node.server.listeningPort}")
```

### Save and Query Data

*Document CRUD operations coming soon in next alpha...*

## 4. Next Steps

*   Explore [Architecture](architecture.html)
*   View [API Reference](api-reference.html)
