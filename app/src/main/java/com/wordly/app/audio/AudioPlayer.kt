package com.wordly.app.audio

import android.media.AudioAttributes
import android.media.MediaPlayer

/** Plays a remote mp3 pronunciation URL (from the dictionary API). One clip at a time. */
class AudioPlayer {

    private var player: MediaPlayer? = null

    fun play(url: String) {
        release()
        player = MediaPlayer().apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            setOnPreparedListener { it.start() }
            setOnCompletionListener { release() }
            setOnErrorListener { _, _, _ ->
                release()
                true
            }
            runCatching {
                setDataSource(url)
                prepareAsync()
            }.onFailure { release() }
        }
    }

    fun release() {
        player?.runCatching { reset(); release() }
        player = null
    }
}
