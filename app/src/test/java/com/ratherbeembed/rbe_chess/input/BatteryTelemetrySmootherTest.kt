package com.ratherbeembed.rbe_chess.input

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BatteryTelemetrySmootherTest {

    private fun smoother() = BatteryTelemetrySmoother(
        lowPct = 20,
        criticalPct = 5,
        rearmPct = 30,
    )

    @Test
    fun `single zero outlier does not replace display or warn`() {
        val smoother = smoother()

        assertEquals(
            BatteryTelemetrySmoother.Update(88, null),
            smoother.record(88),
        )
        assertEquals(
            BatteryTelemetrySmoother.Update(88, null),
            smoother.record(0),
        )
        assertEquals(
            BatteryTelemetrySmoother.Update(73, null),
            smoother.record(73),
        )
    }

    @Test
    fun `two consecutive low reports emit low warning`() {
        val smoother = smoother()

        assertEquals(
            BatteryTelemetrySmoother.Update(null, null),
            smoother.record(19),
        )
        assertEquals(
            BatteryTelemetrySmoother.Update(
                18,
                BatteryTelemetrySmoother.Warning.LOW,
            ),
            smoother.record(18),
        )
    }

    @Test
    fun `two consecutive critical reports emit critical warning`() {
        val smoother = smoother()

        assertEquals(
            BatteryTelemetrySmoother.Update(null, null),
            smoother.record(4),
        )
        assertEquals(
            BatteryTelemetrySmoother.Update(
                3,
                BatteryTelemetrySmoother.Warning.CRITICAL,
            ),
            smoother.record(3),
        )
        assertEquals(
            BatteryTelemetrySmoother.Update(10, null),
            smoother.record(10),
        )
    }

    @Test
    fun `one low then one critical is low warning not critical warning`() {
        val smoother = smoother()

        assertEquals(
            BatteryTelemetrySmoother.Update(null, null),
            smoother.record(10),
        )
        assertEquals(
            BatteryTelemetrySmoother.Update(
                4,
                BatteryTelemetrySmoother.Warning.LOW,
            ),
            smoother.record(4),
        )
        assertEquals(
            BatteryTelemetrySmoother.Update(
                3,
                BatteryTelemetrySmoother.Warning.CRITICAL,
            ),
            smoother.record(3),
        )
    }

    @Test
    fun `rearm allows a later low warning`() {
        val smoother = smoother()

        smoother.record(19)
        assertEquals(
            BatteryTelemetrySmoother.Warning.LOW,
            smoother.record(18).warning,
        )
        assertNull(smoother.record(31).warning)
        smoother.record(19)
        assertEquals(
            BatteryTelemetrySmoother.Warning.LOW,
            smoother.record(18).warning,
        )
    }
}
