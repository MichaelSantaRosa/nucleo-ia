package com.nucleo.ia.core

import android.content.Context
import com.nucleo.ia.NativeBridge
import com.nucleo.ia.db.ExperienceStore
import com.nucleo.ia.learn.ReinforcementLearner
import java.io.File

class IaEngine(private val ctx: Context) {

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

    private var handle: Long = 0
    private lateinit var store: ExperienceStore
    private val rl = ReinforcementLearner()
    private val weightsFile get() = File(ctx.filesDir, "brain.bin")

    init {
        store = ExperienceStore(ctx)
        handle = NativeBridge.nativeCreateBrain(512, 16, 32, 64, 64, 8)
        val f = weightsFile
        if (f.exists()) {
            val data = f.readBytes()
            if (!NativeBridge.nativeLoadWeights(handle, data)) {
                NativeBridge.nativeDestroyBrain(handle)
                handle = NativeBridge.nativeCreateBrain(512, 16, 32, 64, 64, 8)
            }
        }
    }

    fun decide(text: String): Pair<Intent, String> {
        val ids = Tokenizer.encode(text)
        val scores = FloatArray(8)
        NativeBridge.nativeInfer(handle, ids, scores)
        var best = 0
        for (i in 1 until 8) if (scores[i] > scores[best]) best = i
        val intent = Intent.values()[best]
        val reply = buildReply(intent, text)
        store.add(text, best, 1.0f)
        return Pair(intent, reply)
    }

    fun feedback(text: String, intentId: Int, good: Boolean) {
        rl.record(intentId, good)
        if (good) {
            val ids = Tokenizer.encode(text)
            NativeBridge.nativeTrainStep(handle, ids, intentId, 0.01f)
        } else {
            val wrong = (intentId + 1 + (Math.abs(text.length) % 6)) % 8
            val ids = Tokenizer.encode(text)
            NativeBridge.nativeTrainStep(handle, ids, wrong, 0.02f)
        }
        persistWeights()
    }

    fun trainOnIdle(steps: Int = 50, lr: Float = 0.01f) {
        val samples = store.recent(200).shuffled()
        if (samples.isEmpty()) return
        for (s in samples.take(steps)) {
            NativeBridge.nativeTrainStep(handle, Tokenizer.encode(s.text), s.intentId, lr)
        }
        persistWeights()
    }

    fun persistWeights() {
        val data = NativeBridge.nativeSaveWeights(handle)
        if (data != null) weightsFile.writeBytes(data)
    }

    fun destroy() {
        persistWeights()
        NativeBridge.nativeDestroyBrain(handle)
    }

    private fun buildReply(intent: Intent, text: String): String {
        return when (intent) {
            Intent.GREETING -> "Ola! Eu sou o Nucleo, sua IA local. Como posso ajudar?"
            Intent.QUESTION -> "Boa pergunta. Com base no meu conhecimento local, vou tentar responder da melhor forma."
            Intent.TASK -> "Entendido. Estou processando essa tarefa offline."
            Intent.CHITCHAT -> "Interessante! Me conte mais."
            Intent.THANKS -> "De nada! Estou aqui para isso."
            Intent.FAREWELL -> "Ate logo! Vou continuar aprendendo em segundo plano."
            Intent.COMMAND -> "Comando recebido. Executando..."
            Intent.UNKNOWN -> "Recebi sua mensagem. Estou aprendendo a entender melhor."
        }
    }
}