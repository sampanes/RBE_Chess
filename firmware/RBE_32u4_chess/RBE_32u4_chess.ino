// Bump this on every meaningful flash. setup() blinks LED_BUILTIN this
// many times on boot, and setup_helper.h appends "v<N>" to the BLE device
// name. Together they let you verify *from outside* whether the chip is
// running the bits you think it is, no USB cable required.
#define FIRMWARE_VERSION 6

#include "setup_helper.h"

/*
 * NOTES: nRF51822 (32u4)
 *
 * battery for bluefruit https://learn.adafruit.com/bluefruit-nrf52-feather-learning-guide/power-management
 *
 * power switch probably has to go between the En pin and the GND https://io.adafruit.com/blog/tip/2016/12/14/feather-power-switch/
 * (tie EN to GND to turn off 3.3V regulator)
 *
 * Input pin > Button > Ground. When we initialize pinmodes, we set those A (analog) pins to INPUT PULLUP
 *
 * Buttons from left hand fingers: D (pinky), F (ring), J (middle), K (index), space (thumb)
 */
// VERY IMPORTANT set this to true if you want to see serial output, false if using not on pc,
// serial output slows it down and makes it basically not work
#define SERIAL_OUTPUT false

// GPIO corresponding to HID gamepad
#define D_PIN     5
#define F_PIN     6
#define J_PIN     10
#define K_PIN     11
#define Space_PIN 12

// Arduino Example Code: https://learn.adafruit.com/adafruit-feather-32u4-bluefruit-le/pinouts says A9 is connected to v div
#define VBATPIN A9

#define DELAY_MS  1

// Debounce thresholds (ms). is_changed() requires the signal to be stable
// at the new state for at least this long before the transition commits.
// With clean switches a bounce envelope is typically <5 ms, so values of
// 10-15 ms give near-zero perceived latency. The 50/25 split below is
// conservative; lower if input feels sluggish.
#define DOWN_DB_MS 50
#define UP_DB_MS   25

// Press FIFO. The loop pushes each detected press here; tickBle() drains
// it asynchronously, so button scanning is never blocked by a BLE send.
#define KEY_FIFO_SIZE 32
char keyFifo[KEY_FIFO_SIZE];
uint8_t keyFifoHead = 0;
uint8_t keyFifoTail = 0;

// Non-blocking BLE send state machine.
// IDLE         -> ready to send the next batch
// AWAITING_OK  -> command sent, waiting for "OK" or "ERROR" response bytes
enum BleSendState { BLE_IDLE, BLE_AWAITING_OK };
BleSendState bleState = BLE_IDLE;
unsigned long bleStateStartMs = 0;
char bleRespBuf[16];
uint8_t bleRespLen = 0;
#define BLE_RESP_TIMEOUT_MS 1000

int buttonPins[5]                     = { D_PIN, F_PIN, J_PIN, K_PIN, Space_PIN };
int buttonStableState[5]              = {  HIGH,  HIGH,  HIGH,  HIGH,      HIGH };
int buttonCandidateState[5]           = {  HIGH,  HIGH,  HIGH,  HIGH,      HIGH };
unsigned long buttonCandidateSince[5] = {    0,     0,     0,     0,         0 };
char buttonCharacter[5]               = {   'D',   'F',   'J',   'K',       ' ' };

// Button-index constants for readability.
#define BTN_D     0
#define BTN_F     1
#define BTN_J     2
#define BTN_K     3
#define BTN_SPACE 4

// Space-as-modifier chord state (firmware v2).
//
// Space no longer emits on press. Instead:
//   - on Space PRESS: arm chord state (spaceHeld=true, chordConsumed=false)
//   - cycler press while spaceHeld: emit the mapped chord char
//     (Space+D='U' undo, Space+F='M' manual, Space+J='R' repeat,
//     Space+K='N' new-game). Mark chordConsumed so the
//     trailing Space release stays silent and further cycler presses
//     during the same hold are ignored (prevents accidental double-fire).
//   - on Space RELEASE: emit ' ' only if no chord was consumed.
//
// Cycler keys still emit on press, so the move-input loop feels as
// snappy as v1. Only Space pays the press-to-release latency cost.
bool spaceHeld = false;
bool chordConsumed = false;

// Edge type returned by get_edge(). v1 only reported presses; chord
// detection needs releases too (specifically Space's), so the function
// now returns a tri-state.
enum BtnEdge { EDGE_NONE, EDGE_PRESS, EDGE_RELEASE };

// --- Battery telemetry (firmware v5) ----------------------------------
//
// History: v3 tried the standard BLE Battery Service via
// AT+BLEBATTEN=on, but that command isn't supported by this module's
// AT firmware (returns ERROR). v4 made the attempt non-fatal; v5 drops
// the attempt entirely and reports battery via the HID keyboard stream
// instead.
//
// Encoding: every BATTERY_UPDATE_MS we enqueue the literal characters
// 'B' + three zero-padded ASCII digits (e.g. "B025" for 25%, "B100"
// for 100%) into the same FIFO chords and chess input use. The app's
// BatteryReportParser detects the leading 'B' and consumes the next
// three digit keystrokes, surfacing the percentage to the UI + TTS
// warnings without leaking into the chess grammar.
//
// First push fires ~5s after boot so Android has time to settle the
// HID connection; thereafter once per minute.
#define BATTERY_UPDATE_MS 60000UL
#define BATTERY_FIRST_PUSH_MS 5000UL
unsigned long nextBatteryAtMs = BATTERY_FIRST_PUSH_MS;

void blinkVersion(void)
{
  pinMode(LED_BUILTIN, OUTPUT);
  for (int i = 0; i < FIRMWARE_VERSION; i++)
  {
    digitalWrite(LED_BUILTIN, HIGH);
    delay(120);
    digitalWrite(LED_BUILTIN, LOW);
    delay(180);
  }
}

void setup(void)
{
  // Blink first, before BLE init can fail. Confirms the upload landed
  // even if the Bluefruit module isn't responding.
  blinkVersion();

  setup_helper();

  unsigned long now = millis();
  for (int i = 0; i < 5; i++)
  {
    pinMode(buttonPins[i], INPUT_PULLUP);
    buttonCandidateSince[i] = now;
  }
}

// Enqueue the current battery percentage as "B<NNN>" (always 4 chars,
// 3-digit zero-padded percentage). Runs at most once per
// BATTERY_UPDATE_MS via the nextBatteryAtMs gate, with the first push
// BATTERY_FIRST_PUSH_MS after boot. Lives in the same FIFO as chord
// and chess input, so the existing non-blocking BLE state machine
// batches it naturally.
void maybePushBattery(void)
{
  if (millis() < nextBatteryAtMs) return;
  nextBatteryAtMs = millis() + BATTERY_UPDATE_MS;

  int pct = voltage_to_percent(battery_voltage());
  if (pct < 0)   pct = 0;
  if (pct > 100) pct = 100;

  char buf[5];
  snprintf(buf, sizeof(buf), "B%03d", pct);
  for (uint8_t i = 0; i < 4; i++) enqueueKey(buf[i]);

  #if SERIAL_OUTPUT
  Serial.print(F("Battery report queued: ")); Serial.println(buf);
  #endif
}

float battery_voltage()
{
  float measuredvbat = analogRead(VBATPIN);
  measuredvbat *= 2;    // we divided by 2, so multiply back
  measuredvbat *= 3.3;  // Multiply by 3.3V, our reference voltage
  measuredvbat /= 1024; // convert to voltage
  return measuredvbat;
}

// Piecewise-linear single-cell Li-Po SoC curve. The discharge curve is
// nonlinear in real life (mostly flat between ~3.9 V and ~3.7 V, then
// drops fast); this approximation keeps the "is it about to die?" end
// roughly honest without claiming more precision than the rest of the
// system. Returns an integer percentage 0..100.
int voltage_to_percent(float v)
{
  if (v >= 4.20f) return 100;
  if (v >= 4.00f) return (int)(75 + (v - 4.00f) * (25.0f / 0.20f));
  if (v >= 3.85f) return (int)(50 + (v - 3.85f) * (25.0f / 0.15f));
  if (v >= 3.70f) return (int)(25 + (v - 3.70f) * (25.0f / 0.15f));
  if (v >= 3.50f) return (int)( 5 + (v - 3.50f) * (20.0f / 0.20f));
  if (v >= 3.30f) return (int)(     (v - 3.30f) * ( 5.0f / 0.20f));
  return 0;
}

// "Stable for N ms" debounce: the signal must read the new state
// continuously for db_ms before the transition commits. Returns
// EDGE_PRESS on HIGH->LOW, EDGE_RELEASE on LOW->HIGH, EDGE_NONE otherwise.
BtnEdge get_edge(int buttonID)
{
  int reading = digitalRead(buttonPins[buttonID]);

  if (reading != buttonCandidateState[buttonID])
  {
    buttonCandidateState[buttonID] = reading;
    buttonCandidateSince[buttonID] = millis();
    return EDGE_NONE;
  }

  if (reading == buttonStableState[buttonID]) return EDGE_NONE;

  int db_ms = (buttonStableState[buttonID] == HIGH) ? DOWN_DB_MS : UP_DB_MS;
  if (millis() - buttonCandidateSince[buttonID] < (unsigned long)db_ms) return EDGE_NONE;

  buttonStableState[buttonID] = reading;
  return (reading == LOW) ? EDGE_PRESS : EDGE_RELEASE;
}

// Map a cycler index pressed while Space is held to its chord HID
// character. Returns 0 for unmapped slots so the caller can
// skip the enqueue without emitting anything.
char chord_char_for(int buttonID)
{
  switch (buttonID)
  {
    case BTN_D: return 'U'; // undo last pair
    case BTN_F: return 'M'; // toggle manual mode
    case BTN_J: return 'R'; // repeat last spoken output
    case BTN_K: return 'N'; // new game (return to start menu)
    default:    return 0;
  }
}

// --- Press FIFO ---------------------------------------------------------

bool keyFifoEmpty(void) { return keyFifoHead == keyFifoTail; }

void enqueueKey(char c)
{
  uint8_t next = (uint8_t)((keyFifoTail + 1) % KEY_FIFO_SIZE);
  if (next == keyFifoHead)
  {
    // FIFO full -- drop oldest to keep current input responsive.
    keyFifoHead = (uint8_t)((keyFifoHead + 1) % KEY_FIFO_SIZE);
  }
  keyFifo[keyFifoTail] = c;
  keyFifoTail = next;
}

// Pop up to (maxLen-1) chars into dest, NUL-terminated. Returns count.
uint8_t drainKeysToBatch(char* dest, uint8_t maxLen)
{
  uint8_t n = 0;
  while (n < (uint8_t)(maxLen - 1) && !keyFifoEmpty())
  {
    dest[n++] = keyFifo[keyFifoHead];
    keyFifoHead = (uint8_t)((keyFifoHead + 1) % KEY_FIFO_SIZE);
  }
  dest[n] = '\0';
  return n;
}

// --- Non-blocking BLE state machine ------------------------------------

void tickBle(void)
{
  switch (bleState)
  {
    case BLE_IDLE:
    {
      if (!keyFifoEmpty())
      {
        // Keys always win the race -- battery is a slow heartbeat,
        // user input is not.
        char batch[KEY_FIFO_SIZE + 1];
        uint8_t n = drainKeysToBatch(batch, sizeof(batch));
        if (n == 0) return;

        ble.print("AT+BleKeyboard=");
        ble.println(batch);

        bleState = BLE_AWAITING_OK;
        bleStateStartMs = millis();
        bleRespLen = 0;

        #if SERIAL_OUTPUT
        Serial.print(F("BLE> ")); Serial.println(batch);
        #endif
      }
      break;
    }

    case BLE_AWAITING_OK:
    {
      // Drain any response bytes the module has ready, looking for the
      // "OK\r\n" or "ERROR\r\n" reply line. ble.available() is non-blocking.
      while (ble.available())
      {
        char c = (char)ble.read();
        if (c == '\r' || c == '\n')
        {
          bleRespBuf[bleRespLen] = '\0';
          if (strstr(bleRespBuf, "OK"))
          {
            #if SERIAL_OUTPUT
            Serial.println(F("OK!"));
            Serial.println(battery_voltage());
            #endif
            bleState = BLE_IDLE;
            bleRespLen = 0;
            return;
          }
          if (strstr(bleRespBuf, "ERROR"))
          {
            #if SERIAL_OUTPUT
            Serial.println(F("FAILED!"));
            #endif
            bleState = BLE_IDLE;
            bleRespLen = 0;
            return;
          }
          bleRespLen = 0;  // discard line, keep waiting
        }
        else if (bleRespLen < (uint8_t)(sizeof(bleRespBuf) - 1))
        {
          bleRespBuf[bleRespLen++] = c;
        }
      }

      // Hard timeout fallback so a lost ack can't wedge the queue.
      if (millis() - bleStateStartMs > BLE_RESP_TIMEOUT_MS)
      {
        #if SERIAL_OUTPUT
        Serial.println(F("BLE ack timeout"));
        #endif
        bleState = BLE_IDLE;
        bleRespLen = 0;
      }
      break;
    }
  }
}

/**************************************************************************/
/*!
    @brief  Constantly poll for new command or response data
*/
/**************************************************************************/
void loop(void)
{
  // Scan every loop iteration -- never blocked on BLE.
  for (int ii = 0; ii < 5; ii++)
  {
    BtnEdge edge = get_edge(ii);
    if (edge == EDGE_NONE) continue;

    if (ii == BTN_SPACE)
    {
      if (edge == EDGE_PRESS)
      {
        // Arm chord state but don't emit yet -- decision deferred to
        // either the chord branch below or the matching release.
        spaceHeld = true;
        chordConsumed = false;
      }
      else // EDGE_RELEASE
      {
        spaceHeld = false;
        if (!chordConsumed)
        {
          enqueueKey(' ');
        }
        chordConsumed = false;
      }
    }
    else
    {
      // Cycler key. Release edges are irrelevant -- the cycler still
      // emits on press.
      if (edge != EDGE_PRESS) continue;

      if (spaceHeld && !chordConsumed)
      {
        char c = chord_char_for(ii);
        if (c != 0) enqueueKey(c);
        // Mark consumed so a follow-up cycler press during the same
        // hold stays silent rather than double-firing a chord.
        chordConsumed = true;
      }
      else if (!spaceHeld)
      {
        enqueueKey(buttonCharacter[ii]);
      }
      // else: spaceHeld && chordConsumed -- ignore the press silently.
    }
  }

  // Enqueue the periodic battery report (4 chars: "B<NNN>") into the
  // same FIFO chord/chess input uses. Internally gated by nextBatteryAtMs
  // so this is a cheap no-op most ticks.
  maybePushBattery();

  // Advance the BLE state machine; sends only when in IDLE with queued
  // keys.
  tickBle();

  delay(DELAY_MS);
}
