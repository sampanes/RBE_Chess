package com.ratherbeembed.rbe_chess.input

import android.view.KeyEvent

/**
 * Parses battery reports embedded in the BT keypad's HID stream.
 *
 * Firmware v5 emits the literal characters `'B'` + 3 zero-padded ASCII
 * digits once per minute. This parser sits ABOVE [HardwareKeyboardHandler]
 * so those keystrokes never reach the chess grammar — feed every
 * `KeyEvent` keycode through [consume] first.
 *
 * The parser is timeout-protected: if a partial report starts but the
 * trailing digits don't arrive within [timeoutMs], the partial state
 * is discarded the next time a keycode comes in. That defends against
 * the keypad ever emitting a stray `B` that doesn't continue with
 * digits, which would otherwise swallow the next 3 cycler presses.
 *
 * @param nowMs clock injection for unit tests; defaults to wall time.
 * @param timeoutMs how long a partial report can sit before being
 *   discarded. 500 ms is generous — HID keystrokes inside one
 *   `AT+BleKeyboard=` batch arrive within ~50 ms of each other.
 */
class BatteryReportParser(
    private val nowMs: () -> Long = System::currentTimeMillis,
    private val timeoutMs: Long = 500L,
) {
    private var buf: StringBuilder? = null
    private var startedAt: Long = 0L

    sealed interface Result {
        /** Caller should dispatch this keycode normally (chess grammar). */
        data object NotConsumed : Result
        /** Part of an in-flight report. Caller drops the keycode. */
        data object Consumed : Result
        /** Full 3-digit report parsed. Caller drops the keycode and consumes [pct]. */
        data class Complete(val pct: Int) : Result
    }

    fun consume(keyCode: Int): Result {
        // Time out a stalled partial collection before deciding what to do
        // with this keycode.
        val now = nowMs()
        if (buf != null && now - startedAt > timeoutMs) {
            buf = null
        }

        if (buf == null) {
            // Idle state — only 'B' opens a new collection.
            if (keyCode == KeyEvent.KEYCODE_B) {
                buf = StringBuilder()
                startedAt = now
                return Result.Consumed
            }
            return Result.NotConsumed
        }

        // Collecting digits.
        val digit = keyCodeToDigit(keyCode)
        if (digit == null) {
            // Non-digit while collecting — abandon the partial and let
            // the caller dispatch this keycode normally. Whatever the
            // firmware was trying to send is lost, but we don't risk
            // swallowing real input.
            buf = null
            return Result.NotConsumed
        }
        val current = buf!!.append(digit)
        if (current.length < REPORT_DIGIT_COUNT) return Result.Consumed

        val pct = current.toString().toInt().coerceIn(0, 100)
        buf = null
        return Result.Complete(pct)
    }

    private fun keyCodeToDigit(keyCode: Int): Int? = when (keyCode) {
        KeyEvent.KEYCODE_0 -> 0
        KeyEvent.KEYCODE_1 -> 1
        KeyEvent.KEYCODE_2 -> 2
        KeyEvent.KEYCODE_3 -> 3
        KeyEvent.KEYCODE_4 -> 4
        KeyEvent.KEYCODE_5 -> 5
        KeyEvent.KEYCODE_6 -> 6
        KeyEvent.KEYCODE_7 -> 7
        KeyEvent.KEYCODE_8 -> 8
        KeyEvent.KEYCODE_9 -> 9
        else -> null
    }

    companion object {
        const val REPORT_DIGIT_COUNT: Int = 3
    }
}
