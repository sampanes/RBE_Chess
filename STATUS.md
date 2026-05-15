# RBE Chess — Status

Last updated: 2026-05-15 (M1 step 3 Stockfish PoC verified end-to-end on hardware — UCI handshake + bestmove from startpos works).

This file is the single-glance state of the project. Updated at the end of
each session, or as part of the commit that closes a sub-step. If the
verification table or step checklist drifts more than a row or two, treat
as session-priority cleanup before doing other work.

## Where we are

- **Milestone:** M1 — Pocket Mode loop (keyboard → UCI → bestmove → TTS).
- **In flight:** nothing — last sub-step shipped clean.
- **Last completed:** M1 step 3 — Stockfish PoC. `engine/` package
  (`StockfishEngine` interface, `StockfishProcessEngine` real impl,
  `FakeStockfishEngine` for tests). Stockfish 18 Android ARMv8
  dot-product binary fetched via `scripts/fetch-stockfish.sh` (109 MB,
  gitignored — too large for GitHub). Packaging fix:
  `useLegacyPackaging = true` so AGP injects `extractNativeLibs="true"`
  and Android writes a real exec'able file at install time (the
  modern `false` default `mmap`s from APK and breaks `Runtime.exec()`
  with ENOENT — AGENT_NOTES corrected). PoC button on the normal
  screen runs `boot` + `bestMove(startpos, movetime=1000)` and speaks
  the result. Verified on the S22 Ultra 2026-05-15.
- **Next:** M1 step 4 — wire Space commit to engine, speak the
  bestmove, auto-advance bestmove into board state.

## M1 implementation checklist

The order is fixed by `RBE_CHESS_M1_POCKET_MODE_ADDENDUM.md` §"M1
Implementation Order" with the cycler-grammar refinement. Tick boxes
as steps land:

- [x] **1 / 2a** Keyboard input, cycler, `MoveBuffer`, `KeyboardGrammar`,
      `HardwareKeyboardHandler`. Logcat-only feedback. (`e5e51c0`)
- [x] **2b** TTS scaffold: `SpeechOutput`, `BestMoveSpeaker`,
      `SpokenMoveFormatter`. Each cycle press speaks; 2.5 s
      `lifecycleScope` job fires *"Move … to …?"*. Verified on phone
      speaker and dual-BT (BT earbuds + Bluefruit keypad). Promotion
      still deferred to 2d. (`b86f0a4`)
- [x] **2c** Pocket Mode shell: `PocketModeState`, `PocketModeController`
      (`FLAG_KEEP_SCREEN_ON` + brightness dim/restore), `PocketModeScreen`
      (full black, tap-anywhere onExit). "Enter Pocket Mode" button on
      the normal screen. `BestMoveSpeaker.speakCommit()` → "Calculating"
      on Space. Verified on the S22 Ultra 2026-05-15.
- [x] **3** Stockfish PoC: `engine/` package + `scripts/fetch-stockfish.sh`
      + `useLegacyPackaging = true` (forces extractNativeLibs). UCI
      handshake + `bestmove` from startpos verified on the S22 Ultra.
- [ ] **4** Wire `Space` commit to engine; speak bestmove; auto-advance
      the bestmove into board state.
- [ ] **5** Test BT keyboard in Pocket Mode on the S22 Ultra.
- [ ] **post-M1** `AccessibilityService` spike for true screen-off
      (optional; revisit AGP/SDK 36 first per AGENT_NOTES).

## Verification status

| Surface | Status | Note |
|---|---|---|
| `./gradlew assembleDebug` | green | re-confirmed 2026-05-15 with `engine/` package + sf binary in jniLibs |
| `:app:testDebugUnitTest` | 36 / 36 green | adds `FakeStockfishEngineTest` (5); `StockfishProcessEngine` is Android-bound |
| `scripts/fetch-stockfish.sh` | green | idempotent; verifies ELF magic; size-checked against the sf_18 release |
| Compose preview (`ui/AppRoot.kt`) | renders | confirmed in AS |
| App launch on S22 Ultra | green | confirmed 2026-05-14 |
| BT keyboard input on-device | green | Bluefruit paired as "RBE Keypad v1", all 5 keycodes received and dispatched correctly 2026-05-14 |
| Compose recomposition on state change | green | required `@Immutable` on `MoveBuffer` to defeat strong-skipping |
| Firmware v1 input latency | green | non-blocking BLE state machine; user reports "buttery smooth" 2026-05-14 |
| Per-press TTS on-device | green (phone speaker) | "loud and clear" on S22 Ultra speaker 2026-05-15 |
| 2.5 s inactivity prompt on-device | green (phone speaker) | fires on pause, cancels on next press |
| TTS routing to BT A2DP speaker | green | dual-BT verified 2026-05-15: earbuds + Bluefruit keypad together, audio routes to earbuds |
| Pocket Mode entry/exit on-device | green | enter dims + keeps awake; BT keypad still drives TTS through earbuds; tap-anywhere exits and restores brightness 2026-05-15 |
| Stockfish UCI loop on-device | green | "Test Stockfish" button: boot → uci/uciok → isready/readyok → position startpos → go movetime 1000 → bestmove returned and spoken via TTS 2026-05-15 |

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
