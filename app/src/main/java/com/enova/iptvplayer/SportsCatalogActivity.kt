package com.enova.iptvplayer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast

/**
 * Entradas DIRECTAS a las páginas de canal provistas por el usuario.
 * NO son links directos de vídeo ni un reproductor Media3 de estas fuentes.
 */
class SportsCatalogActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var channelList: ListView
    private lateinit var search: EditText
    private lateinit var status: TextView
    private lateinit var fullscreen: FrameLayout
    private var customView: View? = null
    private var customCallback: WebChromeClient.CustomViewCallback? = null
    private var channels = emptyList<SportsChannel>()
    private var visible = emptyList<SportsChannel>()
    private var selected: SportsChannel? = null
    private lateinit var adapter: ArrayAdapter<String>
    private var loadError = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_sports_catalog)

        status = findViewById(R.id.catalogStatus)
        search = findViewById(R.id.catalogSearch)
        channelList = findViewById(R.id.catalogList)
        webView = findViewById(R.id.catalogWebView)
        fullscreen = findViewById(R.id.catalogFullscreen)

        channels = try {
            assets.open("sports_channels.tsv").bufferedReader(Charsets.UTF_8).use {
                SportsCatalog.parseTsv(it.readText())
            }
        } catch (ex: Exception) {
            status.text = "No se encontró el catálogo de canales."
            emptyList()
        }

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, ArrayList<String>())
        channelList.adapter = adapter
        channelList.setOnItemClickListener { _, _, position, _ ->
            val channel = visible.getOrNull(position) ?: return@setOnItemClickListener
            selected = channel
            showMessage("Abriendo " + channel.name + " desde su enlace de canal…")
            webView.loadUrl(channel.pageUrl)
            webView.requestFocus()
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterChannels(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })

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
                if (request == null) return true
                if (request.isForMainFrame &&
                    !SportsPortal.isAllowedNavigation(request.url.toString())) {
                    showMessage(getString(R.string.sports_blocked))
                    return true
                }
                return false
            }
            @Deprecated("Compatibilidad con Android 6")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url != null && !SportsPortal.isAllowedNavigation(url)) {
                    showMessage(getString(R.string.sports_blocked))
                    return true
                }
                return false
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                loadError = false
                if (url == "about:blank") return
                showMessage("Cargando " + (selected?.name ?: "canal") + "…")
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                if (!loadError && url != "about:blank") {
                    showMessage("Página de " + (selected?.name ?: "canal") +
                        " cargada. La reproducción depende de la señal y del sitio.")
                }
            }
            override fun onReceivedError(
                view: WebView?, request: WebResourceRequest?, error: WebResourceError?
            ) {
                if (request?.isForMainFrame == true) {
                    loadError = true
                    showMessage("No se pudo abrir ese canal. Probá en navegador.")
                }
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (customView != null || view == null) {
                    callback?.onCustomViewHidden()
                    return
                }
                customView = view
                customCallback = callback
                fullscreen.addView(view, FrameLayout.LayoutParams(-1, -1))
                fullscreen.visibility = View.VISIBLE
                fullscreen.requestFocus()
            }
            override fun onHideCustomView() {
                closeFullscreen()
            }
        }

        findViewById<Button>(R.id.catalogAgenda).setOnClickListener {
            startActivity(Intent(this, SportsActivity::class.java))
        }
        findViewById<Button>(R.id.catalogRefresh).setOnClickListener {
            if (selected == null) {
                showMessage("Elegí un canal de la lista para abrirlo.")
            } else {
                webView.reload()
            }
        }
        findViewById<Button>(R.id.catalogExternal).setOnClickListener {
            val url = selected?.pageUrl
            if (url == null) {
                showMessage("Primero seleccioná un canal.")
            } else try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
            } catch (_: ActivityNotFoundException) {
                showMessage("No hay navegador compatible instalado.")
            }
        }
        findViewById<Button>(R.id.catalogBack).setOnClickListener { finish() }

        filterChannels("")
        channelList.requestFocus()
    }

    private fun filterChannels(query: String) {
        val needle = query.trim()
        visible = if (needle.isEmpty()) channels else channels.filter {
            it.name.contains(needle, ignoreCase = true) ||
                it.id.contains(needle, ignoreCase = true)
        }
        val repeated = channels.groupingBy { it.name.lowercase() }.eachCount()
        adapter.clear()
        adapter.addAll(visible.map { channel ->
            val suffix = if ((repeated[channel.name.lowercase()] ?: 0) > 1)
                " · " + channel.id else ""
            channel.name + suffix
        })
        adapter.notifyDataSetChanged()
        if (selected == null) {
            showMessage(visible.size.toString() +
                " canales · Elegí uno con el control remoto. Son páginas de TVF90, no enlaces M3U8.")
        }
    }

    private fun showMessage(message: String) {
        status.text = message
    }

    private fun closeFullscreen() {
        if (customView == null) return
        fullscreen.removeAllViews()
        fullscreen.visibility = View.GONE
        customView = null
        customCallback?.onCustomViewHidden()
        customCallback = null
    }

    @Deprecated("Control del botón Atrás en Google TV")
    override fun onBackPressed() {
        if (customView != null) {
            closeFullscreen()
        } else if (selected != null) {
            selected = null
            webView.loadUrl("about:blank")
            showMessage("Elegí otro canal de la lista.")
            channelList.requestFocus()
        } else {
            super.onBackPressed()
        }
    }

    override fun onResume() {
        super.onResume()
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        closeFullscreen()
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }
}
