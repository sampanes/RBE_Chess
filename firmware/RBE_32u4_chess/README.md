# RBE Chess Firmware — 5-button BT keypad

Custom 5-button Bluetooth HID keyboard for the RBE Chess Android app.

## Hardware

- **Board:** Adafruit Feather 32u4 Bluefruit LE (ATmega32u4 + nRF51822).
- **Buttons:** 5 momentary switches, left hand:
  - **Pinky** (HID `D`) — pin 5
  - **Ring** (HID `F`) — pin 6
  - **Middle** (HID `J`) — pin 10
  - **Index** (HID `K`) — pin 11
  - **Thumb** (HID `Space`) — pin 12
- Wiring: input pin → button → GND. Pins are `INPUT_PULLUP`.
- BLE module advertises as `Bluefruit Keyboard`.
- Battery monitor on A9 via 1:1 voltage divider
  ([Adafruit pinout](https://learn.adafruit.com/adafruit-feather-32u4-bluefruit-le/pinouts)).

## Build

Arduino IDE or `arduino-cli`.

**Board:** Tools → Board → "Adafruit Feather 32u4."

**Required external libraries** (Library Manager):

- **Adafruit BluefruitLE nRF51** — provides `Adafruit_BLE.h`,
  `Adafruit_BluefruitLE_SPI.h`, `Adafruit_BluefruitLE_UART.h`.

No other dependencies. The sketch will not compile without the library
above installed.

## Runtime behavior

On each debounced cycler button-down transition (Pinky / Ring / Middle /
Index, emitted as HID `D` / `F` / `J` / `K`), the sketch pushes the
corresponding character onto a press FIFO. A
non-blocking BLE state machine drains the FIFO and sends batches via
`AT+BleKeyboard=<chars>`. Crucially, button scanning continues every loop
iteration even while a BLE send is awaiting its `OK` response — so fast
input bursts are no longer dropped by the AT-command roundtrip.

The receiving phone sees discrete HID keystrokes. The Android app
(`../../app/`) interprets each character per the 4-coordinate cycler
grammar — see the project's `AGENT_NOTES.md` §"Keyboard grammar —
hardware-aware V1".

### Thumb/Space-as-modifier chords (v2)

Space is special. It does **not** emit on press; it emits on release
only if no chord fired during the hold:

| Gesture | Emitted character | App meaning |
|---|---|---|
| Thumb tap (Space press + release, no other key) | `' '` (space) | Commit move (cycler grammar's existing meaning). |
| Hold Thumb + press Pinky | `'U'` | Undo last move pair. |
| Hold Thumb + press Ring | `'M'` | Toggle manual mode. |
| Hold Thumb + press Middle | `'R'` | Repeat last replayable spoken output. |
| Hold Thumb + press Index | `'N'` | New game (back to start menu). |

Once a chord has fired during a Thumb/Space hold, the trailing Thumb/Space release
is silent and any further cycler presses during the same hold are
ignored — release Space and start over to fire another chord.

Cycler keys (Pinky / Ring / Middle / Index, emitted as HID `D` / `F` /
`J` / `K`) still emit immediately on press when Thumb/Space is **not**
held, so move input feels as snappy as v1.

### Battery reporting via the HID stream (v5)

The standard BLE Battery Service path (`AT+BLEBATTEN=on`) is **not
supported** by this nRF51 SPI Friend's AT firmware revision — v4
confirmed via serial log that the command returns ERROR. v5 takes a
different tack: report battery through the HID keyboard stream the
app is already consuming.

How it works:

- `voltage_to_percent()` converts the A9 voltage-divider reading to
  a 0-100 percentage via a piecewise-linear single-cell Li-Po curve.
- `maybePushBattery()` runs every loop, gated on `nextBatteryAtMs`.
  The first push fires `BATTERY_FIRST_PUSH_MS` after boot (default
  5 s); thereafter once per `BATTERY_UPDATE_MS` (default 60 s).
- The push enqueues exactly 4 characters into the existing key FIFO:
  `'B'` followed by 3 zero-padded ASCII digits. Example: `B025`
  for 25 %, `B100` for 100 %, `B003` for 3 %.
- The non-blocking BLE state machine drains the FIFO normally, so
  the battery report rides the same `AT+BleKeyboard=...` batch as
  any concurrent chord or chess input. No interference with input
  latency beyond the ~150 ms BLE roundtrip per send (~0.25 % of any
  given minute).

The app side (`BatteryReportParser`) intercepts the `B` + 3-digit
sequence before the chess grammar sees it, updates a `batteryPct`
state, and issues one-shot TTS warnings on threshold crossings
("Keypad battery low" below 20 %, "critical" below 5 %), re-armed
when the level climbs back above 30 % (e.g. after charging).

The conversion curve is approximate; treat the percentage as a coarse
fuel gauge ("plenty / getting low / charge soon"), not a calibrated
reading.

Dogfooding caveat: one transient `B000` followed by a plausible normal
value has been observed. Future firmware should smooth this by discarding
the first ADC read and averaging/median-filtering several samples before
reporting. App-side warning logic should also require repeated low samples
before speaking low/critical battery.

Debounce is a "stable for N ms" model (signal must hold the new state
continuously for `DOWN_DB_MS` / `UP_DB_MS` before the transition commits).
Defaults are conservative at 50 ms / 25 ms; lower if presses feel
sluggish.

## Verifying which firmware is on the chip

Two independent fingerprints, both driven by `FIRMWARE_VERSION` at the
top of `RBE_32u4_chess.ino`. Bump the constant on every meaningful flash.

1. **Boot LED blink.** On power-up, the onboard LED blinks
   `FIRMWARE_VERSION` times (120 ms on, 180 ms off) before BLE init.
   Power-cycle the keypad and count the flashes — you don't need a phone
   or USB cable.
2. **Versioned BLE device name.** The keypad advertises as
   `RBE Keypad v<N>`. Visible in Android's Bluetooth settings, in the
   pairing prompt, and in any BT scanner app.

After bumping `FIRMWARE_VERSION` and reflashing, you may need to forget
and re-pair on Android (some phones key pairings by device name).

## Debugging

Set `SERIAL_OUTPUT` to `true` at the top of `RBE_32u4_chess.ino` to
stream button events and BLE responses at 115200 baud. The sketch warns
that serial output slows the polling loop, so keep it `false` for
production / battery use.

If the BLE module fails to initialize, `error()` blinks the onboard LED
forever (150 ms on / 150 ms off) so a silent boot failure is visible
without a USB cable.

## Files

- `RBE_32u4_chess.ino` — main sketch (setup, loop, debounce).
- `setup_helper.h` — Bluefruit module init + HID enable.
- `BluefruitConfig.h` — SPI-mode pin definitions. The SW UART defines in
  this file are unused but their pin numbers overlap with button pins;
  see the comment at the top of that file.

## Troubleshooting upload (Windows / Arduino IDE 1.8.x)

Symptom that has bitten this project before:

```
Sketch uses ... bytes ... (compile succeeds)
An error occurred while uploading the sketch
avrdude: butterfly_recv(): programmer is not responding
Found programmer: Id = "?"; type = ?
```

That means avrdude opened the COM port but the Feather's Caterina
bootloader didn't answer. The 32u4 only exposes the bootloader port
for ~8 seconds after a reset, and the IDE's automatic 1200 bps "soft
reset" trick can fail if the running sketch's USB stack has crashed.

**Recipe that has worked reliably here:**

1. Unplug the Feather, plug it back in.
2. (Recommended) Enable upload diagnostics: **File → Preferences →
   Show verbose output during: Upload**. Helps next time something is
   off.
3. Open a known-good primer sketch: **File → Examples → 01.Basics →
   Blink**.
4. **Tools → Board → Adafruit Feather 32u4**.
5. **Tools → Port → COM5** (or whichever number the board enumerates
   as on this machine; check Device Manager → Ports if unsure).
6. Click **Upload**.
7. **The instant the IDE status flips from "Compiling…" to "Uploading…",
   double-tap the RST button** on the Feather. The onboard LED should
   start a slow breathing pulse (Caterina is now alive). Avrdude finds
   it and flashes.
8. Reopen your real sketch and upload normally — no RST gymnastics
   needed for subsequent uploads in the same session, because the
   freshly-flashed sketch's USB stack is healthy and the soft-reset
   trick works again.

If even Blink won't take, the cable is the next suspect (~30% of
random micro-USB cables are charge-only). Try a different cable, then
a different USB port.
