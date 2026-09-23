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
import com.nucleo.ia.core.ExperienceStore
import com.nucleo.ia.core.IaEngine
import com.nucleo.ia.core.ReinforcementLearner
import com.nucleo.ia.services.InternetManager

class MainActivity : AppCompatActivity() {

    private lateinit var engine: IaEngine
    private lateinit var internetManager: InternetManager

    private lateinit var etInput: EditText
    private lateinit var btnSend: Button
    private lateinit var tvStatus: TextView
    private lateinit var tvStats: TextView
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
        recycler = findViewById(R.id.recycler)
        adapter = MessageAdapter()
        recycler.layoutManager = LinearLayoutManager(this)
        recycler.adapter = adapter

        val btnInternet = findViewById<Button>(R.id.btnInternet)
        val tvInternetStatus = findViewById<TextView>(R.id.tvInternetStatus)
        updateInternetLabel(btnInternet, tvInternetStatus)
        btnInternet.setOnClickListener {
            internetManager.cycleMode()
            updateInternetLabel(btnInternet, tvInternetStatus)
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
    }

    private fun askEngine(text: String) {
        adapter.addMessage(text, isUser = true, intentId = -1)
        val intent = engine.decide(text)
        val reply = engine.lastReply ?: "[${intent.label}]"
        adapter.addMessage("[${intent.label}] $reply", isUser = false, intentId = intent.id)
        updateStats()
    }

    private fun onFeedback(position: Int, good: Boolean) {
        val m = adapter.messages[position]
        if (m.intentId < 0) return
        engine.feedback(m.text, m.intentId, good)
        updateStats()
    }

    private fun updateInternetLabel(btn: Button, status: TextView) {
        when (internetManager.mode) {
            InternetManager.Mode.ON -> {
                btn.text = "\uD83C\uDF10 Internet: Ativada"
                status.text = "Internet liberada"
            }
            InternetManager.Mode.OFF -> {
                btn.text = "\uD83C\uDF10 Internet: Desativada"
                status.text = "Modo offline"
            }
            InternetManager.Mode.ASK -> {
                btn.text = "\uD83C\uDF10 Internet: Perguntar"
                status.text = "Pergunta antes de acessar"
            }
        }
    }

    private fun updateStats() {
        val s = engine.stats()
        tvStats.text = "Acertos: ${s.success} | Falhas: ${s.fail} | Total: ${s.total}"
        tvStatus.text = "Precis\u00e3o: ${(engine.accuracy() * 100).toInt()}%"
    }

    override fun onDestroy() {
        super.onDestroy()
        engine.destroy()
    }

    inner class MessageAdapter : RecyclerView.Adapter<MessageAdapter.ViewHolder>() {
        data class Msg(val text: String, val isUser: Boolean, val intentId: Int)
        val messages = mutableListOf<Msg>()

        fun addMessage(text: String, isUser: Boolean, intentId: Int) {
            messages.add(Msg(text, isUser, intentId))
            notifyItemInserted(messages.size - 1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_msg, parent, false)
            return ViewHolder(v)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val m = messages[position]
            holder.tvText.text = m.text
            holder.container.setBackgroundResource(
                if (m.isUser) R.drawable.bg_user else R.drawable.bg_bot
            )
            holder.btnGood.setOnClickListener { onFeedback(holder.bindingAdapterPosition, true) }
            holder.btnBad.setOnClickListener { onFeedback(holder.bindingAdapterPosition, false) }
        }

        class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
            val container: View = v.findViewById(R.id.msgContainer)
            val tvText: TextView = v.findViewById(R.id.tvText)
            val btnGood: Button = v.findViewById(R.id.btnGood)
            val btnBad: Button = v.findViewById(R.id.btnBad)
        }
    }
}