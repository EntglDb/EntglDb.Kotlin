---
layout: default
title: Security & Synchronization
version: v0.2.0
---

# Security & Synchronization

## Secure Channel
EntglDb v0.2.0 introduces a fully encrypted transport layer compatible with `EntglDb.Net`.

### Handshake Protocol
1. **Connection**: Client connects to Server TCP port.
2. **Key Exchange**:
   - Both parties generate ephemeral ECDH keys (NIST P-256).
   - Exchange Public Keys (raw 65 bytes).
   - Compute `SharedSecret`.
3. **Key Derivation**:
   - `EncryptKey` = HKDF-SHA256(SharedSecret, Info=0x00)
   - `DecryptKey` = HKDF-SHA256(SharedSecret, Info=0x01)

### Wire Encryption
All subsequent traffic is wrapped in `SecureEnvelope`:
- **Algorithm**: AES-256-GCM.
- **Structure**: `[IV (12 bytes)] [Ciphertext] [AuthTag (16 bytes)]`.

## Conflict Resolution

### Last-Write-Wins (LWW)
The default strategy. Uses Hybrid Logical Clocks (HLC) to determine the latest version.

### Recursive Merge
For complex JSON documents, EntglDb performs a deep merge:
- **Objects**: Properties from both sides are preserved. If a key collides, the value with the higher HLC wins.
- **Arrays**: Items are matched by `id` field. New items are added; existing items are recursively merged.
