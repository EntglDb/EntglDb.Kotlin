# EntglDb - Kotlin Multiplatform Implementation

A decentralized, offline-first peer-to-peer database for Kotlin/JVM and Android.

## Project Structure (Gradle Multi-Project)

```
├── core/                - Core database engine (KMP)
├── persistence-sqlite/  - SQLite storage adapter
├── network/             - P2P networking layer
├── protocol/            - Protocol Buffers definitions
└── sample-android/      - Android demo app
```

## Development

### Prerequisites
- JDK 17+
- Android SDK (for Android module)

### Build
```bash
./gradlew build
```

### Testing
```bash
./gradlew test
```

### Run Android Sample
```bash
./gradlew :sample-android:installDebug
```

## Architecture

This is a **pure Kotlin rewrite** using Kotlin Multiplatform for:
- JVM servers
- Android apps  
- (Future) Kotlin/Native for iOS

## Protocol Compatibility

Protocol Version: **1.0**  
Compatible with: EntglDb.NET v0.4+, EntglDb.NodeJs v0.1+

## License

MIT
