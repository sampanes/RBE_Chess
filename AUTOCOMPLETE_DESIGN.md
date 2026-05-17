# Design Doc: Smart Autocomplete & Forced Move Detection

This document outlines the proposed "Autocomplete" feature for RBE Chess, designed to reduce keypresses by intelligently pre-filling the target coordinates based on engine evaluation.

## 1. Feature: Predictive Autocomplete

### Goal
Instead of defaulting to `a1` when a user starts a move, the app should predict the most likely target square (`toFile` and `toRank`) as soon as the source square (`fromFile` and `fromRank`) is identified.

### Mechanism
1.  **Trigger:** When the user provides both `fromFile` and `fromRank` (i.e., after the second button press in a move sequence).
2.  **Debounce:** The app waits for the inactivity-prompt delay before querying, so normal scrolling can pass through a source square without autocomplete firing.
3.  **Engine Query:** The app sends the current `MoveHistory` plus the partial "from" coordinate to Stockfish.
4.  **Selection:** Stockfish identifies the "best move" starting from that square.
5.  **Autofill:** The `MoveBuffer`'s coordinates are updated to match the engine's suggested target.
6.  **User Flow:**
    *   If the user agrees with the prediction, they just hit **Thumb (Commit)**.
    *   If they want a different move, they use **Middle (to-file)** or **Index (to-rank)** to override the prediction.

### Landed Conservative Slice

The first implementation deliberately avoids engine preference guesses.
It autofills only when the legal move set is unambiguous:

*   exactly one legal move exists in the whole position, or
*   exactly one legal move starts from the source square the user entered.

Autofilled coordinates are "read pending": the first press on D/F/J/K reads
the preset value without advancing, and the second press advances normally.
The second implementation adds that "basically one good move" behavior with
explicit score comparison. When multiple legal candidates remain, the app asks
Stockfish to search the candidates and only autofills if the best move beats
the runner-up by the configured centipawn margin.

Autofill announcements are queued behind the current board-move phrase so a
forced/suggested move does not cut off the move that caused it. AutoAdvance
engine replies use the same queued path when they follow a typed move.

## 2. Feature: Forced Move Detection & "Read Autocomplete"

### Goal
Identify situations where only one legal move exists for the player or opponent and proactively inform the user via the "Inactivity Prompt" (Speech).

### Mechanism
1.  **State Check:** After every move, the engine evaluates all legal moves for the next player.
2.  **Detection:** If the number of legal moves is exactly 1:
    *   **Opponent's Turn:** The UI replaces "Waiting for opponent..." with "[Move] is forced."
    *   **Player's Turn:** The `MoveBuffer` is immediately pre-filled with the forced move.
3.  **Read Autocomplete:** If a move is forced or the engine has a high-confidence prediction, the "Inactivity Prompt" (which usually asks "Move [piece] to...?") should instead announce the suggestion: *"Forced: [Move]"* or *"Suggestion: [Move]"*.

## 3. Manual Mode Behavior

Autocomplete must respect the user's `GameMode` (AutoAdvance vs. Manual):

| Scenario | AutoAdvance Mode | Manual Mode |
| --- | --- | --- |
| **Piece Selected** | Autofills `to` coords; user can Commit immediately. | Autofills `to` coords; user can use them as a "starting point" or ignore. |
| **Forced Move (Player)** | Pre-fills and announces; user hits Commit. | Pre-fills and announces; user still has to hit Commit to acknowledge. |
| **Forced Move (Opponent)** | Announces "[Move] is forced"; auto-appends both. | Announces "[Move] is forced"; waits for user to manually type/commit. |

In **Manual Mode**, the autocomplete acts as a **non-binding suggestion**. It saves the user from cycling through 8 ranks/files if they happen to agree with the engine, but it never moves a piece without a final "Commit" press from the user.

When the engine gives a manual-mode suggestion, the app prefills the buffer at
that move rather than leaving the user at `a1a1`.

## 3. Technical Implementation Details

### Changes to `MoveBuffer.kt`
- Add a `copyFromEngine(uci: String)` method that parses a 4-character UCI string and sets all four indices.

### Changes to `StockfishProcessEngine.kt`
- Add `scoredMoves(history, candidates, movetimeMs)`.
- It uses `searchmoves` plus `MultiPV`, parses `info score ... pv ...`, and restores `MultiPV` to 1 after the candidate search.

### Changes to `MainActivity.kt`
- Update `handleAction` to trigger the engine prediction once `fromFileIdx` and `fromRankIdx` are non-null.
- Use `lifecycleScope` to ensure the engine call doesn't block the UI thread.

## 4. Future Considerations
- **Toggle Settings:** Allow users to turn off "Predictive Autocomplete" if they find it distracting or want to play purely manually.
- **Confidence Threshold:** Dogfood and tune the current 100 cp score margin before exposing it as a setting.
