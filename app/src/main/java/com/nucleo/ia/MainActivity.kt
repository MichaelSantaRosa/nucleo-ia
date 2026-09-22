package com.nucleo.ia

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

data class Msg(val fromUser: Boolean, val text: String)

class MainActivity : AppCompatActivity() {
    private val msgs = mutableListOf<Msg>()
    private lateinit var adapter: MsgAdapter
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val rv = findViewById<RecyclerView>(R.id.recycler)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = MsgAdapter(msgs)
        rv.adapter = adapter
        findViewById<Button>(R.id.btnSend).setOnClickListener {
            val et = findViewById<EditText>(R.id.etInput)
            val text = et.text.toString().trim()
            if (text.isEmpty()) return@setOnClickListener
            msgs.add(Msg(true, text))
            val (intent, reply) = (application as NucleoApp).engine.decide(text)
            msgs.add(Msg(false, "[${intent.name}] $reply"))
            et.setText("")
            adapter.notifyDataSetChanged()
        }
    }
}

class MsgAdapter(private val items: List<Msg>) : RecyclerView.Adapter<MsgAdapter.VH>() {
    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val tv: TextView = v.findViewById(android.R.id.text1)
    }
    override fun onCreateViewHolder(p: ViewGroup, t: Int) =
        VH(LayoutInflater.from(p.context).inflate(android.R.layout.simple_list_item_1, p, false))
    override fun onBindViewHolder(h: VH, p: Int) {
        val m = items[p]
        h.tv.text = (if (m.fromUser) "Voce: " else "IA: ") + m.text
    }
    override fun getItemCount() = items.size
}