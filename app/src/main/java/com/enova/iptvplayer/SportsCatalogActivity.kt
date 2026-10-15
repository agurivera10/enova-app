package com.enova.iptvplayer

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.graphics.Color
import android.graphics.Bitmap
import android.view.View
import android.view.WindowManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.BaseAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ListView
import android.widget.TextView
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import android.os.Handler
import android.os.Looper

/** Full-screen WebView pages, not raw IPTV video. Site restrictions still apply. */
class SportsCatalogActivity : Activity() {
    private lateinit var webView: WebView
    private lateinit var catalogPage: View
    private lateinit var playerOverlay: FrameLayout
    private lateinit var html5Overlay: FrameLayout
    private lateinit var chromeOverlay: View
    private lateinit var playerStatus: TextView
    private lateinit var catalogStatus: TextView
    private lateinit var list: ListView
    private lateinit var adapter: SportsRowAdapter
    private lateinit var logos: LogoCatalog
    private val handler = Handler(Looper.getMainLooper())
    private var chromeHider: Runnable? = null
    private var webCustomView: View? = null
    private var webCustomCallback: WebChromeClient.CustomViewCallback? = null
    private var channels = emptyList<SportsChannel>()
    private var filtered = emptyList<SportsChannel>()
    private var current: SportsChannel? = null
    private var pageError = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_sports_catalog)
        logos = LogoCatalog(this)
        catalogPage = findViewById(R.id.catalogPage)
        playerOverlay = findViewById(R.id.catalogPlayerOverlay)
        html5Overlay = findViewById(R.id.catalogHtml5Overlay)
        chromeOverlay = findViewById(R.id.catalogPlayerChrome)
        playerStatus = findViewById(R.id.catalogPlayerStatus)
        catalogStatus = findViewById(R.id.catalogStatus)
        list = findViewById(R.id.catalogList)
        webView = findViewById(R.id.catalogWebView)
        channels = try {
            assets.open("sports_channels.tsv").bufferedReader(Charsets.UTF_8).use {
                SportsCatalog.parseTsv(it.readText())
            }
        } catch (_: Exception) {
            catalogStatus.text = "No se encontró el catálogo deportivo."
            emptyList()
        }
        adapter = SportsRowAdapter()
        list.adapter = adapter
        list.setOnItemClickListener { _, _, position, _ ->
            filtered.getOrNull(position)?.let(::openChannel)
        }
        findViewById<EditText>(R.id.catalogSearch).addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterChannels(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        webView.setBackgroundColor(Color.BLACK)
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            javaScriptCanOpenWindowsAutomatically = false
            setSupportMultipleWindows(false)
            allowFileAccess = false
            allowContentAccess = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            mediaPlaybackRequiresUserGesture = false
            loadWithOverviewMode = true
            useWideViewPort = true
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            webView.settings.safeBrowsingEnabled = true
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                if (request?.isForMainFrame == true &&
                    !SportsPortal.isAllowedNavigation(request.url.toString())) {
                    playerStatus.text = "Enlace externo bloqueado. Abrí en navegador."
                    return true
                }
                return false
            }
            @Deprecated("Compatibilidad Android 6")
            override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                if (url != null && !SportsPortal.isAllowedNavigation(url)) {
                    playerStatus.text = "Enlace externo bloqueado."
                    return true
                }
                return false
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                pageError = false
                if (url != "about:blank")
                    playerStatus.text = "Cargando " + (current?.name ?: "canal") + "…"
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                if (url == "about:blank" || current == null || pageError) return
                webView.evaluateJavascript(FULLSCREEN_SCRIPT, null)
                playerStatus.text = (current?.name ?: "Canal") + " · Atrás: volver"
                scheduleHideChrome()
            }
            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                if (request?.isForMainFrame == true) {
                    pageError = true
                    playerStatus.text = "No se pudo abrir el canal. Probá el navegador."
                    showChrome()
                }
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                if (view == null || webCustomView != null) {
                    callback?.onCustomViewHidden()
                    return
                }
                webCustomView = view
                webCustomCallback = callback
                html5Overlay.addView(view, FrameLayout.LayoutParams(-1, -1))
                html5Overlay.visibility = View.VISIBLE
                chromeOverlay.visibility = View.GONE
                immersive(true)
            }
            override fun onHideCustomView() { closeWebCustomView() }
        }
        findViewById<Button>(R.id.catalogAgenda).setOnClickListener {
            startActivity(Intent(this, SportsActivity::class.java))
        }
        findViewById<Button>(R.id.catalogBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.catalogExitPlayer).setOnClickListener { closePlayer() }
        findViewById<Button>(R.id.catalogReloadPlayer).setOnClickListener {
            showChrome()
            webView.reload()
        }
        findViewById<Button>(R.id.catalogOpenExternal).setOnClickListener {
            current?.let {
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(it.pageUrl)))
                } catch (_: ActivityNotFoundException) {
                    playerStatus.text = "No hay navegador compatible instalado."
                    showChrome()
                }
            }
        }
        filterChannels("")
        list.requestFocus()
    }

    private fun filterChannels(text: String) {
        val needle = text.trim()
        filtered = if (needle.isEmpty()) channels else channels.filter {
            it.name.contains(needle, ignoreCase = true) || it.id.contains(needle, ignoreCase = true)
        }
        adapter.notifyDataSetChanged()
        catalogStatus.text = filtered.size.toString() + " canales · Elegí uno con OK"
    }

    private fun openChannel(channel: SportsChannel) {
        current = channel
        catalogPage.visibility = View.GONE
        playerOverlay.visibility = View.VISIBLE
        playerStatus.text = "Abriendo " + channel.name
        showChrome()
        immersive(true)
        webView.loadUrl(channel.pageUrl)
        webView.requestFocus()
    }

    private fun showChrome() {
        chromeHider?.let { handler.removeCallbacks(it) }
        chromeOverlay.visibility = View.VISIBLE
        scheduleHideChrome()
    }

    private fun scheduleHideChrome() {
        chromeHider?.let { handler.removeCallbacks(it) }
        val hide = Runnable {
            if (current != null && webCustomView == null) {
                chromeOverlay.visibility = View.GONE
                webView.requestFocus()
            }
        }
        chromeHider = hide
        handler.postDelayed(hide, 8_000L)
    }

    private fun closeWebCustomView() {
        val view = webCustomView ?: return
        html5Overlay.removeView(view)
        html5Overlay.visibility = View.GONE
        webCustomView = null
        val callback = webCustomCallback
        webCustomCallback = null
        callback?.onCustomViewHidden()
        if (current != null) showChrome()
    }

    private fun closePlayer() {
        chromeHider?.let { handler.removeCallbacks(it) }
        closeWebCustomView()
        current = null
        webView.loadUrl("about:blank")
        playerOverlay.visibility = View.GONE
        catalogPage.visibility = View.VISIBLE
        immersive(false)
        list.requestFocus()
    }

    @Deprecated("Control de atrás en Google TV")
    override fun onBackPressed() {
        when {
            webCustomView != null -> closeWebCustomView()
            current != null -> closePlayer()
            else -> super.onBackPressed()
        }
    }

    @Suppress("DEPRECATION")
    private fun immersive(enabled: Boolean) {
        window.decorView.systemUiVisibility = if (enabled) {
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
        } else View.SYSTEM_UI_FLAG_LAYOUT_STABLE
    }

    override fun onResume() {
        super.onResume()
        if (current != null) immersive(true)
        webView.onResume()
    }

    override fun onPause() {
        webView.onPause()
        super.onPause()
    }

    override fun onDestroy() {
        chromeHider?.let { handler.removeCallbacks(it) }
        closeWebCustomView()
        webView.stopLoading()
        webView.destroy()
        super.onDestroy()
    }

    private inner class SportsRowAdapter : BaseAdapter() {
        private val duplicated = channels.groupingBy { it.name.lowercase() }.eachCount()
        override fun getCount() = filtered.size
        override fun getItem(position: Int) = filtered[position]
        override fun getItemId(position: Int) = position.toLong()
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val row = convertView ?: LayoutInflater.from(this@SportsCatalogActivity)
                .inflate(R.layout.row_sports_channel, parent, false)
            val item = getItem(position)
            row.findViewById<TextView>(R.id.sportsRowName).text = item.name
            row.findViewById<TextView>(R.id.sportsRowSubtitle).text =
                if ((duplicated[item.name.lowercase()] ?: 0) > 1) "TVF90 · " + item.id.uppercase()
                else "ACCESO WEB DIRECTO"
            ChannelArtwork.bind(this@SportsCatalogActivity,
                row.findViewById(R.id.sportsRowArtwork), logos.forSport(item), item.name)
            return row
        }
    }

    companion object {
        private val FULLSCREEN_SCRIPT = """
            (function() {
              try {
                document.documentElement.style.background = '#000';
                document.body.style.background = '#000';
                document.body.style.margin = '0';
                document.body.style.overflow = 'hidden';
                var v = Array.from(document.querySelectorAll('video, iframe'));
                v.sort(function(a,b) { var x = b.getBoundingClientRect(),
                  y = a.getBoundingClientRect(); return x.width*x.height-y.width*y.height; });
                if (v.length) {
                  var e=v[0];
                  e.style.setProperty('position','fixed','important');
                  e.style.setProperty('inset','0','important');
                  e.style.setProperty('width','100vw','important');
                  e.style.setProperty('height','100vh','important');
                  e.style.setProperty('max-width','100vw','important');
                  e.style.setProperty('max-height','100vh','important');
                  e.style.setProperty('z-index','2147483646','important');
                  e.setAttribute('allowfullscreen','');
                  e.setAttribute('allow','autoplay; fullscreen; picture-in-picture');
                }
              } catch(err) {}
            })();
        """.trimIndent()
    }
}
