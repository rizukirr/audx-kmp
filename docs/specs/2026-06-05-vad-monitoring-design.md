---
title: VAD monitoring API (isSpeaking)
date: 2026-06-05
status: draft
---

# VAD monitoring API (isSpeaking) — Design

## Problem

`Audx.process()` returns a per-frame speech probability, but consumers must
hand-roll voice-activity logic from it. The Android sample does
`isSpeechDetected = vad > 0.5f` per 10ms frame, which flickers on breaths,
inter-word gaps, and soft consonants. There is no public, named VAD API on the
wrapper; every consumer reinvents thresholding, and any consumer that gates
recording/transmission on the raw comparison loses syllables.

## Goals

- A public, debounced speech-state query on `Audx`:
  `isSpeaking(threshold: Float = 0.5f, hangoverMs: Int = 200): Boolean`.
- Expose the raw last probability as `val lastVad: Float` (for UI meters).
- Identical behavior on every target (logic lives in commonMain).
- `hangoverMs = 0` degenerates to the raw last-frame comparison
  (audx-android semantics remain reachable through the same API).

## Non-goals

- Speech-start / speech-end transition events or callbacks.
- Running metrics (average probability, speech/silence durations).
- Attack-side smoothing (onset detection is instant by design).
- Any C/JNI changes — the probability already crosses the boundary.

## Constraints

- Frame duration is fixed at 10ms (`audx_calculate_frame_sample` =
  `rate * 10 / 1000`), so `hangoverMs / 10` converts to a frame count.
- The hangover window counts processed frames (stream time), not wall-clock
  time: correct for server-side bursty frame arrival.
- Ring capacity fixed at 100 frames (1s of history, 400 bytes); `hangoverMs`
  clamps to that. Larger hangovers are out of scope until a use case exists.
- JVM: reads/writes must be safe against the existing `process()`/`close()`
  locking. Native: single-owner usage assumed, consistent with the rest of
  the class.

## Approach

Pushback was raised (raw last-frame check adds nothing over the existing
call-site comparison; the value is debounce). The user initially chose raw,
then after the flicker explanation chose the debounced design with both
parameters defaulted.

Mechanism — hangover window over recent probabilities:

- `commonMain` gains an internal pure-Kotlin class `VadRing`: a fixed
  `FloatArray(100)` ring buffer, a monotonically increasing frame counter,
  and a `last: Float`. It owns all window arithmetic
  (`anyAbove(threshold, frames)` with clamping to both capacity and frames
  actually processed). Being common and dependency-free, it is unit-testable
  without native code.
- Each `actual Audx` owns one `VadRing` and pushes the probability into it
  after each successful native call (on JVM, inside the existing `lock`).
- The expect class declares `val lastVad: Float` (delegates to the ring) and
  an `internal` ring accessor; the public query is a single commonMain
  extension so the logic exists exactly once:
  `fun Audx.isSpeaking(threshold: Float = 0.5f, hangoverMs: Int = 200)` =
  any of the last `min(hangoverMs / 10, framesProcessed, 100)` probabilities
  `> threshold` (window of at least one frame when `hangoverMs = 0`).
- Before any frame is processed: `lastVad == 0f`, `isSpeaking(...) == false`.
  After `close()`: values freeze at their last state; querying remains legal.
- Onset is instant (one frame above threshold flips the state true);
  release waits for `hangoverMs` of consecutive sub-threshold audio.

Alternatives considered:

- **EMA smoothing** (one float of state): rejected — symmetric smoothing
  delays speech onset as much as the tail; missing the first ~100ms of
  speech is the wrong trade for recording/streaming.
- **Attack/release counter state machine**: rejected — requires the
  threshold at update time, forcing it into the constructor and breaking the
  per-call `isSpeaking(threshold)` signature.
- **Separate `VadMonitor` class**: rejected by user — extra object to wire;
  the state rides on the `Audx` instance consumers already hold.

## Testing

jvmTest (runs against the real native shim):

- Fresh instance: `lastVad == 0f`, `isSpeaking() == false`.
- After one processed frame: `lastVad` equals the value `process()` returned;
  `isSpeaking(threshold = 0f)` true; `isSpeaking(threshold = 1f)` false.
- Hangover: process a frame, record vad `v`; with `threshold` just below `v`,
  `isSpeaking(threshold, hangoverMs = 0)` reflects only the newest frame,
  while a 200ms hangover still reports true after processing several
  silent/low-probability frames (synthetic zero-filled input), and flips
  false once more than `hangoverMs / 10` low frames have passed.
- Common logic (window arithmetic, clamping) is exercised through the same
  tests; no separate native test target is added in this change.

## Open questions

None.
