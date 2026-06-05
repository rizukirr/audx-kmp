package dev.rizukirr.audx.samples.app

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

const val SAMPLE_RATE = 16_000

/** One-shot microphone capture: [record] blocks until [stop] is called. */
class Recorder {
    @Volatile
    private var recording = false

    /**
     * Captures 16 kHz mono PCM-16 until [stop] is called, then returns every
     * sample read. Call on a background dispatcher. Throws
     * IllegalStateException if the mic cannot be opened.
     */
    @SuppressLint("MissingPermission") // caller ensures RECORD_AUDIO is granted
    fun record(): ShortArray {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, SAMPLE_RATE), // bytes — at least 0.5 s of headroom
        )
        check(audioRecord.state == AudioRecord.STATE_INITIALIZED) { "microphone unavailable" }

        val chunks = ArrayList<ShortArray>()
        try {
            recording = true
            audioRecord.startRecording()
            val chunk = ShortArray(1024)
            while (recording) {
                val n = audioRecord.read(chunk, 0, chunk.size)
                if (n > 0) chunks.add(chunk.copyOf(n))
            }
            audioRecord.stop()
        } finally {
            recording = false
            audioRecord.release()
        }

        val all = ShortArray(chunks.sumOf { it.size })
        var pos = 0
        for (c in chunks) {
            c.copyInto(all, pos)
            pos += c.size
        }
        return all
    }

    fun stop() {
        recording = false
    }
}
