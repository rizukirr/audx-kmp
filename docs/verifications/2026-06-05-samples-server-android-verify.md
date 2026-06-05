# Verification Report — samples (Ktor denoise server + Android recorder app)

**Date:** 2026-06-05
**Spec:** docs/specs/2026-06-05-samples-server-android-design.md
**Plan:** docs/plans/2026-06-05-samples-server-android.md
**Commit verified:** b778333 (branch `vibe/samples-server-android`, base `main`)
**Mode:** critical-requirements 3-pass (R1–R6), single-pass (R7–R12) — chosen by user for cost control. Single-pass verdicts are weaker evidence.

## Repo-level checks

- Tests (server): pass — `cd samples/server && ./gradlew clean test` → exit 0
  ```
  18:38:57.379 INFO  io.ktor.test - saved /tmp/recordings12089609318697739044/denoised-20260605-183857-377.wav
  18:38:57.394 INFO  io.ktor.test - 200 OK: POST - /denoise in 128ms
  18:38:57.407 INFO  io.ktor.test - 200 OK: GET - /health in 1ms
  > Task :test

  BUILD SUCCESSFUL in 2s
  6 actionable tasks: 6 executed
  ```
  JUnit XML: `tests="4"` (WavTest) + `tests="2"` (DenoiserTest) + `tests="3"` (ApplicationTest) = 9 tests, `failures="0" errors="0"` in all three.
- Build (Android): pass — `cd samples/android-app && ./gradlew :app:assembleDebug` → exit 0
  ```
  > Task :app:assembleDebug UP-TO-DATE

  BUILD SUCCESSFUL in 630ms
  37 actionable tasks: 37 up-to-date
  ```
- Type checker / linter: N/A as separate steps — Kotlin compilation is part of both builds above (compile failure = build failure).
- `git status`:
  ```
  (clean — no output from git status --porcelain)
  ```
- `git log --oneline main..HEAD`: 29 commits, `13e39a2` (feat(samples): scaffold server sample project) … `b778333` (chore: complete Task 14 — samples README + final sweep). Includes one authorized plan repair: `3f5b20d plan: bump compileSdk to 36 (activity-compose 1.13.0 requires it)`.
- Surgical-diff pass: **clean** — auditor mapped all 32 changed files to plan tasks 1–14; plan-file changes are checkbox progressions plus the authorized compileSdk amendment. Zero orphans.

## Requirements

### R1 (critical). "`POST /denoise` — body: `audio/wav` bytes (16 kHz, mono, PCM-16). Server parses the WAV header, denoises the PCM, writes `recordings/denoised-yyyyMMdd-HHmmss.wav`, responds `200` with JSON `{"savedAs": "<filename>", "frames": N}`." + "`GET /health` — returns `ok`"
- Passes: partial / yes / yes → **disagreement, escalated to user, resolved: satisfied**
- Disagreement detail: implementation writes `denoised-yyyyMMdd-HHmmss-SSS.wav` (milliseconds appended). User accepted the deviation (collision avoidance); spec amended in place.
- Evidence:
  - `Application.kt:52` — `call.respond(DenoiseResponse(savedAs = file.name, frames = denoised.frames))`
  - Test output: `saved /tmp/recordings…/denoised-20260605-183857-377.wav`, `200 OK: POST - /denoise`, `200 OK: GET - /health`, `400 Bad Request: POST - /denoise`
  - ApplicationTest 3/3 pass (asserts `"savedAs"` in body, `denoised-*.wav` filename, `ok` health body)

### R2 (critical). "Demonstrate the offline denoise loop (`Audx.use` + frame-size chunking) in two contexts."
- Passes: yes / yes / yes — **satisfied**
- Evidence: `samples/server/...{/server}/Denoiser.kt:13-23` and `samples/android-app/...{/app}/Denoiser.kt:10-23` — `Audx(sampleRate = sampleRate).use { ... }`, `frameSize` chunk loop, `audx.process(inFrame, outFrame)`; DenoiserTest asserts `frames == 7` for 1000 samples @16 kHz (ceil(1000/160)), 2/2 pass.

### R3 (critical). "Audio format everywhere: **16 kHz, mono, 16-bit PCM WAV**."
- Passes: yes / yes / yes — **satisfied**
- Evidence: `Recorder.kt:8` `const val SAMPLE_RATE = 16_000` + `CHANNEL_IN_MONO`/`ENCODING_PCM_16BIT`; `Wav.kt:40-41` rejects non-mono/non-16-bit; WAV writer emits PCM/mono/16-bit header; WavTest 4/4 pass incl. `rejects stereo`.

### R4 (critical). "Show real-world consumption of `dev.rizukirr:audx-kmp:0.1.0-SNAPSHOT` from `mavenLocal()` on both desktop JVM (bundled JNI shim) and Android (`jniLibs/<abi>/libaudx_jni.so`)."
- Passes: yes / yes / yes — **satisfied**
- Evidence: `mavenLocal()` in both settings.gradle.kts (server:10, android:12); `implementation("dev.rizukirr:audx-kmp:0.1.0-SNAPSHOT")` in both build files (server:19, app:44); both builds resolve and pass; server tests exercise the bundled JNI shim natively.

### R5 (critical). "Android shims come from `audx-realtime/libs/{arm64-v8a,x86_64}/libaudx_jni.so` (already built), copied into `app/src/main/jniLibs/`."
- Passes: yes / yes / yes — **satisfied**
- Evidence: md5 `19af8bef…` (arm64-v8a) and `b1289a13…` (x86_64) identical between source and jniLibs copies; APK contains `lib/arm64-v8a/libaudx_jni.so` (14843256 B) and `lib/x86_64/libaudx_jni.so` (14890224 B); zero `natives/` resource entries (desktop shims excluded).

### R6 (critical). Testing: "Server: unit tests for `Wav` round-trip and `Denoiser` …" + "Android: `./gradlew assembleDebug` must pass"
- Passes: yes / yes / yes — **satisfied**
- Evidence: 9/9 server tests pass (exit 0); `:app:assembleDebug` exit 0 BUILD SUCCESSFUL.

### R7 (non-goal, single-pass). "Real-time/streaming denoise during recording" must NOT happen
- Pass: yes — **satisfied** — `MainViewModel.kt:42-50`: `denoise(...)` invoked only after `recorder.record()` returns; Recorder.kt has no Audx reference.

### R8 (non-goal, single-pass). "Authentication, TLS, or production-grade server concerns" must NOT be added
- Pass: yes — **satisfied** — grep for ssl/tls/auth/Authorization across server main sources: 0 matches; plain HTTP `embeddedServer(Netty, port = 8080, host = "0.0.0.0")`.

### R9 (non-goal, single-pass). "Returning denoised audio from the server to the client (server saves only)" — response must NOT contain audio
- Pass: yes — **satisfied** — only success response is the small JSON `DenoiseResponse`; no respondBytes/respondFile anywhere.

### R10 (non-goal, single-pass). "iOS/desktop client samples" must NOT exist
- Pass: yes — **satisfied** — `ls samples/` → `android-app`, `README.md`, `server` only.

### R11 (single-pass). "Two **standalone** Gradle projects (own settings, own wrapper)"
- Pass: yes — **satisfied** — both samples have own settings.gradle.kts + gradlew + gradle/wrapper; root settings.gradle.kts does not include them.

### R12 (single-pass). Error handling: server 400/500 + logged; app surfaces permission-denied / UnsatisfiedLinkError / upload failures in status line
- Pass: yes — **satisfied** — `400 Bad Request` evidenced in test run; `Application.kt:42` BadRequest path; `MainViewModel.kt` catch-Throwable → `status = "Error: …"`, `permissionDenied()` → "Microphone permission denied", upload catch → "Upload failed: …"; `MainActivity.kt` launcher else-branch calls `vm.permissionDenied()` and `Text(vm.status)` renders it.

### Untestable (documented proxies)
- "Play Raw vs Play Denoised — denoised should have audibly less background noise" and on-device record/upload flow: **untestable** in this environment (requires a physical device/emulator + human listening). Proxy evidence: APK builds and contains the shims (R5), the identical denoise code path is exercised natively by server tests (R2), end-to-end HTTP path proven by ApplicationTest + live `/health` smoke. Final on-device confirmation listed in the plan's "Manual verification" section for the user.

## Disagreements

- R1 filename format — resolved by user during verification: accepted `-SSS` suffix; spec amended (this commit). No open disagreements remain.

## Overall verdict

**ready** — all critical requirements satisfied (3/3 passes), all single-pass requirements satisfied, repo-level checks pass (exit 0, clean tree), surgical-diff clean, sole disagreement resolved by user decision and recorded. On-device audio quality check remains a documented manual step.
