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
