package com.nucleo.ia.core

object Tokenizer {
    const val VOCAB = 512
    private val charMap: Map<Char, Int> = buildMap {
        "abcdefghijklmnopqrstuvwxyz".forEachIndexed { i, c -> put(c, i + 1) }
        "0123456789".forEachIndexed { i, c -> put(c, i + 27) }
        put(' ', 37); put('.', 38); put(',', 39); put('?', 40); put('!', 41)
    }
    fun encode(text: String, maxLen: Int = 16): IntArray {
        val clean = text.lowercase().trim()
        val out = IntArray(maxLen)
        var idx = 0
        for (c in clean) { if (idx >= maxLen) break; out[idx++] = charMap[c] ?: 0 }
        return out
    }
}