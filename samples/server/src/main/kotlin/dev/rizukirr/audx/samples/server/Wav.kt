package dev.rizukirr.audx.samples.server

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decoded mono PCM-16 audio. */
class Pcm(val sampleRate: Int, val samples: ShortArray)

class WavFormatException(message: String) : Exception(message)

/**
 * Parses a WAV file. Only mono PCM-16 is supported — anything else throws
 * [WavFormatException].
 */
fun parseWav(bytes: ByteArray): Pcm {
    if (bytes.size < 12 ||
        String(bytes, 0, 4, Charsets.US_ASCII) != "RIFF" ||
        String(bytes, 8, 4, Charsets.US_ASCII) != "WAVE"
    ) throw WavFormatException("not a RIFF/WAVE file")

    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    var pos = 12
    var sampleRate = 0
    var fmtSeen = false
    var data: ShortArray? = null

    while (pos + 8 <= bytes.size) {
        val id = String(bytes, pos, 4, Charsets.US_ASCII)
        val size = buf.getInt(pos + 4)
        if (size < 0 || pos + 8 + size > bytes.size) throw WavFormatException("truncated '$id' chunk")
        when (id) {
            "fmt " -> {
                if (size < 16) throw WavFormatException("'fmt ' chunk too small")
                val audioFormat = buf.getShort(pos + 8).toInt()
                val channels = buf.getShort(pos + 10).toInt()
                sampleRate = buf.getInt(pos + 12)
                val bitsPerSample = buf.getShort(pos + 22).toInt()
                if (audioFormat != 1) throw WavFormatException("only PCM supported (got format $audioFormat)")
                if (channels != 1) throw WavFormatException("only mono supported (got $channels channels)")
                if (bitsPerSample != 16) throw WavFormatException("only 16-bit supported (got $bitsPerSample)")
                if (sampleRate <= 0) throw WavFormatException("invalid sample rate $sampleRate")
                fmtSeen = true
            }
            "data" -> {
                val samples = ShortArray(size / 2)
                ByteBuffer.wrap(bytes, pos + 8, size).order(ByteOrder.LITTLE_ENDIAN)
                    .asShortBuffer().get(samples)
                data = samples
            }
        }
        pos += 8 + size + (size and 1) // chunks are word-aligned
    }

    if (!fmtSeen) throw WavFormatException("missing 'fmt ' chunk")
    return Pcm(sampleRate, data ?: throw WavFormatException("missing 'data' chunk"))
}

/** Encodes mono PCM-16 samples as an in-memory WAV file. */
fun wavBytes(sampleRate: Int, samples: ShortArray): ByteArray {
    val dataSize = samples.size * 2
    val buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
    buf.put("RIFF".toByteArray(Charsets.US_ASCII))
    buf.putInt(36 + dataSize)
    buf.put("WAVE".toByteArray(Charsets.US_ASCII))
    buf.put("fmt ".toByteArray(Charsets.US_ASCII))
    buf.putInt(16)             // fmt chunk size
    buf.putShort(1)            // PCM
    buf.putShort(1)            // mono
    buf.putInt(sampleRate)
    buf.putInt(sampleRate * 2) // byte rate = rate * blockAlign
    buf.putShort(2)            // block align = channels * bytesPerSample
    buf.putShort(16)           // bits per sample
    buf.put("data".toByteArray(Charsets.US_ASCII))
    buf.putInt(dataSize)
    for (s in samples) buf.putShort(s)
    return buf.array()
}

fun writeWav(file: File, sampleRate: Int, samples: ShortArray) {
    file.writeBytes(wavBytes(sampleRate, samples))
}
