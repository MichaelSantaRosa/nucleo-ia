package com.nucleo.ia.core

import android.content.Context

class ReinforcementLearner(context: Context) {
    private val prefs = context.getSharedPreferences(
        "rl_prefs",
        Context.MODE_PRIVATE
    )
    private val rewards = mutableMapOf<Int, Float>()

    init {
        val saved = prefs.getString("rewards", "") ?: ""
        if (saved.isNotEmpty()) {
            saved.split(";").forEach { part ->
                val kv = part.split(":")
                if (kv.size == 2) rewards[kv[0].toInt()] = kv[1].toFloat()
            }
        }
    }

    fun update(intentId: Int, reward: Float) {
        rewards[intentId] = (rewards[intentId] ?: 0f) + reward
        persist()
    }

    fun record(intentId: Int, good: Boolean) {
        update(intentId, if (good) 1f else -1f)
    }

    fun rewardFor(intentId: Int): Float = rewards[intentId] ?: 0f

    fun accuracy(): Float {
        if (rewards.isEmpty()) return 0f
        val total = rewards.values.sum()
        return if (total <= 0f) 0f else (total / rewards.size).coerceIn(0f, 1f)
    }

    private fun persist() {
        val s = rewards.entries.joinToString(";") { "${it.key}:${it.value}" }
        prefs.edit().putString("rewards", s).apply()
    }
}