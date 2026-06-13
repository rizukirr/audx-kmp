# audx-kmp

Kotlin Multiplatform wrapper for [audx-realtime](https://github.com/rizukirr/audx-realtime) — real-time audio denoising + VAD.

## Supported platforms

| Platform | Gradle target | Bridge | Status |
|---|---|---|---|
| Linux x64 (native binaries) | `linuxX64` | cinterop, link time | ✅ tested |
| Linux x64 (JVM / servers) | `jvm` | JNI, bundled `.so` | ✅ tested (zero-config) |
| Android apps (ART) | `jvm` + app-supplied `jniLibs/` | JNI | ✅ tested on device (arm64-v8a) and emulator (x86_64) |
| Android (Kotlin/Native) | `androidNativeArm64` / `androidNativeX64` | cinterop | ⚠️ compiles, never executed |
| Windows x64 (native) | `mingwX64` | cinterop | ⚠️ compiles, never executed on Windows |
| Windows / macOS (JVM) | `jvm` | JNI | ❌ no `audx_jni.dll` / `.dylib` bundled yet |
| macOS / iOS (native) | — | — | ❌ no targets (needs a macOS build host) |
| JS / wasm | — | — | ❌ out of scope |

> WARNING: This project is not ready for production yet

## Artifact flow

Published to mavenLocal as `dev.rizukirr:audx-kmp:0.1.0-SNAPSHOT` (run `./gradlew publishToMavenLocal`):

```kotlin
repositories { mavenLocal() }
dependencies { implementation("dev.rizukirr:audx-kmp:0.1.0-SNAPSHOT") }
```

One `Audx` instance per stream: create once, `process()` one 10ms frame per tick, `close()` when the stream ends.

```kotlin
Audx(sampleRate = 16000).use { audx ->
    val output = ShortArray(audx.frameSize)   // 160 samples at 16 kHz
    audioChunks.collect { input ->
        val vad = audx.process(input, output) // output = denoised frame
        if (audx.isSpeaking()) send(output)   // debounced VAD gate
    }
}
```

### Voice activity detection

```kotlin
audx.lastVad                    // raw probability 0..1 of the newest frame (UI meters)
audx.isSpeaking()               // debounced: threshold 0.5, 200ms hangover
audx.isSpeaking(0.7f, 400)      // stricter threshold, longer hold
audx.isSpeaking(hangoverMs = 0) // raw last-frame comparison, no debounce
```

`isSpeaking` is true if any frame within the last `hangoverMs` of processed audio
exceeded the threshold — onset is instant, release waits out breaths and
inter-word gaps, so the state doesn't flicker mid-sentence.

### Android

The jar bundles only desktop natives; apps supply the per-ABI shims
(built by `audx-realtime/scripts/android.sh` into `libs/<abi>/`):

```
app/src/main/jniLibs/arm64-v8a/libaudx_jni.so
app/src/main/jniLibs/x86_64/libaudx_jni.so
```

## Samples

- [`samples/android-app/`](samples/android-app/) — record → denoise → play/upload,
  with live VAD meter + debounced speaking indicator. Device-verified.
- [`samples/server/`](samples/server/) — JVM server consuming the same artifact.
- [`sample-android/`](sample-android/) — minimal VAD-only demo module
  (`./gradlew :sample-android:assembleDebug`).

## Maintainers

All C code lives in `audx-realtime`; this repo vendors prebuilt artifacts:

- `native/libs/<target>/libaudx.a` → cinterop (link time)
- `src/jvmMain/resources/natives/<os>-<arch>/` → bundled into the jvm jar; extracted
  once to `~/.cache/audx-kmp/` (content-hash keyed) and reused across JVM starts

Refresh the desktop shim after C changes:

```bash
cd ../audx-realtime
cmake -B build/linux-x64 -DCMAKE_BUILD_TYPE=Release -DAUDX_BUILD_JNI=ON
cmake --build build/linux-x64 --target audx_jni -j$(nproc)
cp build/linux-x64/lib/libaudx_jni.so ../audx-kmp/src/jvmMain/resources/natives/linux-x64/
strip ../audx-kmp/src/jvmMain/resources/natives/linux-x64/libaudx_jni.so
```

JNI name contract (`src/jvmMain/kotlin/Audx.kt` ↔ `audx-realtime/src/audx_jni.c`):
the four `@JvmStatic external` methods bind to `Java_dev_rizukirr_audx_Audx_native{Create,FrameSize,Process,Destroy}`.
Renaming the Kotlin package/class or methods breaks resolution at runtime — change both sides together.

## Verify

```bash
./gradlew jvmTest                      # JNI smoke + VAD unit tests (12)
./gradlew runDebugExecutableLinuxX64   # native sanity run
./gradlew :sample-android:assembleDebug
```
