# RBE Chess -- Status

Last updated: 2026-10-09 (narrative-sidekick narrator core wired in as the repeat ladder).

Single-glance state of the project. Update it in the commit that changes
what is true here. Long-form history (milestone checklists, firmware
battery saga, the old verification table) is frozen in
[`docs/history/MILESTONES.md`](docs/history/MILESTONES.md).

## Where we are

- **Milestones M1 (Pocket Mode loop), M2 (game lifecycle chords), M3
  (terminal states), M4 (legality guard) and M5 (autocomplete) have landed.**
  The full keypad game loop has been dogfooded on the S22 Ultra with
  firmware v8 and is good enough to build on.
- **Firmware:** v8. Finger cycler keys, Thumb-as-modifier chords
  (`U`/`M`/`R`/`N`), duplicate-key batching fix (v7), and input-gated
  `Bnnn` battery reports (v8).
- **App features beyond the core loop:** display-only board with
  last/current/pending highlights and arrows, Pocket Mode long-press exit,
  promotion pick state, ordinary check announcements, draw detection
  (automatic: fivefold repetition, 75-move rule, insufficient material end
  the game; claimable: threefold and 50-move only get a spoken hint, since
  the physical opponent may not claim), repeat-last with a
  narrative tail (captures, trades, castling, forced moves, eval-based
  "Blunder"/"Great move" tone), finished-game menu with PGN/FEN text export,
  session resume, battery telemetry smoothing, on-screen mini keypad
  simulator.
- **Draw detection (2026-07-06):** `chess/DrawDetector.kt` replays history
  through the shared `chess/PositionReplay.kt` (which also backs
  `BoardProjector` and `FenExporter`). Repetition uses FIDE position
  identity: placement, side to move, castling rights, and en passant only
  when actually capturable. Checkmate/stalemate take precedence.
- **Latest code change (2026-10-08):** all game logic moved out of
  `MainActivity` into the Android-free `game/` package
  (`GameController`, `GameState`, `AutofillCoordinator`, `NarrativeTracker`).
  `MainActivity` is now only Android wiring. Cancelled engine jobs no
  longer report a fake "Engine error" or clear a move committed right after
  Undo.
- **Narrator (2026-10-09):** the narrative-sidekick spec's agreed first build
  steps are implemented in `narrator/`: pure `narrate(event, level)` for
  L0-L3, SAN-to-speech, move quality, and fork/pin/hanging/trade motifs, on
  top of a new perft-verified legal-move generator (`chess/Position.kt`)
  and SAN formatter (`chess/San.kt`). Every row of the spec's golden
  catalog is a test. It is audible through the **repeat ladder**: the first
  Thumb+Middle is the classic replay, the second speaks L2 (SAN move, motif,
  quality, "engine: <move>, <eval>"), the third L3 (adds the engine's line).
  First-pass speech is unchanged. JVM: 270 / 270 tests green.

## Next

0. Listen to the repeat ladder: after any move press Thumb+Middle three
   times. Decide whether L2/L3 wording works by ear, and whether the terse
   L1 ("black e four." / "engine: e five.") should replace the current
   first-pass speech ("Opponent Black played E seven to E five...").
1. Dogfood draw detection with the mini keypad in Manual mode: shuffle both
   knights out and back from the start. Expect "A draw can be claimed by
   threefold repetition." on the third occurrence and an automatic "Draw by
   repetition." on the fifth, opening the export menu with a `1/2-1/2` result.
2. On-device dogfood of the post-2026-05-17 changes that never got a focused
   phone pass: board arrows/highlights, long-press Pocket exit, finished-game
   export menu, session resume, score-gap autocomplete, battery smoothing
   (`B%` mock cycle 88 -> 19 -> 4 -> 3 -> 73).
3. Same pass, re-check that the GameController refactor changed nothing you
   can hear: commit/reply speech order, undo mid-think, manual mode, promotion.
4. If board arrows feel cluttered, simplify the overlay before any new feature.

## Verification

| Surface | Status |
|---|---|
| `.\gradlew.bat testDebugUnitTest` | 270 / 270 green (2026-10-09) |
| Post-refactor phone smoke test (adb key events) | green (2026-10-08) on the refactor build *before* it was rebased onto draw detection: resume, start as black, type + commit, real Stockfish reply, illegal move, undo mid-think, manual mode, end-game menu. Rebased build is JVM-verified only so far |
| Full keypad game loop on hardware | green, semi-thorough dogfood (2026-05-16) |
| Chords, start menu, manual toggle, undo | green on hardware (2026-05-15) |
| BT keypad + BT earbuds TTS routing | green (2026-05-15) |
| Firmware v8 input-gated battery reports | green (2026-05-16) |
| Board affordances, Pocket long-press exit | needs phone recheck |
| Finished-game export, session resume | needs phone recheck |
| Draw detection on-device | needs dogfood (JVM-covered, incl. controller wiring) |
| Repeat ladder (sidekick L2/L3) on-device | needs dogfood (JVM-covered: golden catalog, perft, controller wiring) |
| Promotion pick on hardware | nice-to-have, not yet exercised |

`StockfishProcessEngine` and the Compose UI are only verified on-device;
everything in `game/`, `input/`, `chess/`, `engine/` parsers, `narrative/`,
`narrator/`, `session/` codec and `speech/` formatting is covered by JVM tests.

## Deferred

- Cancel/clear the current input buffer (workaround: Undo and retype).
- Draw offers / agreed draws. Claiming a draw on the user's behalf is
  deliberately not done; the app only announces claimable draws.
- Score-gap autocomplete margin tuning, unless dogfood says it feels pushy.
- True screen-off / locked-screen keyboard capture (AccessibilityService
  spike). Bump AGP and `compileSdk` to 36 first.
- Standard BLE Battery Service (Android Settings battery %). This nRF51
  module rejects `AT+BLEBATTEN`; would need `AT+GATTADDSERVICE`.
- Double-tap Thumb semantics.
- Further out: clocks, draw offers, opening book.

## Cautions

- Keep `StockfishEngine` as the only process-management boundary.
- Never append illegal moves or `bestmove (none)` to `MoveHistory`.
- Do not reintroduce app-side repeat suppression for cycler keys; firmware
  v7 fixed duplicate HID batching without dropping fast human taps.
- Bump `FIRMWARE_VERSION` on every meaningful firmware flash.
- Source files are ASCII only. Write chess glyphs as `\u` escapes.

## Docs map

- [`README.md`](README.md) -- what the app is, controls, hardware, build.
- [`docs/ENGINEERING_NOTES.md`](docs/ENGINEERING_NOTES.md) -- design
  decisions and the canonical keypad grammar (Stockfish packaging, chords,
  battery protocol, start menu).
- [`docs/AUTOCOMPLETE.md`](docs/AUTOCOMPLETE.md) -- M5 autocomplete design.
- [`docs/NARRATIVE.md`](docs/NARRATIVE.md) -- repeat-last narrative design.
- [`narrative-sidekick/`](narrative-sidekick/) -- narration spec; its
  `HANDOFF.md` tracks what the app's `narrator/` package implements.
- [`firmware/RBE_32u4_chess/README.md`](firmware/RBE_32u4_chess/README.md) --
  firmware build, upload recovery, chord and battery protocol.
- `docs/history/` -- original handoff brief, M1 addendum, 2026-05-14 build
  fixes, milestone history. Superseded where ENGINEERING_NOTES disagrees.
