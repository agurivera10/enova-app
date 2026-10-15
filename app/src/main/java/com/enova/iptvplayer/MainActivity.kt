package com.enova.iptvplayer

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ListView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {
    private val worker = Executors.newSingleThreadExecutor()
    private lateinit var store: PlaylistStore
    private lateinit var urlField: EditText
    private lateinit var presetSpinner: Spinner
    private lateinit var importButton: Button
    private lateinit var fileButton: Button
    private lateinit var favoritesButton: Button
    private lateinit var starButton: Button
    private lateinit var groups: Spinner
    private lateinit var list: ListView
    private lateinit var status: TextView
    private lateinit var adapter: ChannelAdapter
    private var channels = emptyList<Channel>()
    private var visibleChannels = emptyList<Channel>()
    private var onlyFavorites = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        store = PlaylistStore(this)
        urlField = findViewById(R.id.playlistUrl)
        presetSpinner = findViewById(R.id.playlistPresets)
        importButton = findViewById(R.id.importUrl)
        fileButton = findViewById(R.id.importFile)
        favoritesButton = findViewById(R.id.onlyFavorites)
        starButton = findViewById(R.id.starChannel)
        groups = findViewById(R.id.groupSpinner)
        list = findViewById(R.id.channelList)
        status = findViewById(R.id.status)
        adapter = ChannelAdapter(this) { channel -> channel.streamUrl in store.favorites() }
        list.adapter = adapter
        channels = store.loadChannels()
        urlField.setText(store.lastSource())
        presetSpinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item,
            PlaylistPresets.entries.map { it.name }).also {
            it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        presetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val preset = PlaylistPresets.entries.getOrNull(position) ?: return
                if (preset.url.isNotBlank()) {
                    urlField.setText(preset.url)
                    status.text = if (preset.isUnverified)
                        "Fuente externa sin verificar. Revisá los permisos y pulsá Cargar URL."
                    else "Fuente seleccionada. Pulsá Cargar URL para leer los canales."
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        rebuildGroups()

        importButton.setOnClickListener { importUrl() }
        fileButton.setOnClickListener { chooseFile() }
        favoritesButton.setOnClickListener {
            onlyFavorites = !onlyFavorites
            favoritesButton.text = if (onlyFavorites) "Mostrar todos" else "Solo favoritos"
            updateList()
        }
        starButton.setOnClickListener {
            val selected = list.selectedItemPosition
            if (selected !in visibleChannels.indices) {
                showMessage("Seleccioná un canal con las flechas y luego pulsá ☆.")
            } else {
                toggleFavorite(visibleChannels[selected])
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
    }

    private fun importUrl() {
        val source = urlField.text.toString().trim()
        val scheme = try { URI(source).scheme?.lowercase() } catch (_: Exception) { null }
        if (scheme != "https" && scheme != "http") {
            showMessage("Ingresá una URL que empiece con https:// o http://.")
            return
        }
        importPlaylist(source) {
            val connection = URL(source).openConnection() as HttpURLConnection
            connection.connectTimeout = 12000
            connection.readTimeout = 20000
            connection.setRequestProperty("User-Agent", "EnovaIPTV/0.1")
            try {
                if (connection.responseCode !in 200..299) {
                    throw IllegalStateException("HTTP " + connection.responseCode)
                }
                connection.inputStream.use(::readLimited)
            } finally {
                connection.disconnect()
            }
        }
    }

    private fun chooseFile() {
        val picker = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            startActivityForResult(picker, FILE_REQUEST)
        } catch (_: Exception) {
            showMessage("No hay un selector de archivos disponible en este televisor.")
        }
    }

    @Deprecated("API tradicional compatible con Google TV")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != FILE_REQUEST || resultCode != RESULT_OK) return
        val document = data?.data ?: return
        importPlaylist(null) {
            contentResolver.openInputStream(document)?.use(::readLimited)
                ?: throw IllegalStateException("No se pudo leer el archivo.")
        }
    }

    private fun importPlaylist(baseUrl: String?, read: () -> String) {
        importButton.isEnabled = false
        fileButton.isEnabled = false
        showMessage("Leyendo lista M3U…")
        worker.execute {
            val result = runCatching {
                val parsed = M3uParser.parse(read(), baseUrl)
                require(parsed.isNotEmpty()) { "No se encontraron canales HTTP(S) válidos." }
                require(parsed.size <= 20000) { "La lista tiene demasiados canales (máximo 20.000)." }
                parsed
            }
            runOnUiThread {
                importButton.isEnabled = true
                fileButton.isEnabled = true
                result.onSuccess {
                    channels = it
                    store.saveChannels(it)
                    store.saveSource(baseUrl.orEmpty())
                    if (baseUrl == null) urlField.setText("")
                    rebuildGroups()
                    showMessage("Lista cargada: " + it.size + " canales. OK para reproducir.")
                }.onFailure {
                    showMessage("No se pudo cargar la lista: " + (it.message ?: "Error desconocido"))
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
        if (visibleChannels.isEmpty()) {
            status.text = if (channels.isEmpty()) "Agregá una URL o importá un archivo M3U para empezar."
                          else "No hay canales en este filtro."
        } else {
            status.text = visibleChannels.size.toString() + " canales · Enter: reproducir · Mantener OK: favorito"
        }
    }

    private fun toggleFavorite(channel: Channel) {
        val isFavorite = store.toggleFavorite(channel.streamUrl)
        updateList()
        showMessage(if (isFavorite) "Agregado a favoritos: " + channel.name
                    else "Quitado de favoritos: " + channel.name)
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
        private const val MAX_PLAYLIST_BYTES = 12 * 1024 * 1024

        private fun readLimited(stream: InputStream): String {
            val output = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            while (true) {
                val size = stream.read(chunk)
                if (size < 0) break
                if (output.size() + size > MAX_PLAYLIST_BYTES) {
                    throw IllegalStateException("La lista supera el máximo de 12 MB.")
                }
                output.write(chunk, 0, size)
            }
            return output.toString(Charsets.UTF_8.name())
        }
    }
}
