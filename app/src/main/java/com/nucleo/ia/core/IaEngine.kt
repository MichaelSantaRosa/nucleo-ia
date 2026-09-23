package com.nucleo.ia.core

import android.content.Context
import android.util.Log

enum class Intent(val id: Int, val label: String) {
    GREETING(0, "saudacao"),
    QUESTION(1, "pergunta"),
    TASK(2, "tarefa"),
    CHITCHAT(3, "conversa"),
    THANKS(4, "agradecimento"),
    FAREWELL(5, "despedida"),
    COMMAND(6, "comando"),
    UNKNOWN(7, "desconhecido")
}

class IaEngine(context: Context) {

    private val store = ExperienceStore(context)
    private val rl = ReinforcementLearner(context)
    private var handle: Long = 0L

    init {
        try {
            handle = NativeBridge.nativeCreateBrain(4096, 32, 32, 64, 64, 8)
            Log.i("IaEngine", "Brain criado (vocab=4096, seq=32), handle=$handle")
        } catch (e: Exception) {
            Log.e("IaEngine", "Falha ao criar brain", e)
        }
    }

    var lastReply: String? = null
        private set

    fun decide(text: String): Intent {
        if (handle == 0L) {
            lastReply = "Motor n\u00e3o inicializado."
            return Intent.UNKNOWN
        }
        return try {
            val ids = Tokenizer.encode(text)
            val scores = FloatArray(8)
            NativeBridge.nativeInfer(handle, ids, scores)
            var best = 0
            for (i in 1 until scores.size) if (scores[i] > scores[best]) best = i
            val intent = Intent.values()[best]
            lastReply = buildReply(intent, text)
            store.record(text, best, true)
            intent
        } catch (e: Exception) {
            Log.e("IaEngine", "Falha em decide()", e)
            lastReply = "Erro ao processar. Tente de novo."
            Intent.UNKNOWN
        }
    }

    fun feedback(text: String, intentId: Int, good: Boolean) {
        if (handle == 0L) return
        try {
            rl.update(intentId, if (good) 1f else -1f)
            val ids = Tokenizer.encode(text)
            val wrong = (intentId + 1 + (Math.abs(text.length) % 6)) % 8
            NativeBridge.nativeTrainStep(handle, ids, if (good) intentId else wrong, if (good) 0.01f else 0.02f)
            store.record(text, intentId, good)
        } catch (e: Exception) {
            Log.e("IaEngine", "Falha em feedback()", e)
        }
    }

    fun trainOnIdle(steps: Int = 50, lr: Float = 0.01f) {
        if (handle == 0L) return
        try {
            for (i in 0 until steps) {
                val intentId = i % 8
                val text = "amostra $i"
                val ids = Tokenizer.encode(text)
                NativeBridge.nativeTrainStep(handle, ids, intentId, lr)
            }
        } catch (e: Exception) {
            Log.e("IaEngine", "Falha em trainOnIdle()", e)
        }
    }

    fun stats(): ExperienceStats = store.getStats()

    fun accuracy(): Float = rl.accuracy()

    fun destroy() {
        if (handle != 0L) {
            try {
                NativeBridge.nativeDestroyBrain(handle)
            } catch (e: Exception) {
                Log.e("IaEngine", "Falha em destroy()", e)
            }
            handle = 0L
        }
    }

    private fun buildReply(intent: Intent, text: String): String = when (intent) {
        Intent.GREETING -> "Ol\u00e1! Como posso ajudar?"
        Intent.QUESTION -> "Boa pergunta. Estou processando."
        Intent.TASK -> "Entendido, vou organizar isso."
        Intent.CHITCHAT -> "Interessante, me conta mais."
        Intent.THANKS -> "De nada!"
        Intent.FAREWELL -> "At\u00e9 logo!"
        Intent.COMMAND -> "Executando comando."
        Intent.UNKNOWN -> "N\u00e3o entendi bem. Pode reformular?"
    }
}