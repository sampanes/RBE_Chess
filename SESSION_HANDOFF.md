# RBE Chess Session Handoff

Use this file first after a context reset, then read `STATUS.md` and
`AGENT_NOTES.md` for full routing details.

## Current State

- User has semi-thoroughly dogfooded firmware v8 input-gated battery
  reports and the repeated physical-piece game loop; both are good enough
  to move on.
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
- Engine bestmove think time was already 3 seconds (`ENGINE_MOVETIME_MS =
  3000`); no code bump was needed for that dogfood note.

## Recent Commits

- `4357c75 Handle terminal Stockfish results`
- `a458c7c Gate battery reports on keypad input`
- `58e33db Add Stockfish legality guard`
- `1cec434 Record v7 keypad dogfood`
- `1ba1411 Split duplicate keypad batches`

## Verification Already Run

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
- Firmware v8 was not compiled from this shell because `arduino-cli` /
  `arduino` are not on PATH.

## Next Recommended Work

1. Install/dogfood full M5 autocomplete with the mini keyboard first, then
   repeat with hardware. Check forced whole-move autofill,
   source-square-only-move autofill, score-gap suggestion autofill, first tap
   reads preset values, second tap advances, Thumb still must commit, source
   autocomplete waits while scrolling, engine replies and forced autofill
   speech wait behind the move phrase, terminal mates/stalemates stop the
   game, and ordinary checks are spoken after checking moves.
2. Tune or disable score-gap suggestion autofill based on dogfood; keep it
   score-based and explicit rather than guessing from legal move shape alone.
3. Board readability is no longer the top blocker because the user now has
   actual pieces, but keep it available if dogfood still requires frequent
   phone checks.
4. Treat Pocket Mode soft-lock as fallback polish if true screen-off remains
   infeasible. Candidate unlock/exit gestures: double tap, long press or tap
   in a specific area, or volume up/down. This is less important than board
   improvements.

## Cautions

- Do not reintroduce app-side repeat suppression for cycler keys; firmware
  v7 solved adjacent duplicate HID batching without dropping rapid human
  taps.
- Keep `StockfishEngine` as the process-management boundary.
- Do not append illegal moves or `bestmove (none)` to `MoveHistory`.
- If editing firmware, bump `FIRMWARE_VERSION` on meaningful flashes.
