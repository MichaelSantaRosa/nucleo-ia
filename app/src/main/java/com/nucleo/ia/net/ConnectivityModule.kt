package com.nucleo.ia.net

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class ConnectivityModule(private val ctx: Context) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    var online = false; private set
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) { refresh() }
    }
    fun startMonitoring() {
        ctx.registerReceiver(receiver, IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION))
        refresh()
    }
    private fun refresh() {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: run { online = false; return }
        val caps = cm.getNetworkCapabilities(net) ?: run { online = false; return }
        online = caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                 caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        if (online) scope.launch { trySync() }
    }
    private suspend fun trySync() {
        try {
            val conn = URL("https://cdn.nucleo-ia.local/knowledge/v1.json").openConnection() as HttpURLConnection
            conn.connectTimeout = 8000; conn.readTimeout = 15000
            if (conn.responseCode == 200) {
                val json = JSONObject(conn.inputStream.bufferedReader().readText())
                val rules = json.optJSONArray("rules")
                if (rules != null) {
                    val prefs = ctx.getSharedPreferences("knowledge", 0).edit()
                    for (i in 0 until rules.length()) prefs.putString("rule_$i", rules.getJSONObject(i).toString())
                    prefs.apply()
                }
            }
            conn.disconnect()
        } catch (_: Exception) { }
    }
}