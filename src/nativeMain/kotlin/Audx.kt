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
        validateCreateArgs(sampleRate, resampleQuality)
    }

    private val state: CPointer<AudxState> =
        audx_create(null, sampleRate.toUInt(), resampleQuality)
            ?: error("audx_create returned NULL (rate=$sampleRate, quality=$resampleQuality)")

    actual val frameSize: Int = audx_calculate_frame_sample(sampleRate.toUInt()).toInt()

    internal actual val vadRing: VadRing = VadRing()

    actual val lastVad: Float get() = vadRing.last

    // 0 = open, 1 = closed; compareAndSet makes close() destroy-once under races.
    private val closed = AtomicInt(0)

    actual fun process(input: ShortArray, output: ShortArray): Float {
        validateFrame(frameSize, input, output)
        check(closed.value == 0) { "Audx is closed" }

        val vad = input.usePinned { pinnedIn ->
            output.usePinned { pinnedOut ->
                audx_process_int(state, pinnedIn.addressOf(0), pinnedOut.addressOf(0))
            }
        }
        val checked = checkVadResult(vad)
        vadRing.push(checked)
        return checked
    }

    actual fun isClosed(): Boolean = closed.value != 0

    actual override fun close() {
        if (!closed.compareAndSet(0, 1)) return
        audx_destroy(state)
    }
}
