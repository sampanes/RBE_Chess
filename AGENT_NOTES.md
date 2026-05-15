# RBE Chess — Agent Notes

Companion to `RBE_CHESS_APP_HANDOFF.md` and `RBE_CHESS_M1_POCKET_MODE_ADDENDUM.md`.
The handoff doc is the original spec; the pocket-mode addendum refines it for
M1 and resolves every open question this file used to track. This file is the
short routing layer over those two docs.

Precedence when docs disagree:

1. **This file** for any topic where a later hardware/build reality has
   diverged from the addendum (notably: keyboard grammar, AGP/SDK level).
2. `RBE_CHESS_M1_POCKET_MODE_ADDENDUM.md` (M1 spec for everything not
   superseded here).
3. `RBE_CHESS_APP_HANDOFF.md` (original spec).

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

`build.gradle.kts` needs (corrected 2026-05-15 after the original
recipe failed on-device with `error=2, No such file or directory`):

```kotlin
android {
    packaging {
        jniLibs {
            // MUST be true for the exec()-the-engine approach.
            // false stores .so uncompressed in the APK and the linker
            // mmap's it directly — System.loadLibrary works, but
            // Runtime.exec() gets ENOENT because nothing is written to
            // disk. Setting this true makes AGP inject
            // `android:extractNativeLibs="true"` into the merged
            // manifest, so Android extracts the binary into
            // nativeLibraryDir as a real exec'able file at install.
            useLegacyPackaging = true
            // do NOT add stockfish to excludes
        }
    }
}
```

The previous version of this section claimed `useLegacyPackaging = false`
worked; that was wrong. The actual on-device behavior with `false` is
that no real file lands at `applicationInfo.nativeLibraryDir +
"/libstockfish.so"`, so `ProcessBuilder.start()` fails immediately.
Confirm the merged manifest has `extractNativeLibs="true"` after a
build with:

```
$ANDROID_SDK/build-tools/<ver>/aapt dump xmltree \
  app/build/outputs/apk/debug/app-debug.apk AndroidManifest.xml \
  | grep extractNativeLibs
# expect: A: android:extractNativeLibs(0x010104ea)=(type 0x12)0xffffffff
```

Trade-off: the 109 MB Stockfish binary becomes part of the install
footprint (~114 MB extracted) on top of the compressed APK copy
(~88 MB). Acceptable for our single-target-device use case.

---

## Build configuration — deviations from the handoff doc

The handoff doc specifies **Android 16 / API 36**. The current build targets
API 35 because AGP 9 / SDK 36 caused a sync failure during M1 step 1. Recorded
deviation:

| Setting | Handoff doc | Current build | Why |
|---|---|---|---|
| AGP | (not specified, scaffold defaulted to 9.2.1) | 8.7.3 | AGP 9 unstable in the local env; 8.7.3 is current-stable. |
| compileSdk | 36 | 35 | AGP 8.7.3 can't compile against SDK 36. |
| targetSdk | 36 | 35 | Matches compileSdk. |
| minSdk | 36 | 26 | The bundled adaptive-icon resource needs API 26+. SDK 36 was an overly tight floor anyway given a single target device. |
| Theme parent | (n/a) | `Theme.Material3.DayNight.NoActionBar` (from MDC 1.12.0) | Conventional Compose XML theme. |
| Plugin alias style | (n/a) | camelCase (`androidApp`, `kotlinAndroid`, `composeCompiler`) | Avoids collisions with the Kotlin DSL's built-in `android`/`kotlin` accessors. |

Practical impact on M1: none. The S22 Ultra runs the app fine on Android 16
regardless of compileSdk 35, and nothing in the Pocket Mode loop needs API 36.

**Revisit trigger:** before starting the post-M1 screen-off / `AccessibilityService`
spike. Some Android 16 foreground service types and Accessibility behavior
changes are API-36-gated; we need to confirm we don't need them before
committing to SDK 35 long-term. If the spike needs an API 36 feature, bump AGP
to 8.10+ (or 9.x once stable) and raise compileSdk/targetSdk back to 36 at
that time.

Build-system context for these decisions is in `BUILD_FIXES_2025_05_14.md`.

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

(The addendum's original "Keyboard grammar v0" question is no longer
listed here; its answer is the "Keyboard grammar — hardware-aware V1"
section below, which is the single source of truth for keyboard input.)

If a future change reopens any of these, update the relevant addendum
section first, then update this table.

---

## Keyboard grammar — hardware-aware V1 (supersedes addendum V0)

The Bluetooth "keyboard" is a custom 5-button HID device — Adafruit Feather
32u4 Bluefruit LE, firmware in (gitignored) `hidden/RBE_32u4_chess_arduino/`.
It only emits the HID keystrokes `D`, `F`, `J`, `K`, `Space`. The addendum's
"type `e2e4` Enter" grammar cannot work on this device. New grammar:

**Four-coordinate cycler.** Each cycle button owns one coordinate of a
from-to move and advances it by one on every press, wrapping around:

| Button | Coordinate | Cycle | TTS on press |
|---|---|---|---|
| **D** | from-file | a → b → c → … → h → a | speak the new letter |
| **F** | from-rank | 1 → 2 → 3 → … → 8 → 1 | speak the new digit |
| **J** | to-file   | a → … → h → a         | speak the new letter |
| **K** | to-rank   | 1 → … → 8 → 1         | speak the new digit |

**Default state at the start of each move:** all four coordinates start
**unset**. For display (inactivity prompt, on-screen text), unset renders
as `'a'` or `'1'`, so an untouched move appears as `a1a1`. **The first
press of a cycle button selects the first value rather than advancing
past it**, so press N lands on the Nth letter / digit:

- 1st D press → 'A' (idx 0)
- 2nd D press → 'B' (idx 1)
- 3rd D press → 'C' (idx 2)
- 8th D press → 'H' (idx 7)
- 9th D press → 'A' (wraps)

A coord the user never touches stays unset and renders as `'a'` / `'1'`
in the prompt. After Commit (Space), all four coords return to unset so
the next move starts fresh.

**Inactivity prompt:** after **2.5 s** of no presses, TTS speaks the
assembled move as a question: *"Move C1 to A1?"*. The timer resets on every
press.

**Space (single tap) — commit and advance:**

1. Apply the entered move to the board state as the opponent's move.
2. Run Stockfish `go movetime 1000`.
3. TTS speaks the bestmove (e.g. *"Best move: D2 to D4"*).
4. **Auto-apply the bestmove** to the board state — the user will play it
   on the physical board next, so the model advances with it.
5. Reset the buffer to `(a, 1, a, 1)`.

**Space (double tap):** reserved.

**Promotion handling.** When the committed move places a pawn on its
promotion rank, the app enters a brief *promotion-pick* state instead of
finalizing on Space. Mapping (best-guess from user's note "k, b, r, q
(two btns are queen)"; confirm before committing code if it matters):

| Input  | Promotion piece |
|---|---|
| **Space** | Queen (default; ~all real games) |
| **K**     | Queen (redundant fast path; right hand) |
| **D**     | Knight |
| **F**     | Bishop |
| **J**     | Rook |

**Not in M1, deferred to a later milestone:**
- ~~Undo last committed move.~~ Shipped in M2 as **Space+D** chord
  (drops the last pair of plies, or one if odd).
- Cancel/clear current input buffer. Still deferred — Undo + retype
  is the workaround.
- "Exit Pocket Mode" gesture (M1: tap the touchscreen anywhere — Activity
  is foregrounded so touch still works).
- ~~A toggleable *manual mode*~~. Shipped in M2 as **Space+F** chord.
  AutoAdvance stays the default; Manual keeps the engine's pick
  advisory ("Suggestion: ...") and only appends the user's typed move
  to history, so the user types every ply themselves. Toggle is sticky
  across moves but resets nothing (history survives the flip).

---

## M2 chord additions (firmware v2)

The v0 single-key emit-on-press model is preserved for the cycler keys
(D / F / J / K) so move input stays snappy. **Space** is reworked into
a modifier:

- **Space alone (press + release with no other key held)** still emits
  `' '` → commit (no behavior change).
- **Hold Space, tap a cycler** emits a distinct HID letter instead of
  the cycler's normal char *and* suppresses the trailing Space release.

Chord assignments (firmware → HID → app action):

| Chord | Emitted | `ChessKey` | `GrammarAction` | Effect |
|---|---|---|---|---|
| Space + D | `U` | `UNDO` | `Undo` | Drop last pair of plies; clear buffer; speak "Undid last move." |
| Space + F | `M` | `TOGGLE_MANUAL` | `ToggleManual` | Flip `GameMode` AutoAdvance ⇄ Manual; speak the new state. |
| Space + J | (none) | — | — | Reserved. Firmware detects + consumes the chord but emits nothing. Candidate assignments: single-ply undo (vs. Space+D's pair undo), exit Pocket Mode, repeat-last-utterance, or pause/resume engine. Pick when a real need arises. |
| Space + K | `N` | `NEW_GAME` | `NewGame` | Cancel engine, clear history + buffer, return to StartMenu. |

Once any chord fires during a Space hold, further cycler presses during
the same hold are silently ignored (prevents double-fires from
slightly-rolling chord gestures). Release Space and re-hold to fire
another chord.

App routing: see `MainActivity.handleGameKey` / `handleMenuKey`. In
StartMenu state, chord keys are no-ops — only F/J (navigate) and Space
(select) do anything.

---

## Battery telemetry (firmware v5)

Battery percentage rides the HID keystream, not a BLE service. Every
minute the firmware enqueues `'B'` + 3 zero-padded ASCII digits
(`B000`–`B100`) into the same FIFO that carries cycler/chord input.
`input/BatteryReportParser` strips the sequence before
`HardwareKeyboardHandler` runs, surfaces `batteryPct` to the UI, and
fires one-shot TTS warnings on threshold crossings (<20 % low,
<5 % critical, re-armed ≥30 %).

Why not the standard BLE Battery Service: v3 tried `AT+BLEBATTEN=on`
and v4 confirmed via serial log that this nRF51 SPI Friend's AT
firmware returns ERROR for that command — it isn't supported on this
module revision. The HID-stream path was the workaround.

**If a future change wants Android Settings to show the keypad
battery too** (the BAS UI affordance), the next-level fallback is the
manual GATT route in `setup_helper.h`:

```
AT+GATTADDSERVICE=UUID=0x180F          # Battery Service
AT+GATTADDCHAR=UUID=0x2A19,PROPERTIES=0x12,MIN_LEN=1,VALUE=100
```

…then `AT+GATTCHAR=<charId>,<pct>` on the same minute cadence instead
of (or alongside) the HID-stream push. Note that Android's Settings
battery display for BLE HID peripherals is OEM-inconsistent — even a
correctly-formed BAS may not render on Samsung One UI. Don't promise
the user it'll appear until it does on their device.

---

## M2 verbal start menu

`AppPhase.StartMenu(selectedIndex)` is the cold-launch state and the
state `Space+K` returns to mid-game. `StartMenuScreen` is verbal-first:
the TTS layer is the primary feedback channel, and the visible Compose
surface is decorative. Two options today:

1. **Play as white** — selecting this immediately triggers a bootstrap
   engine query on empty history so Stockfish speaks white's opening
   move (and, in AutoAdvance mode, auto-appends it to `MoveHistory`).
2. **Play as black** — selecting this leaves history empty and waits
   for the user to type white's first move on the cycler.

Navigation: **F = up, J = down, Space = select**, D / K / chord keys
are ignored. Wraps at the ends.

Bootstrap implementation: `MainActivity.bootstrapEngineMove()`. Manual
mode + Play-as-white still triggers the bootstrap query but speaks the
result as "Suggestion: …" and does **not** append — the user is
expected to type their own first move regardless.
