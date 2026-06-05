package dev.rizukirr.audx

import java.nio.file.Files

actual class Audx actual constructor(
    actual val sampleRate: Int,
    resampleQuality: Int,
) : AutoCloseable {

    init {
        require(sampleRate > 0) { "sampleRate must be positive (got $sampleRate)" }
        require(resampleQuality in QUALITY_MIN..QUALITY_MAX) {
            "resampleQuality must be in $QUALITY_MIN..$QUALITY_MAX (got $resampleQuality)"
        }
    }

    /** Guards handle lifecycle: process()/close() racing would use-after-free the C state. */
    private val lock = Any()

    private val handle: Long = nativeCreate(sampleRate, resampleQuality)
        .takeIf { it != 0L }
        ?: error("audx_create returned NULL (rate=$sampleRate, quality=$resampleQuality)")

    actual val frameSize: Int = nativeFrameSize(handle)

    private var closed: Boolean = false

    actual fun process(input: ShortArray, output: ShortArray): Float {
        require(input.size == frameSize) {
            "input must be $frameSize samples (got ${input.size})"
        }
        require(output.size == frameSize) {
            "output must be $frameSize samples (got ${output.size})"
        }

        synchronized(lock) {
            check(!closed) { "Audx is closed" }
            val vad = nativeProcess(handle, input, output)
            check(vad >= 0f) { "audx_process_int failed (returned $vad)" }
            return vad
        }
    }

    actual fun isClosed(): Boolean = synchronized(lock) { closed }

    actual override fun close() {
        synchronized(lock) {
            if (closed) return
            closed = true
            nativeDestroy(handle)
        }
    }

    companion object {
        // Must be declared before the init block: initializers run in
        // declaration order, and loadNativeLibrary() reads osName.
        private val osName: String = System.getProperty("os.name").lowercase()

        init {
            loadNativeLibrary()
        }

        /**
         * Loads libaudx_jni. The bundled resource is preferred: it is built in
         * lockstep with this class, so it can never be missing a symbol. Only
         * platforms we don't bundle for (e.g. Android, where the app supplies
         * jniLibs/<abi>/libaudx_jni.so) fall back to System.loadLibrary.
         */
        private fun loadNativeLibrary() {
            val fileName = libFileName()
            val resource = "/natives/${platformId()}/$fileName"
            val stream = Audx::class.java.getResourceAsStream(resource)

            if (stream == null) {
                System.loadLibrary("audx_jni")
                return
            }

            val suffix = "." + fileName.substringAfterLast('.')
            val tmp = Files.createTempFile("audx_jni", suffix)
            try {
                stream.use {
                    Files.copy(it, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                }
                @Suppress("UnsafeDynamicallyLoadedCode")
                System.load(tmp.toAbsolutePath().toString())
                tmp.toFile().deleteOnExit()
            } catch (e: Throwable) {
                Files.deleteIfExists(tmp)
                throw e
            }
        }

        private fun platformId(): String {
            val arch = when (val a = System.getProperty("os.arch").lowercase()) {
                "amd64", "x86_64" -> "x64"
                "aarch64", "arm64" -> "arm64"
                else -> a
            }
            val osId = when {
                osName.contains("linux") -> "linux"
                osName.contains("windows") -> "windows"
                osName.contains("mac") || osName.contains("darwin") -> "macos"
                else -> osName
            }
            return "$osId-$arch"
        }

        private fun libFileName(): String = when {
            osName.contains("windows") -> "audx_jni.dll"
            osName.contains("mac") || osName.contains("darwin") -> "libaudx_jni.dylib"
            else -> "libaudx_jni.so"
        }

        @JvmStatic
        private external fun nativeCreate(sampleRate: Int, resampleQuality: Int): Long

        @JvmStatic
        private external fun nativeFrameSize(ptr: Long): Int

        @JvmStatic
        private external fun nativeProcess(ptr: Long, input: ShortArray, output: ShortArray): Float

        @JvmStatic
        private external fun nativeDestroy(ptr: Long)
    }
}
