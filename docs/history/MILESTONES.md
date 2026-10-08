# RBE Chess -- Milestone History

Frozen record of the M1/M2/M5 checklists, the firmware battery saga, and
the on-device verification table as of 2026-07-06. Current state lives in
[`STATUS.md`](../../STATUS.md); this file is history and is not kept up to date.

## M1 implementation checklist

The order is fixed by `RBE_CHESS_M1_POCKET_MODE_ADDENDUM.md` section "M1
Implementation Order" with the cycler-grammar refinement. Tick boxes
as steps land:

- [x] **1 / 2a** Keyboard input, cycler, `MoveBuffer`, `KeyboardGrammar`,
      `HardwareKeyboardHandler`. Logcat-only feedback. (`e5e51c0`)
- [x] **2b** TTS scaffold: `SpeechOutput`, `BestMoveSpeaker`,
      `SpokenMoveFormatter`. Each cycle press speaks; 2.5 s
      `lifecycleScope` job fires *"Move ... to ...?"*. Verified on phone
      speaker and dual-BT (BT earbuds + Bluefruit keypad). Promotion
      still deferred to 2d. (`b86f0a4`)
- [x] **2c** Pocket Mode shell: `PocketModeState`, `PocketModeController`
      (`FLAG_KEEP_SCREEN_ON` + brightness dim/restore), `PocketModeScreen`
      (full black, long-press onExit). "Enter Pocket Mode" button on
      the normal screen. `BestMoveSpeaker.speakCommit()` -> "Calculating"
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

## Firmware v3 -> v4 -> v5 -- battery reporting saga

Single new piece of work post-M2, later adjusted after dogfood:

- **v3 (broken)**: tried the standard BLE Battery Service via
  `AT+BLEBATTEN=on`. On this module's AT firmware that command returns
  ERROR, and `setup_helper.h` treated the failure as fatal via
  `error()` -- so the keypad bricked (LED steady fast blink, never
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
  back above 30 %. `FIRMWARE_VERSION` 4 -> 5 (5-blink boot,
  `RBE Keypad v5` BLE name).
- **v6 (repeat chord)**: keep v5 battery behavior and map
  Thumb+Middle to `R` for repeat-last spoken output.
- **v7 (duplicate-key batching fix)**: keep v6 behavior but split
  adjacent duplicate queued keys across separate `AT+BleKeyboard=...`
  commands. This preserves fast repeated cycler taps while avoiding
  Android held-key repeat behavior. Hardware-confirmed after flashing
  v7: no keyboard spam observed in dogfood.
- **v8 (input-gated battery reports)**: keep the same HID `Bnnn`
  report format, but stop idle timer pushes. When the report timer is
  due, the next real button/chord queues the battery packet. This avoids
  typing `B071` into unrelated apps after RBE Chess is closed. Pending
  flash/hardware verification.

The custom-GATT BAS path (`AT+GATTADDSERVICE` + `AT+GATTADDCHAR`)
remains an option if we ever want Android's Settings UI to show the
percentage too. Punted unless something explicitly needs it -- the
HID-stream path covers the in-app + TTS requirements end-to-end with
no Android Settings dependency.

## Beyond M2 -- roadmap

M1 proved the move loop; M2 makes the *game* operable from the keypad.
Landed after M2:

- **In-app board viewer.** Display-only Compose board on the
  normal in-game screen, oriented with the selected Stockfish/player
  side at the bottom. It projects `MoveHistory` from the start position,
  renders in-square rank/file labels and piece letters, highlights last move
  / current input / pending committed move, and draws arrows for those move
  affordances. It intentionally has no touch input; the board only changes
  through Stockfish auto-advance and keyboard-entered moves.
- **Repeat-last spoken output.** Firmware v6 maps Thumb+Middle to `R`;
  the app maps it to `RepeatLast` and now prefers the last board-changing
  spoken event. Transient statuses such as illegal-move warnings and
  autofill announcements do not replace the replay target.
- **Duplicate-key batching fix.** Firmware v7 splits adjacent duplicate
  keypresses across BLE commands so rapid repeated cycler taps count
  without app-side repeat suppression.
- **M4 legality guard.** Stockfish `go perft 1` now validates
  keypad-entered moves before history mutation. This prevents illegal
  waiting moves from desyncing app history from Stockfish's actual
  position; the dogfood trigger was `e2e4 d7d5 e4d5 e8d7 g1f3 e7e5
  d5e6 a7a5`, where black was in check and `a7a5` was illegal.
- **M3 terminal handling.** Stockfish `bestmove (none)` is parsed as a
  terminal state instead of a move. Mate-score info classifies checkmate;
  otherwise the app calls it stalemate. Terminal positions speak a
  replayable phrase and stop normal move input until Undo/New Game.
- **M5 autocomplete.** `MoveBuffer.copyFromEngine()` can
  prefill UCI coordinates, and `MoveAutofill` picks only unambiguous
  legal moves: exactly one legal move in the position, or exactly one
  legal move from the source square the user entered. First press on an
  autofilled coordinate reads the preset value without advancing it.
  The score-gap path uses Stockfish `searchmoves` / `MultiPV` through
  `StockfishEngine.scoredMoves()` and autofills only when the best scored
  legal candidate beats the runner-up by the configured margin. Source-square
  autocomplete waits for the inactivity-prompt delay before querying the
  engine, so normal rank/file scrolling can pass through suggestible squares.
- **Ordinary check announcements.** `StockfishEngine.isSideToMoveInCheck()`
  reads Stockfish's `d` output and parses the `Checkers:` line. Move speech
  includes "Check" for non-terminal checking moves without changing the
  terminal checkmate/stalemate path.
- **Terminal state after board-changing moves.** After each appended typed
  move or AutoAdvance engine reply, the app checks for zero legal replies and
  classifies checkmate/stalemate from `Checkers:`. This catches engine-delivered
  mates that return a normal `bestmove` instead of `bestmove (none)`.
- **Speech pacing.** `SpeechSink.speakQueued()` lets AutoAdvance engine replies
  and follow-up hints such as forced-move autocomplete wait behind the
  replayable board move phrase while per-press speech still uses flush
  semantics.
- **Mini 5-button keyboard simulator.** `MiniKeyboardInput` mirrors the
  hardware/chord mapping in pure Kotlin, and `MiniKeyboardPanel` exposes
  a tiny on-screen keypad for no-hardware app dogfood. It is intentionally
  UI-only: injected keys go through the same Activity menu/game handlers
  as real HID events.
- **Promotion pick state.** If the user commits a four-coordinate move and
  Stockfish's legal moves only contain promotion-suffixed variants of that
  base move, the app prompts for the promotion piece instead of calling it
  illegal. The chosen piece is then committed as normal UCI, e.g. `e7e8q`.
  Promotion dogfood is nice-to-have, not a blocker for the current phone pass.
- **PGN/FEN text export.** Finished games expose a small keypad menu:
  F/Ring and J/Middle cycle options, Thumb selects. "Save PGN/FEN" writes
  a timestamped `.txt` with a full FEN and PGN-style UCI movetext; "New game"
  returns to the start menu. Live-game Hold+Index now means "end current
  game" so forfeits/abandoned games can also be exported.
- **Session resume.** `SessionSnapshotCodec` serializes the Activity-owned
  game state to app private SharedPreferences via `SessionStore`. Restored
  sessions always come back in normal screen mode, not Pocket Mode, and do
  not resurrect in-flight engine jobs or pending arrows.

What's still deferred:

- **Cancel/clear current input buffer.** Deferred indefinitely for now; Undo
  plus retype is the current workaround.
- ~~**Richer draw detection.**~~ Landed 2026-07-06: repetition, 50/75-move
  rule, and insufficient material via `DrawDetector` (see above). Still not
  covered: claiming a draw *on the user's behalf* (deliberate -- the app only
  announces claimable draws) and draw offers/agreed draws.
- **Evaluation-based autocomplete dogfood.** The score-gap path is implemented
  and JVM/build verified. Dedicated score-margin tuning is deferred
  indefinitely unless dogfood shows it feels pushy or confusing.

Further out: clock / time control, draw offers, takebacks, opening book.

## M5 implementation checklist -- Autocomplete & Predictive Entry

- [x] **A1** `MoveBuffer.copyFromEngine(uci)` implementation.
- [x] **E1** Score-gap autocomplete path: `StockfishEngine.scoredMoves()` uses `searchmoves` / `MultiPV`, parses `info score ... pv ...`, and `MoveAutofill.clearBestScoredMove()` requires a configured margin before autofill.
- [x] **A2** Predictive Trigger: legal-only and score-gap versions landed. Once `from` coordinates are fixed and the user pauses for the inactivity-prompt delay, the app queries legal moves and autofills if the source has exactly one legal move or one scored candidate is clearly ahead.
- [x] **A3** Forced Move Detection: after each applied ply/undo, the app checks `count(legalMoves) == 1` and pre-fills the whole move when true.
- [x] **S1** Read Autocomplete: autofill announcements are queued behind the current move phrase, and the inactivity prompt reads the prefilled buffer.
- [x] **A4** Manual Mode guard: autocomplete never auto-commits; it only mutates the buffer and waits for Thumb.

## Verification status

| Surface | Status | Note |
|---|---|---|
| `./gradlew assembleDebug` | green | re-confirmed 2026-07-06 after draw detection |
| Draw detection on-device | needs dogfood | automatic draws end the game with spoken reason; threefold/50-move only announce "A draw can be claimed by..."; easiest check: shuffle knights back and forth from the start position (threefold hint on the 3rd occurrence, auto-draw on the 5th) |
| `:app:testDebugUnitTest` | 183 / 183 green | adds `DrawDetectorTest` (12) and draw cases in exporter/speaker/session tests; previously includes `NarrativeToneTest` (5), `MoveNarrativeTest` (7), `UciAnalysisParserTest` (5), `SessionSnapshotCodecTest` (3), `GameTextExporterTest` (6), `PromotionPickStateTest` (6), `BatteryTelemetrySmootherTest` (5), `MiniKeyboardInputTest` (3), `MoveAutofillTest` (10), `MoveBufferTest` (17), `BestMoveSpeakerTest` (18), `FakeStockfishEngineTest` (13), `BoardProjectorTest` (7), `UciPerftParserTest` (3), `UciBestMoveParserTest` (4), `UciScoredMoveParserTest` (4), and `UciCheckersParserTest` (3); `StockfishProcessEngine` + the Activity-level commit flow are Android-bound |
| Display-only board viewer | green (JVM/build) | projects startpos + UCI history, supports castling/promotion/en passant display, last/current/pending highlights and arrows |
| `scripts/fetch-stockfish.sh` | green | idempotent; verifies ELF magic; size-checked against the sf_18 release |
| Compose preview (`ui/AppRoot.kt`) | renders | confirmed in AS |
| App launch on S22 Ultra | green | confirmed 2026-05-14 |
| BT keyboard input on-device | green | Bluefruit paired as "RBE Keypad v1", all 5 keycodes received and dispatched correctly 2026-05-14 |
| Firmware v7 duplicate-key batching | green | User-confirmed 2026-05-15: pre-v7 Notepad reproduced held-key spam (`B08...` then repeated `8` until another key); after flashing v7, no keyboard problems observed. |
| Firmware v8 input-gated battery reports | green (semi-thorough dogfood) | User-confirmed 2026-05-16: no idle battery-report typing issue observed; battery packet still queues behind real input once due. |
| Compose recomposition on state change | green | required `@Immutable` on `MoveBuffer` to defeat strong-skipping |
| Firmware v1 input latency | green | non-blocking BLE state machine; user reports "buttery smooth" 2026-05-14 |
| Per-press TTS on-device | green (phone speaker) | "loud and clear" on S22 Ultra speaker 2026-05-15 |
| 2.5 s inactivity prompt on-device | green (phone speaker) | fires on pause, cancels on next press |
| TTS routing to BT A2DP speaker | green | dual-BT verified 2026-05-15: earbuds + Bluefruit keypad together, audio routes to earbuds |
| Pocket Mode entry/exit on-device | needs recheck | enter dims + keeps awake was green 2026-05-15; exit gesture changed from tap-anywhere to long-press 2026-05-17 |
| Stockfish UCI loop on-device | green | Initial proof button verified boot -> uci/uciok -> isready/readyok -> position startpos -> go movetime 1000 -> bestmove spoken via TTS 2026-05-15; the temporary button has since been removed from the normal screen. |
| Thumb -> engine -> bestmove on-device | green (semi-thorough dogfood) | User-confirmed 2026-05-16: real game loop has been tested through repeated physical-piece play enough to move on to M5. |
| Firmware v2 chord detection | green | User-confirmed 2026-05-15: hold Thumb + tap Pinky/Ring/Index emits the right HID codes. |
| Start menu navigation on-device | green | User-confirmed 2026-05-15: cold launch lands in StartMenu, TTS speaks the intro, Ring/Middle cycle, Thumb selects. |
| Manual mode toggle on-device | green | User-confirmed 2026-05-15: Thumb+Ring flips mode and TTS announces. |
| Undo on-device | green | User-confirmed 2026-05-15: Thumb+Pinky drops the last pair, TTS confirms. |
| End game / export menu on-device | needs recheck | Hold+Index now ends a live game and opens finished-game options instead of immediately returning to StartMenu. |
| Session resume on-device | needs recheck | New SharedPreferences-backed snapshot restore should resume live/finished games after app process death/relaunch. |
| Full keypad game loop on-device | green (semi-thorough dogfood) | User-confirmed 2026-05-16: firmware v8 plus repeated game-loop play are good enough for the next feature slice. |
| Firmware v3 BAS battery percentage | broken | v3 made `AT+BLEBATTEN=on` failure fatal; the nRF51 module's AT firmware doesn't support that command, so the keypad bricked into `error()`. |
| Firmware v4 BAS init non-fatal | green | Confirmed via serial: `AT+BLEBATTEN=on` returns ERROR on this module, warning logged, boot continues. Keypad works as keyboard, no BAS visible to Android. |
| Firmware v5 HID-stream battery report | green | User-confirmed 2026-05-15: in-app "Keypad battery: 90%" populated shortly after pairing. TTS warning thresholds not yet exercised at low battery. |
