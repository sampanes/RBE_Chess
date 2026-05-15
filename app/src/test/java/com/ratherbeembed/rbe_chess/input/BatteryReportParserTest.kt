package com.ratherbeembed.rbe_chess.input

import android.view.KeyEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BatteryReportParserTest {

    private class FakeClock(var t: Long = 0L) { fun now(): Long = t }

    @Test
    fun `B + three digits yields Complete with the percentage`() {
        val clock = FakeClock()
        val p = BatteryReportParser(nowMs = clock::now)
        assertEquals(BatteryReportParser.Result.Consumed, p.consume(KeyEvent.KEYCODE_B))
        assertEquals(BatteryReportParser.Result.Consumed, p.consume(KeyEvent.KEYCODE_0))
        assertEquals(BatteryReportParser.Result.Consumed, p.consume(KeyEvent.KEYCODE_2))
        val r = p.consume(KeyEvent.KEYCODE_5)
        assertEquals(BatteryReportParser.Result.Complete(25), r)
    }

    @Test
    fun `B100 yields Complete(100)`() {
        val p = BatteryReportParser()
        p.consume(KeyEvent.KEYCODE_B)
        p.consume(KeyEvent.KEYCODE_1)
        p.consume(KeyEvent.KEYCODE_0)
        assertEquals(
            BatteryReportParser.Result.Complete(100),
            p.consume(KeyEvent.KEYCODE_0),
        )
    }

    @Test
    fun `B000 yields Complete(0)`() {
        val p = BatteryReportParser()
        p.consume(KeyEvent.KEYCODE_B)
        p.consume(KeyEvent.KEYCODE_0)
        p.consume(KeyEvent.KEYCODE_0)
        assertEquals(
            BatteryReportParser.Result.Complete(0),
            p.consume(KeyEvent.KEYCODE_0),
        )
    }

    @Test
    fun `cycler key alone is NotConsumed`() {
        val p = BatteryReportParser()
        assertEquals(
            BatteryReportParser.Result.NotConsumed,
            p.consume(KeyEvent.KEYCODE_D),
        )
    }

    @Test
    fun `non-digit during collection abandons partial and returns NotConsumed`() {
        // Defensive: a stray 'B' shouldn't be allowed to swallow the next
        // few chord/cycler keystrokes. The first non-digit terminates
        // collection and is dispatched normally.
        val p = BatteryReportParser()
        p.consume(KeyEvent.KEYCODE_B)
        p.consume(KeyEvent.KEYCODE_2)
        assertEquals(
            BatteryReportParser.Result.NotConsumed,
            p.consume(KeyEvent.KEYCODE_D),
        )
        // After bailout, the parser is back to idle; next D is also
        // NotConsumed (cycler key, not 'B').
        assertEquals(
            BatteryReportParser.Result.NotConsumed,
            p.consume(KeyEvent.KEYCODE_D),
        )
    }

    @Test
    fun `partial collection times out before next keycode`() {
        val clock = FakeClock(0L)
        val p = BatteryReportParser(nowMs = clock::now, timeoutMs = 500L)
        p.consume(KeyEvent.KEYCODE_B)
        p.consume(KeyEvent.KEYCODE_2)
        // Jump well past the timeout.
        clock.t = 10_000L
        // Next keycode is treated as fresh. A cycler key now is NotConsumed.
        assertEquals(
            BatteryReportParser.Result.NotConsumed,
            p.consume(KeyEvent.KEYCODE_D),
        )
    }

    @Test
    fun `two reports back to back parse independently`() {
        val p = BatteryReportParser()
        p.consume(KeyEvent.KEYCODE_B)
        p.consume(KeyEvent.KEYCODE_0)
        p.consume(KeyEvent.KEYCODE_5)
        val first = p.consume(KeyEvent.KEYCODE_0)
        assertEquals(BatteryReportParser.Result.Complete(50), first)

        p.consume(KeyEvent.KEYCODE_B)
        p.consume(KeyEvent.KEYCODE_0)
        p.consume(KeyEvent.KEYCODE_0)
        val second = p.consume(KeyEvent.KEYCODE_1)
        assertEquals(BatteryReportParser.Result.Complete(1), second)
    }

    @Test
    fun `B followed by non-digit yields NotConsumed for the non-digit`() {
        val p = BatteryReportParser()
        assertEquals(BatteryReportParser.Result.Consumed, p.consume(KeyEvent.KEYCODE_B))
        assertTrue(p.consume(KeyEvent.KEYCODE_SPACE) is BatteryReportParser.Result.NotConsumed)
    }
}
