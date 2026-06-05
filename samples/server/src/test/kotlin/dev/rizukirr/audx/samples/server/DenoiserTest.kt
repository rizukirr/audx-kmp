package dev.rizukirr.audx.samples.server

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals

class DenoiserTest {
    @Test
    fun `preserves length and reports frame count`() {
        val n = 1_000 // deliberately not a multiple of the 160-sample frame at 16 kHz
        val sine = ShortArray(n) { (8_000 * sin(2.0 * PI * 440 * it / 16_000)).toInt().toShort() }
        val result = denoise(16_000, sine)
        assertEquals(n, result.samples.size)
        assertEquals(7, result.frames) // ceil(1000 / 160)
    }

    @Test
    fun `handles empty input`() {
        val result = denoise(16_000, ShortArray(0))
        assertEquals(0, result.samples.size)
        assertEquals(0, result.frames)
    }
}
