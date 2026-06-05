package dev.rizukirr.audx.samples.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.rizukirr.audx.Audx
import dev.rizukirr.audx.isSpeaking
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface UiState {
    data object Idle : UiState
    data object Recording : UiState
    data object Processing : UiState
    data class Ready(val rawFile: File, val denoisedFile: File) : UiState
}

/** Which recording is currently playing back, if any. */
enum class Track { RAW, DENOISED }

class MainViewModel(app: Application) : AndroidViewModel(app) {
    var state by mutableStateOf<UiState>(UiState.Idle)
        private set
    var status by mutableStateOf("Tap Record to start")
        private set
    var serverUrl by mutableStateOf("http://192.168.1.100:8080")

    /** Raw speech probability of the newest processed frame (live, while recording). */
    var vadProbability by mutableFloatStateOf(0f)
        private set

    /** Debounced isSpeaking() — holds through breaths and inter-word gaps. */
    var speaking by mutableStateOf(false)
        private set

    /** Non-null while a recording is playing back; drives the Play/Stop toggle. */
    var playing by mutableStateOf<Track?>(null)
        private set

    private val recorder = Recorder()
    private val player = Player()

    private fun outputDir(): File =
        checkNotNull(getApplication<Application>().getExternalFilesDir(null)) {
            "external storage unavailable"
        }

    fun startRecording() {
        if (state == UiState.Recording) return
        // Recording takes over the audio path: kill any active playback first.
        player.stop()
        playing = null
        state = UiState.Recording
        status = "Recording…"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Live VAD monitor: a second Audx fed frame-by-frame while the
                // batch recording accumulates; its denoised output is discarded.
                val samples = Audx(sampleRate = SAMPLE_RATE).use { monitor ->
                    recorder.record(newVadFeeder(monitor))
                }
                vadProbability = 0f
                speaking = false
                withContext(Dispatchers.Main) {
                    state = UiState.Processing
                    status = "Denoising…"
                }
                val rawFile = File(outputDir(), "raw.wav")
                writeWav(rawFile, SAMPLE_RATE, samples)
                val denoisedFile = File(outputDir(), "denoised.wav")
                writeWav(denoisedFile, SAMPLE_RATE, denoise(SAMPLE_RATE, samples))
                withContext(Dispatchers.Main) {
                    state = UiState.Ready(rawFile, denoisedFile)
                    status = "Ready — %.1f s recorded".format(samples.size / SAMPLE_RATE.toFloat())
                }
            } catch (t: Throwable) { // Throwable: UnsatisfiedLinkError = missing jniLibs
                withContext(Dispatchers.Main) {
                    vadProbability = 0f
                    speaking = false
                    state = UiState.Idle
                    status = "Error: ${t.message ?: t.javaClass.simpleName}"
                }
            }
        }
    }

    /**
     * Slices arbitrary-size recorder chunks into exact [Audx.frameSize] frames,
     * processes each through [monitor], and publishes the VAD state. Runs on
     * the recording thread; Compose snapshot state is safe to write here.
     */
    private fun newVadFeeder(monitor: Audx): (ShortArray) -> Unit {
        val frame = ShortArray(monitor.frameSize)
        val sink = ShortArray(monitor.frameSize)
        var filled = 0
        return { chunk ->
            var off = 0
            while (off < chunk.size) {
                val take = minOf(chunk.size - off, frame.size - filled)
                chunk.copyInto(frame, filled, off, off + take)
                filled += take
                off += take
                if (filled == frame.size) {
                    monitor.process(frame, sink)
                    filled = 0
                    vadProbability = monitor.lastVad
                    speaking = monitor.isSpeaking()
                }
            }
        }
    }

    fun stopRecording() = recorder.stop()

    fun permissionDenied() {
        status = "Microphone permission denied"
    }

    fun togglePlayRaw() = togglePlay(Track.RAW) { it.rawFile }

    fun togglePlayDenoised() = togglePlay(Track.DENOISED) { it.denoisedFile }

    /**
     * First tap plays [track]; tapping the same track again stops it. Starting
     * one track while the other plays switches over (Player stops the previous).
     */
    private fun togglePlay(track: Track, fileOf: (UiState.Ready) -> File) {
        val ready = state as? UiState.Ready ?: return
        if (playing == track) {
            player.stop()
            playing = null
            status = "Stopped"
            return
        }
        val file = fileOf(ready)
        status = try {
            player.play(file) {
                playing = null
                status = "Finished ${file.name}"
            }
            playing = track
            "Playing ${file.name}"
        } catch (t: Throwable) {
            playing = null
            "Playback failed: ${t.message}"
        }
    }

    fun uploadRaw() {
        val ready = state as? UiState.Ready ?: return
        viewModelScope.launch {
            status = "Uploading…"
            status = try {
                "Server: ${upload(serverUrl, ready.rawFile)}"
            } catch (t: Throwable) {
                "Upload failed: ${t.message ?: t.javaClass.simpleName}"
            }
        }
    }

    override fun onCleared() {
        playing = null
        player.stop()
    }
}
