package com.nucleo.ia.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class InternetManager(private val context: Context) {

    enum class Mode { ON, OFF, ASK }

    private val prefs = context.getSharedPreferences("nucleo_prefs", Context.MODE_PRIVATE)

    var mode: Mode
        get() {
            val saved = prefs.getString("internet_mode", Mode.OFF.name) ?: Mode.OFF.name
            return try {
                Mode.valueOf(saved)
            } catch (e: IllegalArgumentException) {
                Mode.OFF
            }
        }
        private set(value) {
            prefs.edit().putString("internet_mode", value.name).apply()
        }

    init {
        // força leitura inicial para validar o valor salvo
        @Suppress("UNUSED_EXPRESSION") mode
    }

    fun cycleMode() {
        mode = when (mode) {
            Mode.OFF -> Mode.ON
            Mode.ON -> Mode.ASK
            Mode.ASK -> Mode.OFF
        }
    }

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun canAccess(): Boolean {
        if (!isOnline()) return false
        return when (mode) {
            Mode.ON -> true
            Mode.OFF -> false
            Mode.ASK -> true
        }
    }

    fun fetch(url: String, timeoutMs: Int = 8000): String? {
        if (!canAccess()) return null
        return try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.setRequestProperty("User-Agent", "NucleoIA/1.0")
            val code = conn.responseCode
            if (code == 200) {
                val text = conn.inputStream.bufferedReader().readText()
                text.take(5000)
            } else null
        } catch (e: Exception) {
            null
        }
    }

    fun searchWikipedia(title: String): String? {
        val encoded = URLEncoder.encode(title, "UTF-8")
        return fetch("https://pt.wikipedia.org/api/rest_v1/page/summary/$encoded")
    }
}