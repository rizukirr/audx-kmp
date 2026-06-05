@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package dev.rizukirr.audx

import cnames.structs.AudxState
import dev.rizukirr.audx.cinterop.audx_calculate_frame_sample
import dev.rizukirr.audx.cinterop.audx_create
import dev.rizukirr.audx.cinterop.audx_destroy
import dev.rizukirr.audx.cinterop.audx_process_int
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned

actual class Audx actual constructor(
    actual val sampleRate: Int,
    resampleQuality: Int
) : AutoCloseable {

    private val state: CPointer<AudxState> =
        audx_create(null, sampleRate.toUInt(), resampleQuality)
            ?: error("audx_create returned NULL (rate=$sampleRate, quality=$resampleQuality)")

    actual val frameSize: Int = audx_calculate_frame_sample(sampleRate.toUInt()).toInt()

    private var closed: Boolean = false

    actual fun process(input: ShortArray, output: ShortArray): Float {
        check(!closed){ "Audx is closed" }
        require(input.size == frameSize) {
            "input must be $frameSize samples (got ${input.size})"
        }
        require(output.size == frameSize) {
            "output must be $frameSize sampels (got ${output.size})"
        }

        return input.usePinned { pinnedIn ->
            output.usePinned { pinnedOut ->
                audx_process_int(state, pinnedIn.addressOf(0), pinnedOut.addressOf(0))
            }
        }
    }

    actual fun isClosed(): Boolean = closed

    actual override fun close() {
        if(closed) return
        closed = true
        audx_destroy(state)
    }


}

