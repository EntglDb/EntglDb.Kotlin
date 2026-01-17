package com.entgldb.core.sync

import com.entgldb.core.Document
import com.entgldb.core.HlcTimestamp
import com.entgldb.core.OplogEntry
import com.entgldb.core.OperationType
import kotlinx.serialization.json.JsonObject
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LastWriteWinsTest {

    private val resolver = LastWriteWinsConflictResolver()
    private val content = JsonObject(emptyMap())

    @Test
    fun resolve_RemoteNewer_AppliesRemote() {
        val localTs = HlcTimestamp(100, 0, "A")
        val remoteTs = HlcTimestamp(200, 0, "B")

        val localDoc = Document("col", "key", content, localTs, false)
        val remoteEntry = OplogEntry("col", "key", OperationType.Put, content, remoteTs)

        val result = resolver.resolve(localDoc, remoteEntry)

        assertTrue(result.shouldApply)
        assertTrue(result.mergedDocument!!.updatedAt == remoteTs)
    }

    @Test
    fun resolve_LocalNewer_IgnoresRemote() {
        val localTs = HlcTimestamp(200, 0, "A")
        val remoteTs = HlcTimestamp(100, 0, "B")

        val localDoc = Document("col", "key", content, localTs, false)
        val remoteEntry = OplogEntry("col", "key", OperationType.Put, content, remoteTs)

        val result = resolver.resolve(localDoc, remoteEntry)

        assertFalse(result.shouldApply)
    }

    @Test
    fun resolve_EqualTimestamp_IgnoresRemote() {
        // LWW usually bias towards higher node ID if timestamps equal, or just keep local.
        // My implementation says: if remote > local, apply. So if equal, ignore.
        val ts = HlcTimestamp(100, 0, "A")

        val localDoc = Document("col", "key", content, ts, false)
        val remoteEntry = OplogEntry("col", "key", OperationType.Put, content, ts)

        val result = resolver.resolve(localDoc, remoteEntry)

        assertFalse(result.shouldApply)
    }

    @Test
    fun resolve_NoLocal_AppliesRemote() {
        val remoteTs = HlcTimestamp(100, 0, "B")
        val remoteEntry = OplogEntry("col", "key", OperationType.Put, content, remoteTs)

        val result = resolver.resolve(null, remoteEntry)

        assertTrue(result.shouldApply)
    }
}
