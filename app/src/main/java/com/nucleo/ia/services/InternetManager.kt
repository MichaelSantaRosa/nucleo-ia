package com.nucleo.ia.services

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class InternetManager(private val context: Context) {

    enum class Mode { ON, OFF, ASK }

    private val prefs = context.getSharedPreferences("nucleo_prefs", Context.MODE_PRIVATE)
    private var connectivityManager: ConnectivityManager? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isCurrentlyOnline: Boolean = false

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

    var onConnectivityChanged: ((Boolean) -> Unit)? = null

    init {
        @Suppress("UNUSED_EXPRESSION") mode
        startMonitoring()
    }

    fun startMonitoring() {
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        updateOnlineState()

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isCurrentlyOnline = true
                onConnectivityChanged?.invoke(true)
            }

            override fun onLost(network: Network) {
                isCurrentlyOnline = false
                onConnectivityChanged?.invoke(false)
            }
        }

        connectivityManager?.registerNetworkCallback(request, networkCallback!!)
    }

    fun stopMonitoring() {
        networkCallback?.let { cb ->
            connectivityManager?.unregisterNetworkCallback(cb)
        }
        networkCallback = null
    }

    private fun updateOnlineState() {
        val cm = connectivityManager ?: return
        val net = cm.activeNetwork ?: run {
            isCurrentlyOnline = false
            return
        }
        val caps = cm.getNetworkCapabilities(net) ?: run {
            isCurrentlyOnline = false
            return
        }
        isCurrentlyOnline = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    fun cycleMode() {
        mode = when (mode) {
            Mode.OFF -> Mode.ON
            Mode.ON -> Mode.ASK
            Mode.ASK -> Mode.OFF
        }
    }

    fun isOnline(): Boolean = isCurrentlyOnline

    fun canAccess(): Boolean {
        if (!isCurrentlyOnline) return false
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