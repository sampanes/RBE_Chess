// Bump this on every meaningful flash. setup() blinks LED_BUILTIN this
// many times on boot, and setup_helper.h appends "v<N>" to the BLE device
// name. Together they let you verify *from outside* whether the chip is
// running the bits you think it is, no USB cable required.
#define FIRMWARE_VERSION 2

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
//     (Space+D='U' undo, Space+F='M' manual, Space+K='N' new-game,
//     Space+J reserved => no emission). Mark chordConsumed so the
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

// Reachable only when SERIAL_OUTPUT is enabled (used inside the
// #if SERIAL_OUTPUT block in tickBle()). Kept for diagnostic re-enable.
float battery_voltage()
{
  float measuredvbat = analogRead(VBATPIN);
  measuredvbat *= 2;    // we divided by 2, so multiply back
  measuredvbat *= 3.3;  // Multiply by 3.3V, our reference voltage
  measuredvbat /= 1024; // convert to voltage
  return measuredvbat;
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
// character. Returns 0 for unmapped / reserved slots so the caller can
// skip the enqueue without emitting anything.
char chord_char_for(int buttonID)
{
  switch (buttonID)
  {
    case BTN_D: return 'U'; // undo last pair
    case BTN_F: return 'M'; // toggle manual mode
    case BTN_J: return 0;   // reserved
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
      if (keyFifoEmpty()) return;

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
        // Mark consumed even on the reserved slot, so a follow-up
        // cycler press during the same hold stays silent rather than
        // double-firing a chord.
        chordConsumed = true;
      }
      else if (!spaceHeld)
      {
        enqueueKey(buttonCharacter[ii]);
      }
      // else: spaceHeld && chordConsumed -- ignore the press silently.
    }
  }

  // Advance the BLE state machine; sends only when in IDLE with queued keys.
  tickBle();

  delay(DELAY_MS);
}
