package dev.rizukirr.audx

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AudxJvmTest {

    @Test
    fun createProcessClose() {
        Audx(sampleRate = 16000).use { audx ->
            assertTrue(audx.frameSize > 0, "frameSize must be positive")
            assertFalse(audx.isClosed())

            val input = ShortArray(audx.frameSize) {
                Random.nextInt(-3000, 3000).toShort()
            }
            val output = ShortArray(audx.frameSize)
            val vad = audx.process(input, output)

            assertTrue(vad in 0f..1f, "vad probability out of range: $vad")
        }
    }

    @Test
    fun processRejectsWrongFrameSize() {
        Audx(sampleRate = 16000).use { audx ->
            val wrong = ShortArray(audx.frameSize + 1)
            val output = ShortArray(audx.frameSize)
            assertFailsWith<IllegalArgumentException> { audx.process(wrong, output) }
        }
    }

    @Test
    fun closeIsIdempotentAndGuardsProcess() {
        val audx = Audx(sampleRate = 16000)
        val frame = ShortArray(audx.frameSize)
        audx.close()
        audx.close() // second close must be a no-op
        assertTrue(audx.isClosed())
        assertFailsWith<IllegalStateException> { audx.process(frame, frame) }
    }

    @Test
    fun constructorRejectsInvalidArguments() {
        assertFailsWith<IllegalArgumentException> { Audx(sampleRate = 0) }
        assertFailsWith<IllegalArgumentException> { Audx(sampleRate = -16000) }
        assertFailsWith<IllegalArgumentException> { Audx(resampleQuality = QUALITY_MAX + 1) }
    }

    @Test
    fun frameSizeMatchesSampleRate() {
        Audx(sampleRate = 48000).use { a48 ->
            Audx(sampleRate = 16000).use { a16 ->
                assertEquals(
                    a48.frameSize / 3,
                    a16.frameSize,
                    "frame size should scale linearly with sample rate",
                )
            }
        }
    }
}
