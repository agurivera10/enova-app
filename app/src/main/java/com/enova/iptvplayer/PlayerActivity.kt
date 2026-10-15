package com.enova.iptvplayer

import android.app.Activity
import android.os.Bundle
import android.view.WindowManager
import android.widget.TextView
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.net.URI

class PlayerActivity : Activity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var title: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(R.layout.activity_player)
        playerView = findViewById(R.id.playerView)
        title = findViewById(R.id.playerTitle)
        title.text = intent.getStringExtra(EXTRA_NAME).orEmpty()
    }

    override fun onStart() {
        super.onStart()
        val streamUrl = intent.getStringExtra(EXTRA_URL) ?: run {
            title.text = "No se recibió una URL para reproducir."
            return
        }
        player = ExoPlayer.Builder(this).build().also { exo ->
            playerView.player = exo
            val builder = MediaItem.Builder().setUri(streamUrl)
            val path = runCatching { URI(streamUrl).path.orEmpty().lowercase() }.getOrDefault("")
            if (path.endsWith(".m3u8")) builder.setMimeType(MimeTypes.APPLICATION_M3U8)
            exo.addListener(object : Player.Listener {
                override fun onPlayerError(error: PlaybackException) {
                    title.text = "No se pudo reproducir el canal: " + error.errorCodeName
                }
            })
            exo.setMediaItem(builder.build())
            exo.prepare()
            exo.playWhenReady = true
        }
    }

    override fun onStop() {
        playerView.player = null
        player?.release()
        player = null
        super.onStop()
    }

    companion object {
        const val EXTRA_NAME = "channel_name"
        const val EXTRA_URL = "channel_url"
    }
}
