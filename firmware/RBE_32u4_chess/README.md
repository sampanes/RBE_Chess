# RBE Chess Firmware — 5-button BT keypad

Custom 5-button Bluetooth HID keyboard for the RBE Chess Android app.

## Hardware

- **Board:** Adafruit Feather 32u4 Bluefruit LE (ATmega32u4 + nRF51822).
- **Buttons:** 5 momentary switches, left hand:
  - **D** (pinky) — pin 5
  - **F** (ring) — pin 6
  - **J** (middle) — pin 10
  - **K** (index) — pin 11
  - **Space** (thumb) — pin 12
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

On each debounced button-down transition, the sketch pushes the
corresponding character (`D`, `F`, `J`, `K`, ` `) onto a press FIFO. A
non-blocking BLE state machine drains the FIFO and sends batches via
`AT+BleKeyboard=<chars>`. Crucially, button scanning continues every loop
iteration even while a BLE send is awaiting its `OK` response — so fast
input bursts are no longer dropped by the AT-command roundtrip.

The receiving phone sees discrete HID keystrokes. The Android app
(`../../app/`) interprets each character per the 4-coordinate cycler
grammar — see the project's `AGENT_NOTES.md` §"Keyboard grammar —
hardware-aware V1".

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
