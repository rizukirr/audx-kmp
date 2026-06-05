package dev.rizukirr.audx.samples.app

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
