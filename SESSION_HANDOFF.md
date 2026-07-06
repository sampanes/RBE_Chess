# RBE Chess Session Handoff

Use this file first after a context reset, then read `STATUS.md` and
`AGENT_NOTES.md` for full routing details.

## Current State

- Draw detection is implemented (2026-07-06). A new pure-Kotlin
  `chess/DrawDetector.kt` replays history through the new shared
  `chess/PositionReplay.kt` (which now also backs `BoardProjector` and
  `FenExporter` — the three duplicated replay implementations are gone).
  It tracks repetition using FIDE position identity (placement + side to
  move + castling rights + en passant *only when actually capturable*),
  the halfmove clock, and dead positions (K vs K, K+minor vs K,
  same-color-bishops-only). Automatic draws — fivefold repetition,
  75-move rule, insufficient material — end the game exactly like
  checkmate/stalemate: new `TerminalState.DRAW_*` values map to new
  `GameEndReason.DRAW_*` values, speak "Draw by ...", open the
  finished-game export menu, export as `1/2-1/2`, and survive session
  resume. Claimable draws — threefold repetition, 50-move rule — do NOT
  end the game: the app queues "A draw can be claimed by ..." behind the
  move phrase (it never replaces the repeat-last target) and play
  continues, because the physical-board opponent may not claim and
  auto-ending a winning position would be wrong. Draw checks run after
  the user's typed move and after AutoAdvance engine replies;
  checkmate/stalemate keep precedence (a mating move trumps the 75-move
  rule, per FIDE).
- User has semi-thoroughly dogfooded firmware v8 input-gated battery
  reports and the repeated physical-piece game loop; both are good enough
  to move on.
- Battery telemetry smoothing is implemented. The app holds the previous
  accepted keypad battery percentage through a single low/critical outlier,
  requires repeated low samples before low/critical TTS warnings, and rearms
  warnings after a report at or above 30%.
- Promotion pick state is implemented. A four-coordinate promotion base move
  such as `e7e8` now opens a one-key pick state when legal moves contain
  suffixed candidates. D/Pinky chooses knight, F/Ring bishop, J/Middle rook,
  and K/Index or Thumb/Space queen. Dogfood for this is nice-to-have/deferred.
- Finished-game export is implemented. Checkmate/stalemate and live-game
  Hold+Index enter a finished-game menu with `Save PGN/FEN` and `New game`.
  Saving writes a timestamped `.txt` with FEN plus PGN-style UCI movetext to
  `Downloads/RBE Chess` on Android 10+.
- Session resume persistence is implemented. A SharedPreferences-backed
  snapshot restores phase, history, buffer, side, mode, terminal/finished
  state, promotion pick, battery display, and mini-keyboard visibility before
  first render. It deliberately does not restore Pocket Mode or in-flight
  engine pending state.
- M5 autocomplete is implemented in the working tree. It still prefers the
  conservative legal-only paths first: autofill the whole buffer if there is
  exactly one legal move in the position, or autofill after source-square
  entry if exactly one legal move starts from that source. If multiple
  candidates remain, the app asks Stockfish for scored candidates with
  `searchmoves` / `MultiPV` and autofills only when the best move clears the
  configured score margin. Source-square autocomplete is debounced by the
  inactivity-prompt delay so normal scrolling can pass through suggestible
  squares before the engine fills the target.
- A no-hardware mini 5-button keyboard simulator is implemented. The tiny
  `Mini off` / `Mini on` toggle appears on both the start menu and normal
  in-game screen. When enabled, P/R/M/I/T inject the same app keys as the
  physical keypad. `Hold` latches Thumb for one chord, remapping the four
  finger buttons to U/M/R/N. `B%` cycles mock battery reports through the
  normal battery handler.
- Autofilled coordinates are marked read-pending, so the first D/F/J/K
  press reads the preset value without advancing it. The second press
  advances normally.
- Repeat-last now prefers the last board-changing event. Illegal-move
  warnings and autocomplete announcements do not replace the repeat target.
  In active games it now appends a compact Kotlin narrative phrase when one
  adds information: captures, recaptures/trades, castling, promotion, forced
  moves, and one legal reply. Emotional scoring is wired through
  `StockfishEngine.analyzePosition()`, which normalizes raw UCI evals to White
  POV before `NarrativeTone` computes the mover-relative "Blunder", "Mistake",
  "Sharp", or "Great move" prefix.
- Ordinary non-terminal "check" announcements are implemented. The app uses
  Stockfish's `d` board dump and parses `Checkers:` after legal typed moves
  and AutoAdvance engine replies; "Check" is part of the replayable move
  phrase. Evaluation-based autocomplete is implemented but still needs
  on-device dogfood before tuning the margin or calling it done.
- Terminal state is now checked after every appended move, including
  AutoAdvance engine replies, so engine-delivered mate/stalemate stops the
  game immediately instead of asking for another move. Manual-mode suggestions
  prefill the buffer, and AutoAdvance replies plus forced/suggestion autofill
  speech queue behind the current move phrase instead of interrupting it.
- Engine bestmove think time is now 4 seconds (`ENGINE_MOVETIME_MS = 4000`)
  after dogfood showed the slower cadence feels better.
- Board readability and Pocket Mode soft-lock have landed. The board draws
  stronger last/current/pending highlights, overlays arrows for those moves,
  and shows a "Pending: ..." line while the engine is checking/thinking.
  Pocket Mode exit is now a long press on the black screen, not tap-anywhere.

## Recent Commits

- `4357c75 Handle terminal Stockfish results`
- `a458c7c Gate battery reports on keypad input`
- `58e33db Add Stockfish legality guard`
- `1cec434 Record v7 keypad dogfood`
- `1ba1411 Split duplicate keypad batches`

## Verification Already Run

- After normalized repeat emotion: `.\gradlew.bat test` passed.
- After normalized repeat emotion: `.\gradlew.bat assembleDebug` passed.
- After terminal handling: `.\gradlew.bat test` passed.
- After terminal handling: `.\gradlew.bat assembleDebug` passed.
- After M5 conservative autocomplete: `.\gradlew.bat test` passed.
- After M5 conservative autocomplete: `.\gradlew.bat assembleDebug` passed.
- After mini keyboard simulator: `.\gradlew.bat test` passed.
- After mini keyboard simulator: `.\gradlew.bat assembleDebug` passed.
- After score-gap autocomplete: `.\gradlew.bat test` passed.
- After score-gap autocomplete: `.\gradlew.bat assembleDebug` passed.
- After ordinary check announcements: `.\gradlew.bat test` passed.
- After ordinary check announcements: `.\gradlew.bat assembleDebug` passed.
- After dogfood fixes for autocomplete timing / terminal state / speech
  pacing: `.\gradlew.bat test` passed.
- After dogfood fixes for autocomplete timing / terminal state / speech
  pacing: `.\gradlew.bat assembleDebug` passed.
- After 4 s engine movetime bump: `.\gradlew.bat test` passed.
- After 4 s engine movetime bump: `.\gradlew.bat assembleDebug` passed.
- After board/Pocket affordance pass: `.\gradlew.bat test` passed.
- After board/Pocket affordance pass: `.\gradlew.bat assembleDebug` passed.
- After battery telemetry smoothing: `.\gradlew.bat test` passed.
- After battery telemetry smoothing: `.\gradlew.bat assembleDebug` passed.
- After promotion pick state: `.\gradlew.bat test` passed.
- After promotion pick state: `.\gradlew.bat assembleDebug` passed.
- After PGN/FEN text export: `.\gradlew.bat test` passed.
- After PGN/FEN text export: `.\gradlew.bat assembleDebug` passed.
- After terminal export-menu speech: `.\gradlew.bat test` passed.
- After terminal export-menu speech: `.\gradlew.bat assembleDebug` passed.
- After session resume persistence: `.\gradlew.bat test` passed.
- After session resume persistence: `.\gradlew.bat assembleDebug` passed.
- Firmware v8 was not compiled from this shell because `arduino-cli` /
  `arduino` are not on PATH.

## Next Recommended Work

0. Dogfood draw detection with the mini keyboard: from a fresh game,
   shuffle both knights out and back repeatedly — expect the claim hint
   ("A draw can be claimed by threefold repetition.") on the third
   occurrence of the position and an automatic "Draw by repetition."
   ending on the fifth, opening the finished-game export menu with a
   `1/2-1/2` PGN result. Note the engine usually avoids repetition when
   it is ahead, so use Manual mode to force the shuffle from both sides.
1. Install/dogfood the board/Pocket and battery smoothing changes with the
   mini keyboard first, then repeat with hardware. Check pending arrows during
   legality/engine think, last/current/pending highlights, ignored cycler taps
   while pending, long-press Pocket Mode exit, and `B%` behavior across
   `88 -> 19 -> 4 -> 3 -> 73`.
2. Re-check full M5 autocomplete in the same pass: forced whole-move
   autofill, source-square-only-move autofill, score-gap suggestion autofill,
   first tap reads preset values, second tap advances, Thumb still must
   commit, source autocomplete waits while scrolling, engine replies and
   forced autofill speech wait behind the move phrase, terminal
   mates/stalemates stop the game, and ordinary checks are spoken after
   checking moves.
3. Re-check end-game/export flow: Hold+Index during a live game should end
   the game, Ring/Middle should cycle `Save PGN/FEN` / `New game`, Thumb on
   save should create a `.txt` in `Downloads/RBE Chess`, and Thumb on new game
   should return to the start menu. Also check the same finished menu after
   checkmate/stalemate if naturally reached.
4. Re-check resume: start a live game, enter a partial buffer, force-stop or
   swipe away/relaunch, and confirm it says `Resumed game...` with the same
   board, mode, side, buffer, and battery display. Repeat once from the
   finished-game export menu.
5. Promotion dogfood is nice-to-have/deferred. Clear-buffer and score-gap
   margin tuning are deferred indefinitely unless dogfood surfaces a real
   pain point.
6. If board arrows feel cluttered, simplify the overlay before adding any new
   gameplay feature.

## Cautions

- Do not reintroduce app-side repeat suppression for cycler keys; firmware
  v7 solved adjacent duplicate HID batching without dropping rapid human
  taps.
- Keep `StockfishEngine` as the process-management boundary.
- Do not append illegal moves or `bestmove (none)` to `MoveHistory`.
- If editing firmware, bump `FIRMWARE_VERSION` on meaningful flashes.
