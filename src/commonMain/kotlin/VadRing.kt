package dev.rizukirr.audx

/**
 * Fixed-capacity ring of the most recent per-frame VAD probabilities.
 *
 * Owns all window arithmetic behind [isSpeaking]. Pure Kotlin so the logic
 * is identical on every target and unit-testable without native code.
 */
internal class VadRing(private val capacity: Int = DEFAULT_CAPACITY) {

    private val values = FloatArray(capacity)
    private var next = 0   // write index
    private var count = 0  // frames recorded, saturates at capacity

    /** Probability from the most recent frame; 0f before any frame. */
    var last: Float = 0f
        private set

    fun push(vad: Float) {
        last = vad
        values[next] = vad
        next = (next + 1) % capacity
        if (count < capacity) count++
    }

    /** True if any of the newest [frames] recorded probabilities exceed [threshold]. */
    fun anyAbove(threshold: Float, frames: Int): Boolean {
        val window = minOf(frames, count)
        var idx = next
        repeat(window) {
            idx = (idx - 1 + capacity) % capacity
            if (values[idx] > threshold) return true
        }
        return false
    }

    companion object {
        /** 1 second of 10ms frames. */
        const val DEFAULT_CAPACITY = 100
    }
}
