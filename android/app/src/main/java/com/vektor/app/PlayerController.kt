package com.vektor.app

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class VideoShape {
    Unknown,
    Portrait,
    Landscape,
    SquareOrNeutral
}

@OptIn(UnstableApi::class)
class PlayerController(private val context: Context) {
    val player: ExoPlayer = ExoPlayer.Builder(context).build()
    private val _currentChannel = MutableStateFlow<Channel?>(null)
    val currentChannel: StateFlow<Channel?> = _currentChannel
    private val _videoShape = MutableStateFlow(VideoShape.Unknown)
    val videoShape: StateFlow<VideoShape> = _videoShape
    private val _videoAspectRatio = MutableStateFlow<Float?>(null)
    val videoAspectRatio: StateFlow<Float?> = _videoAspectRatio
    private val mediaSession: MediaSession = MediaSession.Builder(context, player).build()

    init {
        player.addListener(
            object : Player.Listener {
                override fun onVideoSizeChanged(videoSize: VideoSize) {
                    val ratio = videoSize.safeAspectRatio()
                    _videoAspectRatio.value = ratio
                    _videoShape.value = when {
                        ratio == null -> VideoShape.Unknown
                        ratio < 0.85f -> VideoShape.Portrait
                        ratio > 1.25f -> VideoShape.Landscape
                        else -> VideoShape.SquareOrNeutral
                    }
                }
            }
        )
    }

    fun load(channel: Channel) {
        _currentChannel.value = channel
        _videoShape.value = VideoShape.Unknown
        _videoAspectRatio.value = null
        val metadata = MediaMetadata.Builder()
            .setTitle(channel.title)
            .setAlbumTitle(channel.group)
            .build()
        val item = MediaItem.Builder()
            .setUri(channel.streamUrl)
            .setMediaMetadata(metadata)
            .build()
        player.pause()
        player.playWhenReady = false
        player.setMediaItem(item)
        player.prepare()
    }

    fun play() {
        player.playWhenReady = true
        player.play()
    }

    fun pause() {
        player.pause()
    }

    fun release() {
        player.stop()
        player.clearMediaItems()
        player.release()
        mediaSession.release()
        _currentChannel.value = null
    }

    private fun VideoSize.safeAspectRatio(): Float? {
        if (width <= 0 || height <= 0) return null
        val pixelRatio = if (pixelWidthHeightRatio > 0f) pixelWidthHeightRatio else 1f
        return width.toFloat() * pixelRatio / height.toFloat()
    }
}
