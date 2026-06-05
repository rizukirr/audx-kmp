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

1. The bundled resource `natives/<os>-<arch>/`, extracted once to a per-user
   cache (`~/.cache/audx-kmp/audx_jni-<hash>.so`, keyed by content hash) and
   reused across JVM starts. Preferred because it is built in lockstep with the
   Kotlin facade — a stale shim on `java.library.path` can never shadow it.
2. Platforms with no bundled native (e.g. Android): `System.loadLibrary("audx_jni")`,
   which honors `-Djava.library.path` and Android's `jniLibs/<abi>/`.

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
| `nativeFrameSize(ptr): Int` | `Java_dev_rizukirr_audx_Audx_nativeFrameSize` |
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

### Voice activity detection

Every `process()` call returns the frame's speech probability and records it.
Two accessors expose it:

```kotlin
audx.lastVad                  // raw probability of the newest frame (UI meters)
audx.isSpeaking()             // debounced: threshold 0.5, 200ms hangover
audx.isSpeaking(0.7f, 400)    // stricter threshold, longer hold
audx.isSpeaking(hangoverMs = 0) // raw last-frame comparison, no debounce
```

`isSpeaking` is true if any frame within the last `hangoverMs` of processed
audio exceeded the threshold — onset is instant, release waits out short dips
(breaths, gaps between words), so the state doesn't flicker mid-sentence.

## Verify

```bash
./gradlew jvmTest                      # JNI smoke tests (desktop)
./gradlew runDebugExecutableLinuxX64   # native sanity run
```
