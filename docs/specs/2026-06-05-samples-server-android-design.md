---
title: samples — Ktor denoise server + Android recorder app
date: 2026-06-05
status: approved
---

# samples — Ktor denoise server + Android recorder app — Design

## Problem

audx-kmp has no consumer-facing examples. We want two sample projects under
`samples/` that demonstrate the published artifact end-to-end:

1. **server** — a JVM HTTP server that receives raw audio, denoises it with
   audx, and saves the result to its local disk.
2. **android-app** — an Android app that records audio, denoises it locally
   with the same library, lets the user play raw vs denoised audio, and can
   upload the raw recording to the server.

## Goals

- Show real-world consumption of `dev.rizukirr:audx-kmp:0.1.0-SNAPSHOT` from
  `mavenLocal()` on both desktop JVM (bundled JNI shim) and Android
  (`jniLibs/<abi>/libaudx_jni.so`).
- Demonstrate the offline denoise loop (`Audx.use` + frame-size chunking) in
  two contexts.
- Keep each sample copy-paste friendly as a standalone project.

## Non-goals

- Real-time/streaming denoise during recording (offline pass after stop).
- Authentication, TLS, or production-grade server concerns.
- Returning denoised audio from the server to the client (server saves only).
- iOS/desktop client samples.

## Constraints

- Library is consumed from **mavenLocal** (`./gradlew publishToMavenLocal`
  already run by the user).
- Audio format everywhere: **16 kHz, mono, 16-bit PCM WAV**. Audx resamples
  internally to its 48 kHz engine.
- Android shims come from `audx-realtime/libs/{arm64-v8a,x86_64}/libaudx_jni.so`
  (already built), copied into `app/src/main/jniLibs/`.
- Two **standalone** Gradle projects (own settings, own wrapper) — chosen by
  the user over a single two-module samples build.

## Approach

**Pushback recorded:** an Android-only sample (no server) was offered as a
simpler framing; the user explicitly kept the two-project framing — the server
leg demonstrates the desktop jvm/JNI artifact and a realistic offload pattern.

### Layout

```
samples/
├── server/                  # standalone Kotlin/JVM + Ktor project
│   ├── settings.gradle.kts  # mavenLocal() + mavenCentral()
│   ├── build.gradle.kts
│   ├── gradle/ + gradlew
│   └── src/main/kotlin/...
└── android-app/             # standalone Android project
    ├── settings.gradle.kts  # google() + mavenCentral() + mavenLocal()
    ├── build.gradle.kts
    ├── gradle/ + gradlew
    └── app/
        ├── build.gradle.kts
        └── src/main/...     # incl. jniLibs/{arm64-v8a,x86_64}/libaudx_jni.so
```

### Wire protocol

- `POST /denoise` — body: `audio/wav` bytes (16 kHz, mono, PCM-16). Server
  parses the WAV header, denoises the PCM, writes
  `recordings/denoised-yyyyMMdd-HHmmss.wav`, responds `200` with JSON
  `{"savedAs": "<filename>", "frames": N}`.
- `GET /health` — returns `ok`, used by the phone to check connectivity.
- Raw bytes (not multipart) to keep both sides minimal.

### Server (`samples/server`)

- Kotlin/JVM, Ktor (Netty), kotlinx-serialization for the JSON response.
- `Wav.kt` — minimal WAV read/write: `parseWav(bytes)` → `Pcm(sampleRate,
  samples: ShortArray)` validating PCM-16 mono; `writeWav(file, sampleRate,
  samples)`.
- `Denoiser.kt` — pads the tail to a whole frame, runs frame-size chunks
  through one `Audx(sampleRate).use { ... }`, returns denoised samples.
- `Application.kt` — Ktor module wiring the routes; saves into `recordings/`
  next to the server working dir; listens on `0.0.0.0:8080`.
- Concurrency: one `Audx` per request, created/closed in the handler — no
  shared native state.
- Errors: malformed/unsupported WAV → 400 with message; audx failure
  (`IllegalStateException`) → 500; file IO error → 500. All logged.

### Android app (`samples/android-app`)

- Single `:app` module, Kotlin, Jetpack Compose (Material 3), minSdk 26,
  compile/target 35, ViewModel + coroutines, Ktor client for upload.
- `WavFile.kt` — same minimal WAV read/write (own copy; projects standalone).
- `Recorder` — `AudioRecord` 16 kHz/mono/PCM-16, accumulates on a background
  coroutine, writes `raw.wav` to `getExternalFilesDir(null)` on stop.
- `Denoiser` — offline pass identical in shape to the server's; writes
  `denoised.wav`.
- `Player` — `MediaPlayer` over either file; stops previous playback first.
- `Uploader` — Ktor client `POST <serverUrl>/denoise` with raw.wav bytes;
  surfaces the JSON reply.
- `MainViewModel` — state machine `Idle → Recording → Processing →
  Ready(raw, denoised)` + upload status.
- UI (one screen): server URL field, Record/Stop button, Play Raw, Play
  Denoised, Upload Raw buttons, status line. Play/upload disabled until a
  recording exists.
- Manifest: `RECORD_AUDIO` (runtime request), `INTERNET`,
  `usesCleartextTraffic="true"` for plain-HTTP LAN.
- Errors: permission denied, `UnsatisfiedLinkError` (missing jniLibs), and
  upload failures all surface in the status line.

## Alternatives considered

- **One samples build with two modules** — single wrapper/catalog, open once
  in Android Studio. Rejected by user in favor of copy-paste-friendly
  standalone projects.
- **Samples as subprojects of the root library build** — drags AGP into the
  library build; rejected with the mavenLocal decision.
- **Plain JDK HttpServer / Spring Boot** for the server — rejected for Ktor
  (idiomatic Kotlin, right weight for a sample).
- **48 kHz capture** — library-native rate but 3× larger files; 16 kHz chosen.
- **Real-time denoise during recording** — more threading complexity; offline
  pass after stop chosen.

## Testing

- Server: unit tests for `Wav` round-trip and `Denoiser` (synthetic sine WAV;
  assert sample counts and that audx processes without error). Manual
  end-to-end: `curl --data-binary @test.wav http://localhost:8080/denoise`.
- Android: `./gradlew assembleDebug` must pass; record → play raw/denoised →
  upload verified manually on device/emulator (x86_64 shim covers emulator).

## Open questions

None.
