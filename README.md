# audx-kmp

Kotlin Multiplatform wrapper for [audx-realtime](../audx-realtime) — real-time audio denoising + VAD.

One common API (`dev.rizukirr.audx.Audx`), five targets, two bridging mechanisms:

| Target | Bridge | Native artifact | Bound at |
|---|---|---|---|
| `linuxX64` | cinterop | `native/libs/linux_x64/libaudx.a` | link time |
| `androidNativeArm64` | cinterop | `native/libs/android_arm64/libaudx.a` | link time |
| `androidNativeX64` | cinterop | `native/libs/android_x64/libaudx.a` | link time |
| `mingwX64` | cinterop | `native/libs/mingw_x64/libaudx.a` | link time |
| `jvm` | JNI | `libaudx_jni.so` / `.dll` / `.dylib` | runtime |

## Artifact flow

All C code lives in `audx-realtime`; this repo only vendors prebuilt artifacts:

- **Static libs** (`libaudx.a` per target) → `native/libs/<target>/`, consumed by cinterop (`src/nativeInterop/cinterop/audx.def`).
- **JNI shim** (`libaudx_jni.so`) → `src/jvmMain/resources/natives/<os>-<arch>/`, bundled into the jvm jar.

To refresh the desktop JNI shim after changing `audx-realtime`:

```bash
cd ../audx-realtime
cmake -B build/linux-x64 -DCMAKE_BUILD_TYPE=Release -DAUDX_BUILD_JNI=ON
cmake --build build/linux-x64 --target audx_jni -j$(nproc)
cp build/linux-x64/lib/libaudx_jni.so ../audx-kmp/src/jvmMain/resources/natives/linux-x64/
strip ../audx-kmp/src/jvmMain/resources/natives/linux-x64/libaudx_jni.so
```

## JVM runtime loading

`Audx` (jvm) resolves the native library in order:

1. `System.loadLibrary("audx_jni")` — honors `-Djava.library.path`, and on Android picks up `jniLibs/<abi>/libaudx_jni.so` automatically.
2. Fallback: extract the bundled resource `natives/<os>-<arch>/` to a temp file and `System.load` it (zero-config for desktop/server JVMs on platforms we bundle).

### Android apps (JVM/ART)

The jar bundles only desktop natives. Android consumers add the per-ABI shims
(built by `audx-realtime/scripts/android.sh` into `libs/<abi>/`) to their app:

```
app/src/main/jniLibs/arm64-v8a/libaudx_jni.so
app/src/main/jniLibs/x86_64/libaudx_jni.so
```

### JNI surface

The contract between `src/jvmMain/kotlin/Audx.kt` and `audx-realtime/src/audx_jni.c`:

| Kotlin (`dev.rizukirr.audx.Audx`) | C export |
|---|---|
| `nativeCreate(sampleRate, resampleQuality): Long` | `Java_dev_rizukirr_audx_Audx_nativeCreate` |
| `nativeFrameSize(sampleRate): Int` | `Java_dev_rizukirr_audx_Audx_nativeFrameSize` |
| `nativeProcess(ptr, input, output): Float` | `Java_dev_rizukirr_audx_Audx_nativeProcess` |
| `nativeDestroy(ptr)` | `Java_dev_rizukirr_audx_Audx_nativeDestroy` |

Renaming the Kotlin package/class or the method names breaks symbol resolution
(`UnsatisfiedLinkError`) — change both sides together.

## Usage

One `Audx` instance per stream: create once, `process()` one frame per tick,
`close()` once when the stream ends.

```kotlin
Audx(sampleRate = 16000).use { audx ->
    val output = ShortArray(audx.frameSize)
    audioChunks.collect { input ->         // e.g. 160-sample chunks at 16 kHz
        val vad = audx.process(input, output)
        // output now holds the denoised frame; vad is speech probability 0..1
    }
}
```

## Verify

```bash
./gradlew jvmTest                      # JNI smoke tests (desktop)
./gradlew runDebugExecutableLinuxX64   # native sanity run
```
