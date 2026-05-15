# RBE Chess — Status

Last updated: 2026-05-15 (M1 step 2b TTS scaffold landed; awaiting on-device verification of speech + inactivity prompt).

This file is the single-glance state of the project. Updated at the end of
each session, or as part of the commit that closes a sub-step. If the
verification table or step checklist drifts more than a row or two, treat
as session-priority cleanup before doing other work.

## Where we are

- **Milestone:** M1 — Pocket Mode loop (keyboard → UCI → bestmove → TTS).
- **In flight:** M1 step 2b — TTS scaffold landed; phone speaker
  verified ("loud and clear" per user). Outstanding: dual-BT test
  (Bluefruit keypad paired *and* BT earbuds for audio routing) before
  this row moves to "Last completed."
- **Last completed:** M1 step 2a — 4-coordinate cycler grammar with
  Logcat feedback (commit `e5e51c0`, pushed). Side track: Arduino keypad
  firmware vendored, cleaned up, and rewritten as **firmware v1**
  (`b30215b`) — non-blocking BLE send queue, press FIFO, FIRMWARE_VERSION
  fingerprint via boot LED blink + "RBE Keypad v1" advertised name.
  Verified on hardware: input feels buttery smooth, no missed presses.
- **Next:** dual-BT verification (BT earbuds + Bluefruit keypad
  simultaneously, audio routes to earbuds), then M1 step 2c —
  Pocket Mode shell.

## M1 implementation checklist

The order is fixed by `RBE_CHESS_M1_POCKET_MODE_ADDENDUM.md` §"M1
Implementation Order" with the cycler-grammar refinement. Tick boxes
as steps land:

- [x] **1 / 2a** Keyboard input, cycler, `MoveBuffer`, `KeyboardGrammar`,
      `HardwareKeyboardHandler`. Logcat-only feedback. (`e5e51c0`)
- [~] **2b** TTS scaffold: `SpeechOutput`, `BestMoveSpeaker`,
      `SpokenMoveFormatter` landed. Each cycle press speaks; 2.5 s
      `lifecycleScope` job fires *"Move … to …?"*. Build + 27/27 unit
      tests green. **Awaits on-device check** that audio routes to BT
      A2DP and the prompt timing feels right. Promotion still deferred
      to 2d.
- [ ] **2c** Pocket Mode shell: black/minimal `PocketModeScreen`,
      keep-screen-awake while active, tap-to-exit.
- [ ] **3** Stockfish PoC: drop `libstockfish.so` into
      `jniLibs/arm64-v8a/`, implement `StockfishProcessEngine`, prove a
      hardcoded UCI loop returns a `bestmove`.
- [ ] **4** Wire `Space` commit to engine; speak bestmove; auto-advance
      the bestmove into board state.
- [ ] **5** Test BT keyboard in Pocket Mode on the S22 Ultra.
- [ ] **post-M1** `AccessibilityService` spike for true screen-off
      (optional; revisit AGP/SDK 36 first per AGENT_NOTES).

## Verification status

| Surface | Status | Note |
|---|---|---|
| `./gradlew assembleDebug` | green | re-confirmed 2026-05-15 with `speech/` package |
| `:app:testDebugUnitTest` | 27 / 27 green | adds `SpokenMoveFormatterTest` (10) |
| Compose preview (`ui/AppRoot.kt`) | renders | confirmed in AS |
| App launch on S22 Ultra | green | confirmed 2026-05-14 |
| BT keyboard input on-device | green | Bluefruit paired as "RBE Keypad v1", all 5 keycodes received and dispatched correctly 2026-05-14 |
| Compose recomposition on state change | green | required `@Immutable` on `MoveBuffer` to defeat strong-skipping |
| Firmware v1 input latency | green | non-blocking BLE state machine; user reports "buttery smooth" 2026-05-14 |
| Per-press TTS on-device | green (phone speaker) | "loud and clear" on S22 Ultra speaker 2026-05-15 |
| 2.5 s inactivity prompt on-device | green (phone speaker) | fires on pause, cancels on next press |
| TTS routing to BT A2DP speaker | not yet verified | dual-BT test (earbuds + keypad) is next |
| Stockfish UCI loop | not implemented | step 3 |

## Open follow-ups (scheduled, not blockers)

- Bump AGP and `compileSdk` back to 36 before the post-M1 screen-off
  spike. (AGENT_NOTES §"Build configuration — deviations".)
- "User types their own moves too" mode — future-mode candidate, logged
  in AGENT_NOTES §"Keyboard grammar — hardware-aware V1" → Deferred list.
- Promotion mapping (`D=N, F=B, J=R, K=Q, Space=Q`) is a best-guess.
  Verify with the user before the promo-pick code lands.
- Double-tap Space semantics — TBD.

## Where to read next (precedence: high → low)

1. `AGENT_NOTES.md` — routing layer + every architectural deviation from
   the addendum. Hardware-aware keyboard grammar lives here.
2. `RBE_CHESS_M1_POCKET_MODE_ADDENDUM.md` — M1 spec for everything not
   superseded in AGENT_NOTES.
3. `RBE_CHESS_APP_HANDOFF.md` — original project spec.
4. `BUILD_FIXES_2025_05_14.md` — sidecar narrative on the AGP/SDK
   deviation (Gemini's pass on 2026-05-14).
5. `firmware/RBE_32u4_chess/README.md` — firmware build instructions
   and library dependencies. Read before touching `*.ino`.

## Memory pointers

Project memory under
`%USERPROFILE%\.claude\projects\C--Users-John-Documents-Personal-Projects-RBE-Chess\memory\`
captures user/hardware/grammar context. `MEMORY.md` there is the index.
