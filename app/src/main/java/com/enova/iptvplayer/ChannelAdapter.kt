package com.enova.iptvplayer

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import com.bumptech.glide.Glide

class ChannelAdapter(
    private val context: Context,
    private val isFavorite: (Channel) -> Boolean
) : BaseAdapter() {
    private var channels: List<Channel> = emptyList()

    fun submit(values: List<Channel>) {
        channels = values
        notifyDataSetChanged()
    }

    override fun getCount() = channels.size
    override fun getItem(position: Int) = channels[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = convertView ?: LayoutInflater.from(context).inflate(R.layout.row_channel, parent, false)
        val channel = getItem(position)
        row.findViewById<TextView>(R.id.channelName).text = channel.name
        row.findViewById<TextView>(R.id.channelGroup).text = channel.group
        row.findViewById<TextView>(R.id.channelStar).text = if (isFavorite(channel)) "★" else "☆"
        val logo = row.findViewById<ImageView>(R.id.channelLogo)
        if (channel.logo.isNullOrBlank()) {
            Glide.with(context).clear(logo)
            logo.setImageResource(R.drawable.ic_tv)
        } else {
            Glide.with(context).load(channel.logo)
                .placeholder(R.drawable.ic_tv)
                .error(R.drawable.ic_tv)
                .into(logo)
        }
        return row
    }
}
