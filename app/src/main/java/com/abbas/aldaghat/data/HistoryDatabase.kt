package com.abbas.aldaghat.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.abbas.aldaghat.data.model.HistoryEntry
import com.abbas.aldaghat.domain.HistoryStatus

/**
 * Plain SQLite persistence for the operation log and for in-flight jobs.
 *
 * A plain SQLiteOpenHelper is used on purpose: no annotation processor is required, which keeps
 * the build reproducible offline and avoids a Kotlin/KSP version coupling.
 */
class HistoryDatabase(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DB_NAME, null, DB_VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                source_name TEXT NOT NULL,
                source_uri TEXT NOT NULL,
                output_name TEXT,
                output_uri TEXT,
                original_bytes INTEGER NOT NULL DEFAULT 0,
                output_bytes INTEGER NOT NULL DEFAULT 0,
                width INTEGER NOT NULL DEFAULT 0,
                height INTEGER NOT NULL DEFAULT 0,
                quality TEXT NOT NULL,
                codec TEXT NOT NULL,
                status TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                error TEXT,
                thumb_path TEXT
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX idx_history_created ON $TABLE (created_at DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Version 1 is the first shipped schema.
    }

    @Synchronized
    fun insertRunning(
        sourceName: String,
        sourceUri: String,
        originalBytes: Long,
        quality: String,
        codec: String,
        width: Int,
        height: Int,
        thumbPath: String?,
    ): Long {
        val values = ContentValues().apply {
            put("source_name", sourceName)
            put("source_uri", sourceUri)
            put("original_bytes", originalBytes)
            put("quality", quality)
            put("codec", codec)
            put("width", width)
            put("height", height)
            put("thumb_path", thumbPath)
            put("status", HistoryStatus.RUNNING.name)
            put("created_at", System.currentTimeMillis())
        }
        return writableDatabase.insertOrThrow(TABLE, null, values)
    }

    @Synchronized
    fun complete(
        id: Long,
        outputName: String,
        outputUri: String,
        outputBytes: Long,
        width: Int,
        height: Int,
        thumbPath: String?,
    ) {
        val values = ContentValues().apply {
            put("output_name", outputName)
            put("output_uri", outputUri)
            put("output_bytes", outputBytes)
            put("width", width)
            put("height", height)
            put("status", HistoryStatus.COMPLETED.name)
            put("error", null as String?)
            if (thumbPath != null) put("thumb_path", thumbPath)
        }
        writableDatabase.update(TABLE, values, "id = ?", arrayOf(id.toString()))
    }

    @Synchronized
    fun updateStatus(id: Long, status: HistoryStatus, error: String? = null) {
        val values = ContentValues().apply {
            put("status", status.name)
            put("error", error)
        }
        writableDatabase.update(TABLE, values, "id = ?", arrayOf(id.toString()))
    }

    @Synchronized
    fun markAllRunningAsInterrupted(): Int {
        val values = ContentValues().apply {
            put("status", HistoryStatus.INTERRUPTED.name)
            put("error", "تمت مقاطعة العملية قبل اكتمالها")
        }
        return writableDatabase.update(
            TABLE,
            values,
            "status = ?",
            arrayOf(HistoryStatus.RUNNING.name),
        )
    }

    @Synchronized
    fun delete(id: Long) {
        writableDatabase.delete(TABLE, "id = ?", arrayOf(id.toString()))
    }

    @Synchronized
    fun queryAll(): List<HistoryEntry> {
        val entries = mutableListOf<HistoryEntry>()
        readableDatabase.query(
            TABLE,
            null,
            null,
            null,
            null,
            null,
            "created_at DESC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                entries += HistoryEntry(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    sourceName = cursor.getString(cursor.getColumnIndexOrThrow("source_name")),
                    sourceUri = cursor.getString(cursor.getColumnIndexOrThrow("source_uri")),
                    outputName = cursor.getStringOrNull("output_name"),
                    outputUri = cursor.getStringOrNull("output_uri"),
                    originalBytes = cursor.getLong(cursor.getColumnIndexOrThrow("original_bytes")),
                    outputBytes = cursor.getLong(cursor.getColumnIndexOrThrow("output_bytes")),
                    width = cursor.getInt(cursor.getColumnIndexOrThrow("width")),
                    height = cursor.getInt(cursor.getColumnIndexOrThrow("height")),
                    quality = cursor.getString(cursor.getColumnIndexOrThrow("quality")),
                    codec = cursor.getString(cursor.getColumnIndexOrThrow("codec")),
                    status = HistoryStatus.fromStorage(
                        cursor.getString(cursor.getColumnIndexOrThrow("status")),
                    ),
                    createdAt = cursor.getLong(cursor.getColumnIndexOrThrow("created_at")),
                    error = cursor.getStringOrNull("error"),
                    thumbPath = cursor.getStringOrNull("thumb_path"),
                )
            }
        }
        return entries
    }

    private fun android.database.Cursor.getStringOrNull(column: String): String? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getString(index)
    }

    companion object {
        private const val DB_NAME = "aldaghat.db"
        private const val DB_VERSION = 1
        private const val TABLE = "compression_history"
    }
}
