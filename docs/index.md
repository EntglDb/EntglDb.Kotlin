---
layout: default
---

# EntglDb Kotlin Documentation

**EntglDb** is a decentralized, offline-first peer-to-peer database. This repository contains the **Kotlin** implementation, primarily designed for **Android** (but supports JVM).

## Installation

Add the packages to your `build.gradle.kts`:

```kotlin
implementation("com.entgldb:core:0.8.0")
implementation("com.entgldb:persistence-sqlite:0.8.0")
implementation("com.entgldb:network:0.8.0")
```

## Getting Started

### 1. Initialize

```kotlin
// Android Context required for SqlitePeerStore
val store = SqlitePeerStore(context, "my-db")
val node = EntglDbNode(store)
val discovery = UdpDiscoveryService(context, node.nodeId, 25000)
val server = TcpSyncServer(node.nodeId, 25000, null, store)

// Start services
server.start()
discovery.start()
```

### 2. Save Data

```kotlin
val doc = Document.create("todos", "todo-1", 
    JsonObject(mapOf("title" to JsonPrimitive("Buy Milk")))
)
store.saveDocument(doc)
```

### 3. Subscribe to Changes

```kotlin
scope.launch {
    store.changesApplied.collect { collections ->
        println("Collections changed: $collections")
    }
}
```

## Dynamic Reconfiguration (v0.8.0)

See [Dynamic Reconfiguration](https://github.com/EntglDb/EntglDb.Net/blob/main/docs/dynamic-reconfiguration.md) in the main documentation.

## Links

*   [**Central Documentation**](https://github.com/EntglDb/EntglDb.Net/tree/main/docs) - Architecture, Protocol, and Concepts.
*   [GitHub Repository](https://github.com/EntglDb/EntglDb.Kotlin)
