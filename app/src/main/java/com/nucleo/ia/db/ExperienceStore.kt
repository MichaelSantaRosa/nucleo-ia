package com.nucleo.ia.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class ExperienceStore(context: Context) : SQLiteOpenHelper(context, "nucleo.db", null, 1) {

    data class Experience(val text: String, val intentId: Int, val reward: Float)

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS experiences (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "text TEXT NOT NULL, " +
            "intent_id INTEGER NOT NULL, " +
            "reward REAL NOT NULL, " +
            "created_at INTEGER NOT NULL)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS experiences")
        onCreate(db)
    }

    fun add(text: String, intentId: Int, reward: Float) {
        val values = ContentValues().apply {
            put("text", text)
            put("intent_id", intentId)
            put("reward", reward)
            put("created_at", System.currentTimeMillis())
        }
        writableDatabase.insert("experiences", null, values)
        trimTo(500)
    }

    fun recent(limit: Int): List<Experience> {
        val result = mutableListOf<Experience>()
        readableDatabase.rawQuery(
            "SELECT text, intent_id, reward FROM experiences ORDER BY id DESC LIMIT ?",
            arrayOf(limit.toString())
        ).use { c ->
            while (c.moveToNext()) {
                result.add(Experience(c.getString(0), c.getInt(1), c.getFloat(2)))
            }
        }
        return result
    }

    fun count(): Int {
        readableDatabase.rawQuery("SELECT COUNT(*) FROM experiences", null).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    private fun trimTo(maxRows: Int) {
        val db = writableDatabase
        db.execSQL(
            "DELETE FROM experiences WHERE id NOT IN (SELECT id FROM experiences ORDER BY id DESC LIMIT ?)",
            arrayOf(maxRows.toString())
        )
    }
}