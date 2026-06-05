# Samples (Ktor denoise server + Android recorder app) Implementation Plan

> **For executing agents:** implement this plan task-by-task. Each step uses checkbox (`- [ ]`) syntax. Do not skip steps. Do not batch commits across tasks.

**Goal:** Two standalone sample projects under `samples/` — a Ktor JVM server that denoises uploaded WAV audio and saves it to disk, and an Android Compose app that records, denoises locally, plays raw vs denoised, and uploads raw audio to the server.

**Architecture:** Both samples consume `dev.rizukirr:audx-kmp:0.1.0-SNAPSHOT` from `mavenLocal()` (already published — verified: `~/.m2/repository/dev/rizukirr/audx-kmp-jvm/0.1.0-SNAPSHOT/audx-kmp-jvm-0.1.0-SNAPSHOT.jar` is 12.7 MB and bundles the desktop JNI shim). The server is a single Ktor/Netty module with a raw-bytes `POST /denoise` endpoint; the app is a single-activity Compose app with a ViewModel state machine and per-ABI `jniLibs` shims copied from `audx-realtime/libs/`. All audio is 16 kHz mono PCM-16 WAV.

**Tech stack:** Kotlin 2.3.20, Gradle 8.14.3 (both samples), Ktor 3.5.0 (server + client), kotlinx-serialization (via Kotlin plugin 2.3.20), logback-classic 1.5.18, AGP 8.13.2, compileSdk 36 / targetSdk 35, minSdk 26, Compose BOM 2026.05.01, activity-compose 1.13.0, lifecycle-viewmodel-compose 2.10.0. JDK 21 (installed). `ANDROID_HOME=/home/rizki/Android/Sdk` is set (platform `android-35` installed), so no `local.properties` is needed.

**Conventions for all tasks:**
- All `git` commands run from the repo root `/home/rizki/Projects/audx-kmp`.
- All `./gradlew` commands for the server run from `samples/server/`; for the app from `samples/android-app/`.
- The root `.gitignore` already ignores `.gradle/`, `build/`, `local.properties` as unanchored patterns, so sample build dirs are covered — no `.gitignore` changes needed.
- The first `./gradlew` run in each sample downloads Gradle 8.14.3; this is expected and slow once.

---

## File structure

New (server — `samples/server/`):
- `settings.gradle.kts` — repositories (`mavenLocal()` first) + project name
- `build.gradle.kts` — Kotlin/JVM + serialization + application plugins, deps
- `gradlew`, `gradle/wrapper/gradle-wrapper.jar` — copied from repo root
- `gradle/wrapper/gradle-wrapper.properties` — Gradle 8.14.3 distribution
- `src/main/kotlin/dev/rizukirr/audx/samples/server/Wav.kt` — WAV parse/encode (mono PCM-16 only)
- `src/main/kotlin/dev/rizukirr/audx/samples/server/Denoiser.kt` — offline denoise pass over audx
- `src/main/kotlin/dev/rizukirr/audx/samples/server/Application.kt` — Ktor module: `GET /health`, `POST /denoise`
- `src/main/resources/logback.xml` — console logging
- `src/test/kotlin/dev/rizukirr/audx/samples/server/WavTest.kt`
- `src/test/kotlin/dev/rizukirr/audx/samples/server/DenoiserTest.kt`
- `src/test/kotlin/dev/rizukirr/audx/samples/server/ApplicationTest.kt`
- `README.md` — how to run + curl examples

New (Android — `samples/android-app/`):
- `settings.gradle.kts` — plugin repos (google first) + dependency repos (`mavenLocal()` first) + `include(":app")`
- `build.gradle.kts` — root: AGP/Kotlin/Compose plugins `apply false`
- `gradle.properties` — `android.useAndroidX=true`, JVM args
- `gradlew`, `gradle/wrapper/gradle-wrapper.jar` — copied from repo root
- `gradle/wrapper/gradle-wrapper.properties` — Gradle 8.14.3 distribution
- `app/build.gradle.kts` — android application module
- `app/src/main/AndroidManifest.xml` — permissions, cleartext, launcher activity
- `app/src/main/jniLibs/arm64-v8a/libaudx_jni.so` — copied from `audx-realtime` (committed, matching repo precedent of vendoring `native/libs/*.a`)
- `app/src/main/jniLibs/x86_64/libaudx_jni.so` — copied from `audx-realtime`
- `app/src/main/kotlin/dev/rizukirr/audx/samples/app/WavFile.kt` — WAV encode only (app never parses)
- `app/src/main/kotlin/dev/rizukirr/audx/samples/app/Recorder.kt` — AudioRecord capture
- `app/src/main/kotlin/dev/rizukirr/audx/samples/app/Denoiser.kt` — offline denoise pass
- `app/src/main/kotlin/dev/rizukirr/audx/samples/app/Player.kt` — MediaPlayer wrapper
- `app/src/main/kotlin/dev/rizukirr/audx/samples/app/Uploader.kt` — Ktor client POST
- `app/src/main/kotlin/dev/rizukirr/audx/samples/app/MainViewModel.kt` — state machine
- `app/src/main/kotlin/dev/rizukirr/audx/samples/app/MainActivity.kt` — Compose UI + permission flow

New (top level):
- `samples/README.md` — overview, prerequisites, end-to-end walkthrough

Modified: none (no existing files change).

---

### Task 1: Server project scaffolding → verify: `cd samples/server && ./gradlew help` prints BUILD SUCCESSFUL

**Files:**
- Create: `samples/server/settings.gradle.kts`
- Create: `samples/server/build.gradle.kts`
- Create: `samples/server/gradlew` (copy)
- Create: `samples/server/gradle/wrapper/gradle-wrapper.jar` (copy)
- Create: `samples/server/gradle/wrapper/gradle-wrapper.properties`

- [x] **Step 1: Confirm the library is in mavenLocal**

Run: `ls ~/.m2/repository/dev/rizukirr/audx-kmp-jvm/0.1.0-SNAPSHOT/audx-kmp-jvm-0.1.0-SNAPSHOT.jar`
Expected: the file is listed (it exists; ~12.7 MB). If missing, run `./gradlew publishToMavenLocal` from the repo root first.

- [x] **Step 2: Create directories and copy the Gradle wrapper from the repo root**

```bash
mkdir -p samples/server/gradle/wrapper
cp gradlew samples/server/gradlew
cp gradle/wrapper/gradle-wrapper.jar samples/server/gradle/wrapper/gradle-wrapper.jar
```

- [x] **Step 3: Write the wrapper properties (Gradle 8.14.3)**

Create `samples/server/gradle/wrapper/gradle-wrapper.properties`:

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.14.3-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [x] **Step 4: Write settings.gradle.kts**

Create `samples/server/settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenLocal() // audx-kmp 0.1.0-SNAPSHOT is published locally
        mavenCentral()
    }
}

rootProject.name = "audx-server-sample"
```

- [x] **Step 5: Write build.gradle.kts**

Create `samples/server/build.gradle.kts`:

```kotlin
plugins {
    kotlin("jvm") version "2.3.20"
    kotlin("plugin.serialization") version "2.3.20"
    application
}

group = "dev.rizukirr.audx.samples"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
}

application {
    mainClass.set("dev.rizukirr.audx.samples.server.ApplicationKt")
}

dependencies {
    implementation("dev.rizukirr:audx-kmp:0.1.0-SNAPSHOT")

    implementation("io.ktor:ktor-server-netty:3.5.0")
    implementation("io.ktor:ktor-server-content-negotiation:3.5.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.5.0")
    implementation("io.ktor:ktor-server-call-logging:3.5.0")
    implementation("ch.qos.logback:logback-classic:1.5.18")

    testImplementation(kotlin("test"))
    testImplementation("io.ktor:ktor-server-test-host:3.5.0")
}
```

- [x] **Step 6: Verify the build configures**

Run: `cd samples/server && ./gradlew help`
Expected: `BUILD SUCCESSFUL` (first run downloads the Gradle 8.14.3 distribution).

- [x] **Step 7: Commit**

```bash
git add samples/server
git commit -m "feat(samples): scaffold server sample project"
```

---

### Task 2: Server WAV codec (TDD) → verify: `./gradlew test --tests "dev.rizukirr.audx.samples.server.WavTest"` passes

**Files:**
- Test: `samples/server/src/test/kotlin/dev/rizukirr/audx/samples/server/WavTest.kt`
- Create: `samples/server/src/main/kotlin/dev/rizukirr/audx/samples/server/Wav.kt`

- [x] **Step 1: Write the failing test**

Create `samples/server/src/test/kotlin/dev/rizukirr/audx/samples/server/WavTest.kt`:

```kotlin
package dev.rizukirr.audx.samples.server

import kotlin.io.path.createTempFile
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WavTest {
    private val samples = ShortArray(480) { (it * 37 % 4096).toShort() }

    @Test
    fun `round trips mono pcm16`() {
        val pcm = parseWav(wavBytes(16_000, samples))
        assertEquals(16_000, pcm.sampleRate)
        assertContentEquals(samples, pcm.samples)
    }

    @Test
    fun `writeWav writes parseable file`() {
        val file = createTempFile(suffix = ".wav").toFile()
        try {
            writeWav(file, 16_000, samples)
            val pcm = parseWav(file.readBytes())
            assertContentEquals(samples, pcm.samples)
        } finally {
            file.delete()
        }
    }

    @Test
    fun `rejects garbage`() {
        assertFailsWith<WavFormatException> { parseWav(byteArrayOf(1, 2, 3)) }
    }

    @Test
    fun `rejects stereo`() {
        val bytes = wavBytes(16_000, samples)
        bytes[22] = 2 // channel count lives at offset 22 in the canonical 44-byte header
        assertFailsWith<WavFormatException> { parseWav(bytes) }
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `cd samples/server && ./gradlew test --tests "dev.rizukirr.audx.samples.server.WavTest"`
Expected: FAILED — compilation error (unresolved references `parseWav`, `wavBytes`, `writeWav`, `WavFormatException`).

- [x] **Step 3: Write the implementation**

Create `samples/server/src/main/kotlin/dev/rizukirr/audx/samples/server/Wav.kt`:

```kotlin
package dev.rizukirr.audx.samples.server

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Decoded mono PCM-16 audio. */
class Pcm(val sampleRate: Int, val samples: ShortArray)

class WavFormatException(message: String) : Exception(message)

/**
 * Parses a WAV file. Only mono PCM-16 is supported — anything else throws
 * [WavFormatException].
 */
fun parseWav(bytes: ByteArray): Pcm {
    if (bytes.size < 12 ||
        String(bytes, 0, 4, Charsets.US_ASCII) != "RIFF" ||
        String(bytes, 8, 4, Charsets.US_ASCII) != "WAVE"
    ) throw WavFormatException("not a RIFF/WAVE file")

    val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
    var pos = 12
    var sampleRate = 0
    var fmtSeen = false
    var data: ShortArray? = null

    while (pos + 8 <= bytes.size) {
        val id = String(bytes, pos, 4, Charsets.US_ASCII)
        val size = buf.getInt(pos + 4)
        if (size < 0 || pos + 8 + size > bytes.size) throw WavFormatException("truncated '$id' chunk")
        when (id) {
            "fmt " -> {
                if (size < 16) throw WavFormatException("'fmt ' chunk too small")
                val audioFormat = buf.getShort(pos + 8).toInt()
                val channels = buf.getShort(pos + 10).toInt()
                sampleRate = buf.getInt(pos + 12)
                val bitsPerSample = buf.getShort(pos + 22).toInt()
                if (audioFormat != 1) throw WavFormatException("only PCM supported (got format $audioFormat)")
                if (channels != 1) throw WavFormatException("only mono supported (got $channels channels)")
                if (bitsPerSample != 16) throw WavFormatException("only 16-bit supported (got $bitsPerSample)")
                if (sampleRate <= 0) throw WavFormatException("invalid sample rate $sampleRate")
                fmtSeen = true
            }
            "data" -> {
                val samples = ShortArray(size / 2)
                ByteBuffer.wrap(bytes, pos + 8, size).order(ByteOrder.LITTLE_ENDIAN)
                    .asShortBuffer().get(samples)
                data = samples
            }
        }
        pos += 8 + size + (size and 1) // chunks are word-aligned
    }

    if (!fmtSeen) throw WavFormatException("missing 'fmt ' chunk")
    return Pcm(sampleRate, data ?: throw WavFormatException("missing 'data' chunk"))
}

/** Encodes mono PCM-16 samples as an in-memory WAV file. */
fun wavBytes(sampleRate: Int, samples: ShortArray): ByteArray {
    val dataSize = samples.size * 2
    val buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
    buf.put("RIFF".toByteArray(Charsets.US_ASCII))
    buf.putInt(36 + dataSize)
    buf.put("WAVE".toByteArray(Charsets.US_ASCII))
    buf.put("fmt ".toByteArray(Charsets.US_ASCII))
    buf.putInt(16)             // fmt chunk size
    buf.putShort(1)            // PCM
    buf.putShort(1)            // mono
    buf.putInt(sampleRate)
    buf.putInt(sampleRate * 2) // byte rate = rate * blockAlign
    buf.putShort(2)            // block align = channels * bytesPerSample
    buf.putShort(16)           // bits per sample
    buf.put("data".toByteArray(Charsets.US_ASCII))
    buf.putInt(dataSize)
    for (s in samples) buf.putShort(s)
    return buf.array()
}

fun writeWav(file: File, sampleRate: Int, samples: ShortArray) {
    file.writeBytes(wavBytes(sampleRate, samples))
}
```

- [x] **Step 4: Run the test to verify it passes**

Run: `cd samples/server && ./gradlew test --tests "dev.rizukirr.audx.samples.server.WavTest"`
Expected: `BUILD SUCCESSFUL`, all 4 tests pass.

- [x] **Step 5: Commit**

```bash
git add samples/server/src
git commit -m "feat(samples): server WAV parse/encode for mono PCM-16"
```

---

### Task 3: Server denoiser (TDD) → verify: `./gradlew test --tests "dev.rizukirr.audx.samples.server.DenoiserTest"` passes

This task exercises the real JNI shim on desktop — the same path the library's own `jvmTest` uses.

**Files:**
- Test: `samples/server/src/test/kotlin/dev/rizukirr/audx/samples/server/DenoiserTest.kt`
- Create: `samples/server/src/main/kotlin/dev/rizukirr/audx/samples/server/Denoiser.kt`

- [x] **Step 1: Write the failing test**

Create `samples/server/src/test/kotlin/dev/rizukirr/audx/samples/server/DenoiserTest.kt`:

```kotlin
package dev.rizukirr.audx.samples.server

import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.Test
import kotlin.test.assertEquals

class DenoiserTest {
    @Test
    fun `preserves length and reports frame count`() {
        val n = 1_000 // deliberately not a multiple of the 160-sample frame at 16 kHz
        val sine = ShortArray(n) { (8_000 * sin(2.0 * PI * 440 * it / 16_000)).toInt().toShort() }
        val result = denoise(16_000, sine)
        assertEquals(n, result.samples.size)
        assertEquals(7, result.frames) // ceil(1000 / 160)
    }

    @Test
    fun `handles empty input`() {
        val result = denoise(16_000, ShortArray(0))
        assertEquals(0, result.samples.size)
        assertEquals(0, result.frames)
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `cd samples/server && ./gradlew test --tests "dev.rizukirr.audx.samples.server.DenoiserTest"`
Expected: FAILED — compilation error (unresolved reference `denoise`).

- [x] **Step 3: Write the implementation**

Create `samples/server/src/main/kotlin/dev/rizukirr/audx/samples/server/Denoiser.kt`:

```kotlin
package dev.rizukirr.audx.samples.server

import dev.rizukirr.audx.Audx

/** Result of an offline denoise pass. */
class Denoised(val samples: ShortArray, val frames: Int)

/**
 * Runs [samples] through audx one frame at a time. The tail is zero-padded to
 * a whole frame for processing and the result trimmed back to the input length.
 */
fun denoise(sampleRate: Int, samples: ShortArray): Denoised {
    Audx(sampleRate = sampleRate).use { audx ->
        val frameSize = audx.frameSize
        val frames = (samples.size + frameSize - 1) / frameSize
        val padded = samples.copyOf(frames * frameSize)
        val output = ShortArray(padded.size)
        val inFrame = ShortArray(frameSize)
        val outFrame = ShortArray(frameSize)
        for (i in 0 until frames) {
            padded.copyInto(inFrame, 0, i * frameSize, (i + 1) * frameSize)
            audx.process(inFrame, outFrame)
            outFrame.copyInto(output, i * frameSize)
        }
        return Denoised(output.copyOf(samples.size), frames)
    }
}
```

- [x] **Step 4: Run the test to verify it passes**

Run: `cd samples/server && ./gradlew test --tests "dev.rizukirr.audx.samples.server.DenoiserTest"`
Expected: `BUILD SUCCESSFUL`, both tests pass. (If this fails with `UnsatisfiedLinkError`, the mavenLocal artifact is stale — republish from the repo root with `./gradlew publishToMavenLocal` and retry.)

- [x] **Step 5: Commit**

```bash
git add samples/server/src
git commit -m "feat(samples): server offline denoise pass over audx"
```

---

### Task 4: Server HTTP endpoints (TDD) → verify: `./gradlew test` passes all server tests, including ApplicationTest

**Files:**
- Test: `samples/server/src/test/kotlin/dev/rizukirr/audx/samples/server/ApplicationTest.kt`
- Create: `samples/server/src/main/kotlin/dev/rizukirr/audx/samples/server/Application.kt`
- Create: `samples/server/src/main/resources/logback.xml`

- [x] **Step 1: Write the failing test**

Create `samples/server/src/test/kotlin/dev/rizukirr/audx/samples/server/ApplicationTest.kt`:

```kotlin
package dev.rizukirr.audx.samples.server

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ApplicationTest {
    @Test
    fun `health returns ok`() = testApplication {
        application { module() }
        val response = client.get("/health")
        assertEquals(HttpStatusCode.OK, response.status)
        assertEquals("ok", response.bodyAsText())
    }

    @Test
    fun `denoise saves wav and reports frames`() = testApplication {
        val dir = createTempDirectory("recordings").toFile()
        application { module(recordingsDir = dir) }

        val samples = ShortArray(1_600) { (it % 128).toShort() } // 100 ms at 16 kHz
        val response = client.post("/denoise") { setBody(wavBytes(16_000, samples)) }

        assertEquals(HttpStatusCode.OK, response.status)
        assertTrue(response.bodyAsText().contains("\"savedAs\""))
        val saved = dir.listFiles()!!.single()
        assertTrue(saved.name.startsWith("denoised-") && saved.name.endsWith(".wav"))
        val pcm = parseWav(saved.readBytes())
        assertEquals(16_000, pcm.sampleRate)
        assertEquals(samples.size, pcm.samples.size)
    }

    @Test
    fun `rejects invalid wav`() = testApplication {
        application { module() }
        val response = client.post("/denoise") { setBody(byteArrayOf(1, 2, 3)) }
        assertEquals(HttpStatusCode.BadRequest, response.status)
    }
}
```

- [x] **Step 2: Run the test to verify it fails**

Run: `cd samples/server && ./gradlew test --tests "dev.rizukirr.audx.samples.server.ApplicationTest"`
Expected: FAILED — compilation error (unresolved reference `module`).

- [x] **Step 3: Write the application module**

Create `samples/server/src/main/kotlin/dev/rizukirr/audx/samples/server/Application.kt`:

```kotlin
package dev.rizukirr.audx.samples.server

import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.application.log
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.calllogging.CallLogging
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.serialization.Serializable
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

@Serializable
data class DenoiseResponse(val savedAs: String, val frames: Int)

private val timestampFormat = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS")

fun main() {
    embeddedServer(Netty, port = 8080, host = "0.0.0.0") { module() }.start(wait = true)
}

fun Application.module(recordingsDir: File = File("recordings")) {
    install(CallLogging)
    install(ContentNegotiation) { json() }
    routing {
        get("/health") { call.respondText("ok") }

        post("/denoise") {
            val pcm = try {
                parseWav(call.receive<ByteArray>())
            } catch (e: WavFormatException) {
                call.respondText(e.message ?: "invalid wav", status = HttpStatusCode.BadRequest)
                return@post
            }
            // Audx failures (IllegalStateException) and IO errors propagate and
            // become 500s via Ktor's default exception handling, which logs them.
            val denoised = denoise(pcm.sampleRate, pcm.samples)
            recordingsDir.mkdirs()
            val file = File(recordingsDir, "denoised-${LocalDateTime.now().format(timestampFormat)}.wav")
            writeWav(file, pcm.sampleRate, denoised.samples)
            call.application.log.info("saved ${file.absolutePath}")
            call.respond(DenoiseResponse(savedAs = file.name, frames = denoised.frames))
        }
    }
}
```

Note: the endpoint accepts any mono PCM-16 sample rate (per the spec's `parseWav` contract — audx resamples internally), not just 16 kHz.

- [x] **Step 4: Write logback.xml**

Create `samples/server/src/main/resources/logback.xml`:

```xml
<configuration>
    <appender name="STDOUT" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{HH:mm:ss.SSS} %-5level %logger{24} - %msg%n</pattern>
        </encoder>
    </appender>
    <root level="INFO">
        <appender-ref ref="STDOUT"/>
    </root>
</configuration>
```

- [x] **Step 5: Run the full server test suite**

Run: `cd samples/server && ./gradlew test`
Expected: `BUILD SUCCESSFUL` — WavTest (4), DenoiserTest (2), ApplicationTest (3) all pass.

- [x] **Step 6: Commit**

```bash
git add samples/server/src
git commit -m "feat(samples): server /denoise and /health endpoints"
```

---

### Task 5: Server README + live smoke test → verify: `curl http://localhost:8080/health` returns `ok` against a running server

**Files:**
- Create: `samples/server/README.md`

- [x] **Step 1: Write the README**

Create `samples/server/README.md`:

````markdown
# audx server sample

Receives raw WAV audio over HTTP, denoises it with
[`audx-kmp`](../..) (jvm/JNI artifact), and saves the result to disk.

## Prerequisites

The library must be in mavenLocal: from the repo root run

```bash
./gradlew publishToMavenLocal
```

## Run

```bash
./gradlew run
```

Listens on `0.0.0.0:8080` so devices on the same network can reach it.

## API

- `GET /health` → `200 ok`
- `POST /denoise` — body: WAV bytes (mono, PCM-16; any sample rate, the
  Android sample sends 16 kHz). Saves
  `recordings/denoised-<timestamp>.wav` and replies
  `{"savedAs":"denoised-....wav","frames":N}`.

```bash
curl --data-binary @some-mono-16bit.wav http://localhost:8080/denoise
```

Errors: malformed/unsupported WAV → `400` with a reason; processing or IO
failures → `500` (logged).
````

- [x] **Step 2: Smoke-test the real server**

```bash
cd samples/server
./gradlew run &
SERVER_PID=$!
sleep 20   # first run may compile before starting
curl -s http://localhost:8080/health
kill $SERVER_PID
```

Expected: `curl` prints `ok`. (If `curl` fails because the server is still compiling, wait and retry the curl before killing.)

- [x] **Step 3: Commit**

```bash
git add samples/server/README.md
git commit -m "docs(samples): server README with run + API docs"
```

---

### Task 6: Android project scaffolding → verify: `cd samples/android-app && ./gradlew :app:assembleDebug` prints BUILD SUCCESSFUL

**Files:**
- Create: `samples/android-app/settings.gradle.kts`
- Create: `samples/android-app/build.gradle.kts`
- Create: `samples/android-app/gradle.properties`
- Create: `samples/android-app/gradlew` (copy)
- Create: `samples/android-app/gradle/wrapper/gradle-wrapper.jar` (copy)
- Create: `samples/android-app/gradle/wrapper/gradle-wrapper.properties`
- Create: `samples/android-app/app/build.gradle.kts`
- Create: `samples/android-app/app/src/main/AndroidManifest.xml`
- Create: `samples/android-app/app/src/main/kotlin/dev/rizukirr/audx/samples/app/MainActivity.kt` (placeholder; replaced in Task 13)

- [x] **Step 1: Create directories and copy the Gradle wrapper**

```bash
mkdir -p samples/android-app/gradle/wrapper
mkdir -p samples/android-app/app/src/main/kotlin/dev/rizukirr/audx/samples/app
cp gradlew samples/android-app/gradlew
cp gradle/wrapper/gradle-wrapper.jar samples/android-app/gradle/wrapper/gradle-wrapper.jar
```

- [x] **Step 2: Write the wrapper properties**

Create `samples/android-app/gradle/wrapper/gradle-wrapper.properties`:

```properties
distributionBase=GRADLE_USER_HOME
distributionPath=wrapper/dists
distributionUrl=https\://services.gradle.org/distributions/gradle-8.14.3-bin.zip
zipStoreBase=GRADLE_USER_HOME
zipStorePath=wrapper/dists
```

- [x] **Step 3: Write settings.gradle.kts**

Create `samples/android-app/settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenLocal() // audx-kmp 0.1.0-SNAPSHOT is published locally
        google()
        mavenCentral()
    }
}

rootProject.name = "audx-android-sample"
include(":app")
```

- [x] **Step 4: Write the root build file and gradle.properties**

Create `samples/android-app/build.gradle.kts`:

```kotlin
plugins {
    id("com.android.application") version "8.13.2" apply false
    id("org.jetbrains.kotlin.android") version "2.3.20" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20" apply false
}
```

Create `samples/android-app/gradle.properties`:

```properties
org.gradle.jvmargs=-Xmx4g
android.useAndroidX=true
```

- [x] **Step 5: Write the app module build file**

Create `samples/android-app/app/build.gradle.kts`:

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "dev.rizukirr.audx.samples.app"
    compileSdk = 36 // activity-compose 1.13.0 (and transitives) require >= 36

    defaultConfig {
        applicationId = "dev.rizukirr.audx.samples.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    buildFeatures {
        compose = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        // The audx-kmp jvm jar bundles desktop JNI shims as java resources;
        // Android loads its own copies from jniLibs/, so keep desktop ones out.
        resources.excludes += "natives/**"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation("dev.rizukirr:audx-kmp:0.1.0-SNAPSHOT")

    implementation(platform("androidx.compose:compose-bom:2026.05.01"))
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.10.0")

    implementation("io.ktor:ktor-client-cio:3.5.0")
}
```

- [x] **Step 6: Write the manifest**

Create `samples/android-app/app/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.RECORD_AUDIO" />
    <uses-permission android:name="android.permission.INTERNET" />

    <application
        android:label="Audx Sample"
        android:usesCleartextTraffic="true"
        android:theme="@android:style/Theme.Material.Light.NoActionBar">
        <activity
            android:name=".MainActivity"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.MAIN" />
                <category android:name="android.intent.category.LAUNCHER" />
            </intent-filter>
        </activity>
    </application>

</manifest>
```

(`usesCleartextTraffic` because the sample server is plain HTTP on the LAN.)

- [x] **Step 7: Write the placeholder MainActivity**

Create `samples/android-app/app/src/main/kotlin/dev/rizukirr/audx/samples/app/MainActivity.kt`:

```kotlin
package dev.rizukirr.audx.samples.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MaterialTheme { Text("audx sample") } }
    }
}
```

- [x] **Step 8: Verify the debug build**

Run: `cd samples/android-app && ./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL` (first run downloads Gradle + Android dependencies; slow once).

- [x] **Step 9: Commit**

```bash
git add samples/android-app
git commit -m "feat(samples): scaffold Android sample app"
```

---

### Task 7: Bundle the Android JNI shims → verify: `unzip -l app/build/outputs/apk/debug/app-debug.apk` lists `lib/arm64-v8a/libaudx_jni.so` and `lib/x86_64/libaudx_jni.so`

**Files:**
- Create: `samples/android-app/app/src/main/jniLibs/arm64-v8a/libaudx_jni.so` (copy)
- Create: `samples/android-app/app/src/main/jniLibs/x86_64/libaudx_jni.so` (copy)

- [x] **Step 1: Copy the per-ABI shims from audx-realtime**

```bash
mkdir -p samples/android-app/app/src/main/jniLibs/arm64-v8a
mkdir -p samples/android-app/app/src/main/jniLibs/x86_64
cp /home/rizki/Projects/audx-realtime/libs/arm64-v8a/libaudx_jni.so samples/android-app/app/src/main/jniLibs/arm64-v8a/
cp /home/rizki/Projects/audx-realtime/libs/x86_64/libaudx_jni.so samples/android-app/app/src/main/jniLibs/x86_64/
```

(Only `libaudx_jni.so` is needed — verified self-contained: its `NEEDED` entries are only `libm`/`libdl`/`libc`. `libaudx_src.so` is not used.)

- [x] **Step 2: Rebuild and inspect the APK**

```bash
cd samples/android-app
./gradlew :app:assembleDebug
unzip -l app/build/outputs/apk/debug/app-debug.apk | grep libaudx_jni.so
```

Expected: two lines — `lib/arm64-v8a/libaudx_jni.so` and `lib/x86_64/libaudx_jni.so`.

- [x] **Step 3: Confirm the desktop natives were excluded**

Run: `unzip -l app/build/outputs/apk/debug/app-debug.apk | grep natives/ || echo EXCLUDED`
Expected: `EXCLUDED` (no `natives/...` java-resource entries from the jvm jar).

- [x] **Step 4: Commit**

```bash
git add samples/android-app/app/src/main/jniLibs
git commit -m "feat(samples): vendor Android per-ABI audx JNI shims"
```

---

### Task 8: App WAV encoder → verify: `./gradlew :app:assembleDebug` BUILD SUCCESSFUL

The app only writes WAV files (MediaPlayer reads them; the server parses them), so this is the encode half of the server's `Wav.kt`.

**Files:**
- Create: `samples/android-app/app/src/main/kotlin/dev/rizukirr/audx/samples/app/WavFile.kt`

- [x] **Step 1: Write the implementation**

Create `samples/android-app/app/src/main/kotlin/dev/rizukirr/audx/samples/app/WavFile.kt`:

```kotlin
package dev.rizukirr.audx.samples.app

import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Encodes mono PCM-16 samples as an in-memory WAV file. */
fun wavBytes(sampleRate: Int, samples: ShortArray): ByteArray {
    val dataSize = samples.size * 2
    val buf = ByteBuffer.allocate(44 + dataSize).order(ByteOrder.LITTLE_ENDIAN)
    buf.put("RIFF".toByteArray(Charsets.US_ASCII))
    buf.putInt(36 + dataSize)
    buf.put("WAVE".toByteArray(Charsets.US_ASCII))
    buf.put("fmt ".toByteArray(Charsets.US_ASCII))
    buf.putInt(16)             // fmt chunk size
    buf.putShort(1)            // PCM
    buf.putShort(1)            // mono
    buf.putInt(sampleRate)
    buf.putInt(sampleRate * 2) // byte rate = rate * blockAlign
    buf.putShort(2)            // block align = channels * bytesPerSample
    buf.putShort(16)           // bits per sample
    buf.put("data".toByteArray(Charsets.US_ASCII))
    buf.putInt(dataSize)
    for (s in samples) buf.putShort(s)
    return buf.array()
}

fun writeWav(file: File, sampleRate: Int, samples: ShortArray) {
    file.writeBytes(wavBytes(sampleRate, samples))
}
```

- [x] **Step 2: Verify it compiles**

Run: `cd samples/android-app && ./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [x] **Step 3: Commit**

```bash
git add samples/android-app/app/src
git commit -m "feat(samples): app WAV encoder"
```

---

### Task 9: App microphone recorder → verify: `./gradlew :app:assembleDebug` BUILD SUCCESSFUL

**Files:**
- Create: `samples/android-app/app/src/main/kotlin/dev/rizukirr/audx/samples/app/Recorder.kt`

- [x] **Step 1: Write the implementation**

Create `samples/android-app/app/src/main/kotlin/dev/rizukirr/audx/samples/app/Recorder.kt`:

```kotlin
package dev.rizukirr.audx.samples.app

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder

const val SAMPLE_RATE = 16_000

/** One-shot microphone capture: [record] blocks until [stop] is called. */
class Recorder {
    @Volatile
    private var recording = false

    /**
     * Captures 16 kHz mono PCM-16 until [stop] is called, then returns every
     * sample read. Call on a background dispatcher. Throws
     * IllegalStateException if the mic cannot be opened.
     */
    @SuppressLint("MissingPermission") // caller ensures RECORD_AUDIO is granted
    fun record(): ShortArray {
        val minBuf = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
        )
        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            maxOf(minBuf, SAMPLE_RATE), // bytes — at least 0.5 s of headroom
        )
        check(audioRecord.state == AudioRecord.STATE_INITIALIZED) { "microphone unavailable" }

        val chunks = ArrayList<ShortArray>()
        try {
            recording = true
            audioRecord.startRecording()
            val chunk = ShortArray(1024)
            while (recording) {
                val n = audioRecord.read(chunk, 0, chunk.size)
                if (n > 0) chunks.add(chunk.copyOf(n))
            }
            audioRecord.stop()
        } finally {
            recording = false
            audioRecord.release()
        }

        val all = ShortArray(chunks.sumOf { it.size })
        var pos = 0
        for (c in chunks) {
            c.copyInto(all, pos)
            pos += c.size
        }
        return all
    }

    fun stop() {
        recording = false
    }
}
```

- [x] **Step 2: Verify it compiles**

Run: `cd samples/android-app && ./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [x] **Step 3: Commit**

```bash
git add samples/android-app/app/src
git commit -m "feat(samples): app AudioRecord-based recorder"
```

---

### Task 10: App denoiser → verify: `./gradlew :app:assembleDebug` BUILD SUCCESSFUL

**Files:**
- Create: `samples/android-app/app/src/main/kotlin/dev/rizukirr/audx/samples/app/Denoiser.kt`

- [x] **Step 1: Write the implementation**

Create `samples/android-app/app/src/main/kotlin/dev/rizukirr/audx/samples/app/Denoiser.kt`:

```kotlin
package dev.rizukirr.audx.samples.app

import dev.rizukirr.audx.Audx

/**
 * Offline denoise pass: zero-pads the tail to a whole frame, processes frame
 * by frame, trims back to the input length.
 */
fun denoise(sampleRate: Int, samples: ShortArray): ShortArray {
    Audx(sampleRate = sampleRate).use { audx ->
        val frameSize = audx.frameSize
        val frames = (samples.size + frameSize - 1) / frameSize
        val padded = samples.copyOf(frames * frameSize)
        val output = ShortArray(padded.size)
        val inFrame = ShortArray(frameSize)
        val outFrame = ShortArray(frameSize)
        for (i in 0 until frames) {
            padded.copyInto(inFrame, 0, i * frameSize, (i + 1) * frameSize)
            audx.process(inFrame, outFrame)
            outFrame.copyInto(output, i * frameSize)
        }
        return output.copyOf(samples.size)
    }
}
```

- [x] **Step 2: Verify it compiles**

Run: `cd samples/android-app && ./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [x] **Step 3: Commit**

```bash
git add samples/android-app/app/src
git commit -m "feat(samples): app offline denoise pass over audx"
```

---

### Task 11: App player + uploader → verify: `./gradlew :app:assembleDebug` BUILD SUCCESSFUL

**Files:**
- Create: `samples/android-app/app/src/main/kotlin/dev/rizukirr/audx/samples/app/Player.kt`
- Create: `samples/android-app/app/src/main/kotlin/dev/rizukirr/audx/samples/app/Uploader.kt`

- [ ] **Step 1: Write the player**

Create `samples/android-app/app/src/main/kotlin/dev/rizukirr/audx/samples/app/Player.kt`:

```kotlin
package dev.rizukirr.audx.samples.app

import android.media.MediaPlayer
import java.io.File

/** Plays one WAV file at a time; starting a new one stops the previous. */
class Player {
    private var player: MediaPlayer? = null

    fun play(file: File) {
        stop()
        player = MediaPlayer().apply {
            setDataSource(file.absolutePath)
            prepare()
            start()
        }
    }

    fun stop() {
        player?.release()
        player = null
    }
}
```

- [ ] **Step 2: Write the uploader**

Create `samples/android-app/app/src/main/kotlin/dev/rizukirr/audx/samples/app/Uploader.kt`:

```kotlin
package dev.rizukirr.audx.samples.app

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** POSTs [wavFile] to `<serverUrl>/denoise` and returns the server's reply body. */
suspend fun upload(serverUrl: String, wavFile: File): String = withContext(Dispatchers.IO) {
    HttpClient(CIO).use { client ->
        val response = client.post(serverUrl.trimEnd('/') + "/denoise") {
            contentType(ContentType("audio", "wav"))
            setBody(wavFile.readBytes())
        }
        val body = response.bodyAsText()
        if (!response.status.isSuccess()) throw IOException("HTTP ${response.status.value}: $body")
        body
    }
}
```

- [ ] **Step 3: Verify it compiles**

Run: `cd samples/android-app && ./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add samples/android-app/app/src
git commit -m "feat(samples): app WAV playback and raw-audio upload"
```

---

### Task 12: App ViewModel state machine → verify: `./gradlew :app:assembleDebug` BUILD SUCCESSFUL

**Files:**
- Create: `samples/android-app/app/src/main/kotlin/dev/rizukirr/audx/samples/app/MainViewModel.kt`

- [ ] **Step 1: Write the implementation**

Create `samples/android-app/app/src/main/kotlin/dev/rizukirr/audx/samples/app/MainViewModel.kt`:

```kotlin
package dev.rizukirr.audx.samples.app

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
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

class MainViewModel(app: Application) : AndroidViewModel(app) {
    var state by mutableStateOf<UiState>(UiState.Idle)
        private set
    var status by mutableStateOf("Tap Record to start")
        private set
    var serverUrl by mutableStateOf("http://192.168.1.100:8080")

    private val recorder = Recorder()
    private val player = Player()

    private fun outputDir(): File =
        checkNotNull(getApplication<Application>().getExternalFilesDir(null)) {
            "external storage unavailable"
        }

    fun startRecording() {
        if (state == UiState.Recording) return
        state = UiState.Recording
        status = "Recording…"
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val samples = recorder.record()
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
                    state = UiState.Idle
                    status = "Error: ${t.message ?: t.javaClass.simpleName}"
                }
            }
        }
    }

    fun stopRecording() = recorder.stop()

    fun permissionDenied() {
        status = "Microphone permission denied"
    }

    fun playRaw() {
        (state as? UiState.Ready)?.let { play(it.rawFile) }
    }

    fun playDenoised() {
        (state as? UiState.Ready)?.let { play(it.denoisedFile) }
    }

    private fun play(file: File) {
        status = try {
            player.play(file)
            "Playing ${file.name}"
        } catch (t: Throwable) {
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

    override fun onCleared() = player.stop()
}
```

- [ ] **Step 2: Verify it compiles**

Run: `cd samples/android-app && ./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add samples/android-app/app/src
git commit -m "feat(samples): app ViewModel record/denoise/upload state machine"
```

---

### Task 13: App Compose UI + mic permission → verify: `./gradlew :app:assembleDebug` BUILD SUCCESSFUL

**Files:**
- Modify: `samples/android-app/app/src/main/kotlin/dev/rizukirr/audx/samples/app/MainActivity.kt` (replace the Task 6 placeholder entirely)

- [ ] **Step 1: Replace MainActivity with the full UI**

Replace the entire contents of `samples/android-app/app/src/main/kotlin/dev/rizukirr/audx/samples/app/MainActivity.kt` with:

```kotlin
package dev.rizukirr.audx.samples.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) { MainScreen() }
            }
        }
    }
}

@Composable
fun MainScreen(vm: MainViewModel = viewModel()) {
    val context = LocalContext.current
    val micLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.startRecording() else vm.permissionDenied()
    }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedTextField(
            value = vm.serverUrl,
            onValueChange = { vm.serverUrl = it },
            label = { Text("Server URL") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        val recording = vm.state == UiState.Recording
        Button(
            onClick = {
                when {
                    recording -> vm.stopRecording()
                    context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
                        PackageManager.PERMISSION_GRANTED -> vm.startRecording()
                    else -> micLauncher.launch(Manifest.permission.RECORD_AUDIO)
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (recording) "Stop" else "Record")
        }

        val ready = vm.state is UiState.Ready
        Button(onClick = vm::playRaw, enabled = ready, modifier = Modifier.fillMaxWidth()) {
            Text("Play Raw")
        }
        Button(onClick = vm::playDenoised, enabled = ready, modifier = Modifier.fillMaxWidth()) {
            Text("Play Denoised")
        }
        Button(onClick = vm::uploadRaw, enabled = ready, modifier = Modifier.fillMaxWidth()) {
            Text("Upload Raw to Server")
        }

        Text(vm.status)
    }
}
```

(`Context.checkSelfPermission` is API 23+; minSdk is 26, so no compat shim is needed.)

- [ ] **Step 2: Verify it compiles**

Run: `cd samples/android-app && ./gradlew :app:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Commit**

```bash
git add samples/android-app/app/src
git commit -m "feat(samples): app Compose UI with record/play/upload flow"
```

---

### Task 14: Samples README + final build sweep → verify: `samples/README.md` exists; both `./gradlew test` (server) and `./gradlew :app:assembleDebug` (app) pass

**Files:**
- Create: `samples/README.md`

- [ ] **Step 1: Write the README**

Create `samples/README.md`:

````markdown
# audx-kmp samples

Two standalone projects demonstrating `dev.rizukirr:audx-kmp:0.1.0-SNAPSHOT`:

- **[server/](server/)** — Ktor JVM server: `POST /denoise` receives a WAV,
  denoises it (jvm/JNI artifact), saves `recordings/denoised-<timestamp>.wav`.
- **[android-app/](android-app/)** — Compose app: records 16 kHz mono audio,
  denoises locally (per-ABI `jniLibs` shims), plays raw vs denoised, and
  uploads the raw recording to the server.

## Prerequisites

1. Publish the library to mavenLocal (from the repo root):

   ```bash
   ./gradlew publishToMavenLocal
   ```

2. The Android shims are vendored at
   `android-app/app/src/main/jniLibs/{arm64-v8a,x86_64}/libaudx_jni.so`.
   To refresh them after changing `audx-realtime`:

   ```bash
   bash ../audx-realtime/scripts/android.sh   # builds libs/<abi>/libaudx_jni.so
   cp ../audx-realtime/libs/arm64-v8a/libaudx_jni.so android-app/app/src/main/jniLibs/arm64-v8a/
   cp ../audx-realtime/libs/x86_64/libaudx_jni.so android-app/app/src/main/jniLibs/x86_64/
   ```

## End-to-end walkthrough

1. Start the server (listens on `0.0.0.0:8080`):

   ```bash
   cd server && ./gradlew run
   ```

2. Find your machine's LAN IP (e.g. `ip -4 addr show | grep 'inet '`).

3. Install the app on a device/emulator on the same network:

   ```bash
   cd android-app && ./gradlew :app:installDebug
   ```

   (Emulators reach the host at `http://10.0.2.2:8080`.)

4. In the app: set the Server URL → **Record** → speak → **Stop** →
   compare **Play Raw** vs **Play Denoised** → **Upload Raw to Server**.
   The server saves its own denoised copy under `server/recordings/`.
````

- [ ] **Step 2: Run the final build sweep**

```bash
cd samples/server && ./gradlew test
cd ../android-app && ./gradlew :app:assembleDebug
```

Expected: both print `BUILD SUCCESSFUL`; all 9 server tests pass.

- [ ] **Step 3: Commit**

```bash
git add samples/README.md
git commit -m "docs(samples): top-level samples README with e2e walkthrough"
```

---

## Manual verification (post-plan, requires a human + device)

Not executable by the agent; listed for the user:

1. Run the server, install the app on a phone on the same Wi-Fi (or emulator with `http://10.0.2.2:8080`).
2. Record a few seconds of speech with background noise.
3. Play Raw vs Play Denoised — denoised should have audibly less background noise.
4. Upload Raw — status line shows the server's JSON; a new `denoised-*.wav` appears in `samples/server/recordings/` and is playable.
