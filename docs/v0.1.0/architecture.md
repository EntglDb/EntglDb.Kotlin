---
layout: default
title: Architecture v0.1.0
---

# Architecture Overview

EntglDb Kotlin follows a modular architecture designed for flexibility, testability, and cross-platform compatibility.

## Modules

### 1. `core`
The foundation of the library. It contains:
*   **Interfaces**: `IPeerStore`, `IDatasource`, `IDocument`.
*   **Algorithms**: Hybrid Logical Clock (HLC) for distributed timestamps.
*   **Conflict Resolution**: Interfaces for handling data conflicts (Last-Write-Wins, Recursive Merge).
*   **Models**: Basic data structures.

### 2. `network`
Handles all peer-to-peer communication.
*   **Discovery**: `UdpDiscoveryService` broadcasts presence on the local network using UDP multicast.
*   **Transport**: Uses TCP for reliable data synchronization.
*   **Security**: `SecureHandshakeService` implements ECDH (Elliptic Curve Diffie-Hellman) for key exchange and AES-256-GCM for encrypted communication.
*   **Orchestration**: `SyncOrchestrator` manages the gossip protocol to propagate updates efficiently.

### 3. `persistence-sqlite`
A robust storage implementation backed by Android's SQLite.
*   **Schema**: Automatically manages tables for documents and operations logs.
*   **Indexing**: Supports Creating and managing indexes for fast queries.
*   **WAL Mode**: Optimized for concurrent reads and writes.

### 4. `protocol`
Defines the wire format for synchronization.
*   **Protobuf**: Uses Google Protocol Buffers for efficient, compact serialization.
*   **Compatibility**: Ensures messages can be understood by EntglDb.Net peers.

## Data Flow

1.  **Write**: Application writes data to `IPeerStore`.
2.  **Log**: Operation is appended to the local OpLog (Operation Log).
3.  **Sync Trigger**: `SyncOrchestrator` wakes up or receives a notification.
4.  **Gossip**: Node connects to random peers via TCP.
5.  **Exchange**: Nodes exchange HLC timestamps and missing OpLog entries via `SyncProtocol`.
6.  **Apply**: Remote operations are applied locally, resolving conflicts if necessary.

## Security Model

*   **Authentication**: Shared Secret (Pre-Shared Key) model. All peers in a mesh must share the same `AUTH_TOKEN`.
*   **Encryption**: All TCP traffic is encrypted with ephemeral AES-256 keys derived from ECDH handshake.
*   **Identity**: Nodes are identified by a unique String ID (UUID recommended), not by IP address.
