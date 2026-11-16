package com.example.simplemusic

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.PowerManager

data class Song(val title: String, val artist: String, val folder: String, val uri: Uri)

object Player {
    private lateinit var appCtx: Context
    private var mp: MediaPlayer? = null
    private var focusReq: AudioFocusRequest? = null

    var queue: List<Song> = emptyList()
        private set
    var index: Int = -1
        private set

    val listeners = mutableListOf<() -> Unit>()

    val current: Song? get() = queue.getOrNull(index)
    val isPlaying: Boolean get() = mp?.isPlaying == true

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        if (change == AudioManager.AUDIOFOCUS_LOSS ||
            change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT
        ) pause()
    }

    private fun changed() {
        listeners.toList().forEach { it() }
    }

    fun play(ctx: Context, songs: List<Song>, start: Int) {
        appCtx = ctx.applicationContext
        queue = songs
        index = start
        appCtx.startForegroundService(Intent(appCtx, PlayerService::class.java))
        startCurrent()
    }

    private fun startCurrent() {
        val song = current ?: return
        mp?.release()
        mp = null
        val p = MediaPlayer()
        try {
            p.setAudioAttributes(attrs)
            p.setWakeMode(appCtx, PowerManager.PARTIAL_WAKE_LOCK)
            p.setDataSource(appCtx, song.uri)
            p.setOnCompletionListener { next() }
            p.setOnErrorListener { _, _, _ -> next(); true }
            p.prepare()
            mp = p
            requestFocus()
            p.start()
        } catch (e: Exception) {
            p.release()
            mp = null
            if (index < queue.lastIndex) {
                index++
                startCurrent()
            } else {
                stop()
            }
            return
        }
        changed()
    }

    fun toggle() {
        val p = mp ?: return
        if (p.isPlaying) {
            pause()
        } else {
            requestFocus()
            p.start()
            changed()
        }
    }

    fun pause() {
        val p = mp ?: return
        if (p.isPlaying) {
            p.pause()
            changed()
        }
    }

    fun next() {
        if (index < queue.lastIndex) {
            index++
            startCurrent()
        } else {
            stop()
        }
    }

    fun prev() {
        val p = mp ?: return
        if (p.currentPosition > 3000 || index == 0) {
            p.seekTo(0)
        } else {
            index--
            startCurrent()
        }
    }

    fun stop() {
        mp?.release()
        mp = null
        queue = emptyList()
        index = -1
        abandonFocus()
        changed()
    }

    private fun requestFocus() {
        val req = focusReq ?: AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attrs)
            .setOnAudioFocusChangeListener(focusListener)
            .build()
            .also { focusReq = it }
        appCtx.getSystemService(AudioManager::class.java).requestAudioFocus(req)
    }

    private fun abandonFocus() {
        focusReq?.let { appCtx.getSystemService(AudioManager::class.java).abandonAudioFocusRequest(it) }
        focusReq = null
    }
}
