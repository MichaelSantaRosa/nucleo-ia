package com.nucleo.ia.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

class InternetManager(private val context: Context) {

    enum class Mode { ON, OFF, ASK }

    var mode: Mode = Mode.OFF
        set(value) {
            field = value
            prefs.edit().putString("internet_mode", value.name).apply()
        }

    var lastService: String = ""
        private set

    private val prefs = context.getSharedPreferences("nucleo_prefs", Context.MODE_PRIVATE)

    init {
        val saved = prefs.getString("internet_mode", Mode.OFF.name) ?: Mode.OFF.name
        mode = try {
    Mode.valueOf(saved)
} catch (e: IllegalArgumentException) {
    Mode.OFF
}
    }

    fun isOnline(): Boolean {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val net = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(net) ?: return false
        return caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
               caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
    }

    fun canAccess(): Boolean {
        return when (mode) {
            Mode.ON -> isOnline()
            Mode.OFF -> false
            Mode.ASK -> false  // o MainActivity pergunta antes
        }
    }

    fun fetch(url: String, timeoutMs: Int = 8000): String? {
        if (!canAccess()) return null
        return try {
            lastService = url.substringBefore("/").substringAfter("//")
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = timeoutMs
            conn.readTimeout = timeoutMs
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "NucleoIA/1.0")
            val code = conn.responseCode
            if (code == 200) {
                val sb = StringBuilder()
                BufferedReader(conn.inputStream.reader()).use { r ->
                    var line: String?
                    while (r.readLine().also { line = it } != null) {
                        sb.append(line).append("\n")
                        if (sb.length > 5000) break
                    }
                }
                conn.disconnect()
                sb.toString()
            } else {
                conn.disconnect()
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    fun searchWikipedia(query: String): String? {
        val encoded = query.replace(" ", "_").replace("+", "%2B")
        val url = "https://pt.wikipedia.org/api/rest_v1/page/summary/$encoded"
        return fetch(url)
    }
}