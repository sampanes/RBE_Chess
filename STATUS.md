# RBE Chess — Status

Last updated: 2026-05-15 (Firmware v7 duplicate-key batching fix hardware-confirmed: rapid repeated taps no longer show keyboard spam after flashing v7.)

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
  same Thumb/Space commit path step 4 introduced.
- **Last completed (hardware):** firmware v7 repeated-key batching fix.
  Adjacent duplicate queued keypresses are split across BLE commands
  instead of sent as `AT+BleKeyboard=DD...`, which keeps rapid human
  repeated taps countable without Android treating them as a held key.
  The app-side repeat skip was removed because it dropped legitimate
  fast taps. Undo still leaves repeat memory on the current board state
  rather than the undo action. Dogfood note: before v7, phone Notepad
  reproduced the failure as `B08...` followed by repeated `8` spam until
  another key interrupted it; after flashing v7, no keyboard problems
  were observed.
- **Last completed (code):** M2 — Thumb-as-modifier chord support
  (firmware v2 bumped from v1; LED blinks twice on boot, BLE advertises
  `RBE Keypad v2`). Held Thumb + cycler emits a distinct HID letter:
  Thumb+Pinky → `U` (undo), Thumb+Ring → `M` (manual toggle),
  Thumb+Index → `N` (new game), Thumb+Middle reserved (no emission).
  Thumb tap alone still emits ` ` as commit. App side:
  `AppPhase` (StartMenu / InGame) +
  `GameMode` (AutoAdvance / Manual). New `StartMenuScreen` is verbal-
  first (Ring up, Middle down, Thumb select; Pinky/Index no-op in menu); two options
  Play-as-white / Play-as-black. Play-as-white bootstraps with an
  immediate engine query on empty history so Stockfish speaks white's
  opener; AutoAdvance also appends it. Manual mode keeps the engine's
  reply advisory ("Suggestion: ...") and only appends the user's typed
  move. Undo cancels in-flight engine work, drops the last pair of
  plies (or one if odd), clears the buffer, says "Undid last move."
  New-game chord cancels engine work, clears history, returns to the
  start menu. JVM: 76 / 76 tests green; `assembleDebug` green.
- **Next:** app/gameflow dogfood discussion. The keypad transport is
  stable enough to focus on turn-state sync, commit flow, undo/replay
  semantics, terminal positions, and illegal-move handling.

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
      on Thumb/Space. Verified on the S22 Ultra 2026-05-15.
- [x] **3** Stockfish PoC: `engine/` package + `scripts/fetch-stockfish.sh`
      + `useLegacyPackaging = true` (forces extractNativeLibs). UCI
      handshake + `bestmove` from startpos verified on the S22 Ultra.
- [~] **4** Wire Thumb/Space commit to engine; speak bestmove; auto-advance
      the bestmove into board state. `chess/MoveHistory.kt` + new
      `MainActivity.commitMove(...)` ship the wiring. JVM-green;
      hardware verification pending. Bundled into the M2 hardware
      test rather than verified standalone.
- [ ] **5** Test BT keyboard in Pocket Mode on the S22 Ultra.
- [ ] **post-M1** `AccessibilityService` spike for true screen-off
      (optional; revisit AGP/SDK 36 first per AGENT_NOTES).

## M2 implementation checklist

- [x] **F1** Firmware v2: Thumb-as-modifier chord detection. Bumped
      `FIRMWARE_VERSION` to 2; new `BtnEdge` tri-state replaces the
      v1 press-only `is_changed`. Thumb/Space defers emission until release
      (and only emits if no chord fired); held Thumb + cycler emits
      `U`/`M`/`N` (Thumb+Middle reserved). README updated with the chord
      table.
- [x] **A1** App: `ChessKey.UNDO/TOGGLE_MANUAL/NEW_GAME` + grammar
      actions; `HardwareKeyboardHandler` routes `KEYCODE_U/M/N`.
- [x] **A2** `MoveHistory.undoLastPair()` (drops up to two plies);
      Undo handler cancels engine work, clears buffer, speaks "Undid
      last move."
- [x] **A3** `GameMode` toggle. AutoAdvance appends engine reply;
      Manual leaves it advisory (`speakSuggestion`).
- [x] **A4** `AppPhase.StartMenu` + `StartMenuScreen`. Ring/Middle cycle,
      Thumb selects. Play-as-white triggers a bootstrap engine query;
      Play-as-black waits for user input. Cold launch speaks the menu
      intro.
- [x] **A5** New-game chord returns to StartMenu, cancels engine,
      clears history + buffer.
- [x] **HW1** Flash firmware v2 to the Bluefruit Feather; verify
      2-blink boot, `RBE Keypad v2` BLE name, re-pair if Android keys
      pairings by name. (User-confirmed chord emissions reach the app
      as expected, 2026-05-15.)
- [~] **HW2** S22 Ultra end-to-end: cold launch lands in start menu
      with TTS; Ring/Middle navigate; Thumb picks a side; play-as-white hears
      engine opener; play-as-black waits for input; commit cycle still
      works (M1 step 4); each chord does its thing. Chord paths +
      menu + manual + undo + new-game confirmed; full game loop
      (multiple commit cycles in a row) not yet exercised.

## Firmware v3 → v4 → v5 — battery reporting saga

Single new piece of work post-M2. Three attempts:

- **v3 (broken)**: tried the standard BLE Battery Service via
  `AT+BLEBATTEN=on`. On this module's AT firmware that command returns
  ERROR, and `setup_helper.h` treated the failure as fatal via
  `error()` — so the keypad bricked (LED steady fast blink, never
  advertised, serial monitor caught the one error message only if it
  was already open before boot).
- **v4 (diagnosis)**: made the BAS attempt non-fatal. Serial log
  confirmed `AT+BLEBATTEN=on` returns ERROR on this nRF51 SPI Friend.
  The BAS-via-AT path is dead on this module. Keypad still works as a
  keyboard.
- **v5 (battery HID stream)**: report battery through the existing HID stream
  instead. Firmware enqueues `'B'` + 3 zero-padded ASCII digits (e.g.
  `B025`) into the same FIFO chords/chess input use, every 60 s, first
  push 5 s after boot. App-side `BatteryReportParser` intercepts the
  sequence before the chess grammar sees it, updates a `batteryPct`
  state shown on the normal screen, and issues one-shot TTS warnings
  on crossing 20 % (low) and 5 % (critical), re-armed when % climbs
  back above 30 %. `FIRMWARE_VERSION` 4 → 5 (5-blink boot,
  `RBE Keypad v5` BLE name).
- **v6 (repeat chord)**: keep v5 battery behavior and map
  Thumb+Middle to `R` for repeat-last spoken output.
- **v7 (duplicate-key batching fix)**: keep v6 behavior but split
  adjacent duplicate queued keys across separate `AT+BleKeyboard=...`
  commands. This preserves fast repeated cycler taps while avoiding
  Android held-key repeat behavior. Hardware-confirmed after flashing
  v7: no keyboard spam observed in dogfood.

The custom-GATT BAS path (`AT+GATTADDSERVICE` + `AT+GATTADDCHAR`)
remains an option if we ever want Android's Settings UI to show the
percentage too. Punted unless something explicitly needs it — the
HID-stream path covers the in-app + TTS requirements end-to-end with
no Android Settings dependency.

## Beyond M2 — roadmap

M1 proved the move loop; M2 makes the *game* operable from the keypad.
Landed after M2:

- **Minimal in-app board viewer.** Display-only Compose board on the
  normal in-game screen, oriented with the selected Stockfish/player
  side at the bottom. It projects `MoveHistory` from the start position,
  renders rank/file labels and piece letters, and highlights the source
  and target squares of the last applied UCI move. It intentionally has
  no touch input; the board only changes through Stockfish auto-advance
  and keyboard-entered moves.
- **Repeat-last spoken output.** Firmware v6 maps Thumb+Middle to `R`;
  the app maps it to `RepeatLast` and replays the last replayable
  spoken move/status without changing history or querying Stockfish.
- **Duplicate-key batching fix.** Firmware v7 splits adjacent duplicate
  keypresses across BLE commands so rapid repeated cycler taps count
  without app-side repeat suppression.

What's still deferred:

- **M3 - terminal-state handling.** Detect when the current position is
  over instead of treating `bestmove (none)` like a normal move. Acceptance:
  app says a useful terminal phrase ("checkmate", "stalemate", or "game
  over"), does not auto-append `(none)`, stops asking Stockfish for moves
  until Undo/New Game, and keeps the visible board/history intact. Cheapest
  first pass: make the engine bridge return a structured result that can
  represent `bestmove (none)` and consume Stockfish `info ... mate ...`
  lines where available.
- **M4 - keypad move legality guard.** Reject impossible/illegal user
  input before appending it to `MoveHistory` or sending it as the next
  position. Acceptance: illegal moves leave history unchanged, keep or
  clear the buffer deliberately, and speak a short correction such as
  "Illegal move." Candidate implementation: use a small JVM/Android chess
  rules library if one is clean; otherwise ask Stockfish for legal moves
  from the current position and validate the typed UCI against that set.
- **Battery telemetry smoothing.** A transient `0%` followed by a normal
  value should not immediately fire a critical warning. Require repeated
  low samples or firmware-side averaged/median ADC reads before speaking
  low/critical battery.
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

Further out: clock / time control, draw offers, takebacks,
PGN/FEN export, opening book.

## Verification status

| Surface | Status | Note |
|---|---|---|
| `./gradlew assembleDebug` | green | re-confirmed 2026-05-15 after duplicate-key batching fix |
| `:app:testDebugUnitTest` | 76 / 76 green | includes `BoardProjectorTest` (7) and `BestMoveSpeakerTest` (7); `StockfishProcessEngine` + the Activity-level commit flow are Android-bound |
| Display-only board viewer | green (JVM/build) | projects startpos + UCI history, supports castling/promotion/en passant display, last-move source/target highlights |
| `scripts/fetch-stockfish.sh` | green | idempotent; verifies ELF magic; size-checked against the sf_18 release |
| Compose preview (`ui/AppRoot.kt`) | renders | confirmed in AS |
| App launch on S22 Ultra | green | confirmed 2026-05-14 |
| BT keyboard input on-device | green | Bluefruit paired as "RBE Keypad v1", all 5 keycodes received and dispatched correctly 2026-05-14 |
| Firmware v7 duplicate-key batching | green | User-confirmed 2026-05-15: pre-v7 Notepad reproduced held-key spam (`B08...` then repeated `8` until another key); after flashing v7, no keyboard problems observed. |
| Compose recomposition on state change | green | required `@Immutable` on `MoveBuffer` to defeat strong-skipping |
| Firmware v1 input latency | green | non-blocking BLE state machine; user reports "buttery smooth" 2026-05-14 |
| Per-press TTS on-device | green (phone speaker) | "loud and clear" on S22 Ultra speaker 2026-05-15 |
| 2.5 s inactivity prompt on-device | green (phone speaker) | fires on pause, cancels on next press |
| TTS routing to BT A2DP speaker | green | dual-BT verified 2026-05-15: earbuds + Bluefruit keypad together, audio routes to earbuds |
| Pocket Mode entry/exit on-device | green | enter dims + keeps awake; BT keypad still drives TTS through earbuds; tap-anywhere exits and restores brightness 2026-05-15 |
| Stockfish UCI loop on-device | green | Initial proof button verified boot → uci/uciok → isready/readyok → position startpos → go movetime 1000 → bestmove spoken via TTS 2026-05-15; the temporary button has since been removed from the normal screen. |
| Thumb → engine → bestmove on-device | pending | step-4 commit path: type a move on the Bluefruit, press Thumb, "Calculating" + bestmove spoken, history advances by two plies. Not yet run on hardware. |
| Firmware v2 chord detection | green | User-confirmed 2026-05-15: hold Thumb + tap Pinky/Ring/Index emits the right HID codes. |
| Start menu navigation on-device | green | User-confirmed 2026-05-15: cold launch lands in StartMenu, TTS speaks the intro, Ring/Middle cycle, Thumb selects. |
| Manual mode toggle on-device | green | User-confirmed 2026-05-15: Thumb+Ring flips mode and TTS announces. |
| Undo on-device | green | User-confirmed 2026-05-15: Thumb+Pinky drops the last pair, TTS confirms. |
| New game on-device | green | User-confirmed 2026-05-15: Thumb+Index returns to StartMenu mid-game. |
| Thumb → engine → bestmove on-device | partial | Chord paths verified, but no full game played yet — leaving the commit/engine/auto-advance cycle as not-yet-validated end-to-end. |
| Firmware v3 BAS battery percentage | broken | v3 made `AT+BLEBATTEN=on` failure fatal; the nRF51 module's AT firmware doesn't support that command, so the keypad bricked into `error()`. |
| Firmware v4 BAS init non-fatal | green | Confirmed via serial: `AT+BLEBATTEN=on` returns ERROR on this module, warning logged, boot continues. Keypad works as keyboard, no BAS visible to Android. |
| Firmware v5 HID-stream battery report | green | User-confirmed 2026-05-15: in-app "Keypad battery: 90%" populated shortly after pairing. TTS warning thresholds not yet exercised at low battery. |

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
