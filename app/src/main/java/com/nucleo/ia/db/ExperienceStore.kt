package com.nucleo.ia.db

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase

data class Experience(val text: String, val intentId: Int, val confidence: Float, val success: Boolean)

class ExperienceStore(ctx: Context) {
    private val db: SQLiteDatabase = ctx.openOrCreateDatabase("nucleo.db", 0, null).apply {
        execSQL("CREATE TABLE IF NOT EXISTS experience (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, text TEXT, intent_id INTEGER, " +
                "confidence REAL, success INTEGER, ts INTEGER)")
    }

    fun save(text: String, intentId: Int, conf: Float, success: Boolean) {
        val cv = ContentValues().apply {
            put("text", text); put("intent_id", intentId)
            put("confidence", conf); put("success", if (success) 1 else 0)
            put("ts", System.currentTimeMillis())
        }
        db.insert("experience", null, cv)
        db.execSQL("DELETE FROM experience WHERE id NOT IN (SELECT id FROM experience ORDER BY ts DESC LIMIT 500)")
    }

    fun recent(limit: Int): MutableList<Experience> {
        val list = mutableListOf<Experience>()
        db.rawQuery("SELECT text,intent_id,confidence,success FROM experience ORDER BY ts DESC LIMIT $limit", null).use { c ->
            while (c.moveToNext()) {
                list.add(Experience(c.getString(0), c.getInt(1), c.getFloat(2), c.getInt(3) == 1))
            }
        }
        return list
    }
}