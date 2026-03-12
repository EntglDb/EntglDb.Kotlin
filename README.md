# EntglDb.Kotlin — Student Project Scaffold

Kotlin implementation of EntglDb, the peer-to-peer data synchronization middleware.

> **Questo repository è uno scaffold vuoto** per il progetto universitario.
> L'implementazione di riferimento è `EntglDb.Net` (v1.0.0, .NET 8/10).

## Project Structure (Gradle Multi-Project)

```
├── core/                - Core engine: VectorClock, HLC, Oplog, Conflict Resolution
├── persistence-sqlite/  - SQLite storage adapter (Android SQLite KTX)
├── network/             - P2P networking layer (Ktor, TCP + UDP discovery)
├── protocol/            - Protocol Buffers (sync.proto — already compiled)
└── app/                 - Android sample application (Jetpack Compose)
```

## Prerequisites

- JDK 21+
- Android SDK (API 24+)
- Android Studio Meerkat or newer

## Build

```bash
./gradlew build
```

## Protocol Compatibility

Protocol Version: **v5**
Compatible with: EntglDb.Net v1.0.0
Features: Brotli compression, Secure Handshake (Noise), Gossip, Interest-Aware Sync, Snapshot.

### Protocol Messages (`proto/sync.proto`)

Il protocollo di rete è già definito in `protocol/src/main/proto/sync.proto`.
La compilazione Protobuf è gestita automaticamente da Gradle — non modificare il file a meno che
non si stia estendendo il protocollo.

## Reference Implementation

| Concetto | Dove guardare in EntglDb.Net |
|---|---|
| VectorClock, HLC | `src/EntglDb.Core/VectorClock.cs`, `HlcTimestamp.cs` |
| OplogEntry, hash chain | `src/EntglDb.Core/OplogEntry.cs` |
| Conflict Resolution (LWW / Merge) | `src/EntglDb.Core/sync/` |
| TCP networking, handshake | `src/EntglDb.Network/sync/TcpPeerClient.cs` |
| UDP Discovery | `src/EntglDb.Network/discovery/` |
| Snapshot / Gap Recovery | `src/EntglDb.Network/sync/SyncOrchestrator.cs` |

## License

MIT
