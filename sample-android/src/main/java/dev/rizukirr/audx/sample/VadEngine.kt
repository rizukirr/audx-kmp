package dev.rizukirr.audx.sample

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import dev.rizukirr.audx.Audx
import dev.rizukirr.audx.isSpeaking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class VadUiState(
    val isRecording: Boolean = false,
    val vad: Float = 0f,
    val rawSpeaking: Boolean = false,
    val debouncedSpeaking: Boolean = false,
    val error: String? = null,
)

/**
 * Owns one Audx instance and a microphone loop: reads one 10ms frame at a
 * time, denoises it, and publishes raw vs debounced VAD state for the UI.
 */
class VadEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val audx = Audx(sampleRate = SAMPLE_RATE)
    private var job: Job? = null

    val state = MutableStateFlow(VadUiState())

    @SuppressLint("MissingPermission")
    fun start() {
        if (job != null) return
        job = scope.launch(Dispatchers.IO) {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf * 4, audx.frameSize * 10),
            )
            val input = ShortArray(audx.frameSize)
            val output = ShortArray(audx.frameSize)
            try {
                check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord init failed" }
                record.startRecording()
                state.update { it.copy(isRecording = true, error = null) }
                while (isActive) {
                    // read can return short counts: accumulate exactly one frame
                    var filled = 0
                    while (filled < input.size) {
                        val n = record.read(input, filled, input.size - filled)
                        check(n > 0) { "AudioRecord read error: $n" }
                        filled += n
                    }
                    val vad = audx.process(input, output)
                    state.update {
                        it.copy(
                            vad = vad,
                            rawSpeaking = vad > 0.5f,
                            debouncedSpeaking = audx.isSpeaking(),
                        )
                    }
                }
            } catch (e: Exception) {
                state.update { it.copy(error = e.message ?: e.toString()) }
            } finally {
                runCatching { record.stop() }
                record.release()
                state.update {
                    it.copy(
                        isRecording = false,
                        vad = 0f,
                        rawSpeaking = false,
                        debouncedSpeaking = false,
                    )
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun destroy() {
        stop()
        scope.cancel()
        audx.close()
    }

    companion object {
        const val SAMPLE_RATE = 16_000
    }
}
