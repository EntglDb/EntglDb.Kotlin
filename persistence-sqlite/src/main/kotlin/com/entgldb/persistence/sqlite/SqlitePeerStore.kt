package com.entgldb.persistence.sqlite

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.entgldb.core.Document
import com.entgldb.core.HlcTimestamp
import com.entgldb.core.OplogEntry
import com.entgldb.core.OperationType
import com.entgldb.core.storage.IPeerStore
import com.entgldb.core.sync.IConflictResolver
import com.entgldb.core.sync.LastWriteWinsConflictResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

class SqlitePeerStore(
    context: Context, 
    dbName: String,
    private val conflictResolver: IConflictResolver = LastWriteWinsConflictResolver()
) : IPeerStore {

    private val dbHelper = DbHelper(context, dbName)
    private val _changesApplied = MutableSharedFlow<List<String>>(replay = 0)
    
    override val changesApplied: Flow<List<String>> = _changesApplied.asSharedFlow()

    // private val json = Json { ignoreUnknownKeys = true } // Use JsonHelpers.json

    override suspend fun saveDocument(document: Document) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        saveDocumentInternal(db, document)
    }

    private fun saveDocumentInternal(db: SQLiteDatabase, document: Document) {
        val values = ContentValues().apply {
            put("Collection", document.collection)
            put("Key", document.key)
            put("JsonData", document.content.toString())
            put("IsDeleted", if (document.isDeleted) 1 else 0)
            put("HlcWall", document.updatedAt.physicalTime)
            put("HlcLogic", document.updatedAt.logicalCounter)
            put("HlcNode", document.updatedAt.nodeId)
        }
        db.replace("Documents", null, values)
    }

    override suspend fun getDocument(collection: String, key: String): Document? = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        return@withContext getDocumentInternal(db, collection, key)
    }

    private fun getDocumentInternal(db: SQLiteDatabase, collection: String, key: String): Document? {
        val cursor = db.rawQuery(
            "SELECT Key, JsonData, IsDeleted, HlcWall, HlcLogic, HlcNode FROM Documents WHERE Collection = ? AND Key = ?",
            arrayOf(collection, key)
        )
        cursor.use {
            if (it.moveToFirst()) {
                return mapDocument(it, collection)
            }
        }
        return null
    }

    override suspend fun appendOplogEntry(entry: OplogEntry) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        appendOplogEntryInternal(db, entry)
    }

    private fun appendOplogEntryInternal(db: SQLiteDatabase, entry: OplogEntry) {
        val values = ContentValues().apply {
            put("Collection", entry.collection)
            put("Key", entry.key)
            put("Operation", entry.operation.ordinal)
            put("JsonData", entry.payload?.toString())
            put("IsDeleted", if (entry.operation == OperationType.Delete) 1 else 0)
            put("HlcWall", entry.timestamp.physicalTime)
            put("HlcLogic", entry.timestamp.logicalCounter)
            put("HlcNode", entry.timestamp.nodeId)
        }
        db.insert("Oplog", null, values)
    }

    override suspend fun getOplogAfter(timestamp: HlcTimestamp): List<OplogEntry> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        // HlcWall > ? OR (HlcWall = ? AND HlcLogic > ?)
        val cursor = db.rawQuery(
            """
            SELECT Collection, Key, Operation, JsonData, HlcWall, HlcLogic, HlcNode 
            FROM Oplog 
            WHERE HlcWall > ? OR (HlcWall = ? AND HlcLogic > ?)
            ORDER BY HlcWall ASC, HlcLogic ASC
            """,
            arrayOf(
                timestamp.physicalTime.toString(),
                timestamp.physicalTime.toString(),
                timestamp.logicalCounter.toString()
            )
        )

        val result = mutableListOf<OplogEntry>()
        cursor.use {
            while (it.moveToNext()) {
                result.add(mapOplogEntry(it))
            }
        }
        return@withContext result
    }

    override suspend fun getLatestTimestamp(): HlcTimestamp = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT MAX(HlcWall) as Wall, MAX(HlcLogic) as Logic, HlcNode FROM Oplog ORDER BY HlcWall DESC, HlcLogic DESC LIMIT 1",
            null
        )
        cursor.use {
            if (it.moveToFirst()) {
                val wall = if (it.isNull(0)) 0L else it.getLong(0)
                val logic = if (it.isNull(1)) 0 else it.getInt(1)
                val node = if (it.isNull(2)) "0" else it.getString(2)
                return@withContext HlcTimestamp(wall, logic, node)
            }
        }
        return@withContext HlcTimestamp(0, 0, "0")
    }

    override suspend fun applyBatch(documents: List<Document>, oplogEntries: List<OplogEntry>) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransaction()
        try {
            // Apply documents first (initial sync or snapshot) if any
            for (doc in documents) {
                saveDocumentInternal(db, doc)
            }

            // Apply oplog entries with conflict resolution
            for (entry in oplogEntries) {
                val localDoc = getDocumentInternal(db, entry.collection, entry.key)
                val resolution = conflictResolver.resolve(localDoc, entry)

                val mergedDoc = resolution.mergedDocument
                if (resolution.shouldApply && mergedDoc != null) {
                    saveDocumentInternal(db, mergedDoc)
                }

                // Only append to oplog if we actually applied it? 
                // Or if it's new information.
                // For now, consistent with existing logic: append.
                appendOplogEntryInternal(db, entry)
            }
            db.setTransactionSuccessful()
            
            // Emit changed collections
            val changedCollections = documents.map { it.collection } + oplogEntries.map { it.collection }
            if (changedCollections.isNotEmpty()) {
                _changesApplied.emit(changedCollections.distinct())
            }
        } finally {
            db.endTransaction()
        }
    }

    override suspend fun applyRemoteChanges(changes: List<OplogEntry>) {
        applyBatch(emptyList(), changes)
    }

    override suspend fun queryDocuments(
        collection: String,
        filterJson: String?,
        skip: Int?,
        take: Int?,
        orderBy: String?,
        ascending: Boolean
    ): List<Document> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val sb = StringBuilder("SELECT Key, JsonData, IsDeleted, HlcWall, HlcLogic, HlcNode FROM Documents WHERE Collection = ? AND IsDeleted = 0")
        val args = mutableListOf(collection)

        if (filterJson != null) {
            sb.append(" AND ($filterJson)")
        }

        if (orderBy != null) {
            sb.append(" ORDER BY json_extract(JsonData, '$.$orderBy') ${if (ascending) "ASC" else "DESC"}")
        } else {
            sb.append(" ORDER BY Key ASC")
        }
        
        if (take != null) {
            sb.append(" LIMIT $take")
            if (skip != null) {
                sb.append(" OFFSET $skip")
            }
        }

        val cursor = db.rawQuery(sb.toString(), args.toTypedArray())
        val result = mutableListOf<Document>()
        cursor.use {
            while (it.moveToNext()) {
                result.add(mapDocument(it, collection))
            }
        }
        return@withContext result
    }

    override suspend fun countDocuments(collection: String, filterJson: String?): Int = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val sb = StringBuilder("SELECT COUNT(*) FROM Documents WHERE Collection = ? AND IsDeleted = 0")
        val args = mutableListOf(collection)
         if (filterJson != null) {
            sb.append(" AND ($filterJson)")
        }
        val cursor = db.rawQuery(sb.toString(), args.toTypedArray())
        cursor.use {
            if (it.moveToFirst()) {
                return@withContext it.getInt(0)
            }
        }
        return@withContext 0
    }

    override suspend fun getCollections(): List<String> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT DISTINCT Collection FROM Documents ORDER BY Collection", null)
        val result = mutableListOf<String>()
        cursor.use {
             while (it.moveToNext()) {
                result.add(it.getString(0))
            }
        }
        return@withContext result
    }

    override suspend fun ensureIndex(collection: String, propertyPath: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        
        // Sanitize for safety
        val safeColl = collection.replace(Regex("[^a-zA-Z0-9]"), "")
        val safeProp = propertyPath.replace(Regex("[^a-zA-Z0-9_.]"), "")
        val indexName = "IDX_${safeColl}_${safeProp.replace(".", "_")}"
        
        // SQLite JSON index syntax
        // CREATE INDEX IF NOT EXISTS {indexName} ON Documents(json_extract(JsonData, '$.{safeProp}')) WHERE Collection = '{collection}'
        val sql = "CREATE INDEX IF NOT EXISTS $indexName ON Documents(json_extract(JsonData, '$.$safeProp')) WHERE Collection = '$safeColl'"
        
        db.execSQL(sql)
    }

    private fun mapDocument(cursor: Cursor, collection: String): Document {
        val key = cursor.getString(0)
        val jsonData = cursor.getString(1)
        val isDeleted = cursor.getInt(2) == 1
        val hlcWall = cursor.getLong(3)
        val hlcLogic = cursor.getInt(4)
        val hlcNode = cursor.getString(5)
        
        val content = if (jsonData != null) com.entgldb.core.common.JsonHelpers.json.parseToJsonElement(jsonData) else JsonObject(emptyMap())
        
        return Document(collection, key, content, HlcTimestamp(hlcWall, hlcLogic, hlcNode), isDeleted)
    }

    private fun mapOplogEntry(cursor: Cursor): OplogEntry {
        val collection = cursor.getString(0)
        val key = cursor.getString(1)
        val operation = OperationType.values()[cursor.getInt(2)]
        val jsonData = cursor.getString(3)
        val hlcWall = cursor.getLong(4)
        val hlcLogic = cursor.getInt(5)
        val hlcNode = cursor.getString(6)
        
        val payload = if (jsonData != null) com.entgldb.core.common.JsonHelpers.json.parseToJsonElement(jsonData) else null

        return OplogEntry(collection, key, operation, payload, HlcTimestamp(hlcWall, hlcLogic, hlcNode))
    }

    private class DbHelper(context: Context, name: String) : SQLiteOpenHelper(context, name, null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            // db.enableWriteAheadLogging() is handled in onConfigure
            // Do not run PRAGMA journal_mode via execSQL here as it returns a result and confuses some runners
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS Documents (
                    Collection TEXT NOT NULL,
                    Key TEXT NOT NULL,
                    JsonData TEXT,
                    IsDeleted INTEGER NOT NULL,
                    HlcWall INTEGER NOT NULL,
                    HlcLogic INTEGER NOT NULL,
                    HlcNode TEXT NOT NULL,
                    PRIMARY KEY (Collection, Key)
                )
            """)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS Oplog (
                    Id INTEGER PRIMARY KEY AUTOINCREMENT,
                    Collection TEXT NOT NULL,
                    Key TEXT NOT NULL,
                    Operation INTEGER NOT NULL,
                    JsonData TEXT,
                    IsDeleted INTEGER NOT NULL,
                    HlcWall INTEGER NOT NULL,
                    HlcLogic INTEGER NOT NULL,
                    HlcNode TEXT NOT NULL
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS IDX_Oplog_HlcWall ON Oplog(HlcWall);")
        }

        override fun onUpgrade(db: SQLiteDatabase?, oldVersion: Int, newVersion: Int) {
            // Drop and recreate in demo
        }
        
        override fun onConfigure(db: SQLiteDatabase) {
             super.onConfigure(db)
             db.enableWriteAheadLogging()
        }
    }
}
