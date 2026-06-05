# VAD Monitoring API + Android Sample Implementation Plan

> **For executing agents:** implement this plan task-by-task. Each step uses checkbox (`- [ ]`) syntax. Do not skip steps. Do not batch commits across tasks.

**Goal:** Add a debounced `isSpeaking(threshold, hangoverMs)` VAD API to the `audx-kmp` library and an Android sample app that shows it working live from the microphone.

**Architecture:** A pure-Kotlin `VadRing` (fixed ring buffer of recent per-frame probabilities) lives in `commonMain` and owns all window arithmetic; each platform `actual Audx` pushes into it from `process()`. The public API is `val lastVad` on the expect class plus an `isSpeaking()` extension. A new `:sample-android` Compose module consumes the published mavenLocal artifact + `jniLibs` shims and renders raw vs debounced speech state side by side.

**Tech stack:** Kotlin Multiplatform 2.3.21, Gradle 9.4.1, AGP 9.2.0 (built-in Kotlin, no `kotlin-android` plugin), Compose BOM 2026.04.01, `kotlin("test")` on jvmTest. All version choices mirror the proven combo in `~/Projects/audx-android` (read-only reference — never modify that repo).

**Working directory for all commands:** `/home/rizki/Projects/audx-kmp` unless stated otherwise.

---

## File structure

New:
- `src/commonMain/kotlin/VadRing.kt` — internal ring buffer of recent VAD probabilities; all window arithmetic
- `src/jvmTest/kotlin/VadRingTest.kt` — pure-Kotlin unit tests for the ring
- `sample-android/build.gradle.kts` — Android application module
- `sample-android/src/main/AndroidManifest.xml` — mic permission + single activity
- `sample-android/src/main/java/dev/rizukirr/audx/sample/VadEngine.kt` — AudioRecord loop → `Audx.process()` → UI state
- `sample-android/src/main/java/dev/rizukirr/audx/sample/MainActivity.kt` — Compose UI (meter + raw/debounced indicators)
- `sample-android/src/main/jniLibs/arm64-v8a/libaudx_jni.so` — copied from `~/Projects/audx-realtime/libs/arm64-v8a/`
- `sample-android/src/main/jniLibs/x86_64/libaudx_jni.so` — copied from `~/Projects/audx-realtime/libs/x86_64/`

Modified:
- `gradle/wrapper/gradle-wrapper.properties` — Gradle 8.4 → 9.4.1
- `gradle/libs.versions.toml` — Kotlin 2.3.20 → 2.3.21; add AGP/Compose entries
- `build.gradle.kts` — add `apply false` aliases for the Android/Compose plugins
- `settings.gradle.kts` — add `google()` repos, `mavenLocal()`, include `:sample-android`
- `src/commonMain/kotlin/Audx.kt` — `FRAME_MS`, `lastVad` + internal `vadRing` on the expect class, `isSpeaking()` extension
- `src/jvmMain/kotlin/Audx.kt` — own a `VadRing`, push from `process()`
- `src/nativeMain/kotlin/Audx.kt` — own a `VadRing`, push from `process()`
- `src/jvmTest/kotlin/AudxJvmTest.kt` — integration test for `lastVad`/`isSpeaking`
- `README.md` — document the VAD API and the sample

---

### Task 1: Toolchain bump (Gradle 9.4.1, Kotlin 2.3.21) → verify: `./gradlew jvmTest compileKotlinLinuxX64` prints BUILD SUCCESSFUL on Gradle 9.4.1

**Files:**
- Modify: `gradle/wrapper/gradle-wrapper.properties`
- Modify: `gradle/libs.versions.toml`

- [x] **Step 1: Point the wrapper at Gradle 9.4.1**

In `gradle/wrapper/gradle-wrapper.properties` replace the `distributionUrl` line:

```properties
distributionUrl=https\://services.gradle.org/distributions/gradle-9.4.1-bin.zip
```

- [x] **Step 2: Bump Kotlin in the version catalog**

In `gradle/libs.versions.toml` change the kotlin version line to:

```toml
kotlin = "2.3.21"
```

- [x] **Step 3: Confirm the wrapper resolves**

Run: `./gradlew --version`
Expected: output contains `Gradle 9.4.1` (the distribution is already in `~/.gradle/wrapper/dists` from the audx-android project, so no long download).

- [x] **Step 4: Confirm the library is still green**

Run: `./gradlew jvmTest compileKotlinLinuxX64 compileKotlinMingwX64 compileKotlinAndroidNativeArm64 compileKotlinAndroidNativeX64`
Expected: `BUILD SUCCESSFUL`. If Gradle 9 surfaces new deprecation *warnings*, ignore them; only failures block.

- [x] **Step 5: Commit**

```bash
git add gradle/wrapper/gradle-wrapper.properties gradle/libs.versions.toml
git commit -m "build: bump to Gradle 9.4.1 and Kotlin 2.3.21"
```

---

### Task 2: VadRing (TDD) → verify: `./gradlew jvmTest --tests "dev.rizukirr.audx.VadRingTest"` passes

**Files:**
- Create: `src/jvmTest/kotlin/VadRingTest.kt`
- Create: `src/commonMain/kotlin/VadRing.kt`

- [x] **Step 1: Write the failing tests**

Create `src/jvmTest/kotlin/VadRingTest.kt` with exactly:

```kotlin
package dev.rizukirr.audx

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VadRingTest {

    @Test
    fun emptyRingReportsSilence() {
        val ring = VadRing()
        assertEquals(0f, ring.last)
        assertFalse(ring.anyAbove(threshold = 0f, frames = 20))
    }

    @Test
    fun lastTracksNewestPush() {
        val ring = VadRing()
        ring.push(0.9f)
        assertEquals(0.9f, ring.last)
        ring.push(0.2f)
        assertEquals(0.2f, ring.last)
    }

    @Test
    fun thresholdComparisonIsStrict() {
        val ring = VadRing()
        ring.push(0.5f)
        assertFalse(ring.anyAbove(threshold = 0.5f, frames = 1))
        assertTrue(ring.anyAbove(threshold = 0.49f, frames = 1))
    }

    @Test
    fun windowExcludesFramesOlderThanRequested() {
        val ring = VadRing()
        ring.push(0.9f)
        repeat(20) { ring.push(0.1f) }
        // the 0.9 frame is now 21 frames back
        assertFalse(ring.anyAbove(threshold = 0.5f, frames = 20))
        assertTrue(ring.anyAbove(threshold = 0.5f, frames = 21))
    }

    @Test
    fun ringWrapsAtCapacity() {
        val ring = VadRing(capacity = 4)
        ring.push(0.9f)
        repeat(4) { ring.push(0.1f) } // overwrites the 0.9 slot
        assertFalse(ring.anyAbove(threshold = 0.5f, frames = 100))
        assertEquals(0.1f, ring.last)
    }

    @Test
    fun windowLargerThanRecordedClampsToRecorded() {
        val ring = VadRing()
        ring.push(0.9f)
        assertTrue(ring.anyAbove(threshold = 0.5f, frames = 100))
    }
}
```

- [x] **Step 2: Run to confirm failure**

Run: `./gradlew jvmTest --tests "dev.rizukirr.audx.VadRingTest"`
Expected: FAIL — compilation error, unresolved reference `VadRing`.

- [x] **Step 3: Implement VadRing**

Create `src/commonMain/kotlin/VadRing.kt` with exactly:

```kotlin
package dev.rizukirr.audx

/**
 * Fixed-capacity ring of the most recent per-frame VAD probabilities.
 *
 * Owns all window arithmetic behind [isSpeaking]. Pure Kotlin so the logic
 * is identical on every target and unit-testable without native code.
 */
internal class VadRing(private val capacity: Int = DEFAULT_CAPACITY) {

    private val values = FloatArray(capacity)
    private var next = 0   // write index
    private var count = 0  // frames recorded, saturates at capacity

    /** Probability from the most recent frame; 0f before any frame. */
    var last: Float = 0f
        private set

    fun push(vad: Float) {
        last = vad
        values[next] = vad
        next = (next + 1) % capacity
        if (count < capacity) count++
    }

    /** True if any of the newest [frames] recorded probabilities exceed [threshold]. */
    fun anyAbove(threshold: Float, frames: Int): Boolean {
        val window = minOf(frames, count)
        var idx = next
        repeat(window) {
            idx = (idx - 1 + capacity) % capacity
            if (values[idx] > threshold) return true
        }
        return false
    }

    companion object {
        /** 1 second of 10ms frames. */
        const val DEFAULT_CAPACITY = 100
    }
}
```

- [x] **Step 4: Run to confirm pass**

Run: `./gradlew jvmTest --tests "dev.rizukirr.audx.VadRingTest"`
Expected: PASS (6 tests).

- [x] **Step 5: Commit**

```bash
git add src/commonMain/kotlin/VadRing.kt src/jvmTest/kotlin/VadRingTest.kt
git commit -m "feat: add VadRing — windowed history of per-frame VAD probabilities"
```

---

### Task 3: lastVad + isSpeaking on Audx → verify: full `./gradlew jvmTest compileKotlinLinuxX64 compileKotlinMingwX64 compileKotlinAndroidNativeArm64 compileKotlinAndroidNativeX64` BUILD SUCCESSFUL

**Files:**
- Modify: `src/commonMain/kotlin/Audx.kt`
- Modify: `src/jvmMain/kotlin/Audx.kt`
- Modify: `src/nativeMain/kotlin/Audx.kt`
- Test: `src/jvmTest/kotlin/AudxJvmTest.kt`

- [x] **Step 1: Write the failing integration test**

In `src/jvmTest/kotlin/AudxJvmTest.kt`, add this test method inside the `AudxJvmTest` class (after `constructorRejectsInvalidArguments`):

```kotlin
    @Test
    fun vadStateBeforeAndAfterProcessing() {
        Audx(sampleRate = 16000).use { audx ->
            assertEquals(0f, audx.lastVad)
            assertFalse(audx.isSpeaking())

            val input = ShortArray(audx.frameSize) {
                Random.nextInt(-3000, 3000).toShort()
            }
            val output = ShortArray(audx.frameSize)
            val vad = audx.process(input, output)

            assertEquals(vad, audx.lastVad)
            // vad is always >= 0 (negatives throw), so -1f must register as speech
            assertTrue(audx.isSpeaking(threshold = -1f))
            // probabilities never exceed 1, and the comparison is strict
            assertFalse(audx.isSpeaking(threshold = 1f))
        }
    }
```

- [x] **Step 2: Run to confirm failure**

Run: `./gradlew jvmTest --tests "dev.rizukirr.audx.AudxJvmTest"`
Expected: FAIL — compilation error, unresolved references `lastVad` / `isSpeaking`.

- [x] **Step 3: Extend the common contract**

Replace the full contents of `src/commonMain/kotlin/Audx.kt` with:

```kotlin
package dev.rizukirr.audx

const val FRAME_RATE: Int = 48_000
const val QUALITY_DEFAULT: Int = 4
const val QUALITY_VOIP = 3
const val QUALITY_MIN: Int = 0
const val QUALITY_MAX: Int = 10

/** Duration of one audx frame in milliseconds (fixed by the C library). */
const val FRAME_MS: Int = 10

expect class Audx(
    sampleRate: Int = FRAME_RATE,
    resampleQuality: Int = QUALITY_DEFAULT,
) : AutoCloseable {
    val sampleRate: Int
    val frameSize: Int

    /** Speech probability from the most recent process() call; 0f before any frame. */
    val lastVad: Float

    internal val vadRing: VadRing

    fun process(input: ShortArray, output: ShortArray): Float
    fun isClosed(): Boolean
    override fun close()
}

/**
 * Debounced voice-activity state.
 *
 * True if any frame processed within the last [hangoverMs] of audio had a
 * speech probability above [threshold]. Onset is instant (one frame above
 * threshold flips the state true); release waits for [hangoverMs] of
 * consecutive sub-threshold audio. The window counts processed frames
 * (stream time), not wall-clock time. `hangoverMs = 0` degenerates to the
 * raw last-frame comparison.
 *
 * May lag a concurrent process() call by one frame; no stronger
 * cross-thread guarantee is made.
 */
fun Audx.isSpeaking(threshold: Float = 0.5f, hangoverMs: Int = 200): Boolean {
    val frames = (hangoverMs / FRAME_MS).coerceAtLeast(1)
    return vadRing.anyAbove(threshold, frames)
}

// Shared guards — every actual delegates here so the contract (and its
// error messages) cannot drift between platforms.

internal fun validateCreateArgs(sampleRate: Int, resampleQuality: Int) {
    require(sampleRate > 0) { "sampleRate must be positive (got $sampleRate)" }
    require(resampleQuality in QUALITY_MIN..QUALITY_MAX) {
        "resampleQuality must be in $QUALITY_MIN..$QUALITY_MAX (got $resampleQuality)"
    }
}

internal fun validateFrame(frameSize: Int, input: ShortArray, output: ShortArray) {
    require(input.size == frameSize) {
        "input must be $frameSize samples (got ${input.size})"
    }
    require(output.size == frameSize) {
        "output must be $frameSize samples (got ${output.size})"
    }
}

internal fun checkVadResult(vad: Float): Float {
    check(vad >= 0f) { "audx_process_int failed (returned $vad)" }
    return vad
}
```

- [x] **Step 4: Wire the JVM actual**

In `src/jvmMain/kotlin/Audx.kt`, make these three edits:

(a) After the `private val lock = Any()` declaration, add:

```kotlin
    internal actual val vadRing: VadRing = VadRing()

    actual val lastVad: Float get() = vadRing.last
```

(b) Replace the body of `process` so the result is pushed into the ring (full method):

```kotlin
    actual fun process(input: ShortArray, output: ShortArray): Float {
        validateFrame(frameSize, input, output)
        synchronized(lock) {
            check(!closed) { "Audx is closed" }
            val vad = checkVadResult(nativeProcess(handle, input, output))
            vadRing.push(vad)
            return vad
        }
    }
```

(c) No other changes to this file.

- [x] **Step 5: Wire the native actual**

In `src/nativeMain/kotlin/Audx.kt`, make these two edits:

(a) After the `actual val frameSize` declaration, add:

```kotlin
    internal actual val vadRing: VadRing = VadRing()

    actual val lastVad: Float get() = vadRing.last
```

(b) Replace the `process` method with (full method):

```kotlin
    actual fun process(input: ShortArray, output: ShortArray): Float {
        validateFrame(frameSize, input, output)
        check(closed.value == 0) { "Audx is closed" }

        val vad = input.usePinned { pinnedIn ->
            output.usePinned { pinnedOut ->
                audx_process_int(state, pinnedIn.addressOf(0), pinnedOut.addressOf(0))
            }
        }
        val checked = checkVadResult(vad)
        vadRing.push(checked)
        return checked
    }
```

- [x] **Step 6: Run the full verification matrix**

Run: `./gradlew jvmTest compileKotlinLinuxX64 compileKotlinMingwX64 compileKotlinAndroidNativeArm64 compileKotlinAndroidNativeX64`
Expected: `BUILD SUCCESSFUL`; jvmTest runs 12 tests (6 VadRingTest + 6 AudxJvmTest including the new one), all passing.

- [x] **Step 7: Commit**

```bash
git add src/commonMain/kotlin/Audx.kt src/jvmMain/kotlin/Audx.kt src/nativeMain/kotlin/Audx.kt src/jvmTest/kotlin/AudxJvmTest.kt
git commit -m "feat: add lastVad and debounced isSpeaking(threshold, hangoverMs) API"
```

---

### Task 4: Document and republish → verify: `./gradlew publishToMavenLocal` BUILD SUCCESSFUL

**Files:**
- Modify: `README.md`

- [x] **Step 1: Document the VAD API**

In `README.md`, insert this section between the `## Usage` section's code block and `## Verify`:

````markdown
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
````

- [x] **Step 2: Republish to mavenLocal**

Run: `./gradlew publishToMavenLocal`
Expected: `BUILD SUCCESSFUL` (the sample module consumes this snapshot).

- [x] **Step 3: Commit**

```bash
git add README.md
git commit -m "docs: document lastVad and isSpeaking VAD API"
```

---

### Task 5: Android build infra + sample skeleton → verify: `./gradlew :sample-android:assembleDebug` BUILD SUCCESSFUL

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Create: `sample-android/build.gradle.kts`
- Create: `sample-android/src/main/AndroidManifest.xml`
- Create: `sample-android/src/main/java/dev/rizukirr/audx/sample/MainActivity.kt`

- [x] **Step 1: Repositories and module include**

Replace the full contents of `settings.gradle.kts` with:

```kotlin
pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        mavenLocal()
    }
}

rootProject.name = "audx-kmp"
include(":sample-android")
```

- [x] **Step 2: Version catalog entries**

Replace the full contents of `gradle/libs.versions.toml` with:

```toml
[versions]
kotlin = "2.3.21"
agp = "9.2.0"
composeBom = "2026.04.01"
activityCompose = "1.13.0"
coreKtx = "1.18.0"
kotlinxCoroutines = "1.10.2"

[libraries]
androidx-core-ktx = { group = "androidx.core", name = "core-ktx", version.ref = "coreKtx" }
androidx-activity-compose = { group = "androidx.activity", name = "activity-compose", version.ref = "activityCompose" }
androidx-compose-bom = { group = "androidx.compose", name = "compose-bom", version.ref = "composeBom" }
androidx-compose-ui = { group = "androidx.compose.ui", name = "ui" }
androidx-compose-material3 = { group = "androidx.compose.material3", name = "material3" }
kotlinx-coroutines-android = { module = "org.jetbrains.kotlinx:kotlinx-coroutines-android", version.ref = "kotlinxCoroutines" }

[plugins]
kotlinMultiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" }
androidApplication = { id = "com.android.application", version.ref = "agp" }
composeCompiler = { id = "org.jetbrains.kotlin.plugin.compose", version.ref = "kotlin" }
```

- [x] **Step 3: Root plugin declarations**

In `build.gradle.kts`, replace the `plugins { ... }` block with:

```kotlin
plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.composeCompiler) apply false
    `maven-publish`
}
```

- [x] **Step 4: Sample module build file**

Create `sample-android/build.gradle.kts` with exactly (AGP 9 idioms — built-in Kotlin, no `kotlin-android` plugin — mirrored from the proven audx-android sample):

```kotlin
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.androidApplication)
    alias(libs.plugins.composeCompiler)
}

kotlin {
    compilerOptions {
        jvmTarget = JvmTarget.JVM_21
    }
}

android {
    namespace = "dev.rizukirr.audx.sample"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "dev.rizukirr.audx.sample"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation("dev.rizukirr:audx-kmp:0.1.0-SNAPSHOT")
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
}
```

- [x] **Step 5: Manifest**

Create `sample-android/src/main/AndroidManifest.xml` with exactly:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest xmlns:android="http://schemas.android.com/apk/res/android">

    <uses-permission android:name="android.permission.RECORD_AUDIO" />

    <application
        android:label="Audx VAD"
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

- [x] **Step 6: Skeleton activity (replaced in Task 7)**

Create `sample-android/src/main/java/dev/rizukirr/audx/sample/MainActivity.kt` with exactly:

```kotlin
package dev.rizukirr.audx.sample

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Text("Audx VAD sample — UI lands in the next task")
            }
        }
    }
}
```

- [x] **Step 7: Build the module**

Run: `./gradlew :sample-android:assembleDebug`
Expected: `BUILD SUCCESSFUL`. Also re-run `./gradlew jvmTest` once — expected `BUILD SUCCESSFUL` — to prove the library module is unaffected by the AGP addition.

- [x] **Step 8: Commit**

```bash
git add settings.gradle.kts gradle/libs.versions.toml build.gradle.kts sample-android/
git commit -m "feat: add :sample-android module skeleton (AGP 9.2, Compose)"
```

---

### Task 6: Bundle JNI shims → verify: `unzip -l sample-android/build/outputs/apk/debug/sample-android-debug.apk` lists `lib/arm64-v8a/libaudx_jni.so` and `lib/x86_64/libaudx_jni.so`

**Files:**
- Create: `sample-android/src/main/jniLibs/arm64-v8a/libaudx_jni.so`
- Create: `sample-android/src/main/jniLibs/x86_64/libaudx_jni.so`

- [x] **Step 1: Copy the shims from audx-realtime**

```bash
mkdir -p sample-android/src/main/jniLibs/arm64-v8a sample-android/src/main/jniLibs/x86_64
cp /home/rizki/Projects/audx-realtime/libs/arm64-v8a/libaudx_jni.so sample-android/src/main/jniLibs/arm64-v8a/
cp /home/rizki/Projects/audx-realtime/libs/x86_64/libaudx_jni.so sample-android/src/main/jniLibs/x86_64/
```

- [x] **Step 2: Confirm the shims export the full JNI surface**

Run: `nm -D sample-android/src/main/jniLibs/x86_64/libaudx_jni.so | grep Java_`
Expected: four symbols — `nativeCreate`, `nativeDestroy`, `nativeFrameSize`, `nativeProcess`.

- [x] **Step 3: Rebuild and inspect the APK**

```bash
./gradlew :sample-android:assembleDebug
unzip -l sample-android/build/outputs/apk/debug/sample-android-debug.apk | grep libaudx_jni
```

Expected: two lines, `lib/arm64-v8a/libaudx_jni.so` and `lib/x86_64/libaudx_jni.so`.

- [x] **Step 4: Commit**

```bash
git add sample-android/src/main/jniLibs/
git commit -m "feat: bundle per-ABI audx_jni shims in the sample app"
```

---

### Task 7: VAD demo UI → verify: `./gradlew :sample-android:assembleDebug` BUILD SUCCESSFUL with the full UI in place

**Files:**
- Create: `sample-android/src/main/java/dev/rizukirr/audx/sample/VadEngine.kt`
- Modify: `sample-android/src/main/java/dev/rizukirr/audx/sample/MainActivity.kt`

- [ ] **Step 1: The audio engine**

Create `sample-android/src/main/java/dev/rizukirr/audx/sample/VadEngine.kt` with exactly:

```kotlin
package dev.rizukirr.audx.sample

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import dev.rizukirr.audx.Audx
import dev.rizukirr.audx.isSpeaking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class VadUiState(
    val isRecording: Boolean = false,
    val vad: Float = 0f,
    val rawSpeaking: Boolean = false,
    val debouncedSpeaking: Boolean = false,
    val error: String? = null,
)

/**
 * Owns one Audx instance and a microphone loop: reads one 10ms frame at a
 * time, denoises it, and publishes raw vs debounced VAD state for the UI.
 */
class VadEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val audx = Audx(sampleRate = SAMPLE_RATE)
    private var job: Job? = null

    val state = MutableStateFlow(VadUiState())

    @SuppressLint("MissingPermission")
    fun start() {
        if (job != null) return
        job = scope.launch(Dispatchers.IO) {
            val minBuf = AudioRecord.getMinBufferSize(
                SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                maxOf(minBuf * 4, audx.frameSize * 10),
            )
            val input = ShortArray(audx.frameSize)
            val output = ShortArray(audx.frameSize)
            try {
                check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord init failed" }
                record.startRecording()
                state.update { it.copy(isRecording = true, error = null) }
                while (isActive) {
                    // read can return short counts: accumulate exactly one frame
                    var filled = 0
                    while (filled < input.size) {
                        val n = record.read(input, filled, input.size - filled)
                        check(n > 0) { "AudioRecord read error: $n" }
                        filled += n
                    }
                    val vad = audx.process(input, output)
                    state.update {
                        it.copy(
                            vad = vad,
                            rawSpeaking = vad > 0.5f,
                            debouncedSpeaking = audx.isSpeaking(),
                        )
                    }
                }
            } catch (e: Exception) {
                state.update { it.copy(error = e.message ?: e.toString()) }
            } finally {
                runCatching { record.stop() }
                record.release()
                state.update {
                    it.copy(
                        isRecording = false,
                        vad = 0f,
                        rawSpeaking = false,
                        debouncedSpeaking = false,
                    )
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    fun destroy() {
        stop()
        scope.cancel()
        audx.close()
    }

    companion object {
        const val SAMPLE_RATE = 16_000
    }
}
```

- [ ] **Step 2: The UI**

Replace the full contents of `sample-android/src/main/java/dev/rizukirr/audx/sample/MainActivity.kt` with:

```kotlin
package dev.rizukirr.audx.sample

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val engine = VadEngine()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                VadScreen(engine)
            }
        }
    }

    override fun onDestroy() {
        engine.destroy()
        super.onDestroy()
    }
}

@Composable
fun VadScreen(engine: VadEngine) {
    val state by engine.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasPermission = granted
        if (granted) engine.start()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Audx VAD demo", style = MaterialTheme.typography.titleLarge)

        LinearProgressIndicator(
            progress = { state.vad },
            modifier = Modifier.fillMaxWidth(),
        )
        Text("VAD probability: %.2f".format(state.vad))

        IndicatorRow(label = "Raw (vad > 0.5, flickers)", active = state.rawSpeaking)
        IndicatorRow(label = "Debounced isSpeaking()", active = state.debouncedSpeaking)

        Button(onClick = {
            when {
                state.isRecording -> engine.stop()
                hasPermission -> engine.start()
                else -> permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }) {
            Text(if (state.isRecording) "Stop" else "Record")
        }

        state.error?.let {
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun IndicatorRow(label: String, active: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label)
        Box(
            modifier = Modifier
                .size(20.dp)
                .background(
                    color = if (active) Color(0xFF2E7D32) else Color(0xFFBDBDBD),
                    shape = CircleShape,
                )
        )
    }
}
```

- [ ] **Step 3: Build**

Run: `./gradlew :sample-android:assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Commit**

```bash
git add sample-android/src/main/java/
git commit -m "feat: VAD demo UI — live meter, raw vs debounced indicators"
```

---

### Task 8: Emulator verification (manual gate) → verify: screenshot during recording shows the meter and both indicators; logcat has no FATAL from dev.rizukirr.audx.sample

**Files:** none (verification only)

- [ ] **Step 1: Device check**

Run: `adb devices`
Expected: at least one `device` line. **If empty, STOP and ask the user to boot an emulator** (e.g. `$ANDROID_HOME/emulator/emulator -avd $($ANDROID_HOME/emulator/emulator -list-avds | head -1) &`) — do not continue headless.

- [ ] **Step 2: Install and grant**

```bash
adb install -r sample-android/build/outputs/apk/debug/sample-android-debug.apk
adb shell pm grant dev.rizukirr.audx.sample android.permission.RECORD_AUDIO
adb shell am start -n dev.rizukirr.audx.sample/.MainActivity
```

Expected: `Success` from install; activity starts.

- [ ] **Step 3: Drive and observe**

```bash
adb exec-out screencap -p > /tmp/vad-idle.png
```

Read `/tmp/vad-idle.png`; locate the Record button; tap it via `adb shell input tap <x> <y>`; wait 3 seconds; then:

```bash
adb exec-out screencap -p > /tmp/vad-recording.png
adb logcat -d | grep -E "FATAL|AndroidRuntime.*dev.rizukirr" | head
```

Expected: `/tmp/vad-recording.png` shows the button reading "Stop", the probability text, and the two indicator rows (emulator mic may yield low/zero VAD — the *UI updating without crashing* is the gate; speak into the host mic if it is wired through). Logcat grep returns nothing.

- [ ] **Step 4: Report**

No commit. Report the two screenshots and logcat result to the user — this is the spec's manual verification evidence.
