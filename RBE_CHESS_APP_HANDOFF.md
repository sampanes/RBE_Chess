# RBE Chess App Handoff

## Project Summary

RBE Chess is a local-first Android chess assistant for running Stockfish directly on a phone, with no internet dependency required for core analysis.

The first target device is a Samsung Galaxy S22 Ultra running Android 16 / API 36. The current project assumption is that this app was created against Android 16-level tooling because the target phone reports Android 16. The app should be developed as a modern Kotlin Android app, preferably using Jetpack Compose, Gradle, and a repo-first workflow that can be operated by human developers and CLI-based LLM coding agents.

The core concept is simple:

1. The user communicates board changes through buttons, keyboard shortcuts, or later a richer board UI.
2. The app maintains an internal chess position.
3. The app sends the current position to Stockfish using UCI.
4. Stockfish thinks locally on the phone.
5. The app displays the best move, and eventually optional evaluation details.

The app is not meant to be an online chess client, a chess.com replacement, or a cloud AI product. It is a focused offline chess engine interface built around fast board input and local analysis.

## Primary Goal

Build an Android app that lets the user quickly enter or update a chess position, press a button or keyboard shortcut, and receive the best move from a locally bundled Stockfish engine.

The app should work without internet access.

## Design Philosophy

This project should stay boring, testable, and local.

Do not build clever chess logic in Kotlin unless it is needed for input validation or board-state tracking. Stockfish is the engine. The Android app is the interface, state manager, and UCI bridge.

The user wants to use CLI LLM tooling such as Claude Code, Codex, or similar agents. Therefore, the repo should be easy for agents to understand. Prefer explicit file names, clear package boundaries, small classes, and documented interfaces over dense clever abstractions.

The first usable version should prioritize:

- reliable local Stockfish execution
- clean UCI communication
- simple board-state tracking
- hardware/Bluetooth keyboard control
- visible best-move output
- predictable logs and testability

Visual polish comes after the engine loop works.

## Target Platform

Primary target:

- Android version: Android 16
- API level: 36
- Codename: Baklava
- Device: Samsung Galaxy S22 Ultra
- Architecture: ARM64 / arm64-v8a

Development assumptions:

- Kotlin
- Jetpack Compose
- Gradle wrapper
- Android Studio for SDK/device/debugging support
- Terminal-first workflow for builds and LLM agent work
- Physical-device testing prioritized over emulator testing

## Non-Goals

Do not spend early effort on:

- online multiplayer
- cloud engine analysis
- chess.com or lichess integration
- account systems
- ads
- Firebase
- remote databases
- AI commentary
- opening books
- Syzygy tablebases
- polished drag-and-drop board UI
- full PGN database management
- custom chess engine development

These can be reconsidered later, but they are not part of the initial build.

## Core Architecture

Recommended high-level modules:

```text
Android UI
  -> Input layer
  -> Board state / move history
  -> UCI command generator
  -> Stockfish process manager
  -> UCI output parser
  -> Best-move display
```

Suggested package layout:

```text
app/src/main/java/com/ratherbeembed/rbe_chess/
  MainActivity.kt

  ui/
    AppRoot.kt
    BoardScreen.kt
    AnalysisPanel.kt
    KeyboardHelpPanel.kt

  input/
    HardwareKeyboardHandler.kt
    MoveInputController.kt

  chess/
    BoardState.kt
    Move.kt
    MoveHistory.kt
    Fen.kt
    UciMove.kt

  engine/
    StockfishEngine.kt
    UciCommand.kt
    UciResponse.kt
    BestMove.kt
    EngineSettings.kt

  logging/
    AppLog.kt
```

Suggested test layout:

```text
app/src/test/java/com/ratherbeembed/rbe_chess/
  chess/
    FenTest.kt
    MoveHistoryTest.kt
    UciMoveTest.kt

  engine/
    UciCommandTest.kt
    UciResponseParserTest.kt
```

## Stockfish Integration

The app should use Stockfish as a local native engine.

Preferred interaction model:

```text
Kotlin app starts Stockfish as a local process
Kotlin app writes UCI commands to stdin
Kotlin app reads UCI responses from stdout
Kotlin app parses `bestmove`
```

Basic UCI interaction:

```text
uci
isready
setoption name Threads value 4
setoption name Hash value 64
ucinewgame
position startpos moves e2e4 e7e5 g1f3
go movetime 1000
```

Expected output includes:

```text
bestmove b8c6
```

The first milestone should not require a fully interactive board. It can be a hardcoded or text-entered position that sends a command to Stockfish and displays the returned best move.

## Engine Settings

Initial defaults for the S22 Ultra:

```text
Threads: 3 or 4
Hash: 64 MB or 128 MB
MultiPV: 1
Ponder: false
Move time: 500 ms to 2000 ms
```

Avoid maxing out all CPU cores at first. Sustained all-core analysis can cause heat, throttling, and battery drain.

Later settings screen:

- Threads
- Hash size
- Move time
- Depth limit
- MultiPV count
- Engine reset button
- Engine diagnostic log viewer

## Hardware Keyboard Support

Bluetooth keyboard support is a major desired interaction mode.

Android should generally treat a Bluetooth keyboard as a hardware keyboard. The app should handle key events at the activity or focused composable level.

Early useful shortcuts:

```text
A-H: select file
1-8: select rank
Enter: confirm square or move
Backspace: undo last input
Space: analyze current position
R: reset board
U: undo last move
F: flip board
?: show keyboard help
```

A simple input grammar is acceptable early:

```text
e2e4
g1f3
e7e8q
```

Do not overbuild the keyboard system before Stockfish works.

## Board Input Plan

Milestone 1 input:

- Text field for UCI move list
- Button: Analyze
- Text output: bestmove

Milestone 2 input:

- Simple board state
- Move list shown on screen
- Undo/reset
- Analyze button

Milestone 3 input:

- Hardware keyboard shortcuts
- Fast square selection
- Move confirmation
- Keyboard help overlay

Milestone 4 input:

- Tappable board
- Piece movement UI
- Board orientation toggle
- Optional FEN import/export

## Data Model

The app should track either:

1. Start position plus UCI move history, or
2. FEN plus optional move history.

For early development, prefer:

```text
position startpos moves ...
```

This is easier to reason about and works well with UCI. Later, FEN support should be added for arbitrary positions.

Important types:

```kotlin
data class UciMove(
    val from: String,
    val to: String,
    val promotion: Char? = null
)

data class MoveHistory(
    val moves: List<UciMove>
)

data class BestMove(
    val move: UciMove,
    val ponder: UciMove? = null
)
```

## User Experience

The app should feel like a compact engine console, not a full entertainment chess platform.

Initial screen concept:

```text
[RBE Chess]

Position:
startpos

Moves:
e2e4 e7e5 g1f3

[Analyze 1s]

Best move:
b8c6

Engine:
Stockfish ready
Threads 4 | Hash 64 MB | Offline
```

Later screen concept:

```text
Top:
- app title
- engine status
- analysis button

Center:
- board

Bottom:
- move list
- best move
- eval/depth/time
- shortcut hints
```

The best move should be large and unambiguous.

## Logging and Diagnostics

Add diagnostic logging early.

Useful logs:

- app started
- engine binary path found
- engine process started
- sent UCI command
- received UCI response
- bestmove parsed
- engine error
- process stopped
- process restart

A lightweight in-app diagnostic panel would be useful later.

## Agent Instructions

Coding agents should follow these rules:

- Do not replace the Android project with Flutter, React Native, Unity, or a web app.
- Do not implement a chess engine.
- Do not add online services unless specifically requested.
- Do not add Firebase.
- Do not add account/login systems.
- Do not put API keys in the Android app.
- Keep Stockfish isolated behind an engine interface.
- Keep UCI parsing covered by unit tests.
- Keep keyboard input separate from engine logic.
- Prefer small commits and small files.
- Run Gradle tests before claiming completion.

Preferred local commands:

```bash
./gradlew test
./gradlew assembleDebug
./gradlew installDebug
```

On Windows Git Bash, these may work:

```bash
./gradlew test
./gradlew assembleDebug
./gradlew installDebug
```

On Windows PowerShell:

```powershell
.\gradlew.bat test
.\gradlew.bat assembleDebug
.\gradlew.bat installDebug
```

## First Milestone

Goal: prove the app can communicate with Stockfish.

Acceptance criteria:

- App launches on the S22 Ultra.
- App starts local Stockfish successfully.
- App sends `uci`.
- App receives engine ID/options.
- App sends `isready`.
- App receives `readyok`.
- App sends a hardcoded position.
- App sends `go movetime 1000`.
- App displays a parsed `bestmove`.

No board UI is required for this milestone.

## Second Milestone

Goal: user can enter a simple move list and analyze it.

Acceptance criteria:

- Text input accepts UCI moves such as `e2e4 e7e5 g1f3`.
- App builds a valid UCI `position startpos moves ...` command.
- Analyze button sends current position to Stockfish.
- Best move updates on screen.
- Invalid input is rejected or clearly reported.
- Engine can be restarted without restarting the app.

## Third Milestone

Goal: Bluetooth keyboard input becomes useful.

Acceptance criteria:

- Physical keyboard input is detected.
- User can enter moves without touching the screen.
- Space or Enter can trigger analysis.
- Backspace/undo works.
- A keyboard help screen exists.
- App remains usable with touch input.

## Fourth Milestone

Goal: simple board UI.

Acceptance criteria:

- Board renders correctly.
- User can tap squares to enter moves.
- Move history updates board position.
- Undo/reset works.
- Best move can be highlighted.
- Board can be flipped.

## Stockfish Packaging Risk

The main technical risk is native engine packaging.

Avoid old Android patterns that copy executables into writable app storage and then run `chmod +x`. Newer Android versions restrict executing files from app-writable directories.

Preferred direction:

- Package the engine as a native Android artifact where possible.
- Use `arm64-v8a`.
- Keep engine startup path and permissions explicit.
- Document the final packaging strategy once proven.

Possible alternatives if direct process execution becomes painful:

1. Use Android NDK packaging conventions.
2. Wrap Stockfish with JNI.
3. Use an existing Android-compatible Stockfish/OEX-style packaging approach.
4. Build a small native bridge that exposes a controlled engine interface.

Do not spend major time on final packaging until a minimal proof-of-concept has demonstrated local UCI communication on the target device.

## Future Enhancements

Potential later features:

- FEN import/export
- PGN import/export
- copy best move
- copy full analysis line
- eval bar
- MultiPV
- engine depth/time display
- board arrows
- opening book
- tablebase support
- saved positions
- study mode
- quick side-to-move toggle
- illegal move validation
- voice readout of best move
- watch/phone companion mode

## Definition of Done for V0

V0 is complete when:

- The app installs on the S22 Ultra.
- It runs offline.
- It starts bundled Stockfish.
- It can analyze a position.
- It displays a best move.
- It supports at least one fast non-touch input path.
- The project builds from the command line.
- Core UCI parsing has unit tests.
- The repo contains enough documentation for a CLI LLM agent to continue safely.

## Suggested Immediate Next Task

Create a minimal `AGENT_NOTES.md` at repo root and then implement the first Stockfish proof-of-concept behind a `StockfishEngine` interface.

The first implementation can use a hardcoded command sequence and a hardcoded position. The goal is not chess UI yet. The goal is proving that local engine execution and UCI parsing work on the phone.
