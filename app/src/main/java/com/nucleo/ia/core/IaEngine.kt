package com.nucleo.ia.core

import android.content.Context
import com.nucleo.ia.db.ExperienceStore
import com.nucleo.ia.learn.ReinforcementLearner
import java.io.File
import kotlin.random.Random

class IaEngine(private val ctx: Context) {
    enum class Intent(val id: Int, val name: String) {
        GREETING(0, "saudacao"), QUESTION(1, "pergunta"), TASK(2, "tarefa"),
        CHITCHAT(3, "conversa"), THANKS(4, "agradecimento"),
        FAREWELL(5, "despedida"), COMMAND(6, "comando"), UNKNOWN(7, "desconhecido")
    }
    private var handle: Long = 0
    private lateinit var store: ExperienceStore
    private val rl = ReinforcementLearner()
    private val weightsFile get() = File(ctx.filesDir, "brain.bin")

    fun init() {
        handle = NativeBridge.nativeCreate()
        store = ExperienceStore(ctx)
        if (weightsFile.exists()) NativeBridge.nativeLoadWeights(handle, weightsFile.readBytes())
    }

    fun decide(userText: String): Pair<Intent, String> {
        val ids = Tokenizer.encode(userText)
        val scores = NativeBridge.nativeForward(handle, ids)
        val best = scores.indices.maxByOrNull { scores[it] } ?: Intent.UNKNOWN.id
        val intent = Intent.values().firstOrNull { it.id == best } ?: Intent.UNKNOWN
        val success = scores[best] > 0.4f
        store.save(userText.take(200), intent.id, scores[best], success)
        rl.observe(intent.id, success)
        return intent to respond(intent)
    }

    private fun respond(i: Intent): String = when (i) {
        Intent.GREETING -> "Ola! Como posso ajudar?"
        Intent.QUESTION -> "Processei offline sua pergunta."
        Intent.TASK -> "Tarefa registrada localmente."
        Intent.CHITCHAT -> "Entendi. Continue."
        Intent.THANKS -> "De nada!"
        Intent.FAREWELL -> "Ate logo."
        Intent.COMMAND -> "Comando executado."
        Intent.UNKNOWN -> "Nao reconheci. Reformule?"
    }

    fun trainOnIdle(steps: Int = 50, lr: Float = 0.01f) {
        val samples = store.recent(200)
        if (samples.isEmpty()) return
        Random.shuffle(samples)
        for (s in samples.take(steps)) {
            NativeBridge.nativeTrainStep(handle, Tokenizer.encode(s.text), s.intentId, lr)
        }
        persistWeights()
    }

    fun persistWeights() { weightsFile.writeBytes(NativeBridge.nativeSaveWeights(handle)) }
    fun weightBytes(): Long = NativeBridge.nativeWeightBytes(handle)
}