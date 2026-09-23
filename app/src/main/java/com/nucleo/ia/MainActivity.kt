package com.nucleo.ia

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nucleo.ia.core.IaEngine
import com.nucleo.ia.services.InternetManager

private data class Message(
    val text: String,
    val isUser: Boolean,
    val intentId: Int
)

private class MessageAdapter(
    private val onFeedback: (Int, Boolean) -> Unit
) : RecyclerView.Adapter<MessageAdapter.ViewHolder>() {

    val messages = mutableListOf<Message>()

    fun addMessage(text: String, isUser: Boolean, intentId: Int) {
        messages.add(Message(text, isUser, intentId))
        notifyItemInserted(messages.lastIndex)
    }

    override fun getItemCount(): Int = messages.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_msg, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = messages[position]
        holder.tvText.text = message.text
        holder.container.setBackgroundResource(
            if (message.isUser) R.drawable.bg_user else R.drawable.bg_bot
        )
        holder.btnGood.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) onFeedback(pos, true)
        }
        holder.btnBad.setOnClickListener {
            val pos = holder.adapterPosition
            if (pos != RecyclerView.NO_POSITION) onFeedback(pos, false)
        }
    }

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val container: View = view.findViewById(R.id.msgContainer)
        val tvText: TextView = view.findViewById(R.id.tvText)
        val btnGood: Button = view.findViewById(R.id.btnGood)
        val btnBad: Button = view.findViewById(R.id.btnBad)
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var engine: IaEngine
    private lateinit var internetManager: InternetManager

    private lateinit var etInput: EditText
    private lateinit var btnSend: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvStats: TextView
    private lateinit var tvInternetStatus: TextView
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: MessageAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        engine = IaEngine(this)
        internetManager = InternetManager(this)

        etInput = findViewById(R.id.etInput)
        btnSend = findViewById(R.id.btnSend)
        tvStatus = findViewById(R.id.tvStatus)
        tvStats = findViewById(R.id.tvStats)
        tvInternetStatus = findViewById(R.id.tvInternetStatus)
        recycler = findViewById(R.id.recycler)
        adapter = MessageAdapter(::onFeedback)
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        val btnInternet = findViewById<Button>(R.id.btnInternet)
        updateInternetLabel(btnInternet)
        btnInternet.setOnClickListener {
            internetManager.cycleMode()
            updateInternetLabel(btnInternet)
        }

        internetManager.onConnectivityChanged = { online ->
            runOnUiThread {
                updateInternetStatusText(online)
            }
        }

        btnSend.setOnClickListener {
            val text = etInput.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            etInput.setText("")

            val needsInternet = listOf("pesquis", "busca", "google", "wikipedia")
                .any { text.contains(it, ignoreCase = true) }

            if (needsInternet && internetManager.mode == InternetManager.Mode.ASK) {
                AlertDialog.Builder(this)
                    .setTitle("Acesso \u00e0 Internet")
                    .setMessage("Esta tarefa precisa da internet. Permitir?")
                    .setPositiveButton("Permitir") { _, _ -> askEngine(text) }
                    .setNegativeButton("Agora n\u00e3o") { _, _ -> askEngine(text) }
                    .show()
            } else {
                askEngine(text)
            }
        }

        updateInternetStatusText(internetManager.isOnline())
    }

    private fun askEngine(text: String) {
        adapter.addMessage(text, isUser = true, intentId = -1)
        val intent = engine.decide(text)
        val reply = engine.lastReply ?: "[${intent.label}]"
        adapter.addMessage("[${intent.label}] $reply", isUser = false, intentId = intent.id)
        updateStats()
    }

    private fun onFeedback(position: Int, good: Boolean) {
        if (position !in adapter.messages.indices) return
        val message = adapter.messages[position]
        if (message.intentId < 0) return
        engine.feedback(message.text, message.intentId, good)
        updateStats()
    }

    private fun updateInternetLabel(btn: Button) {
        when (internetManager.mode) {
            InternetManager.Mode.ON -> btn.text = "\uD83C\uDF10 Internet: Ativada"
            InternetManager.Mode.OFF -> btn.text = "\uD83C\uDF10 Internet: Desativada"
            InternetManager.Mode.ASK -> btn.text = "\uD83C\uDF10 Internet: Perguntar"
        }
    }

    private fun updateInternetStatusText(online: Boolean) {
        val modeText = when (internetManager.mode) {
            InternetManager.Mode.ON -> "liberada"
            InternetManager.Mode.OFF -> "desativada"
            InternetManager.Mode.ASK -> "perguntar antes"
        }
        tvInternetStatus.text = if (online) {
            "\u2705 Conectada ($modeText)"
        } else {
            "\u274C Sem conex\u00e3o ($modeText)"
        }
    }

    private fun updateStats() {
        val s = engine.stats()
        tvStats.text = "Acertos: ${s.success} | Falhas: ${s.fail} | Total: ${s.total}"
        tvStatus.text = "Precis\u00e3o: ${(engine.accuracy() * 100).toInt()}%"
    }

    override fun onDestroy() {
        super.onDestroy()
        internetManager.stopMonitoring()
        engine.destroy()
    }
}