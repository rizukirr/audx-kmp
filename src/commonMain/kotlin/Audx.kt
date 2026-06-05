package dev.rizukirr.audx

const val FRAME_RATE: Int = 48_000
const val QUALITY_DEFAULT: Int = 4
const val QUALITY_VOIP = 3
const val QUALITY_MIN: Int = 0
const val QUALITY_MAX: Int = 10

/** Duration of one audx frame in milliseconds (fixed by the C library). */
const val FRAME_MS: Int = 10

expect class Audx(
    sampleRate: Int = FRAME_RATE,
    resampleQuality: Int = QUALITY_DEFAULT,
) : AutoCloseable {
    val sampleRate: Int
    val frameSize: Int

    /** Speech probability from the most recent process() call; 0f before any frame. */
    val lastVad: Float

    internal val vadRing: VadRing

    fun process(input: ShortArray, output: ShortArray): Float
    fun isClosed(): Boolean
    override fun close()
}

/**
 * Debounced voice-activity state.
 *
 * True if any frame processed within the last [hangoverMs] of audio had a
 * speech probability above [threshold]. Onset is instant (one frame above
 * threshold flips the state true); release waits for [hangoverMs] of
 * consecutive sub-threshold audio. The window counts processed frames
 * (stream time), not wall-clock time. `hangoverMs = 0` degenerates to the
 * raw last-frame comparison.
 *
 * May lag a concurrent process() call by one frame; no stronger
 * cross-thread guarantee is made.
 */
fun Audx.isSpeaking(threshold: Float = 0.5f, hangoverMs: Int = 200): Boolean {
    val frames = (hangoverMs / FRAME_MS).coerceAtLeast(1)
    return vadRing.anyAbove(threshold, frames)
}

// Shared guards — every actual delegates here so the contract (and its
// error messages) cannot drift between platforms.

internal fun validateCreateArgs(sampleRate: Int, resampleQuality: Int) {
    require(sampleRate > 0) { "sampleRate must be positive (got $sampleRate)" }
    require(resampleQuality in QUALITY_MIN..QUALITY_MAX) {
        "resampleQuality must be in $QUALITY_MIN..$QUALITY_MAX (got $resampleQuality)"
    }
}

internal fun validateFrame(frameSize: Int, input: ShortArray, output: ShortArray) {
    require(input.size == frameSize) {
        "input must be $frameSize samples (got ${input.size})"
    }
    require(output.size == frameSize) {
        "output must be $frameSize samples (got ${output.size})"
    }
}

internal fun checkVadResult(vad: Float): Float {
    check(vad >= 0f) { "audx_process_int failed (returned $vad)" }
    return vad
}
