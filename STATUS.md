# RBE Chess — Status

Last updated: 2026-05-15 (M1 step 4 wired: Space → engine → bestmove + auto-advance history. Code-complete, JVM-green; awaiting on-device verification).

This file is the single-glance state of the project. Updated at the end of
each session, or as part of the commit that closes a sub-step. If the
verification table or step checklist drifts more than a row or two, treat
as session-priority cleanup before doing other work.

## Where we are

- **Milestone:** M1 — Pocket Mode loop (keyboard → UCI → bestmove → TTS).
- **In flight:** M1 step 4 on-device verification (code-complete, awaiting
  hardware run).
- **Last completed (code):** M1 step 4 — Space commit wired to engine.
  New `chess/MoveHistory.kt` (immutable UCI ply list, Compose-stable).
  `MainActivity.commitMove(opponentMove)` speaks "Calculating", boots the
  engine (idempotent), calls `engine.bestMove(history + opp, movetime=1000)`,
  speaks the bestmove via `SpokenMoveFormatter`, and on success appends
  both plies to `moveHistory` (engine error leaves history unchanged so the
  user can retry without a corrupt move list). Concurrent-Space guard
  drops re-entered commits while `engineJob.isActive`. `NormalScreen`
  surfaces the running history line. JVM: 42 / 42 tests green;
  `assembleDebug` green. The Stockfish PoC button still works against
  startpos as an independent diagnostic.
- **Next:** verify step 4 end-to-end on the S22 Ultra (type a move on
  the Bluefruit keypad → engine bestmove spoken via BT earbuds →
  history advances → next move continues from advanced state).

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
- [~] **4** Wire `Space` commit to engine; speak bestmove; auto-advance
      the bestmove into board state. `chess/MoveHistory.kt` + new
      `MainActivity.commitMove(...)` ship the wiring. JVM-green;
      hardware verification pending.
- [ ] **5** Test BT keyboard in Pocket Mode on the S22 Ultra.
- [ ] **post-M1** `AccessibilityService` spike for true screen-off
      (optional; revisit AGP/SDK 36 first per AGENT_NOTES).

## Beyond M1 — roadmap

M1 is deliberately a "one game, you play white, no exit" loop — it
proves the *move* loop works. The next milestone covers everything M1
punts on around the *game* itself.

### M2 — Game Lifecycle

No design decisions locked in yet; captured here so these stop being
invisible follow-ups:

- **Side select.** Tell the app which colour you're playing before the
  first move. M1's implicit default is **user + engine play as black**:
  `MainActivity.commitMove(opponentMove)` treats every typed move as
  the *opponent's*, so the first ply typed is white's opener and the
  engine answers for black. To play white instead, the engine has to
  move first off an empty history before the user types anything, and
  the user then enters the opponent's replies — same loop after the
  bootstrap. Likely a startup screen toggle or a dedicated keystroke
  before any moves are typed.
- **New game / reset.** Keyboard-driven way to clear `MoveHistory` and
  start over without relaunching the app. Today the only escape hatch
  is force-stop + reopen.
- **Resign / end-of-game state.** Explicit "this game is over" signal
  so the engine stops being asked for moves on a finished position,
  and TTS can say something useful ("you resigned" / "checkmate" /
  "stalemate").
- **Terminal-position detection.** Notice checkmate, stalemate, and
  forced draws automatically. Stockfish already reports mate in its
  `info` lines; cheapest path is probably to consume those rather
  than ship our own rules engine.

Not in M2 (further out): clock / time control, draw offers, takebacks,
PGN/FEN export, opening book.

## Verification status

| Surface | Status | Note |
|---|---|---|
| `./gradlew assembleDebug` | green | re-confirmed 2026-05-15 after step 4 changes |
| `:app:testDebugUnitTest` | 42 / 42 green | adds `MoveHistoryTest` (6); `StockfishProcessEngine` + the Activity-level commit flow are Android-bound |
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
| Space → engine → bestmove on-device | pending | step-4 commit path: type a move on the Bluefruit, press Space, "Calculating" + bestmove spoken, history advances by two plies. Not yet run on hardware. |

## Open follow-ups (scheduled, not blockers)

- Bump AGP and `compileSdk` back to 36 before the post-M1 screen-off
  spike. (AGENT_NOTES §"Build configuration — deviations".)
- *Manual mode* (working title: "user types their own moves too") —
  optional toggle where Stockfish still speaks the bestmove but the
  user types their *own* move on the cycler instead of the engine
  auto-advancing. Motivation: deliberately deviate from the engine —
  e.g. play a sub-optimal move against a friend, then recover from
  there — while still hearing the engine's pick as a hidden advisor.
  Gated on whether real-world M1 testing finds the cycler intuitive
  enough that doubling input per move isn't punishing. Logged in
  AGENT_NOTES §"Keyboard grammar — hardware-aware V1" → Deferred list.
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
