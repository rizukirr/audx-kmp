package dev.rizukirr.audx

import java.nio.file.Files

actual class Audx actual constructor(
    actual val sampleRate: Int,
    resampleQuality: Int,
) : AutoCloseable {

    private val handle: Long = nativeCreate(sampleRate, resampleQuality)
        .takeIf { it != 0L }
        ?: error("audx_create returned NULL (rate=$sampleRate, quality=$resampleQuality)")

    actual val frameSize: Int = nativeFrameSize(sampleRate)

    private var closed: Boolean = false

    actual fun process(input: ShortArray, output: ShortArray): Float {
        check(!closed) { "Audx is closed" }
        require(input.size == frameSize) {
            "input must be $frameSize samples (got ${input.size})"
        }
        require(output.size == frameSize) {
            "output must be $frameSize samples (got ${output.size})"
        }

        return nativeProcess(handle, input, output)
    }

    actual fun isClosed(): Boolean = closed

    actual override fun close() {
        if (closed) return
        closed = true
        nativeDestroy(handle)
    }

    companion object {
        init {
            loadNativeLibrary()
        }

        /**
         * Loads libaudx_jni: first via System.loadLibrary (java.library.path,
         * Android jniLibs), then by extracting the bundled resource for the
         * current OS/arch to a temp file.
         */
        private fun loadNativeLibrary() {
            try {
                System.loadLibrary("audx_jni")
                return
            } catch (_: UnsatisfiedLinkError) {
                // fall through to bundled resource
            }

            val resource = "/natives/${platformId()}/${libFileName()}"
            val stream = Audx::class.java.getResourceAsStream(resource)
                ?: throw UnsatisfiedLinkError(
                    "audx_jni not found on java.library.path and no bundled native for $resource"
                )

            val tmp = Files.createTempFile("audx_jni", libSuffix())
            stream.use { Files.copy(it, tmp, java.nio.file.StandardCopyOption.REPLACE_EXISTING) }
            tmp.toFile().deleteOnExit()
            @Suppress("UnsafeDynamicallyLoadedCode")
            System.load(tmp.toAbsolutePath().toString())
        }

        private fun platformId(): String {
            val os = System.getProperty("os.name").lowercase()
            val arch = when (val a = System.getProperty("os.arch").lowercase()) {
                "amd64", "x86_64" -> "x64"
                "aarch64", "arm64" -> "arm64"
                else -> a
            }
            val osId = when {
                os.contains("linux") -> "linux"
                os.contains("windows") -> "windows"
                os.contains("mac") || os.contains("darwin") -> "macos"
                else -> os
            }
            return "$osId-$arch"
        }

        private fun libFileName(): String {
            val os = System.getProperty("os.name").lowercase()
            return when {
                os.contains("windows") -> "audx_jni.dll"
                os.contains("mac") || os.contains("darwin") -> "libaudx_jni.dylib"
                else -> "libaudx_jni.so"
            }
        }

        private fun libSuffix(): String {
            val os = System.getProperty("os.name").lowercase()
            return when {
                os.contains("windows") -> ".dll"
                os.contains("mac") || os.contains("darwin") -> ".dylib"
                else -> ".so"
            }
        }

        @JvmStatic
        private external fun nativeCreate(sampleRate: Int, resampleQuality: Int): Long

        @JvmStatic
        private external fun nativeFrameSize(sampleRate: Int): Int

        @JvmStatic
        private external fun nativeProcess(ptr: Long, input: ShortArray, output: ShortArray): Float

        @JvmStatic
        private external fun nativeDestroy(ptr: Long)
    }
}
