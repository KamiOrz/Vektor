package com.vektor.app

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
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
    private val trackSelector = DefaultTrackSelector(context)
    val player: ExoPlayer = ExoPlayer.Builder(context)
        .setTrackSelector(trackSelector)
        .build()
    private val _currentChannel = MutableStateFlow<Channel?>(null)
    val currentChannel: StateFlow<Channel?> = _currentChannel
    private val _videoShape = MutableStateFlow(VideoShape.Unknown)
    val videoShape: StateFlow<VideoShape> = _videoShape
    private val _videoAspectRatio = MutableStateFlow<Float?>(null)
    val videoAspectRatio: StateFlow<Float?> = _videoAspectRatio
    private val mediaSession: MediaSession = MediaSession.Builder(context, player).build()
    private var hdrCompatibilityMode = true
    private var lastVideoOverrideSignature: String? = null

    init {
        player.addListener(
            object : Player.Listener {
                override fun onTracksChanged(tracks: Tracks) {
                    applyHdrCompatibility(tracks)
                }

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

    fun setHdrCompatibilityMode(enabled: Boolean) {
        if (hdrCompatibilityMode == enabled) return
        hdrCompatibilityMode = enabled
        lastVideoOverrideSignature = null
        applyHdrCompatibility(player.currentTracks)
    }

    fun load(channel: Channel, autoPlay: Boolean = false) {
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
        if (!autoPlay) {
            player.pause()
        }
        player.playWhenReady = autoPlay
        player.setMediaItem(item)
        player.prepare()
        if (autoPlay) {
            player.play()
        }
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

    private fun applyHdrCompatibility(tracks: Tracks) {
        if (!hdrCompatibilityMode) {
            if (lastVideoOverrideSignature != null) {
                trackSelector.setParameters(
                    trackSelector.buildUponParameters()
                        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                )
                lastVideoOverrideSignature = null
            }
            return
        }

        val candidate = tracks.getGroups()
            .filter { it.getType() == C.TRACK_TYPE_VIDEO }
            .mapNotNull { group ->
                val sdrIndices = (0 until group.length)
                    .filter { group.isTrackSupported(it) && !group.getTrackFormat(it).isHdr() }
                if (sdrIndices.isEmpty()) {
                    null
                } else {
                    val score = sdrIndices.maxOf { group.getTrackFormat(it).pixelScore() }
                    group to (sdrIndices to score)
                }
            }
            .maxByOrNull { it.second.second }

        if (candidate == null) {
            if (lastVideoOverrideSignature != null) {
                trackSelector.setParameters(
                    trackSelector.buildUponParameters()
                        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                )
                lastVideoOverrideSignature = null
            }
            return
        }

        val group = candidate.first
        val indices = candidate.second.first
        val signature = "${group.getMediaTrackGroup().hashCode()}:${indices.joinToString(",")}"
        if (signature == lastVideoOverrideSignature) return
        trackSelector.setParameters(
            trackSelector.buildUponParameters()
                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                .setOverrideForType(TrackSelectionOverride(group.getMediaTrackGroup(), indices))
        )
        lastVideoOverrideSignature = signature
    }

    private fun androidx.media3.common.Format.isHdr(): Boolean {
        val info = colorInfo ?: return false
        return ColorInfo.isTransferHdr(info)
    }

    private fun androidx.media3.common.Format.pixelScore(): Int {
        val widthValue = width.takeIf { it > 0 } ?: 0
        val heightValue = height.takeIf { it > 0 } ?: 0
        return widthValue * heightValue
    }

    private fun VideoSize.safeAspectRatio(): Float? {
        if (width <= 0 || height <= 0) return null
        val pixelRatio = if (pixelWidthHeightRatio > 0f) pixelWidthHeightRatio else 1f
        return width.toFloat() * pixelRatio / height.toFloat()
    }
}
