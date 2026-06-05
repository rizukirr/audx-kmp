package dev.rizukirr.audx.samples.app

import android.media.MediaPlayer
import java.io.File

/** Plays one WAV file at a time; starting a new one stops the previous. */
class Player {
    private var player: MediaPlayer? = null

    fun play(file: File) {
        stop()
        player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            start()
        }
    }

    fun stop() {
        player?.release()
        player = null
    }
}
