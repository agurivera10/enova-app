package com.enova.iptvplayer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Muestra la web deportiva en una pantalla apta para D-pad.
 * No lee el HTML ni copia links de streaming; el propio sitio controla su contenido.
 */
class SportsActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private lateinit var progress: ProgressBar
    private lateinit var agendaButton: Button
    private lateinit var channelsButton: Button
    private val handler = Handler(Looper.getMainLooper())
    private var agendaVisible = true
    private var resumed = false
    private val automaticRefresh = object : Runnable {
        override fun run() {
            if (!resumed) return
            if (agendaVisible && ::webView.isInitialized) {
                status.text = "Consultando la agenda (cada 2 min mientras está abierta)…"
                webView.reload()
            }
            handler.postDelayed(this, SportsPortal.REFRESH_INTERVAL_MS)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sports)

        status = findViewById(R.id.sportsStatus)
        progress = findViewById(R.id.sportsProgress)
        agendaButton = findViewById(R.id.sportsAgendaButton)
        channelsButton = findViewById(R.id.sportsChannelsButton)
        webView = findViewById(R.id.sportsWebView)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.settings.safeBrowsingEnabled = true
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url?.toString() ?: return true
                // No se interfiere con recursos/subframes cargados por el sitio.
                if (request.isForMainFrame && !SportsPortal.isAllowedNavigation(url)) {
                    status.text = getString(R.string.sports_blocked)
                    return true
                }
                return false
            }
            @Deprecated("Compatibilidad Android 6")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url != null && !SportsPortal.isAllowedNavigation(url)) {
                    status.text = getString(R.string.sports_blocked)
                    return true
                }
                return false
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                progress.visibility = View.VISIBLE
                status.text = getString(R.string.sports_loading)
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                status.text = if (agendaVisible) {
                    "Página de agenda consultada a las $time · Recarga cada 2 min"
                } else {
                    "Página de canales · Usá el control remoto para navegar"
                }
            }
            override fun onReceivedError(
                view: WebView?,
                request: WebResourceRequest?,
                error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    progress.visibility = View.GONE
                    status.text = getString(R.string.sports_failed)
                }
            }
        }

        agendaButton.setOnClickListener { showSection(true) }
        channelsButton.setOnClickListener {
            startActivity(Intent(this, SportsCatalogActivity::class.java))
            finish()
        }
        findViewById<Button>(R.id.sportsReloadButton).setOnClickListener { webView.reload() }
        findViewById<Button>(R.id.sportsBrowserButton).setOnClickListener { openExternalBrowser() }
        findViewById<Button>(R.id.sportsBackButton).setOnClickListener { finish() }

        showSection(true)
    }

    private fun showSection(agenda: Boolean) {
        agendaVisible = agenda
        agendaButton.isEnabled = !agenda
        channelsButton.isEnabled = agenda
        val destination = if (agenda) SportsPortal.AGENDA_URL else SportsPortal.HOME_URL
        webView.loadUrl(destination)
    }

    private fun openExternalBrowser() {
        val destination = if (agendaVisible) SportsPortal.AGENDA_URL else SportsPortal.HOME_URL
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(destination)))
        } catch (_: ActivityNotFoundException) {
            status.text = "No hay navegador compatible instalado en Google TV."
        }
    }

    override fun onResume() {
        super.onResume()
        resumed = true
        handler.removeCallbacks(automaticRefresh)
        handler.postDelayed(automaticRefresh, SportsPortal.REFRESH_INTERVAL_MS)
        webView.onResume()
    }

    override fun onPause() {
        resumed = false
        handler.removeCallbacks(automaticRefresh)
        webView.onPause()
        super.onPause()
    }

    @Deprecated("Comportamiento tradicional del botón Atrás")
    override fun onBackPressed() {
        if (webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(automaticRefresh)
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }
}
