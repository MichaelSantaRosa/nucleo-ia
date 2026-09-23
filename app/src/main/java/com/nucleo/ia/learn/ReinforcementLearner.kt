package com.nucleo.ia.learn

class ReinforcementLearner {

    private val goodCounts = IntArray(8)
    private val badCounts = IntArray(8)

    fun record(intentId: Int, good: Boolean) {
        if (intentId < 0 || intentId >= 8) return
        if (good) goodCounts[intentId]++ else badCounts[intentId]++
    }

    fun accuracy(): Float {
        var totalGood = 0
        var totalBad = 0
        for (i in 0 until 8) {
            totalGood += goodCounts[i]
            totalBad += badCounts[i]
        }
        val total = totalGood + totalBad
        if (total == 0) return 0.5f
        return totalGood.toFloat() / total.toFloat()
    }

    fun reset() {
        goodCounts.fill(0)
        badCounts.fill(0)
    }
}