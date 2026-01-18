---
layout: default
title: Android Robustness
version: v0.2.0
---

# Android Robustness

EntglDb is designed to survive the aggressive background killing policies of modern Android versions.

## Foreground Service
To ensure your node remains reachable, use the provided `EntglDbService`.

### Setup
1. **Manifest**:
   ```xml
   <manifest ...>
       <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
       <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
       
       <application>
            <service android:name="com.entgldb.network.service.EntglDbService"
                     android:foregroundServiceType="dataSync"
                     android:exported="false" />
       </application>
   </manifest>
   ```

2. **Binding**:
   Bind your Activity or Application to the service to inject your `EntglDbNode` instance.

3. **Starting**:
   Send an Intent with `ACTION_START` to promote the service to the foreground.

## Network Awareness
The library automatically monitors network state:
- **Auto-Pause**: When connectivity is lost, UDP Discovery stops to save battery.
- **Auto-Resume**: When connectivity returns, discovery restarts immediately.
