package dev.rizukirr.audx

import kotlin.random.Random

fun main() {
    Audx(sampleRate = 16000).use { audx ->
        val input = ShortArray(audx.frameSize) {
            Random.nextInt(-3000, 3000).toShort()
        }
        val output = ShortArray(audx.frameSize)
        val vad = audx.process(input, output)
        println("frame: ${audx.frameSize} sampels, vad probability: $vad")
        println("first 5 in: ${input.take(5).joinToString()}")
        println("first 5 out: ${output.take(5).joinToString()}")
    }
    println("Audx closed cleanly")
}
