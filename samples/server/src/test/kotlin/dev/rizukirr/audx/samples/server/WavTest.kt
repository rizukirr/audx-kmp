package dev.rizukirr.audx.samples.server

import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WavTest {
    private val samples = ShortArray(480) { (it * 37 % 4096).toShort() }

    @Test
    fun `round trips mono pcm16`() {
        val pcm = parseWav(wavBytes(16_000, samples))
        assertEquals(16_000, pcm.sampleRate)
        assertContentEquals(samples, pcm.samples)
    }

    @Test
    fun `writeWav writes parseable file`() {
        val file = createTempFile(suffix = ".wav").toFile()
        try {
            writeWav(file, 16_000, samples)
            val pcm = parseWav(file.readBytes())
            assertContentEquals(samples, pcm.samples)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `rejects garbage`() {
        assertFailsWith<WavFormatException> { parseWav(byteArrayOf(1, 2, 3)) }
    }

    @Test
    fun `rejects stereo`() {
        val bytes = wavBytes(16_000, samples)
        bytes[22] = 2 // channel count lives at offset 22 in the canonical 44-byte header
        assertFailsWith<WavFormatException> { parseWav(bytes) }
    }
}
