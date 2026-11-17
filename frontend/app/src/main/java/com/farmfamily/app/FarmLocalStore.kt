package com.farmfamily.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONObject

internal data class PendingField(
    val localId: String,
    val name: String,
    val area: Double,
    val areaUnit: String,
    val location: String,
    val notes: String?
) {
    fun toJson(familyId: String): JSONObject = JSONObject()
        .put("family_id", familyId)
        .put("local_id", localId)
        .put("name", name)
        .put("area", area)
        .put("area_unit", areaUnit)
        .put("location", JSONObject().put("label", location))
        .put("notes", notes ?: JSONObject.NULL)
}

internal data class CachedFieldRecords(
    val fields: List<FarmFieldRecord>,
    val pendingCount: Int
)

internal class FarmLocalStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE farm_fields (
                family_id TEXT NOT NULL,
                local_id TEXT NOT NULL,
                server_id TEXT,
                name TEXT NOT NULL,
                area REAL,
                area_unit TEXT,
                location TEXT NOT NULL,
                sync_status TEXT NOT NULL,
                PRIMARY KEY (family_id, local_id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE pending_field_inserts (
                family_id TEXT NOT NULL,
                local_id TEXT NOT NULL,
                payload TEXT NOT NULL,
                PRIMARY KEY (family_id, local_id)
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE field_cache_state (
                family_id TEXT PRIMARY KEY,
                loaded_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        createGenericRecordTables(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 3) createGenericRecordTables(db)
    }

    private fun createGenericRecordTables(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE IF NOT EXISTS farm_record_cache (family_id TEXT NOT NULL, table_name TEXT NOT NULL, record_id TEXT NOT NULL, payload TEXT NOT NULL, PRIMARY KEY(family_id,table_name,record_id))")
        db.execSQL("CREATE TABLE IF NOT EXISTS pending_record_creates (family_id TEXT NOT NULL, table_name TEXT NOT NULL, local_id TEXT NOT NULL, payload TEXT NOT NULL, created_at INTEGER NOT NULL, sync_status TEXT NOT NULL DEFAULT 'PENDING', PRIMARY KEY(family_id,table_name,local_id))")
    }

    fun cacheRecords(familyId: String, table: String, records: List<JSONObject>) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete("farm_record_cache", "family_id=? AND table_name=?", arrayOf(familyId, table))
            records.forEach { record ->
                val id = record.optString("id")
                if (id.isNotBlank()) db.insertWithOnConflict("farm_record_cache", null, ContentValues().apply {
                    put("family_id", familyId); put("table_name", table); put("record_id", id); put("payload", record.toString())
                }, SQLiteDatabase.CONFLICT_REPLACE)
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun cachedRecords(familyId: String, table: String): List<JSONObject> = readableDatabase.query(
        "farm_record_cache", arrayOf("payload"), "family_id=? AND table_name=?", arrayOf(familyId, table), null, null, "rowid DESC"
    ).use { cursor -> buildList { while (cursor.moveToNext()) runCatching { JSONObject(cursor.getString(0)) }.getOrNull()?.let(::add) } }

    fun queueRecordCreate(familyId: String, table: String, localId: String, payload: JSONObject) {
        writableDatabase.insertWithOnConflict("pending_record_creates", null, ContentValues().apply {
            put("family_id", familyId); put("table_name", table); put("local_id", localId); put("payload", payload.toString())
            put("created_at", System.currentTimeMillis()); put("sync_status", "PENDING")
        }, SQLiteDatabase.CONFLICT_REPLACE)
    }

    fun pendingRecordCreates(familyId: String, table: String): List<Pair<String, JSONObject>> = readableDatabase.query(
        "pending_record_creates", arrayOf("local_id", "payload", "sync_status"), "family_id=? AND table_name=?", arrayOf(familyId, table), null, null, "created_at ASC"
    ).use { cursor -> buildList { while (cursor.moveToNext()) runCatching { cursor.getString(0) to JSONObject(cursor.getString(1)).put("_local_sync_status", cursor.getString(2)) }.getOrNull()?.let(::add) } }

    fun markPendingRecordFailed(familyId: String, table: String, localId: String) {
        writableDatabase.update("pending_record_creates", ContentValues().apply { put("sync_status", "FAILED") }, "family_id=? AND table_name=? AND local_id=?", arrayOf(familyId, table, localId))
    }

    fun remapPendingRecordReferences(familyId: String, localId: String, serverId: String) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.query("pending_record_creates", arrayOf("table_name", "local_id", "payload"), "family_id=?", arrayOf(familyId), null, null, "created_at ASC").use { cursor ->
                while (cursor.moveToNext()) {
                    val table = cursor.getString(0)
                    val pendingId = cursor.getString(1)
                    val payload = JSONObject(cursor.getString(2))
                    val keys = when (table) {
                        "crop_seasons" -> listOf("field_id", "crop_id")
                        "seed_records", "fertilizer_records", "labor_records", "machinery_usage", "expenses", "harvest_records" -> listOf("crop_season_id") + if (table == "expenses") listOf("category_id") else emptyList()
                        "sales" -> listOf("crop_season_id", "harvest_record_id")
                        else -> emptyList()
                    }
                    var changed = false
                    keys.forEach { key -> if (payload.optString(key) == localId) { payload.put(key, serverId); changed = true } }
                    if (changed) db.update("pending_record_creates", ContentValues().apply { put("payload", payload.toString()) }, "family_id=? AND table_name=? AND local_id=?", arrayOf(familyId, table, pendingId))
                }
            }
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }

    fun removePendingRecordCreate(familyId: String, table: String, localId: String) {
        writableDatabase.delete("pending_record_creates", "family_id=? AND table_name=? AND local_id=?", arrayOf(familyId, table, localId))
    }

    fun cacheFields(familyId: String, fields: List<FarmFieldRecord>) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete(
                "farm_fields",
                "family_id = ? AND local_id NOT IN (SELECT local_id FROM pending_field_inserts WHERE family_id = ?)",
                arrayOf(familyId, familyId)
            )
            fields.forEach { field ->
                val localId = field.localId ?: field.id
                writableDatabase.insertWithOnConflict(
                    "farm_fields",
                    null,
                    ContentValues().apply {
                        put("family_id", familyId)
                        put("local_id", localId)
                        put("server_id", field.id)
                        put("name", field.name)
                        put("area", field.area)
                        put("area_unit", field.areaUnit)
                        put("location", field.location)
                        put("sync_status", "SYNCED")
                    },
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            writableDatabase.insertWithOnConflict(
                "field_cache_state",
                null,
                ContentValues().apply {
                    put("family_id", familyId)
                    put("loaded_at", System.currentTimeMillis())
                },
                SQLiteDatabase.CONFLICT_REPLACE
            )
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun savePendingField(familyId: String, field: PendingField) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.insertWithOnConflict(
                "farm_fields",
                null,
                ContentValues().apply {
                    put("family_id", familyId)
                    put("local_id", field.localId)
                    put("name", field.name)
                    put("area", field.area)
                    put("area_unit", field.areaUnit)
                    put("location", field.location)
                    put("sync_status", "PENDING")
                },
                SQLiteDatabase.CONFLICT_REPLACE
            )
            writableDatabase.insertWithOnConflict(
                "pending_field_inserts",
                null,
                ContentValues().apply {
                    put("family_id", familyId)
                    put("local_id", field.localId)
                    put("payload", field.toJson(familyId).toString())
                },
                SQLiteDatabase.CONFLICT_REPLACE
            )
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun pendingFields(familyId: String): List<PendingField> =
        readableDatabase.query(
            "pending_field_inserts",
            arrayOf("local_id", "payload"),
            "family_id = ?",
            arrayOf(familyId),
            null,
            null,
            "rowid ASC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    val payload = JSONObject(cursor.getString(1))
                    val location = payload.optJSONObject("location")?.optString("label").orEmpty()
                    add(
                        PendingField(
                            localId = cursor.getString(0),
                            name = payload.getString("name"),
                            area = payload.getDouble("area"),
                            areaUnit = payload.getString("area_unit"),
                            location = location,
                            notes = if (payload.isNull("notes")) null else payload.optString("notes")
                        )
                    )
                }
            }
        }

    fun markFieldSynced(familyId: String, localId: String, serverId: String) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.update(
                "farm_fields",
                ContentValues().apply {
                    put("server_id", serverId)
                    put("sync_status", "SYNCED")
                },
                "family_id = ? AND local_id = ?",
                arrayOf(familyId, localId)
            )
            writableDatabase.delete(
                "pending_field_inserts",
                "family_id = ? AND local_id = ?",
                arrayOf(familyId, localId)
            )
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun markFieldSyncFailed(familyId: String, localId: String) {
        writableDatabase.update(
            "farm_fields",
            ContentValues().apply { put("sync_status", "FAILED") },
            "family_id = ? AND local_id = ?",
            arrayOf(familyId, localId)
        )
    }

    fun discardPendingField(familyId: String, localId: String) {
        writableDatabase.beginTransaction()
        try {
            writableDatabase.delete(
                "pending_field_inserts",
                "family_id = ? AND local_id = ?",
                arrayOf(familyId, localId)
            )
            writableDatabase.delete(
                "farm_fields",
                "family_id = ? AND local_id = ? AND sync_status = 'FAILED'",
                arrayOf(familyId, localId)
            )
            writableDatabase.setTransactionSuccessful()
        } finally {
            writableDatabase.endTransaction()
        }
    }

    fun fieldSyncStatus(familyId: String, localId: String): String? =
        readableDatabase.query(
            "farm_fields",
            arrayOf("sync_status"),
            "family_id = ? AND local_id = ?",
            arrayOf(familyId, localId),
            null,
            null,
            null,
            "1"
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }

    fun cachedFields(familyId: String): CachedFieldRecords? {
        val hasSnapshot = readableDatabase.query(
            "field_cache_state",
            arrayOf("family_id"),
            "family_id = ?",
            arrayOf(familyId),
            null,
            null,
            null,
            "1"
        ).use { it.moveToFirst() }
        val fields = readableDatabase.query(
            "farm_fields",
            arrayOf("local_id", "server_id", "name", "area", "area_unit", "location", "sync_status"),
            "family_id = ?",
            arrayOf(familyId),
            null,
            null,
            "name COLLATE NOCASE ASC"
        ).use { cursor ->
            buildList {
                while (cursor.moveToNext()) {
                    add(
                        FarmFieldRecord(
                            id = cursor.getString(1) ?: cursor.getString(0),
                            localId = cursor.getString(0),
                            name = cursor.getString(2),
                            area = if (cursor.isNull(3)) null else cursor.getDouble(3),
                            areaUnit = cursor.getString(4),
                            location = cursor.getString(5),
                            syncStatus = cursor.getString(6)
                        )
                    )
                }
            }
        }
        val pendingCount = pendingFields(familyId).size
        if (!hasSnapshot && fields.isEmpty()) return null
        return CachedFieldRecords(fields, pendingCount)
    }

    private companion object {
        const val DATABASE_NAME = "farm_family_local.db"
        const val DATABASE_VERSION = 3
    }
}
