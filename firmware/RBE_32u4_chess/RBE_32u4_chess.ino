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
 * 
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
#define DOWN_DB_MS  50
#define UP_DB_MS    25

int buttonPins[5]               = { D_PIN, F_PIN, J_PIN, K_PIN, Space_PIN };
int buttonPrevState[5]          = {  HIGH,  HIGH,  HIGH,  HIGH,      HIGH };
unsigned long buttonTDelta[5]   = {     0,     0,     0,     0,         0 };
char buttonCharacter[5]         = {   'D',   'F',   'J',   'K',       ' ' };

String retstring = "";

void setup(void)
{
  setup_helper();

  // Set up input Pins
  for(int i=0; i< 5; i++)
  {
    pinMode(buttonPins[i], INPUT_PULLUP);
    buttonTDelta[i] = millis();
  }
}

float battery_voltage()
{ 
  float measuredvbat = analogRead(VBATPIN);
  measuredvbat *= 2;    // we divided by 2, so multiply back
  measuredvbat *= 3.3;  // Multiply by 3.3V, our reference voltage
  measuredvbat /= 1024; // convert to voltage
//  Serial.print("VBat: " ); Serial.println(measuredvbat);
  return measuredvbat;
}

bool debounce(int buttonID, int newstate)
{
  unsigned long diff = millis() - buttonTDelta[buttonID];
  int db_ms = (buttonPrevState[buttonID] == HIGH) ? DOWN_DB_MS : UP_DB_MS;
  if ( diff > db_ms )
  {
    buttonPrevState[buttonID] = newstate;
    buttonTDelta[buttonID] = millis();
    return true;
  }
  return false;
}

bool is_changed(int buttonID)
{
  int newstate = digitalRead(buttonPins[buttonID]);
  if (newstate != buttonPrevState[buttonID])
  {
    // Button State Changed, could be bounce or noise
    if ( debounce(buttonID, newstate) && newstate == LOW )
    {
      return true;
    }
  }
  return false;
}

String add_char_to_string(int buttonID)
{
  return retstring + buttonCharacter[buttonID];
}

/**************************************************************************/
/*!
    @brief  Constantly poll for new command or response data
*/
/**************************************************************************/
void loop(void)
{
  for ( int ii=0; ii<5; ii++)
  {
     if (is_changed(ii))
     {
      retstring = add_char_to_string(ii);
     }
  }
  if (!retstring.equals(""))
  {
    #if SERIAL_OUTPUT
    Serial.println(retstring);
    #endif
    ble.print("AT+BleKeyboard=");
    ble.println(retstring);

    if( ble.waitForOK() )
    {
      #if SERIAL_OUTPUT
      Serial.println( F("OK!") );
      Serial.println( battery_voltage());
      #endif
    }else
    {
      #if SERIAL_OUTPUT
      Serial.println( F("FAILED!") );
      #endif
    }
    retstring = "";
  }
  delay(DELAY_MS);
}
