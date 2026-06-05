package dev.rizukirr.audx

const val FRAME_RATE: Int = 48_000
const val QUALITY_DEFAULT: Int = 4
const val QUALITY_VOIP = 3
const val QUALITY_MIN: Int = 0
const val QUALITY_MAX: Int = 10

expect class Audx(
    sampleRate: Int = FRAME_RATE,
    resampleQuality: Int = QUALITY_DEFAULT,
) : AutoCloseable {
    val sampleRate: Int
    val frameSize: Int

    fun process(input: ShortArray, output: ShortArray): Float
    fun isClosed(): Boolean
    override fun close()
}
