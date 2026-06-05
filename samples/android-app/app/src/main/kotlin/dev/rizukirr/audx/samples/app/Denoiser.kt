package dev.rizukirr.audx.samples.app

import dev.rizukirr.audx.Audx

/**
 * Offline denoise pass: zero-pads the tail to a whole frame, processes frame
 * by frame, trims back to the input length.
 */
fun denoise(sampleRate: Int, samples: ShortArray): ShortArray {
    Audx(sampleRate = sampleRate).use { audx ->
        val frameSize = audx.frameSize
        val frames = (samples.size + frameSize - 1) / frameSize
        val padded = samples.copyOf(frames * frameSize)
        val output = ShortArray(padded.size)
        val inFrame = ShortArray(frameSize)
        val outFrame = ShortArray(frameSize)
        for (i in 0 until frames) {
            padded.copyInto(inFrame, 0, i * frameSize, (i + 1) * frameSize)
            audx.process(inFrame, outFrame)
            outFrame.copyInto(output, i * frameSize)
        }
        return output.copyOf(samples.size)
    }
}
