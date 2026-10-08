# Narrative Mode

Narrative mode should make the repeat button more useful without turning it
into a board dump. The goal is a short spoken judgment plus one concrete board
fact:

- "Mistake. Traded knights on E four."
- "Forced. King F one."
- "Only move. Queen takes H four. Checkmate."
- "Sharp. Nasty fork."

The architecture splits into two domains:

- **Geometry: Kotlin.** Captures, trades, pins, pressure, forks, discovered
  attacks, material, and short history patterns should come from our own board
  model. Stockfish will not explain those concepts directly.
- **Evaluation: Stockfish.** Blunder/good-move tone should come from score
  deltas, not from hand-written chess heuristics.

Runtime target: Kotlin geometry should be imperceptible. Full-board attack maps
on an 8x8 board are cheap enough to recompute on every committed move. Prefer
clear, tested code first; optimize to bitboards only if profiling proves the
simple model is a real cost.

## Engine Data We Have Today

The current `StockfishEngine` wrapper can already power reliable state labels:

- `legalMoves(history)`: returns all legal UCI moves using `go perft 1`.
- `isSideToMoveInCheck(history)`: parses Stockfish `d` output and its
  `Checkers:` line.
- `bestMove(history, movetimeMs)`: returns only a UCI move or a terminal state.
- `scoredMoves(history, candidates, movetimeMs)`: returns scored candidate
  moves using `searchmoves` and `MultiPV`, currently intended for autocomplete.

From this we can say, without new engine APIs:

- `legalMoves.size == 1`: "Forced" / "Only move"
- `legalMoves.isEmpty() && inCheck`: "Checkmate"
- `legalMoves.isEmpty() && !inCheck`: "Stalemate"
- `inCheck`: "Check"

## Engine Bridge

The first analysis bridge is implemented. `StockfishEngine.analyzePosition()`
judges a position without asking the app to apply the returned best move:

```kotlin
data class AnalysisSummary(
    val whiteCentipawns: Int?,
    val mate: MateScore?,
    val bestMove: String?,
    val principalVariation: List<String>,
)

data class MateScore(
    val winner: ChessSide,
    val plies: Int,
)
```

Implementation:

1. Send `position startpos moves ...`.
2. Run `go movetime X` or `go depth X`.
3. Keep the latest useful `info ... score cp|mate ... pv ...` line.
4. Stop at `bestmove`.
5. Normalize the score before returning it.

### Score Normalization

Do not expose raw UCI score signs to app logic. Stockfish's `score cp` is from
the engine/search point of view, which in practice must be treated as tied to
the side to move unless we normalize it ourselves.

Normalize to **White POV** at the wrapper boundary:

```kotlin
val whiteCentipawns =
    if (sideToMove(history) == ChessSide.WHITE) rawCp else -rawCp
```

Then derive mover-relative deltas in the narrator:

```kotlin
val whiteDelta = after.whiteCentipawns - before.whiteCentipawns
val moverGain =
    if (mover == ChessSide.WHITE) whiteDelta else -whiteDelta
```

Current `NarrativeTone` interpretation:

- `moverGain <= -200`: "Blunder"
- `moverGain in -199..-100`: "Mistake"
- `moverGain in -99..99`: no emotional prefix
- `moverGain in 100..199`: "Sharp"
- `moverGain >= 200`: "Great move"

Mate scores return structured `MateScore`. `AnalysisSummary.whiteComparisonCp`
is the one tested helper that converts mate to a large White-POV comparison
score while preserving winner and distance-to-mate.

## Comparing A Move Against The Ideal

To say whether a move was good, store analysis for the position before the move
and compare it with analysis after the move.

To say what the player should have done instead, also keep the previous
position's `bestMove` / principal variation. The best response after the move is
not the same as the player's missed best move; it belongs to the opponent in the
new position.

For repeat narration we do not need to speak full PV lines at first. Store them
anyway so later phrases can say:

- "Best was Knight F three."
- "Threat is Queen H four."
- "Mate threat."

## Kotlin Geometry Roadmap

The existing `BoardProjector` is enough for display and simple captures, but
narrative geometry should become a real chess-state module. This is worth the
development time if it stays deterministic, fast, and well tested.

Start with a plain model:

- board squares and pieces
- legal-ish piece rays and attacks
- king square lookup
- attack map by side
- defender map by side
- last-move facts: piece moved, captured piece, promotion, castling, en passant
- short move-event history buffer

Then layer higher-order facts:

- absolute pins
- pinned-to-queen / pinned-to-king distinction
- square pressure deltas
- defenders added or removed
- hanging pieces
- forks
- skewers
- discovered attacks
- overloaded defenders
- trapped pieces
- material and exchange balance
- pawn structure events

Stockfish remains the source of "good or bad." Kotlin geometry explains "what
happened."

## Phrase Catalog

Keep phrases economical. The user knows the game context; avoid "White did..."
unless it removes ambiguity.

### Move Facts

| Phrase | Trigger |
| --- | --- |
| "Takes [piece] on [square]." | Destination or en passant capture. |
| "Trades [piece]s on [square]." | Previous move captured on same square and this move recaptures there. |
| "Queens." | Promotion to queen. |
| "Promotes to [piece]." | Non-queen promotion. |
| "Castles." | King moves two files and rook moves. |

### Pressure And Tactics

| Phrase | Trigger |
| --- | --- |
| "Pins the [piece]." | Moved piece creates an absolute pin to king, or major relative pin to queen. |
| "Nasty fork." | Moved piece attacks two valuable targets, especially king/queen/rook. |
| "Discovered attack." | Moving piece opens a rook/bishop/queen ray onto a valuable target. |
| "Piling on [square]." | Attacker count on an occupied enemy square increases. |
| "Reinforces [square]." | Defender count on an owned important square increases. |
| "Hangs [piece]." | Own valuable piece becomes attacked and insufficiently defended. |

### Game State

| Phrase | Trigger |
| --- | --- |
| "Forced." | Side to move before this move had exactly one legal move. |
| "Only move." | Side to move after this move has exactly one legal reply. |
| "Check." | Side to move after this move is in check. |
| "Checkmate." | No legal replies and side to move is in check. |
| "Stalemate." | No legal replies and side to move is not in check. |

### History Patterns

| Phrase | Trigger |
| --- | --- |
| "Trading everything." | Last 3-4 half-moves are captures. |
| "Same piece again." | Same piece moves repeatedly in a short window. |
| "Pawn tension breaks." | Pawn capture resolves a locked/contact pawn structure. |
| "Locking it up." | Pawn push closes a pawn chain/blockade. |
| "Shuffling." | Several quiet non-pawn, non-capture moves in a row. |

### State Summary

These are optional for a later "where are we?" interaction, not the first repeat
implementation.

| Phrase | Trigger |
| --- | --- |
| "Dead even." | Material tied and normalized eval roughly equal. |
| "Up a piece." | Material advantage around a minor piece or more. |
| "Down the exchange." | Rook-for-minor imbalance. |
| "Endgame." | Queens off or low material. |

## Assembly Priority

Repeat narration should produce one compact phrase, not every true fact.

Priority:

1. Terminal state.
2. Emotional prefix from normalized score delta.
3. Forced/only-move status.
4. Most concrete move fact: capture, trade, promotion, castle.
5. Strongest tactical geometry: mate threat, fork, pin, discovered attack,
   hanging piece, pressure delta.
6. Check suffix.
7. Waiting phrase only if needed for usability.

Template:

```text
[Emotion.] [Forced/Only move.] [Move fact or tactic.] [Check/terminal.]
```

Examples:

- "Mistake. Trades knights on E four."
- "Forced. King F one."
- "Sharp. Nasty fork. Check."
- "Great move. Pins the queen."
- "Only move. Queen takes H four. Checkmate."

## Landed Slice

1. `AnalysisSummary` and `StockfishEngine.analyzePosition(...)`.
2. White-POV score normalization in the wrapper.
3. `NarrativeTone` for mover-relative emotional prefixes.
4. `MoveNarrative` for first-pass geometry:
   captures, recaptures/trades, promotion, castling, forced moves, one legal
   reply.
5. Thumb+Middle in an active game speaks the existing replay phrase first, then
   appends the latest narrative when available.

Pins, square pressure, and richer attack maps are the next geometry layer. They
are not shortcuts or nice-to-haves; they are the intended path for making the
narrative useful without relying on Stockfish to explain chess.
