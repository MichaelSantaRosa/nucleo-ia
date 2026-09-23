package com.nucleo.ia.learn

class ReinforcementLearner(private val nActions: Int = 8, private val alpha: Float = 0.1f) {
    private val q = Array(nActions) { FloatArray(4) }
    private var lastState = 0
    private fun stateOf(c: Float) = when { c < 0.3f -> 0; c < 0.5f -> 1; c < 0.7f -> 2; else -> 3 }
    fun observe(action: Int, success: Boolean) {
        val r = if (success) 1f else -1f
        q[action][lastState] += alpha * (r - q[action][lastState])
    }
    fun setState(c: Float) { lastState = stateOf(c) }
    fun bestActionScore(a: Int): Float = q[a][lastState]
}