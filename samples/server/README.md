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
