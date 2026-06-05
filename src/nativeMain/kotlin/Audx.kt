@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.rizukirr.audx

import cnames.structs.AudxState
import dev.rizukirr.audx.cinterop.audx_calculate_frame_sample
import dev.rizukirr.audx.cinterop.audx_create
import dev.rizukirr.audx.cinterop.audx_destroy
import dev.rizukirr.audx.cinterop.audx_process_int
import kotlin.concurrent.AtomicInt
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned

actual class Audx actual constructor(
    actual val sampleRate: Int,
    resampleQuality: Int
) : AutoCloseable {

    init {
        require(sampleRate > 0) { "sampleRate must be positive (got $sampleRate)" }
        require(resampleQuality in QUALITY_MIN..QUALITY_MAX) {
            "resampleQuality must be in $QUALITY_MIN..$QUALITY_MAX (got $resampleQuality)"
        }
    }

    private val state: CPointer<AudxState> =
        audx_create(null, sampleRate.toUInt(), resampleQuality)
            ?: error("audx_create returned NULL (rate=$sampleRate, quality=$resampleQuality)")

    actual val frameSize: Int = audx_calculate_frame_sample(sampleRate.toUInt()).toInt()

    // 0 = open, 1 = closed; compareAndSet makes close() destroy-once under races.
    private val closed = AtomicInt(0)

    actual fun process(input: ShortArray, output: ShortArray): Float {
        check(closed.value == 0) { "Audx is closed" }
        require(input.size == frameSize) {
            "input must be $frameSize samples (got ${input.size})"
        }
        require(output.size == frameSize) {
            "output must be $frameSize samples (got ${output.size})"
        }

        val vad = input.usePinned { pinnedIn ->
            output.usePinned { pinnedOut ->
                audx_process_int(state, pinnedIn.addressOf(0), pinnedOut.addressOf(0))
            }
        }
        check(vad >= 0f) { "audx_process_int failed (returned $vad)" }
        return vad
    }

    actual fun isClosed(): Boolean = closed.value != 0

    actual override fun close() {
        if (!closed.compareAndSet(0, 1)) return
        audx_destroy(state)
    }
}
