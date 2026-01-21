package com.entgldb.core

import com.entgldb.core.storage.IPeerStore
import com.entgldb.core.HlcTimestamp
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class PeerDatabase(
    val store: IPeerStore,
    val nodeId: String
) {
    private val collections = ConcurrentHashMap<String, PeerCollection>()
    private var localClock: HlcTimestamp? = null
    private val clockMutex = Mutex()

    suspend fun initialize() {
        // Restore clock
        val latest = store.getLatestTimestamp()
        localClock = if (latest.physicalTime > 0) latest else HlcTimestamp(System.currentTimeMillis(), 0, nodeId)
    }

    fun collection(name: String): PeerCollection {
         return collections.computeIfAbsent(name) { PeerCollection(store, name, this) }
    }

    suspend fun tick(): HlcTimestamp {
        clockMutex.withLock {
            val now = System.currentTimeMillis()
            val current = localClock ?: HlcTimestamp(now, 0, nodeId)
            
            val next = if (now > current.physicalTime) {
                HlcTimestamp(now, 0, nodeId)
            } else {
                HlcTimestamp(current.physicalTime, current.logicalCounter + 1, nodeId)
            }
            localClock = next
            return next
        }
    }
    
    val changesApplied: kotlinx.coroutines.flow.Flow<List<String>> get() = store.changesApplied
}
