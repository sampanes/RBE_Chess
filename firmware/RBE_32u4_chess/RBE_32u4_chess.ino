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

// Debounce thresholds (ms). is_changed() now requires the signal to be
// stable at the new state for at least this long before the transition is
// committed (was: rate-limit-since-last-accepted-transition). With clean
// switches a bounce envelope is typically <5 ms, so values of 10-15 ms
// give near-zero perceived latency. The 50/25 split below is conservative
// and adds ~50 ms of press latency; lower if input feels sluggish.
#define DOWN_DB_MS 50
#define UP_DB_MS   25

// Max chars buffered between BLE flushes. retBuf only grows when multiple
// buttons commit in the same loop iteration, which is rare; 8 is plenty.
#define RET_BUF_CAP 8

int buttonPins[5]                    = { D_PIN, F_PIN, J_PIN, K_PIN, Space_PIN };
int buttonStableState[5]             = {  HIGH,  HIGH,  HIGH,  HIGH,      HIGH };
int buttonCandidateState[5]          = {  HIGH,  HIGH,  HIGH,  HIGH,      HIGH };
unsigned long buttonCandidateSince[5] = {    0,     0,     0,     0,         0 };
char buttonCharacter[5]              = {   'D',   'F',   'J',   'K',       ' ' };

char retBuf[RET_BUF_CAP] = "";
uint8_t retLen = 0;

void setup(void)
{
  setup_helper();

  // Set up input pins
  unsigned long now = millis();
  for (int i = 0; i < 5; i++)
  {
    pinMode(buttonPins[i], INPUT_PULLUP);
    buttonCandidateSince[i] = now;
  }
}

// Reachable only when SERIAL_OUTPUT is enabled (used inside the
// #if SERIAL_OUTPUT block in loop()). Kept for diagnostic re-enable.
float battery_voltage()
{
  float measuredvbat = analogRead(VBATPIN);
  measuredvbat *= 2;    // we divided by 2, so multiply back
  measuredvbat *= 3.3;  // Multiply by 3.3V, our reference voltage
  measuredvbat /= 1024; // convert to voltage
  return measuredvbat;
}

// "Stable for N ms" debounce: the signal must read the new state
// continuously for db_ms before the transition commits. Returns true
// only on the HIGH->LOW (press) transition, not on release.
bool is_changed(int buttonID)
{
  int reading = digitalRead(buttonPins[buttonID]);

  // Reading differs from the current candidate -> new candidate, reset timer.
  if (reading != buttonCandidateState[buttonID])
  {
    buttonCandidateState[buttonID] = reading;
    buttonCandidateSince[buttonID] = millis();
    return false;
  }

  // Reading matches candidate; if candidate already matches stable, nothing to do.
  if (reading == buttonStableState[buttonID]) return false;

  // Candidate differs from stable -- has it been stable long enough?
  int db_ms = (buttonStableState[buttonID] == HIGH) ? DOWN_DB_MS : UP_DB_MS;
  if (millis() - buttonCandidateSince[buttonID] < (unsigned long)db_ms) return false;

  // Commit the new stable state; report only the press (LOW) transition.
  buttonStableState[buttonID] = reading;
  return reading == LOW;
}

/**************************************************************************/
/*!
    @brief  Constantly poll for new command or response data
*/
/**************************************************************************/
void loop(void)
{
  for (int ii = 0; ii < 5; ii++)
  {
    if (is_changed(ii) && retLen < RET_BUF_CAP - 1)
    {
      retBuf[retLen++] = buttonCharacter[ii];
      retBuf[retLen] = '\0';
    }
  }

  if (retLen > 0)
  {
    #if SERIAL_OUTPUT
    Serial.println(retBuf);
    #endif
    ble.print("AT+BleKeyboard=");
    ble.println(retBuf);

    if (ble.waitForOK())
    {
      #if SERIAL_OUTPUT
      Serial.println(F("OK!"));
      Serial.println(battery_voltage());
      #endif
    }
    else
    {
      #if SERIAL_OUTPUT
      Serial.println(F("FAILED!"));
      #endif
    }

    retBuf[0] = '\0';
    retLen = 0;
  }

  delay(DELAY_MS);
}
