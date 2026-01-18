---
layout: default
title: API Reference
version: v0.2.0
---

# API Reference

## Core

### `EntglDbNode`
The main entry point for the EntglDb peer-to-peer database.

```kotlin
// Initialization
val node = EntglDbNode(tcpServer, discovery, orchestrator)
node.start()
node.stop()
```

### `IPeerStore`
Interface for the local persistence layer.

- `put(collection, key, jsonObject)`: Save a document.
- `get(collection, key)`: Retrieve a document.
- `getOplogSince(timestamp)`: Retrieve changes for synchronization.
- `applyRemoteChanges(entries)`: Apply changes from a peer (with Conflict Resolution).

## Sync & Network

### `RecursiveMergeConflictResolver`
**New in v0.2.0**
Resolves conflicts by recursively merging JSON objects and arrays.
- **Objects**: Unions keys.
- **Arrays**: Unions items by `id` (or `_id`).
- **Primitives**: Last-Write-Wins (LWW).

### `EntglDbService`
**New in v0.2.0**
Android Foreground Service to keep the node alive.

```kotlin
// Permission required in Manifest
// <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />

// Start service
val intent = Intent(context, EntglDbService::class.java)
intent.action = EntglDbService.ACTION_START
context.startService(intent)
```

## Protocol
- **Wire Format**: `[Length (4 LE)] [Type (1)] [Payload]`
- **Encryption**: AES-256-GCM
- **Handshake**: ECDH (P256) + HKDF-SHA256
