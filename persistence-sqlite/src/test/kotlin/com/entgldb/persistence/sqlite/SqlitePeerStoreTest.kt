package com.entgldb.persistence.sqlite

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.entgldb.core.Document
import com.entgldb.core.HlcTimestamp
import com.entgldb.core.OplogEntry
import com.entgldb.core.OperationType
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Ignore
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class SqlitePeerStoreTest {

    private lateinit var store: SqlitePeerStore
    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Before
    fun setup() {
        store = SqlitePeerStore(context, "test-db-${System.currentTimeMillis()}.db")
    }

    @Test
    fun saveAndGetDocument_Works() = runBlocking {
        val ts = HlcTimestamp(100, 0, "node1")
        val content = JsonObject(mapOf("foo" to JsonPrimitive("bar")))
        val doc = Document("users", "u1", content, ts, false)

        store.saveDocument(doc)

        val retrieved = store.getDocument("users", "u1")
        assertNotNull(retrieved)
        assertEquals("users", retrieved!!.collection)
        assertEquals("u1", retrieved.key)
        assertEquals(ts, retrieved.updatedAt)
    }

    @Test
    fun applyBatch_InsertsDocumentsAndOplog() = runBlocking {
        val ts = HlcTimestamp(100, 0, "node1")
        val content = JsonObject(mapOf("foo" to JsonPrimitive("bar")))
        
        val doc = Document("users", "u1", content, ts, false)
        val entry = OplogEntry("users", "u1", OperationType.Put, content, ts)

        store.applyBatch(listOf(doc), listOf(entry))

        // Check doc
        val retrievedDoc = store.getDocument("users", "u1")
        assertNotNull(retrievedDoc)

        // Check oplog
        val oplog = store.getOplogAfter(HlcTimestamp(0, 0, "0"))
        assertEquals(1, oplog.size)
        assertEquals("u1", oplog[0].key)
    }

    @Ignore("Requires json_extract support - not available in Robolectric legacy SQLite")
    @Test
    fun queryDocuments_SimpleFilter() = runBlocking {
        val ts = HlcTimestamp(100, 0, "node1")
        val content1 = JsonObject(mapOf("tags" to JsonPrimitive("admin")))
        val content2 = JsonObject(mapOf("tags" to JsonPrimitive("user")))
        
        store.saveDocument(Document("users", "u1", content1, ts, false))
        store.saveDocument(Document("users", "u2", content2, ts, false))

        // Note: Raw filter using json_extract
        // In Robolectric/sqlite-ktx standard, json_extract might not work if SQLite version is old, 
        // but Robolectric usually ships with reasonably recent SQLite.
        val results = store.queryDocuments("users", "json_extract(JsonData, '$.tags') = 'admin'")
        
        assertEquals(1, results.size)
        assertEquals("u1", results[0].key)
    }

    @Ignore("Requires functional index support - not available in Robolectric legacy SQLite")
    @Test
    fun ensureIndex_RunsWithoutError() = runBlocking {
        store.ensureIndex("users", "tags")
        // Pass if no exception
    }
}
