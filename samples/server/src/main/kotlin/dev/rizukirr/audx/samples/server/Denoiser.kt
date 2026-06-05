package dev.rizukirr.audx.samples.server

import dev.rizukirr.audx.Audx

/** Result of an offline denoise pass. */
class Denoised(val samples: ShortArray, val frames: Int)

/**
 * Runs [samples] through audx one frame at a time. The tail is zero-padded to
 * a whole frame for processing and the result trimmed back to the input length.
 */
fun denoise(sampleRate: Int, samples: ShortArray): Denoised {
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
        return Denoised(output.copyOf(samples.size), frames)
    }
}
