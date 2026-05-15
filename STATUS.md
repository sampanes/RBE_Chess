# RBE Chess — Status

Last updated: 2026-05-15 (M2 in flight: firmware v2 chords + verbal start menu + manual mode toggle + undo + new-game flow. Code-complete, JVM-green; hardware verification pending for both M1 step 4 and M2).

This file is the single-glance state of the project. Updated at the end of
each session, or as part of the commit that closes a sub-step. If the
verification table or step checklist drifts more than a row or two, treat
as session-priority cleanup before doing other work.

## Where we are

- **Milestone:** M2 — Game Lifecycle. M1 step 4 was committed as
  code-complete-but-untested (`ecbeb6b`); M2 builds on top to make the
  app dogfoodable as a real game loop (start a game, undo, switch modes,
  start over) instead of "one game forever from the implicit opening."
- **In flight:** hardware verification of both M1 step 4 AND M2 — both
  layers ship together since M2 keys the firmware-v2 chords through the
  same Space-commit path step 4 introduced.
- **Last completed (code):** M2 — Space-as-modifier chord support
  (firmware v2 bumped from v1; LED blinks twice on boot, BLE advertises
  `RBE Keypad v2`). Held Space + cycler emits a distinct HID letter:
  Space+D → `U` (undo), Space+F → `M` (manual toggle), Space+K → `N`
  (new game), Space+J reserved (no emission). Space tap alone still
  emits ` ` as commit. App side: `AppPhase` (StartMenu / InGame) +
  `GameMode` (AutoAdvance / Manual). New `StartMenuScreen` is verbal-
  first (F up, J down, Space select; D/K no-op in menu); two options
  Play-as-white / Play-as-black. Play-as-white bootstraps with an
  immediate engine query on empty history so Stockfish speaks white's
  opener; AutoAdvance also appends it. Manual mode keeps the engine's
  reply advisory ("Suggestion: ...") and only appends the user's typed
  move. Undo cancels in-flight engine work, drops the last pair of
  plies (or one if odd), clears the buffer, says "Undid last move."
  New-game chord cancels engine work, clears history, returns to the
  start menu. JVM: 52 / 52 tests green; `assembleDebug` green.
- **Next:** flash firmware v2 (`RBE_32u4_chess.ino`), confirm 2 LED
  blinks on boot + BLE name `v2`, re-pair phone if needed. Then on the
  S22 Ultra: verify M1 step 4 (commit → engine → bestmove + auto-
  advance) AND M2 (chord paths actually deliver `U`/`M`/`N` HID keys;
  start menu navigates; undo/manual/new-game all do the right thing
  end-to-end).

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
      hardware verification pending. Bundled into the M2 hardware
      test rather than verified standalone.
- [ ] **5** Test BT keyboard in Pocket Mode on the S22 Ultra.
- [ ] **post-M1** `AccessibilityService` spike for true screen-off
      (optional; revisit AGP/SDK 36 first per AGENT_NOTES).

## M2 implementation checklist

- [x] **F1** Firmware v2: Space-as-modifier chord detection. Bumped
      `FIRMWARE_VERSION` to 2; new `BtnEdge` tri-state replaces the
      v1 press-only `is_changed`. Space defers emission until release
      (and only emits if no chord fired); held Space + cycler emits
      `U`/`M`/`N` (Space+J reserved). README updated with the chord
      table.
- [x] **A1** App: `ChessKey.UNDO/TOGGLE_MANUAL/NEW_GAME` + grammar
      actions; `HardwareKeyboardHandler` routes `KEYCODE_U/M/N`.
- [x] **A2** `MoveHistory.undoLastPair()` (drops up to two plies);
      Undo handler cancels engine work, clears buffer, speaks "Undid
      last move."
- [x] **A3** `GameMode` toggle. AutoAdvance appends engine reply;
      Manual leaves it advisory (`speakSuggestion`).
- [x] **A4** `AppPhase.StartMenu` + `StartMenuScreen`. F/J cycle,
      Space selects. Play-as-white triggers a bootstrap engine query;
      Play-as-black waits for user input. Cold launch speaks the menu
      intro.
- [x] **A5** New-game chord returns to StartMenu, cancels engine,
      clears history + buffer.
- [ ] **HW1** Flash firmware v2 to the Bluefruit Feather; verify
      2-blink boot, `RBE Keypad v2` BLE name, re-pair if Android keys
      pairings by name.
- [ ] **HW2** S22 Ultra end-to-end: cold launch lands in start menu
      with TTS; F/J navigate; Space picks a side; play-as-white hears
      engine opener; play-as-black waits for input; commit cycle still
      works (M1 step 4); each chord does its thing.

## Beyond M2 — roadmap

M1 proved the move loop; M2 makes the *game* operable from the keypad.
What's still deferred:

- **Resign / end-of-game state.** Explicit "this game is over" signal
  so the engine stops being asked for moves on a finished position,
  and TTS can say something useful ("you resigned" / "checkmate" /
  "stalemate"). Per the user's mental model, this is currently
  redundant with New Game — the app doesn't track win/loss, and
  ending a game means starting another. Revisit if/when scoring or
  game-history persistence shows up.
- **Terminal-position detection.** Notice checkmate, stalemate, and
  forced draws automatically. Stockfish already reports mate in its
  `info` lines; cheapest path is probably to consume those rather
  than ship our own rules engine.
- **Reserved Space+J chord.** Firmware v2 detects the chord but emits
  nothing for it. No assignment yet — candidates: undo a single ply
  rather than a pair, exit Pocket Mode, repeat-last-utterance.

Further out: clock / time control, draw offers, takebacks,
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
| Firmware v2 chord detection | pending | Hold Space + tap D/F/K, expect HID `U`/`M`/`N` instead of Space-then-letter. Space tap alone still emits ` `. Not yet flashed. |
| Start menu navigation on-device | pending | Cold launch lands in StartMenu, TTS speaks the intro; F/J cycle options; Space selects. |
| Manual mode toggle on-device | pending | Space+F flips mode; engine reply is advisory, history advances by one ply per Space. |
| Undo on-device | pending | Space+D drops the last pair of plies; TTS confirms. |
| New game on-device | pending | Space+K returns to StartMenu mid-game; history clears. |

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
