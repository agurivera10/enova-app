package com.enova.iptvplayer

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.DataSource
import com.bumptech.glide.load.engine.GlideException
import com.bumptech.glide.request.RequestListener
import com.bumptech.glide.request.target.Target

/**
 * Se muestra el monograma SOLAMENTE mientras el logo carga o si la carga falla.
 * Glide reutiliza filas: se cancela la petición anterior y se comprueba la URL actual
 * para que una respuesta antigua nunca modifique una tarjeta reciclada.
 */
object ChannelArtwork {
    private val palettes = intArrayOf(
        Color.rgb(46, 93, 158),
        Color.rgb(113, 67, 165),
        Color.rgb(13, 124, 129),
        Color.rgb(157, 69, 90),
        Color.rgb(58, 105, 84),
        Color.rgb(161, 101, 43)
    )

    fun bind(context: Context, box: FrameLayout, logoUrl: String?, name: String) {
        val monogram = box.findViewById<TextView>(R.id.artworkMonogram)
        val logo = box.findViewById<ImageView>(R.id.artworkImage)
        val color = palettes[(name.hashCode().toLong() and 0x7fffffffL).rem(palettes.size).toInt()]
        box.background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(color, Color.rgb(23, 32, 57))
        ).also { it.cornerRadius = 20f }

        val initials = name.uppercase().split(Regex("\\s+")).take(2)
            .mapNotNull { it.firstOrNull()?.toString() }.joinToString("")
        monogram.text = initials.ifBlank { "TV" }
        monogram.gravity = Gravity.CENTER
        monogram.setTypeface(null, Typeface.BOLD)

        val url = logoUrl?.takeIf { it.startsWith("https://") }
        // Always clear the old image on reused channel rows.
        Glide.with(context).clear(logo)
        box.tag = url
        monogram.visibility = View.VISIBLE
        logo.visibility = View.GONE
        if (url == null) return

        Glide.with(context).load(url)
            .listener(object : RequestListener<Drawable> {
                override fun onLoadFailed(
                    e: GlideException?, model: Any?, target: Target<Drawable>,
                    isFirstResource: Boolean
                ): Boolean {
                    if (box.tag == url) {
                        monogram.visibility = View.VISIBLE
                        logo.visibility = View.GONE
                    }
                    return false
                }

                override fun onResourceReady(
                    resource: Drawable, model: Any, target: Target<Drawable>?,
                    dataSource: DataSource, isFirstResource: Boolean
                ): Boolean {
                    if (box.tag == url) {
                        monogram.visibility = View.GONE
                        logo.visibility = View.VISIBLE
                    }
                    return false
                }
            }).into(logo)
    }
}
