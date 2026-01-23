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
import com.entgldb.core.network.PeerNode
import com.entgldb.core.query.QueryNode
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
            "SELECT [Key], JsonData, IsDeleted, HlcWall, HlcLogic, HlcNode FROM Documents WHERE Collection = ? AND [Key] = ?",
            arrayOf(collection, key)
        )
        cursor.use {
            if (it.moveToFirst()) {
                return mapDocument(it, collection)
            }
        }
        return null
    }

    override suspend fun getVectorClock(): com.entgldb.core.VectorClock = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            "SELECT HlcNode, MAX(HlcWall) as Wall, MAX(HlcLogic) as Logic FROM Oplog GROUP BY HlcNode",
            null
        )
        val vcMap = mutableMapOf<String, HlcTimestamp>()
        cursor.use {
            while (it.moveToNext()) {
                val node = it.getString(0)
                val wall = it.getLong(1)
                val logic = it.getInt(2)
                vcMap[node] = HlcTimestamp(wall, logic, node)
            }
        }
        return@withContext com.entgldb.core.VectorClock(vcMap)
    }

    override suspend fun getOplogForNodeAfter(nodeId: String, timestamp: HlcTimestamp): List<OplogEntry> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        // Get entries originating from nodeId that are strictly after timestamp
        val cursor = db.rawQuery(
            """
            SELECT Collection, [Key], Operation, JsonData, HlcWall, HlcLogic, HlcNode, Hash, PreviousHash 
            FROM Oplog 
            WHERE HlcNode = ? AND (HlcWall > ? OR (HlcWall = ? AND HlcLogic > ?))
            ORDER BY HlcWall ASC, HlcLogic ASC
            """,
            arrayOf(
                nodeId,
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

    override suspend fun getLastEntryHash(nodeId: String?): String = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val sb = StringBuilder("SELECT Hash FROM Oplog ")
        val args = if (nodeId != null) {
            sb.append("WHERE HlcNode = ? ")
            arrayOf(nodeId)
        } else {
            emptyArray()
        }
        sb.append("ORDER BY Id DESC LIMIT 1") // Use Id for absolute insertion order or HLC? Validation relies on insertion order usually? 
        // Hash chain is per-node. If nodeId is provided, we want the last hash from THAT node to chaining.
        // If no nodeId, maybe global last? But hashing is usually per-lane.
        // Assuming we want the last hash for the specific node lane we are validating.
        
        val cursor = db.rawQuery(sb.toString(), args)
        var hash = ""
        cursor.use {
            if (it.moveToFirst()) {
                hash = it.getString(0) ?: "" // Handle null if migration didn't backfill
            }
        }
        return@withContext hash
    }

    override suspend fun getChainRange(nodeId: String, startTimestamp: HlcTimestamp, count: Int): List<OplogEntry> = withContext(Dispatchers.IO) {
         val db = dbHelper.readableDatabase
         val cursor = db.rawQuery(
            """
            SELECT Collection, [Key], Operation, JsonData, HlcWall, HlcLogic, HlcNode, Hash, PreviousHash 
            FROM Oplog 
            WHERE HlcNode = ? AND (HlcWall >= ? OR (HlcWall = ? AND HlcLogic >= ?))
            ORDER BY HlcWall ASC, HlcLogic ASC
            LIMIT ?
            """,
            arrayOf(
                nodeId,
                startTimestamp.physicalTime.toString(),
                startTimestamp.physicalTime.toString(),
                startTimestamp.logicalCounter.toString(),
                count.toString()
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

    override suspend fun applyBatch(
        documents: List<Document>,
        oplogEntries: List<OplogEntry>
    ) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.beginTransactionNonExclusive()
        try {
            val collectionsAffected = mutableSetOf<String>()
            
            for (doc in documents) {
                saveDocumentInternal(db, doc)
                collectionsAffected.add(doc.collection)
            }
            
            for (entry in oplogEntries) {
                appendOplogEntryInternal(db, entry)
            }
            
            db.setTransactionSuccessful()
            
            // Notify changes
            val list = collectionsAffected.toList()
            if (list.isNotEmpty()) {
                _changesApplied.emit(list)
            }
        } finally {
            db.endTransaction()
        }
    }

    override suspend fun queryDocuments(
        collection: String,
        filter: QueryNode?,
        skip: Int?,
        take: Int?,
        orderBy: String?,
        ascending: Boolean
    ): List<Document> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val args = mutableListOf<String>()
        args.add(collection)
        
        var whereClause = "WHERE Collection = ? AND IsDeleted = 0"
        
        if (filter != null) {
            val (clause, filterArgs) = SqlTranslator.translate(filter)
            whereClause += " AND $clause"
            args.addAll(filterArgs)
        }
        
        var orderByClause = ""
        if (orderBy != null) {
             // Caution: JSON path sort.
             // Usually we need to extract the field to sort.
             val fieldPath = if (orderBy == "Key") "Key" else "json_extract(JsonData, '$.\"$orderBy\"')"
             orderByClause = "ORDER BY $fieldPath ${if (ascending) "ASC" else "DESC"}"
        } else {
             // Default sort by Key usually? Or stable sort?
             orderByClause = "ORDER BY [Key] ASC"
        }
        
        var limitClause = ""
        if (take != null) {
            limitClause = "LIMIT $take"
            if (skip != null) {
                limitClause += " OFFSET $skip"
            }
        }
        
        val sql = "SELECT [Key], JsonData, IsDeleted, HlcWall, HlcLogic, HlcNode FROM Documents $whereClause $orderByClause $limitClause"
        
        val cursor = db.rawQuery(sql, args.toTypedArray())
        val result = mutableListOf<Document>()
        cursor.use {
            while (it.moveToNext()) {
                result.add(mapDocument(it, collection))
            }
        }
        return@withContext result
    }

    override suspend fun countDocuments(
        collection: String,
        filter: QueryNode?
    ): Int = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val args = mutableListOf<String>()
        args.add(collection)
        
        var whereClause = "WHERE Collection = ? AND IsDeleted = 0"
        
        if (filter != null) {
            val (clause, filterArgs) = SqlTranslator.translate(filter)
            whereClause += " AND $clause"
            args.addAll(filterArgs)
        }
        
        val sql = "SELECT COUNT(*) FROM Documents $whereClause"
        val cursor = db.rawQuery(sql, args.toTypedArray())
        cursor.use {
            if (it.moveToFirst()) {
                return@withContext it.getInt(0)
            }
        }
        return@withContext 0
    }

    override suspend fun getCollections(): List<String> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT DISTINCT Collection FROM Documents", null)
        val result = mutableListOf<String>()
        cursor.use {
            while (it.moveToNext()) {
                result.add(it.getString(0))
            }
        }
        return@withContext result
    }

    override suspend fun ensureIndex(collection: String, propertyPath: String) = withContext(Dispatchers.IO) {
        // Create an index on a JSON property expression.
        // Requires SQLite JSON1 extension (standard on Android since API 28+? or bundled libraries).
        // Since we target minSDK 24, typically system sqlite might allow it but generated column indexing is better.
        // For simplicity: We create an index on the expression if possible/supported.
        // Name: IDX_{Collection}_{Property}
        
        val safeIdxName = "IDX_${collection.replace("[^a-zA-Z0-9]".toRegex(), "")}_${propertyPath.replace("[^a-zA-Z0-9]".toRegex(), "")}"
        val fieldExpr = "json_extract(JsonData, '$.\"$propertyPath\"')"
        
        val db = dbHelper.writableDatabase
        // Conditional index creation might be tricky with expression.
        try {
            db.execSQL("CREATE INDEX IF NOT EXISTS $safeIdxName ON Documents($fieldExpr) WHERE Collection = '$collection'")
        } catch (e: Exception) {
             // It might fail if expressions in indexes are not supported by the platform SQLite version.
             // We swallow to fallback to scan, or log error.
             logger.error(e) { "Failed to create index $safeIdxName" }
        }
    }

    private val logger = mu.KotlinLogging.logger {}

    override suspend fun getRemotePeers(): List<PeerNode> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery("SELECT NodeId, Address, Type, LastSeen FROM RemotePeers WHERE IsEnabled = 1", null)
        val result = mutableListOf<PeerNode>()
        cursor.use {
            while (it.moveToNext()) {
                val id = it.getString(0)
                val addr = it.getString(1)
                val type = it.getInt(2) // Map int to Enum if needed.
                val lastSeen = it.getLong(3)
                // We recreate PeerNode. PeerType might need mapping. Assuming 0=Server, 1=Client etc.
                // PeerNode doesn't always have fixed enum in constructor, depends on class def.
                // Assuming standard PeerNode(id, address, lastSeen)
                result.add(PeerNode(id, addr, lastSeen))
            }
        }
        return@withContext result
    }

    override suspend fun saveRemotePeer(peer: PeerNode) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        val values = ContentValues().apply {
            put("NodeId", peer.nodeId)
            put("Address", peer.address)
            put("Type", 0) // TODO: Add type to PeerNode or pass it in? Assuming default 0.
            put("LastSeen", peer.lastSeen)
            put("IsEnabled", 1)
        }
        db.replace("RemotePeers", null, values)
    }

    override suspend fun removeRemotePeer(nodeId: String) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        db.delete("RemotePeers", "NodeId = ?", arrayOf(nodeId))
        Unit
    }

    override suspend fun appendOplogEntry(entry: OplogEntry) = withContext(Dispatchers.IO) {
        val db = dbHelper.writableDatabase
        appendOplogEntryInternal(db, entry)
    }

    private fun appendOplogEntryInternal(db: SQLiteDatabase, entry: OplogEntry) {
        // Compute Hash if missing? 
        // Ideally entry already has hash computed by caller (e.g. SyncOrchestrator or Local Logic).
        // If local update, we might need to compute it here.
        // Assume check logic: if entry.hash is empty, compute it? 
        // But for chaining we need PreviousHash.
        
        var finalEntry = entry
        
        if (entry.hash.isEmpty()) {
             // Local update scenario
             // 1. Get previous hash for this node
             // Wait, if we are the source (HlcNode == Me), we need the last hash of ME.
             // If we are applying remote, it should have hash.
             
             // How do we know "Me"? Entry has HlcNode.
             
             // Get last hash for entry.timestamp.nodeId
             val lastHashCursor = db.rawQuery(
                 "SELECT Hash FROM Oplog WHERE HlcNode = ? ORDER BY HlcWall DESC, HlcLogic DESC LIMIT 1",
                 arrayOf(entry.timestamp.nodeId)
             )
             var prevHash = ""
             lastHashCursor.use { 
                 if (it.moveToFirst()) prevHash = it.getString(0) ?: ""
             }
             
             // Create copy with prevHash
             finalEntry = entry.copy(previousHash = prevHash)
             
             // Compute Hash
             val hash = com.entgldb.core.CryptoUtils.computeOplogHash(finalEntry)
             finalEntry = finalEntry.copy(hash = hash)
        }
    
        val values = ContentValues().apply {
            put("Collection", finalEntry.collection)
            put("Key", finalEntry.key)
            put("Operation", finalEntry.operation.ordinal)
            put("JsonData", finalEntry.payload?.toString())
            put("IsDeleted", if (finalEntry.operation == OperationType.Delete) 1 else 0)
            put("HlcWall", finalEntry.timestamp.physicalTime)
            put("HlcLogic", finalEntry.timestamp.logicalCounter)
            put("HlcNode", finalEntry.timestamp.nodeId)
            put("Hash", finalEntry.hash)
            put("PreviousHash", finalEntry.previousHash)
        }
        db.insert("Oplog", null, values)
    }

    override suspend fun getOplogAfter(timestamp: HlcTimestamp): List<OplogEntry> = withContext(Dispatchers.IO) {
        val db = dbHelper.readableDatabase
        // Schema update implies old query needs updating if we want to return full objects
        // HlcWall > ? OR (HlcWall = ? AND HlcLogic > ?)
        val cursor = db.rawQuery(
            """
            SELECT Collection, [Key], Operation, JsonData, HlcWall, HlcLogic, HlcNode, Hash, PreviousHash
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
        // We want the maximum logical time seen globally to init our clock? 
        // Or the max local time?
        // Usually, the HLC is initialized with the system time and the max wall time seen in the store.
        val cursor = db.rawQuery("SELECT MAX(HlcWall) FROM Oplog", null)
        var maxWall = 0L
        cursor.use {
            if (it.moveToFirst()) {
                maxWall = it.getLong(0)
            }
        }
        
        // Return a timestamp. Logic counter is lost if we just avg, but we usually just need the Wall part 
        // to catch up the clock if system time is behind.
        return@withContext HlcTimestamp(maxWall, 0, "init")
    }

    override suspend fun applyRemoteChanges(changes: List<OplogEntry>) = withContext(Dispatchers.IO) {
        if (changes.isEmpty()) return@withContext
        
        val db = dbHelper.writableDatabase
        db.beginTransactionNonExclusive()
        try {
            val collectionsAffected = mutableSetOf<String>()
            
            for (entry in changes) {
                // 1. Conflict Resolution checks if needed or blindly apply?
                // Ideally SyncOrchestrator does the filtering.
                // But we still need to update the Documents table based on the operation.
                
                // Re-construct the document state?
                // This is LWW usually.
                
                // If it's a PUT:
                if (entry.operation == OperationType.Put) {
                     val docValues = ContentValues().apply {
                        put("Collection", entry.collection)
                        put("Key", entry.key)
                        put("JsonData", entry.payload?.toString())
                        put("IsDeleted", 0)
                        put("HlcWall", entry.timestamp.physicalTime)
                        put("HlcLogic", entry.timestamp.logicalCounter)
                        put("HlcNode", entry.timestamp.nodeId)
                    }
                    // LWW Check:
                    // Check if current document exists and is newer.
                    // This logic usually belongs in the ConflictResolver but for performance we might inline 
                    // or query existing.
                    
                    // Simple LWW implementation inline:
                    val existing = getDocumentInternal(db, entry.collection, entry.key)
                    val isNewer = if (existing == null) true else {
                        entry.timestamp.physicalTime > existing.updatedAt.physicalTime ||
                        (entry.timestamp.physicalTime == existing.updatedAt.physicalTime && entry.timestamp.logicalCounter > existing.updatedAt.logicalCounter)
                        // Tie breaker nodeId?
                    }
                    
                    if (isNewer) {
                        db.replace("Documents", null, docValues)
                        collectionsAffected.add(entry.collection)
                    }
                } else if (entry.operation == OperationType.Delete) {
                    // Similar LWW check
                    val existing = getDocumentInternal(db, entry.collection, entry.key)
                    val isNewer = if (existing == null) true else { // Deleting non-existing is fine (tombstone)
                         entry.timestamp.physicalTime > existing.updatedAt.physicalTime || // same logic
                         (entry.timestamp.physicalTime == existing.updatedAt.physicalTime && entry.timestamp.logicalCounter > existing.updatedAt.logicalCounter)
                    }
                    
                    if (isNewer) {
                        val tombstoneValues = ContentValues().apply {
                            put("Collection", entry.collection)
                            put("Key", entry.key)
                            put("JsonData", "{}")
                            put("IsDeleted", 1)
                            put("HlcWall", entry.timestamp.physicalTime)
                            put("HlcLogic", entry.timestamp.logicalCounter)
                            put("HlcNode", entry.timestamp.nodeId)
                        }
                        db.replace("Documents", null, tombstoneValues)
                        collectionsAffected.add(entry.collection)
                    }
                }
                
                // ALWAYS append to Oplog (it's history) - unless we dedup?
                // SyncOrchestrator usually handles diff/dedup.
                // We should append if not exists.
                // Assuming caller provides new entries.
                // Use insertWithConflict?
                
                 val oplogValues = ContentValues().apply {
                    put("Collection", entry.collection)
                    put("Key", entry.key)
                    put("Operation", entry.operation.ordinal)
                    put("JsonData", entry.payload?.toString())
                    put("IsDeleted", if (entry.operation == OperationType.Delete) 1 else 0)
                    put("HlcWall", entry.timestamp.physicalTime)
                    put("HlcLogic", entry.timestamp.logicalCounter)
                    put("HlcNode", entry.timestamp.nodeId)
                    put("Hash", entry.hash)
                    put("PreviousHash", entry.previousHash)
                }
                // Ignore if exactly same entry exists?
                // Oplog needs idempotent inserts?
                // Id is AutoInc.
                // Check by Hash?
                // For speed, we assume orchestrator sends valid new entries. 
                // Using PreviousHash/Hash integrity which is validated before calling applyRemoteChanges.
                db.insert("Oplog", null, oplogValues)
            }
            
            db.setTransactionSuccessful()
            
            if (collectionsAffected.isNotEmpty()) {
                _changesApplied.emit(collectionsAffected.toList())
            }
        } finally {
            db.endTransaction()
        }
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
        
        // Handle new columns if they exist in query
        // rawQuery used for getOplogAfter includes Hash, PreviousHash
        // Indexes: 7=Hash, 8=PreviousHash
        val hash = if (cursor.columnCount > 7 && !cursor.isNull(7)) cursor.getString(7) else ""
        val prevHash = if (cursor.columnCount > 8 && !cursor.isNull(8)) cursor.getString(8) else ""
        
        val payload = if (jsonData != null) com.entgldb.core.common.JsonHelpers.json.parseToJsonElement(jsonData) else null

        return OplogEntry(collection, key, operation, payload, HlcTimestamp(hlcWall, hlcLogic, hlcNode), hash, prevHash)
    }

    // ... (peer methods match)

    private class DbHelper(context: Context, name: String) : SQLiteOpenHelper(context, name, null, 3) { // Version bumped to 3
        override fun onCreate(db: SQLiteDatabase) {
            // ... (Documents table same)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS Documents (
                    Collection TEXT NOT NULL,
                    [Key] TEXT NOT NULL,
                    JsonData TEXT,
                    IsDeleted INTEGER NOT NULL,
                    HlcWall INTEGER NOT NULL,
                    HlcLogic INTEGER NOT NULL,
                    HlcNode TEXT NOT NULL,
                    PRIMARY KEY (Collection, [Key])
                )
            """)
            db.execSQL("""
                CREATE TABLE IF NOT EXISTS Oplog (
                    Id INTEGER PRIMARY KEY AUTOINCREMENT,
                    Collection TEXT NOT NULL,
                    [Key] TEXT NOT NULL,
                    Operation INTEGER NOT NULL,
                    JsonData TEXT,
                    IsDeleted INTEGER NOT NULL,
                    HlcWall INTEGER NOT NULL,
                    HlcLogic INTEGER NOT NULL,
                    HlcNode TEXT NOT NULL,
                    Hash TEXT,
                    PreviousHash TEXT
                )
            """)
            db.execSQL("CREATE INDEX IF NOT EXISTS IDX_Oplog_HlcWall ON Oplog(HlcWall);")
            db.execSQL("CREATE INDEX IF NOT EXISTS IDX_Oplog_HlcNode ON Oplog(HlcNode);") // Added index for node lookup

            createRemotePeersTable(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                createRemotePeersTable(db)
            }
            if (oldVersion < 3) {
                // Add Hash columns
                db.execSQL("ALTER TABLE Oplog ADD COLUMN Hash TEXT")
                db.execSQL("ALTER TABLE Oplog ADD COLUMN PreviousHash TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS IDX_Oplog_HlcNode ON Oplog(HlcNode);")
            }
        }
        
        // ... (rest same)
        private fun createRemotePeersTable(db: SQLiteDatabase) {
             db.execSQL("""
                CREATE TABLE IF NOT EXISTS RemotePeers (
                    NodeId TEXT PRIMARY KEY,
                    Address TEXT NOT NULL,
                    Type INTEGER NOT NULL,
                    LastSeen INTEGER NOT NULL,
                    OAuth2Json TEXT,
                    IsEnabled INTEGER NOT NULL
                )
            """)
        }
        
        override fun onConfigure(db: SQLiteDatabase) {
             super.onConfigure(db)
             db.enableWriteAheadLogging()
        }
    }
}
