package com.enova.iptvplayer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.ProgressBar
import android.widget.ArrayAdapter
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.Executors

/** TV-first dashboard with visible playlist sources, live channels and sports. */
class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var store: PlaylistStore
    private lateinit var logos: LogoCatalog
    private lateinit var urlField: EditText
    private lateinit var importButton: Button
    private lateinit var fileButton: Button
    private lateinit var favoritesButton: Button
    private lateinit var starButton: Button
    private lateinit var groups: Spinner
    private lateinit var list: ListView
    private lateinit var status: TextView
    private lateinit var sourceStatus: TextView
    private lateinit var channelCount: TextView
    private lateinit var progress: ProgressBar
    private lateinit var adapter: ChannelAdapter
    private var channels = emptyList<Channel>()
    private var visibleChannels = emptyList<Channel>()
    private var onlyFavorites = false
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = PlaylistStore(this)
        logos = LogoCatalog(this)
        urlField = findViewById(R.id.playlistUrl)
        importButton = findViewById(R.id.importUrl)
        fileButton = findViewById(R.id.importFile)
        favoritesButton = findViewById(R.id.onlyFavorites)
        starButton = findViewById(R.id.starChannel)
        groups = findViewById(R.id.groupSpinner)
        list = findViewById(R.id.channelList)
        status = findViewById(R.id.status)
        sourceStatus = findViewById(R.id.sourceStatus)
        channelCount = findViewById(R.id.channelCount)
        progress = findViewById(R.id.loadingProgress)
        adapter = ChannelAdapter(this, { it.streamUrl in store.favorites() }, logos)
        list.adapter = adapter
        channels = store.loadChannels()
        urlField.setText(store.lastSource())
        sourceStatus.text = if (channels.isEmpty()) {
            "Elegí una fuente para comenzar"
        } else {
            "Fuente: " + displaySource(store.lastSource())
        }
        rebuildGroups()

        val ids = listOf(
            R.id.presetArgentina,
            R.id.presetGlobal,
            R.id.presetDeportes,
            R.id.presetLista1,
            R.id.presetLista
        )
        PlaylistPresets.entries.drop(1).zip(ids).forEach { (source, id) ->
            findViewById<Button>(id).setOnClickListener {
                urlField.setText(source.url)
                importUrl(source.url)
            }
        }
        importButton.setOnClickListener { importUrl(urlField.text.toString().trim()) }
        fileButton.setOnClickListener { chooseFile() }
        findViewById<Button>(R.id.openSports).setOnClickListener {
            startActivity(Intent(this, SportsCatalogActivity::class.java))
        }
        findViewById<Button>(R.id.openAgenda).setOnClickListener {
            startActivity(Intent(this, SportsActivity::class.java))
        }
        favoritesButton.setOnClickListener {
            onlyFavorites = !onlyFavorites
            favoritesButton.text = if (onlyFavorites) "★ Mostrar todos" else "★ Favoritos"
            updateList()
        }
        starButton.setOnClickListener {
            val channel = visibleChannels.getOrNull(list.selectedItemPosition)
            if (channel == null) {
                showMessage("Seleccioná un canal en la lista.")
            } else {
                toggleFavorite(channel)
            }
        }
        groups.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                updateList()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        list.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val channel = visibleChannels.getOrNull(position) ?: return@OnItemClickListener
            startActivity(Intent(this, PlayerActivity::class.java).apply {
                putExtra(PlayerActivity.EXTRA_NAME, channel.name)
                putExtra(PlayerActivity.EXTRA_URL, channel.streamUrl)
            })
        }
        list.onItemLongClickListener = AdapterView.OnItemLongClickListener { _, _, position, _ ->
            visibleChannels.getOrNull(position)?.let { toggleFavorite(it) }
            true
        }
        updateList()
        if (channels.isEmpty() && store.lastSource().isBlank()) {
            // First run: start with the small Argentina playlist, but don't block navigation.
            importUrl(PlaylistPresets.entries[2].url)
        }
    }

    private fun displaySource(source: String): String {
        return PlaylistPresets.entries.firstOrNull { it.url == source }?.name
            ?: if (source.isBlank()) "Archivo local" else "URL personalizada"
    }

    private fun importUrl(source: String) {
        if (loading) return
        val scheme = try { URI(source).scheme?.lowercase() } catch (_: Exception) { null }
        if (scheme != "https" && scheme != "http") {
            showMessage("La dirección debe empezar con https:// o http://")
            return
        }
        fetchPlaylist(source) {
            val connection = URL(source).openConnection() as HttpURLConnection
            connection.connectTimeout = 12000
            connection.readTimeout = 25000
            connection.setRequestProperty("User-Agent", "EnovaIPTV/0.2")
            try {
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("Respuesta HTTP " + connection.responseCode)
                }
                connection.inputStream.use(::readLimited)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun chooseFile() {
        if (loading) return
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            startActivityForResult(picker, FILE_REQUEST)
        } catch (_: Exception) {
            showMessage("Este televisor no tiene un selector de archivos compatible.")
        }
    }

    @Deprecated("Selector de archivos compatible con Google TV")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != FILE_REQUEST || resultCode != RESULT_OK) return
        val document = data?.data ?: return
        fetchPlaylist(null) {
            contentResolver.openInputStream(document)?.use(::readLimited)
                ?: throw IllegalStateException("No se pudo leer el archivo.")
        }
    }

    private fun fetchPlaylist(source: String?, read: () -> String) {
        if (loading) return
        loading = true
        importButton.isEnabled = false
        fileButton.isEnabled = false
        progress.visibility = View.VISIBLE
        sourceStatus.text = "Conectando con la fuente…"
        showMessage("Descargando y organizando canales…")
        worker.execute {
            val result = runCatching {
                val parsed = M3uParser.parse(read(), source)
                require(parsed.isNotEmpty()) { "La lista no tiene canales HTTP(S) válidos." }
                require(parsed.size <= 30000) { "La lista tiene demasiados canales (máximo 30.000)." }
                parsed
            }
            runOnUiThread {
                loading = false
                importButton.isEnabled = true
                fileButton.isEnabled = true
                progress.visibility = View.GONE
                if (isFinishing || isDestroyed) return@runOnUiThread
                result.onSuccess { parsed ->
                    channels = parsed
                    store.saveChannels(parsed)
                    store.saveSource(source.orEmpty())
                    rebuildGroups()
                    sourceStatus.text = "✓ " + displaySource(source.orEmpty()) +
                        " · " + parsed.size + " señales"
                    showMessage("Lista lista. Elegí un canal y presioná OK para verlo.")
                }.onFailure { error ->
                    sourceStatus.text = "No se pudo cargar la fuente"
                    showMessage((error.message ?: "Error de conexión") +
                        " · Probá otra lista.")
                }
            }
        }
    }

    private fun rebuildGroups() {
        val values = listOf("Todas las categorías") +
            channels.map { it.group }.distinct().sortedWith(String.CASE_INSENSITIVE_ORDER)
        groups.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, values).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        groups.setSelection(0)
        updateList()
    }

    private fun updateList() {
        val selectedGroup = groups.selectedItem?.toString() ?: "Todas las categorías"
        val favorites = store.favorites()
        visibleChannels = channels.filter { channel ->
            (selectedGroup == "Todas las categorías" || channel.group == selectedGroup) &&
                (!onlyFavorites || channel.streamUrl in favorites)
        }
        adapter.submit(visibleChannels)
        channelCount.text = visibleChannels.size.toString() + " CANALES"
        status.text = when {
            channels.isEmpty() && !loading -> "Elegí una lista M3U del panel izquierdo."
            visibleChannels.isEmpty() -> "No hay canales con este filtro."
            else -> "OK: reproducir · OK sostenido: favorito · Atrás: volver"
        }
    }

    private fun toggleFavorite(channel: Channel) {
        val added = store.toggleFavorite(channel.streamUrl)
        updateList()
        showMessage((if (added) "★ Favorito: " else "Quitado: ") + channel.name)
    }

    private fun showMessage(message: String) {
        status.text = message
    }

    override fun onDestroy() {
        worker.shutdownNow()
        super.onDestroy()
    }

    companion object {
        private const val FILE_REQUEST = 200
        private const val MAX_PLAYLIST_BYTES = 18 * 1024 * 1024

        private fun readLimited(stream: InputStream): String {
            val output = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (true) {
                val size = stream.read(chunk)
                if (size < 0) break
                if (output.size() + size > MAX_PLAYLIST_BYTES) {
                    throw IllegalStateException("La lista supera los 18 MB permitidos.")
                }
                output.write(chunk, 0, size)
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }
}
