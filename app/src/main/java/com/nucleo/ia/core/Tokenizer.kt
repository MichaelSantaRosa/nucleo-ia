package com.nucleo.ia.core

object Tokenizer {
    const val VOCAB = 512
    const val SEQ_LEN = 16

    private val charMap: Map<Char, Int> = buildMap {
        for (c in 'a'..'z') put(c, (c - 'a' + 1))       // 1..26
        for (c in '0'..'9') put(c, (c - '0' + 27))      // 27..36
        put(' ', 37)
        put('.', 38)
        put(',', 39)
        put('?', 40)
        put('!', 41)
    }

    fun encode(text: String): IntArray {
        val out = IntArray(SEQ_LEN) // preenchido com zeros por padrão
        val lower = text.lowercase()
        var i = 0
        for (ch in lower) {
            if (i >= SEQ_LEN) break
            val id = charMap[ch] ?: 0
            out[i] = id
            i++
        }
        return out
    }

    fun decode(ids: IntArray): String {
        val reverse = charMap.entries.associate { (k, v) -> v to k }
        return ids.filter { it != 0 }.mapNotNull { reverse[it] }.joinToString("")
    }
}