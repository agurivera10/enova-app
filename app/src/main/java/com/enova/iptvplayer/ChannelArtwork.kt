package com.enova.iptvplayer

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide

/** Composable TV-friendly thumbnail: verified logo, otherwise branded monogram. */
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
        val background = box.findViewById<TextView>(R.id.artworkMonogram)
        val logo = box.findViewById<ImageView>(R.id.artworkImage)
        val color = palettes[(name.hashCode().toLong() and 0x7fffffffL).rem(palettes.size).toInt()]
        val shape = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(color, Color.rgb(23, 32, 57))
        ).also { it.cornerRadius = 20f }
        box.background = shape
        val initial = name.uppercase().split(Regex("\\s+")).take(2)
            .mapNotNull { it.firstOrNull()?.toString() }.joinToString("")
        background.text = initial.ifBlank { "TV" }
        background.gravity = Gravity.CENTER
        background.setTypeface(null, Typeface.BOLD)
        if (!logoUrl.isNullOrBlank() && logoUrl.startsWith("https://")) {
            logo.visibility = android.view.View.VISIBLE
            Glide.with(context).load(logoUrl)
                .error(R.drawable.ic_tv)
                .into(logo)
        } else {
            Glide.with(context).clear(logo)
            logo.visibility = android.view.View.GONE
        }
    }
}
