package dev.rizukirr.audx.samples.app

import android.media.MediaPlayer
import java.io.File

/** Plays one WAV file at a time; starting a new one stops the previous. */
class Player {
    private var player: MediaPlayer? = null

    /**
     * Starts [file]. [onComplete] fires once when playback reaches the end
     * naturally (not when [stop] interrupts it).
     */
    fun play(file: File, onComplete: () -> Unit = {}) {
        stop()
        player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            setOnCompletionListener {
                this@Player.stop()
                onComplete()
            }
            prepare()
            start()
        }
    }

    fun stop() {
        player?.release()
        player = null
    }
}
