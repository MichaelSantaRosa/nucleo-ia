package com.nucleo.ia

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.nucleo.ia.core.IaEngine
import com.nucleo.ia.db.ExperienceStore
import com.nucleo.ia.learn.ReinforcementLearner
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var engine: IaEngine
    private lateinit var store: ExperienceStore
    private lateinit var rl: ReinforcementLearner
    private val messages = mutableListOf<Msg>()
    private lateinit var adapter: MsgAdapter
    private lateinit var etInput: EditText
    private lateinit var btnSend: ImageButton
    private lateinit var tvStatus: TextView
    private lateinit var tvStats: TextView

    data class Msg(val fromUser: Boolean, val text: String, val intentId: Int = -1)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        engine = (application as NucleoApp).engine
        store = ExperienceStore(this)
        rl = ReinforcementLearner()

        etInput = findViewById(R.id.etInput)
        btnSend = findViewById(R.id.btnSend)
        tvStatus = findViewById(R.id.tvStatus)
        tvStats = findViewById(R.id.tvStats)

        val rv = findViewById<RecyclerView>(R.id.recycler)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = MsgAdapter()
        rv.adapter = adapter

        btnSend.setOnClickListener { send() }
        etInput.setOnEditorActionListener { _, _, _ -> send(); true }

        addMsg(false, "Ola! Eu sou o Nucleo, sua IA 100% offline. Digite algo para comecar.")
        updateStats()
    }

    private fun send() {
        val text = etInput.text.toString().trim()
        if (text.isEmpty()) return
        etInput.setText("")
        addMsg(true, text)
        val (intent, reply) = engine.decide(text)
        addMsg(false, "[${intent.label}] $reply", intent.id)
        updateStats()
    }

    private fun addMsg(fromUser: Boolean, text: String, intentId: Int = -1) {
        messages.add(Msg(fromUser, text, intentId))
        adapter.notifyItemInserted(messages.size - 1)
    }

    private fun updateStats() {
        val total = store.count()
        val acc = rl.accuracy()
        tvStats.text = String.format(Locale.US, "Experiencias: %d | Precisao RL: %.1f%%", total, acc * 100)
        tvStatus.text = "Nucleo ativo | Offline"
    }

    override fun onDestroy() {
        engine.persistWeights()
        super.onDestroy()
    }

    inner class MsgAdapter : RecyclerView.Adapter<MsgAdapter.VH>() {
        inner class VH(view: View) : RecyclerView.ViewHolder(view) {
            val container: LinearLayout = view.findViewById(R.id.msgContainer)
            val tvText: TextView = view.findViewById(R.id.tvText)
            val btnGood: ImageButton = view.findViewById(R.id.btnGood)
            val btnBad: ImageButton = view.findViewById(R.id.btnBad)
        }

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
            val v = layoutInflater.inflate(R.layout.item_msg, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val m = messages[position]
            holder.tvText.text = m.text
            holder.container.setBackgroundResource(
                if (m.fromUser) R.drawable.bg_user else R.drawable.bg_bot
            )
            holder.btnGood.visibility = if (m.fromUser) View.GONE else View.VISIBLE
            holder.btnBad.visibility = if (m.fromUser) View.GONE else View.VISIBLE
            holder.btnGood.setOnClickListener {
                if (m.intentId >= 0) { engine.feedback(m.text, m.intentId, true); updateStats() }
            }
            holder.btnBad.setOnClickListener {
                if (m.intentId >= 0) { engine.feedback(m.text, m.intentId, false); updateStats() }
            }
        }

        override fun getItemCount(): Int = messages.size
    }
}