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
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

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
        const val DATABASE_VERSION = 2
    }
}
