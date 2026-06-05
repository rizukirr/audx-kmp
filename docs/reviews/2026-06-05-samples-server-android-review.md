# Review — samples (Ktor denoise server + Android recorder app)

**Date:** 2026-06-05
**Spec:** docs/specs/2026-06-05-samples-server-android-design.md
**Plan:** docs/plans/2026-06-05-samples-server-android.md
**Verify report:** docs/verifications/2026-06-05-samples-server-android-verify.md
**Commits under review:** main..HEAD (13e39a2..HEAD) on `vibe/samples-server-android`

## Diff summary

- Files changed: 34 (+2 binary .so files)
- Lines added: 1502, removed: 64 — of the additions, 468 are the two vendored `gradlew` scripts and ~95 the verification report; hand-written sample code is ~600 lines
- Commits: 30 (14 feat/docs task commits, 14 plan-checkbox commits, 1 plan repair, 1 verification/spec-note commit)

## Findings

### Block

(none)

### Warn

- **16 kHz capture is not guaranteed on every Android device.** Only 44.1 kHz is universally guaranteed for `AudioRecord`; 16 kHz works on virtually all modern devices but a failure is possible. Mitigated: `Recorder.kt:33` `check(audioRecord.state == AudioRecord.STATE_INITIALIZED) { "microphone unavailable" }` surfaces it in the status line rather than crashing. No code change recommended for a sample; noting the risk.
- **Playback/overwrite race.** Starting a new recording while `Play Raw` is still playing overwrites `raw.wav` while `MediaPlayer` may hold the file (`MainViewModel.kt:46` writes fixed filenames; `Player.kt` only stops on next `play()`/`onCleared`). Worst case is a playback glitch in a sample app. Follow-up if desired: call `player.stop()` at the top of `startRecording()` (1 line).
- **Weak JSON assertion.** `ApplicationTest.kt:32` asserts `bodyAsText().contains("\"savedAs\"")` rather than decoding the JSON and checking `frames` equals the expected frame count (10 for 1600 samples @160/frame). The saved-file assertions partly compensate. Follow-up test would be ~3 lines.

### Nit

- `app/src/main/jniLibs/` adds ~28 MB of vendored binaries to git history — consistent with this repo's existing precedent (`native/libs/*.a`) and the user-approved plan, but worth remembering when the repo is published.
- WAV writer code is duplicated between `samples/server/Wav.kt` and `samples/android-app/.../WavFile.kt` — mandated by the spec ("own copy; projects standalone"), not a defect.

## Pass results

- **Pass 1 spec coverage:** all Goals/Constraints implemented and verified (see verify report R1–R12); Non-goals respected (no streaming denoise, no auth/TLS, no audio in response, no extra clients). No findings.
- **Pass 2 plan fidelity:** all 14 tasks' Files present in diff; commit messages match plan wording verbatim; commit order follows task order; one authorized plan repair (`compileSdk` 35→36) committed as its own change. No findings.
- **Pass 3 code quality:** no public API divergence from spec naming (`Audx`, `denoise`, `DenoiseResponse`, `UiState`); no uncalled exports; warns above.
- **Pass 4 simplicity:** largest hand-written construct is `MainViewModel.kt` (100 lines) — a state machine with 4 states and 6 user actions; halving it would drop required behavior (status surfacing, file plumbing). No bloat candidates.
- **Pass 5 surgical-diff:** independent auditor verdict `clean`, all files/hunks trace to plan tasks; the two post-plan doc commits (verification report, spec note) are authorized verify-gate outputs.
- **Pass 6 self-critique:** below.

## Self-critique (three risks)

1. **ABI gap on 32-bit devices** — an armeabi-v7a-only device installs the APK but `System.loadLibrary` fails (only arm64-v8a/x86_64 shipped). Mitigation: status line surfaces `UnsatisfiedLinkError`; spec scoped shims to these two ABIs. Follow-up: `ndk.abiFilters` to refuse install on unsupported ABIs (2 lines) if ever distributed.
2. **Device-specific 16 kHz capture failure** — see Warn 1; surfaced, not silently broken.
3. **LAN reachability assumptions** — upload depends on cleartext HTTP + same network; wrong URL yields a caught failure in the status line (`Upload failed: …`), and the README documents `10.0.2.2` for emulators. No silent failure path found.

## Diff

Run: `git diff main..HEAD` in `.vibe-worktrees/2026-06-05-samples-server-android`
(1502 insertions; not inlined here because 468 lines are vendored gradlew scripts and 2 files are 14 MB binaries.)

## Sign-off

- [ ] User reviewed findings.
- [ ] User reviewed diff.
- [ ] User approves proceeding to finish-branch.
