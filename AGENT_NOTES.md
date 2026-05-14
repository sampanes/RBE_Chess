# RBE Chess — Agent Notes

Companion to `RBE_CHESS_APP_HANDOFF.md` and `RBE_CHESS_M1_POCKET_MODE_ADDENDUM.md`.
The handoff doc is the original spec; the pocket-mode addendum refines it for
M1 and resolves every open question this file used to track. This file is the
short routing layer over those two docs.

Precedence when docs disagree:

1. `RBE_CHESS_M1_POCKET_MODE_ADDENDUM.md` (most recent, M1-specific)
2. `RBE_CHESS_APP_HANDOFF.md` (original spec)
3. This file

If anything is still ambiguous, ask the user before deviating.

---

## Extra requirements (not in the handoff doc)

The user added two requirements verbally after the handoff doc was written.
They are first-class, not nice-to-haves.

### 1. Bluetooth keyboard input — "Pocket Mode," not screen-off (M1)

The original verbal requirement was "BT keyboard input must keep working with
the screen off / app in the background." The pocket-mode addendum refines this
into two distinct technical problems:

- **Pocket Mode (M1 target):** the RBE Chess Activity stays foregrounded with
  a black/minimal UI. Screen is technically on (cheap on OLED), Activity keeps
  keyboard focus, app ignores touch except a deliberate exit. Reliable on
  Android 16. This is what M1 implements.
- **True screen-off / locked-screen input (deferred experiment):** would
  require an `AccessibilityService` with `canRequestFilterKeyEvents=true`. Per
  the addendum this is post-M1, gated on the Pocket Mode path working first,
  and must be empirically tested on the S22 Ultra — Android may not deliver
  arbitrary BT keys while locked or suspended.

**Decision (revised):** M1 uses an in-Activity `HardwareKeyboardHandler` while
the Pocket Mode screen is foregrounded. No `AccessibilityService`, no
foreground service, no wake locks beyond Activity-scoped keep-awake.
`AccessibilityService` is M2+ and, if ever added, only relays key events to
the engine layer — it must not own the Stockfish process.

### 2. Text-to-speech readout over Bluetooth speakers

The best move must be spoken aloud over whatever Bluetooth audio device is
currently connected (earbuds, speaker). The user wants eyes-free, hands-mostly-
free operation at a physical chess board.

Implications:

- Use Android `TextToSpeech` with `AudioAttributes` set to
  `USAGE_MEDIA` / `CONTENT_TYPE_SPEECH`. When BT A2DP is connected, media
  audio routes to it by default.
- Need to handle the case where TTS is requested while screen is off — TTS
  works fine in that state as long as the service stays alive (hence the
  foreground service).
- Pronunciation: speak moves as `"E two to E four"`, not the raw UCI string.
  A small UCI-to-spoken-text formatter belongs in its own file with unit tests.

---

## Stockfish packaging decision

The handoff doc flags packaging as the main technical risk and forbids the old
`chmod +x` pattern. Modern Android (16 / API 36) enforces W^X strictly on
app-writable storage, so the legacy approach will not work.

**Decision:** ship the Stockfish binary as `libstockfish.so` inside
`app/src/main/jniLibs/arm64-v8a/`.

Reasons:

- The Android packaging manager extracts files matching `lib*.so` into the
  app's `nativeLibraryDir`, which is read-only but has execute permission.
- This is `exec()`-able directly: `Runtime.getRuntime().exec(...)` against
  `applicationInfo.nativeLibraryDir + "/libstockfish.so"`.
- Keeps Stockfish a black-box process behind a UCI stdin/stdout pipe, which
  matches the handoff doc's "interaction model" diagram exactly.
- Avoids pulling Stockfish C++ source into the build. We can upgrade the
  engine by swapping a single `.so` file.
- Falls back cleanly to JNI later if `exec()` ever stops working on a future
  Android version.

The binary itself will be sourced from the official Stockfish project's
Android arm64 release. Source/license to be recorded in `app/src/main/jniLibs/README.md`
when the binary is added.

`build.gradle.kts` will need:

```kotlin
android {
    packaging {
        jniLibs {
            useLegacyPackaging = false
            // do NOT add stockfish to excludes
        }
    }
}
```

`useLegacyPackaging = false` is the default on modern AGP; it keeps the `.so`
uncompressed and `exec`-able from `nativeLibraryDir`.

---

## Architecture sketch (M1, aligned with addendum)

```text
com.ratherbeembed.rbe_chess/
  MainActivity.kt
  RbeChessApp.kt              # Application subclass

  ui/                         # Compose UI
    AppRoot.kt
    AnalysisPanel.kt
    KeyboardHelpPanel.kt

  input/
    HardwareKeyboardHandler.kt      # Activity-scoped key dispatch
    KeyboardGrammar.kt              # <from><to>[<promo>] + control keys
    PocketModeKeyRouter.kt          # routes keys differently in Pocket Mode

  pocket/
    PocketModeController.kt         # enter/exit, keep-awake, brightness
    PocketModeScreen.kt             # black/minimal Compose screen
    PocketModeState.kt              # buffer + status

  chess/
    BoardState.kt
    Move.kt
    MoveHistory.kt
    UciMove.kt

  engine/
    StockfishEngine.kt              # interface
    StockfishProcessEngine.kt       # exec-based impl
    FakeStockfishEngine.kt          # canned bestmoves, for tests
    EngineSession.kt                # owns the running process + IO loop
    UciCommand.kt
    UciResponse.kt
    BestMove.kt
    EngineSettings.kt

  speech/
    SpeechOutput.kt                 # TextToSpeech wrapper, audio focus
    BestMoveSpeaker.kt              # speaks bestmove + status phrases
    SpokenMoveFormatter.kt          # "e2e4" -> "E two to E four"

  logging/
    AppLog.kt
```

Deferred to a later milestone (not present in M1):

```text
accessibility/                # post-M1 experimental screen-off spike
  RbeChessAccessibilityService.kt
  AccessibilityKeyRelay.kt    # relays keys ONLY; never owns the engine

service/                      # only if true screen-off mode is pursued
  EngineForegroundService.kt
```

`StockfishEngine` stays a pure Kotlin interface so the UI/speech/pocket
layers never touch process management. `FakeStockfishEngine` exists from day
one so everything above the engine can be developed and tested without the
real binary.

---

## Milestone order (addendum overrides handoff)

The addendum collapses what was previously four milestones into a single
M1 that proves the full Pocket Mode loop end-to-end. Implementation order
inside M1 is fixed by §"M1 Implementation Order" of the addendum.

### M1 — Pocket Mode loop (the only milestone we are currently planning)

Build, in this order:

1. Scaffold to Kotlin + Compose; replace appcompat MainActivity.
2. Activity-scoped `HardwareKeyboardHandler` + `KeyboardGrammar`.
3. `SpeechOutput` / `BestMoveSpeaker` / `SpokenMoveFormatter` with TTS.
4. Hardcoded UCI proof against `libstockfish.so` (engine boots, returns a
   bestmove for a hardcoded position).
5. Wire the live move list to `StockfishProcessEngine`.
6. Pocket Mode black/minimal screen + `PocketModeController`.
7. Test BT keyboard input while in Pocket Mode on the S22 Ultra.
8. Optional spike: `AccessibilityService` for true screen-off — only if
   step 7 fully works and the user wants to extend it.

Full acceptance criteria are in §"M1 Acceptance Criteria" of the addendum
(14 checks, all on the S22 Ultra).

### Deferred (post-M1)

- True screen-off input via `AccessibilityService` (experimental — addendum
  §"True Screen-Off Mode: Experimental").
- Foreground service + wake locks (only if screen-off path is pursued).
- Compose board UI, tappable squares, FEN/PGN, MultiPV, etc. (handoff M4+
  and §Future Enhancements).

---

## Agent operating rules (extends handoff §Agent Instructions and addendum §Agent Guardrails)

- Order of precedence: addendum > handoff > this file. Read all three before
  starting non-trivial work.
- Never store the Stockfish binary anywhere except `jniLibs/arm64-v8a/`.
- Never copy the engine binary to app-writable storage and `chmod +x` it.
- Keep `StockfishEngine` as the only seam where process management leaks
  into the rest of the codebase.
- For M1, the primary key event path is the foregrounded Activity's
  `HardwareKeyboardHandler`. Do not add `AccessibilityService` to satisfy M1.
- If an `AccessibilityService` is ever added (post-M1 spike), it must only
  relay key events to the engine layer. It must not own the Stockfish
  process or TTS engine.
- `SpeechOutput` must use speech-oriented `AudioAttributes` and request
  audio focus before speaking; abandon focus when utterance completes.
  Rely on system audio routing for BT — do not build custom BT routing.
- Add unit tests for: UCI response parsing, UciMove parsing,
  `SpokenMoveFormatter`, `KeyboardGrammar`, and the `position startpos moves
  ...` command builder.
- Run `./gradlew test` before claiming a milestone complete.

---

## Open questions — RESOLVED in the M1 addendum

All four open questions this file used to track are answered by
`RBE_CHESS_M1_POCKET_MODE_ADDENDUM.md`. Pointers, not duplicated content:

| Question | Resolved by addendum section | Short answer |
|---|---|---|
| Stockfish binary source | §"Stockfish Binary Source" | Stockfish 18 Android ARMv8 Dot Product (`sf_18`); fallback plain ARMv8. Record in `app/src/main/jniLibs/README.md` when the `.so` is added. |
| Engine settings defaults | §"Engine Settings Defaults" | Threads=3, Hash=64 MB, MultiPV=1, Ponder=false, movetime=1000 ms. Run the 20-analysis thermal test before raising. |
| AccessibilityService UX | §"AccessibilityService UX" | Not required for M1. When added later: explicit consent screen, two-button choice, opens `Settings.ACTION_ACCESSIBILITY_SETTINGS`. |
| Keyboard grammar v0 | §"Keyboard Grammar V0" | `<from><to>[<promo>]` + Enter, with Backspace/Ctrl+Backspace/U/Space/?/Esc controls and the spoken-feedback rules listed there. |

If a future change reopens any of these, update the relevant addendum
section first, then update this table.
