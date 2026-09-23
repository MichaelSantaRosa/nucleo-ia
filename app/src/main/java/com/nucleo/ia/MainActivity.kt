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
import com.nucleo.ia.core.ExperienceStore
import com.nucleo.ia.core.ReinforcementLearner
import com.nucleo.ia.services.InternetManager

class MainActivity : AppCompatActivity() {

    private lateinit var engine: IaEngine
    private lateinit var store: ExperienceStore
    private lateinit var rl: ReinforcementLearner
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

        engine = (application as NucleoApp).engine
        store = ExperienceStore(this)
        rl = ReinforcementLearner()
        internetManager = InternetManager(this)

        etInput = findViewById(R.id.etInput)
        btnSend = findViewById(R.id.btnSend)
        tvStatus = findViewById(R.id.tvStatus)
        tvStats = findViewById(R.id.tvStats)
        recycler = findViewById(R.id.recycler)

        val btnInternet = findViewById<Button>(R.id.btnInternet)
        val tvInternetStatus = findViewById<TextView>(R.id.tvInternetStatus)

        // Toggle de internet
        btnInternet.setOnClickListener {
            internetManager.mode = when (internetManager.mode) {
                InternetManager.Mode.OFF -> InternetManager.Mode.ON
                InternetManager.Mode.ON -> InternetManager.Mode.ASK
                InternetManager.Mode.ASK -> InternetManager.Mode.OFF
            }
            when (internetManager.mode) {
                InternetManager.Mode.ON -> btnInternet.text = "Ativada"
                InternetManager.Mode.OFF -> btnInternet.text = "Desativada"
                InternetManager.Mode.ASK -> btnInternet.text = "Perguntar"
            }
            tvInternetStatus.text = ""
        }

        // Botão enviar
        btnSend.setOnClickListener { send() }

        // RecyclerView
        recycler.layoutManager = LinearLayoutManager(this)
        adapter = MessageAdapter()
        recycler.adapter = adapter

        updateStats()
    }

    private fun send() {
        val text = etInput.text.toString().trim()
        if (text.isEmpty()) return

        val needsInternet =
            text.contains("pesquis", ignoreCase = true) ||
            text.contains("busca", ignoreCase = true) ||
            text.contains("google", ignoreCase = true) ||
            text.contains("wikipedia", ignoreCase = true)

        if (needsInternet && internetManager.mode == InternetManager.Mode.ASK) {
            AlertDialog.Builder(this)
                .setTitle("Acesso à Internet")
                .setMessage("Esta tarefa precisa da internet. Permitir?")
                .setPositiveButton("Sim", null)
                .setNegativeButton("Não", null)
                .show()
        }

        etInput.setText("")
        addMsg(true, text)

        val (intent, reply) = engine.decide(text)
        addMsg(false, "[${intent.label}] $reply", text, intent.id)
        updateStats()
    }

    private fun addMsg(isUser: Boolean, text: String, original: String? = null, intentId: Int = -1) {
        adapter.addMessage(text, isUser)
        if (!isUser && original != null && intentId != -1) {
            store.record(original, intentId, success = true)
            rl.update(intentId, reward = 1f)
        }
    }

    private fun updateStats() {
        val stats = store.getStats()
        tvStats.text = "Acertos: ${stats.success} | Falhas: ${stats.fail} | Total: ${stats.total}"
    }

    // Adapter para o RecyclerView
    inner class MessageAdapter : RecyclerView.Adapter<MessageAdapter.ViewHolder>() {

        private val messages = mutableListOf<Pair<String, Boolean>>()

        fun addMessage(text: String, isUser: Boolean) {
            messages.add(Pair(text, isUser))
            notifyItemInserted(messages.size - 1)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_msg, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val (text, isUser) = messages[position]
            holder.bind(text, isUser)
        }

        override fun getItemCount(): Int = messages.size

        inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            private val tvText: TextView = itemView.findViewById(R.id.tvText)
            private val btnGood: Button = itemView.findViewById(R.id.btnGood)
            private val btnBad: Button = itemView.findViewById(R.id.btnBad)

            fun bind(text: String, isUser: Boolean) {
                tvText.text = text
                if (isUser) {
                    itemView.setBackgroundResource(R.drawable.bg_user)
                    btnGood.visibility = View.GONE
                    btnBad.visibility = View.GONE
                } else {
                    itemView.setBackgroundResource(R.drawable.bg_bot)
                    btnGood.visibility = View.VISIBLE
                    btnBad.visibility = View.VISIBLE
                }
            }
        }
    }
}