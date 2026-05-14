# RBE Chess M1 Addendum: Pocket Keyboard Mode and Spoken Best Move

## Purpose

This addendum updates the RBE Chess app goal with a more specific operating mode:

The app should be usable while the phone is in a pocket or otherwise not visually attended. The user should be able to enter moves from a Bluetooth keyboard, trigger local Stockfish analysis, and hear the best move spoken through Bluetooth speakers or headphones.

This shifts the app from a normal foreground chess UI into a semi-headless chess assistant.

## Updated Product Goal

RBE Chess should support two operating modes:

1. **Normal Mode**
   - App is visible.
   - User enters moves through text, touch, or hardware keyboard.
   - App displays best move on screen.

2. **Pocket Mode**
   - App is intentionally prepared for non-visual use.
   - Phone can be placed in a pocket.
   - Bluetooth keyboard input remains the primary input path.
   - App speaks confirmations and best moves through Android TextToSpeech.
   - App runs Stockfish locally.
   - No internet required.

## Important Android Reality Check

There are two different ideas that sound similar but are technically different:

1. **True screen-off mode**
   - The display is actually off.
   - The device may suspend.
   - The phone may be locked.
   - Arbitrary Bluetooth keyboard input may or may not be delivered to the app.
   - This must be treated as experimental until tested on the S22 Ultra running Android 16.

2. **Pocket black-screen mode**
   - The app stays in the foreground.
   - The screen is technically on, but the UI is black/minimal.
   - The Activity can retain keyboard focus.
   - The app can ignore touch input.
   - This is much more likely to work reliably.
   - On an OLED phone, a black screen is visually close to “off,” although the device is not actually asleep.

For M1, implement **Pocket black-screen mode first**. Treat true screen-off/background keyboard capture as a later experiment.

## Recommended M1 Strategy

M1 should prove the following:

- Bluetooth keyboard input works while the RBE Chess Activity is foregrounded.
- Pocket Mode can switch the visible UI to a black/minimal interface.
- The app can keep enough wake state to continue receiving keyboard input.
- The app can run Stockfish locally.
- The app can speak “best move” through Android TextToSpeech.
- The app can be used without looking at the screen.

Do not make AccessibilityService mandatory for M1 unless normal foreground keyboard input fails.

## Pocket Mode V0

Pocket Mode V0 should be a foreground Activity state, not a background service.

Behavior:

```text
User opens app
User taps "Pocket Mode"
App shows a black/minimal screen
App keeps keyboard focus
App optionally keeps the screen awake
App lowers screen brightness for this Activity
App ignores touch input except a deliberate exit gesture/button
User enters moves with Bluetooth keyboard
App speaks input confirmation
User presses analyze shortcut
App runs Stockfish
App speaks best move
```

Suggested Pocket Mode screen:

```text
RBE Chess Pocket Mode

Keyboard active.
Stockfish ready.
Last move: e2e4
Best move: c7c5

Press ? for help.
Hold Esc to exit.
```

For actual pocket use, the UI can be almost entirely black.

## True Screen-Off Mode: Experimental

True screen-off mode should be tracked separately.

Hypothesis:

- An AccessibilityService with key-event filtering may observe global hardware key events.
- A foreground service plus wake lock may keep the engine/control path alive.
- Android/Samsung lock-screen behavior may still prevent arbitrary chess-key input when the screen is off or locked.

The Android docs support AccessibilityService key filtering in general, but they do not establish that arbitrary Bluetooth keyboard input will be delivered while the screen is off, locked, or suspended. Therefore, this must be empirically tested on the actual S22 Ultra.

Test matrix:

```text
Case A: App visible, screen on
Expected: normal Activity key events work

Case B: Pocket Mode, black UI, screen technically on
Expected: normal Activity key events work

Case C: App visible, manually press power button, screen off
Expected: unknown

Case D: AccessibilityService enabled, screen off but not locked
Expected: unknown

Case E: AccessibilityService enabled, screen off and locked
Expected: unknown

Case F: Bluetooth media keys only, screen off
Expected: likely more reliable than arbitrary A-H / 1-8 keys, but insufficient for full move grammar
```

M1 should only require A and B.

## Proposed Architecture Update

Add these components:

```text
input/
  HardwareKeyboardHandler.kt
  KeyboardGrammar.kt
  PocketModeKeyRouter.kt

pocket/
  PocketModeController.kt
  PocketModeScreen.kt
  PocketModeState.kt

speech/
  SpeechOutput.kt
  BestMoveSpeaker.kt
  SpokenMoveFormatter.kt

engine/
  StockfishEngine.kt
  EngineSession.kt
  EngineSettings.kt
```

Later, if true screen-off mode proves possible:

```text
accessibility/
  RbeChessAccessibilityService.kt
  AccessibilityKeyRelay.kt

service/
  EngineForegroundService.kt
```

Do not put Stockfish process ownership in the AccessibilityService. If an AccessibilityService is added, it should only relay commands to the app/engine layer.

## Keyboard Grammar

The grammar that originally appeared here assumed a full alphanumeric
Bluetooth keyboard. The real input device is a custom 5-button HID device
(Adafruit Feather 32u4 Bluefruit LE) that only emits D / F / J / K / Space.
The real M1 grammar is documented in `AGENT_NOTES.md` §"Keyboard grammar —
hardware-aware V1". This section is intentionally short so that the
hardware-aware grammar is the single source of truth.

## Spoken Best Move

Use Android TextToSpeech for M1.

Best-move speech examples:

```text
bestmove b8c6
Speak: "Best move: B eight to C six."

bestmove e7e8q
Speak: "Best move: E seven to E eight, queen."

Engine not ready
Speak: "Engine not ready."

Invalid input
Speak: "Invalid move."
```

M1 should rely on the system-selected audio output route. If Bluetooth speakers/headphones are connected and selected for media output, Android should route TTS there. Do not build custom Bluetooth routing for M1.

Audio behavior:

- Request audio focus before speaking.
- Use speech-oriented audio attributes.
- Prefer transient audio focus if utterances are brief.
- Abandon audio focus after speech completes.
- Add an utterance progress listener so the app knows whether speech completed or failed.

Do not overbuild a media-session architecture for M1 unless Android 16 behavior requires it.

## Stockfish Binary Source

Resolved for M1:

Use the official Stockfish 18 Android ARMv8 Dot Product build first.

Record this in `jniLibs/README.md`:

```text
Engine: Stockfish 18
Release tag: sf_18
Primary build: Android ARMv8 Dot Product
Primary URL:
https://github.com/official-stockfish/Stockfish/releases/download/sf_18/stockfish-android-armv8-dotprod.tar

Fallback build: Android ARMv8
Fallback URL:
https://github.com/official-stockfish/Stockfish/releases/download/sf_18/stockfish-android-armv8.tar

Official download page:
https://stockfishchess.org/download/

Official release page:
https://github.com/official-stockfish/Stockfish/releases/tag/sf_18
```

Reasoning:

- Stockfish 18 is the current official release identified for this project.
- The official download page lists Android ARMv8 as recommended for most users.
- The official download page lists Android ARMv8 Dot Product as faster and recommended for modern devices.
- The S22 Ultra should be treated as a modern ARM64 device.

If the Dot Product build fails on-device, fall back to plain Android ARMv8.

## Engine Settings Defaults

Resolved for M1:

```text
Threads: 3
Hash: 64 MB
MultiPV: 1
Ponder: false
Move time: 1000 ms
```

Rationale:

- Threads=3 is conservative for heat and battery.
- Hash=64 MB is enough for early local analysis.
- Move time of 1000 ms gives fast audible feedback.
- Pocket Mode should optimize for fast interaction, not maximum engine depth.

Do not raise these defaults until basic thermal behavior is measured.

M1 thermal test:

```text
Run 20 consecutive analyses at movetime 1000.
Record:
- phone comfort/heat by hand
- whether Android shows thermal warnings
- whether Stockfish response time degrades
- battery drop over the test
- whether TTS stutters
```

Later tuning candidates:

```text
Threads: 4
Hash: 128 MB
Move time: 1500-2000 ms
Depth-limited analysis mode
```

## AccessibilityService UX

Resolved for M1:

AccessibilityService is **not required for M1**. It is an optional/experimental feature for true screen-off/global-key mode.

When implemented, the UX should be explicit and consent-heavy.

First-launch prompt should not demand Accessibility. Instead:

```text
Pocket Mode works best while RBE Chess is open.

Experimental background keyboard mode can try to listen for Bluetooth keyboard shortcuts even when RBE Chess is not visible. Android requires Accessibility permission for this.

Only enable this if you understand that Android will warn you that the app may observe key input.
```

Buttons:

```text
[Use normal Pocket Mode]
[Set up experimental background keyboard mode]
```

The setup button should open Android Accessibility Settings using:

```kotlin
Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
```

Do not fake or hide what the permission does. The whole point of this feature is global key filtering, and Android treats that as sensitive.

Accessibility implementation notes:

- Declare `android.permission.BIND_ACCESSIBILITY_SERVICE`.
- Declare service metadata XML.
- Set `canRequestFilterKeyEvents=true`.
- Request `FLAG_REQUEST_FILTER_KEY_EVENTS`.
- Override `onKeyEvent`.
- Do not consume keys unless they match RBE Chess command grammar.
- Do not log arbitrary key input.
- Do not store raw key history.
- Provide an obvious in-app status indicator showing whether Accessibility mode is enabled.
- Provide an obvious way to disable the mode.

## Wake / Power Strategy

M1 should avoid aggressive wake locks unless normal Pocket Mode fails.

Preferred M1 behavior:

- Keep the app foregrounded in Pocket Mode.
- Keep the Activity focused.
- Use a black/minimal UI.
- Use Activity-level screen-awake behavior only while Pocket Mode is active.
- Clear the keep-awake behavior immediately when exiting Pocket Mode.

If true screen-off mode is implemented later:

- Use a foreground service with an obvious notification.
- Use the shortest possible partial wake lock only when actively waiting for input or running analysis.
- Release wake locks aggressively.
- Assume battery impact is real and measure it.

## M1 Acceptance Criteria

M1 is complete when all of these work on the S22 Ultra:

```text
1. App launches.
2. Stockfish starts locally.
3. App sends `uci`.
4. App receives engine ID/options.
5. App sends `isready`.
6. App receives `readyok`.
7. User can enter a from-to move using the 5-button HID keyboard, per
   the hardware-aware grammar in AGENT_NOTES.md.
8. Each press is spoken back via TTS (letter or digit).
9. After ~2.5 s of inactivity the app speaks the assembled move as a
   "Move <from> to <to>?" question.
10. User presses Space and the app commits + analyzes.
11. App receives `bestmove`.
12. App displays the best move.
13. App speaks the best move through Android TTS.
14. App auto-applies the bestmove to its own board state, ready for the
    next opponent move.
15. Pocket Mode black/minimal screen still accepts Bluetooth keyboard input.
16. Pocket Mode can be exited intentionally.
```

True screen-off input is not required for M1.

## M1 Implementation Order

1. Build visible keyboard input in the Activity (`HardwareKeyboardHandler`,
   `KeyboardGrammar`, `MoveBuffer`). Logcat-only feedback at first.
2. Add TTS speech output (`SpeechOutput`, `SpokenMoveFormatter`). Each
   button press speaks; inactivity prompt fires per AGENT_NOTES grammar.
   Keyboard and TTS must land together to be perceivable.
3. Add hardcoded Stockfish UCI proof.
4. Connect move list + commits to Stockfish; speak bestmove.
5. Add Pocket Mode black/minimal screen.
6. Test Bluetooth keyboard in Pocket Mode on the S22 Ultra.
7. Only then test AccessibilityService as a spike (post-M1 if at all).

## Agent Guardrails

Agents should not treat AccessibilityService as the main path until the normal foreground/Pocket Mode path is working.

Agents should not add cloud services.

Agents should not build custom Bluetooth pairing or routing for M1.

Agents should not add speech recognition.

Agents should not implement a chess engine.

Agents should prioritize a working input -> UCI -> bestmove -> spoken output loop.
