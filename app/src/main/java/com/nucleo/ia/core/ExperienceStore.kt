package com.nucleo.ia.core

import android.content.Context

data class ExperienceStats(
    val success: Int,
    val fail: Int,
    val total: Int
)

class ExperienceStore(context: Context) {
    private val prefs = context.getSharedPreferences(
        "experience_store",
        Context.MODE_PRIVATE
    )

    fun record(input: String, intentId: Int, success: Boolean) {
        val total = prefs.getInt("total", 0) + 1
        val successes = prefs.getInt("success", 0) + if (success) 1 else 0
        val failures = prefs.getInt("fail", 0) + if (success) 0 else 1
        prefs.edit()
            .putInt("total", total)
            .putInt("success", successes)
            .putInt("fail", failures)
            .apply()
    }

    fun getStats(): ExperienceStats {
        return ExperienceStats(
            success = prefs.getInt("success", 0),
            fail = prefs.getInt("fail", 0),
            total = prefs.getInt("total", 0)
        )
    }
}