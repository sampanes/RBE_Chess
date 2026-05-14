# RBE Chess — Status

Last updated: 2026-05-14 (after firmware vendoring + cleanup).

This file is the single-glance state of the project. Updated at the end of
each session, or as part of the commit that closes a sub-step. If the
verification table or step checklist drifts more than a row or two, treat
as session-priority cleanup before doing other work.

## Where we are

- **Milestone:** M1 — Pocket Mode loop (keyboard → UCI → bestmove → TTS).
- **In flight:** nothing — last sub-step shipped clean.
- **Last completed:** M1 step 2a — 4-coordinate cycler grammar with
  Logcat feedback (commit `e5e51c0`, pushed). Side track: Arduino keypad
  firmware was vendored into `firmware/RBE_32u4_chess/` and cleaned up
  (char-buffer instead of `String`, stable-for-N-ms debounce, LED-blink
  error indicator, BluefruitConfig.h pin-collision note, README).
- **Next:** M1 step 2b — TTS scaffold + 2.5 s inactivity prompt.

## M1 implementation checklist

The order is fixed by `RBE_CHESS_M1_POCKET_MODE_ADDENDUM.md` §"M1
Implementation Order" with the cycler-grammar refinement. Tick boxes
as steps land:

- [x] **1 / 2a** Keyboard input, cycler, `MoveBuffer`, `KeyboardGrammar`,
      `HardwareKeyboardHandler`. Logcat-only feedback. (`e5e51c0`)
- [ ] **2b** TTS scaffold: `SpeechOutput`, `BestMoveSpeaker`,
      `SpokenMoveFormatter`. Each cycle press speaks; 2.5 s inactivity
      timer fires *"Move … to …?"*. Promotion can fold in here or 2d.
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
| `./gradlew assembleDebug` | green | confirmed in AS terminal 2026-05-14 |
| `:app:testDebugUnitTest` | 17 / 17 green | `MoveBufferTest` + `KeyboardGrammarTest` |
| Compose preview (`ui/AppRoot.kt`) | renders | confirmed in AS |
| App launch on S22 Ultra | **NOT VERIFIED** | not run on-device since M1 step 1; risk grows each step |
| BT keyboard input on-device | **NOT VERIFIED** | pair-and-test pending — pairs with 2b/2c |
| TTS over BT speakers | not implemented | step 2b |
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
