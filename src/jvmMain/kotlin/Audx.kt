package dev.rizukirr.audx

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.security.MessageDigest

actual class Audx actual constructor(
    actual val sampleRate: Int,
    resampleQuality: Int,
) : AutoCloseable {

    init {
        validateCreateArgs(sampleRate, resampleQuality)
    }

    /** Guards handle lifecycle: process()/close() racing would use-after-free the C state. */
    private val lock = Any()

    internal actual val vadRing: VadRing = VadRing()

    actual val lastVad: Float get() = vadRing.last

    private val handle: Long = nativeCreate(sampleRate, resampleQuality)
        .takeIf { it != 0L }
        ?: error("audx_create returned NULL (rate=$sampleRate, quality=$resampleQuality)")

    actual val frameSize: Int = nativeFrameSize(handle)

    private var closed: Boolean = false

    actual fun process(input: ShortArray, output: ShortArray): Float {
        validateFrame(frameSize, input, output)
        synchronized(lock) {
            check(!closed) { "Audx is closed" }
            val vad = checkVadResult(nativeProcess(handle, input, output))
            vadRing.push(vad)
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
         *
         * The resource is extracted once to a per-user cache keyed by content
         * hash; subsequent JVM starts reuse the cached file instead of paying
         * the ~14MB copy again.
         */
        private fun loadNativeLibrary() {
            val fileName = libFileName()
            val resource = "/natives/${platformId()}/$fileName"
            val stream = Audx::class.java.getResourceAsStream(resource)

            if (stream == null) {
                System.loadLibrary("audx_jni")
                return
            }

            val bytes = stream.use { it.readBytes() }
            val hash = MessageDigest.getInstance("SHA-256").digest(bytes)
                .joinToString("") { "%02x".format(it) }
                .take(16)
            val suffix = fileName.substringAfterLast('.')

            val cached = cacheDir().resolve("audx_jni-$hash.$suffix")
            if (!Files.exists(cached) || Files.size(cached) != bytes.size.toLong()) {
                val tmp = Files.createTempFile(cached.parent, "audx_jni", ".tmp")
                try {
                    Files.write(tmp, bytes)
                    Files.move(
                        tmp, cached,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE,
                    )
                } catch (e: Throwable) {
                    Files.deleteIfExists(tmp)
                    throw e
                }
            }

            @Suppress("UnsafeDynamicallyLoadedCode")
            System.load(cached.toAbsolutePath().toString())
        }

        private fun cacheDir(): Path {
            val home = System.getProperty("user.home")
            val dir = if (home.isNullOrBlank()) {
                Paths.get(System.getProperty("java.io.tmpdir"), "audx-kmp")
            } else {
                Paths.get(home, ".cache", "audx-kmp")
            }
            return Files.createDirectories(dir)
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
