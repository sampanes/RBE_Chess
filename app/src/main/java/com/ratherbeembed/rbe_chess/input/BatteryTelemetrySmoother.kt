package com.ratherbeembed.rbe_chess.input

class BatteryTelemetrySmoother(
    private val lowPct: Int,
    private val criticalPct: Int,
    private val rearmPct: Int,
    private val requiredLowSamples: Int = 2,
) {
    init {
        require(criticalPct < lowPct) { "critical threshold must be below low threshold" }
        require(lowPct < rearmPct) { "low threshold must be below rearm threshold" }
        require(requiredLowSamples >= 1) { "required samples must be positive" }
    }

    enum class Warning {
        LOW,
        CRITICAL,
    }

    data class Update(
        val displayPct: Int?,
        val warning: Warning?,
    )

    private var acceptedPct: Int? = null
    private var lowStreak = 0
    private var criticalStreak = 0
    private var warnedLow = false
    private var warnedCritical = false

    fun record(rawPct: Int): Update {
        val pct = rawPct.coerceIn(0, 100)

        if (pct < lowPct) {
            lowStreak += 1
            criticalStreak = if (pct < criticalPct) criticalStreak + 1 else 0
            if (lowStreak < requiredLowSamples) {
                return Update(displayPct = acceptedPct, warning = null)
            }
        } else {
            lowStreak = 0
            criticalStreak = 0
        }

        acceptedPct = pct

        if (pct >= rearmPct) {
            warnedLow = false
            warnedCritical = false
            return Update(displayPct = acceptedPct, warning = null)
        }

        if (
            pct < criticalPct &&
            criticalStreak >= requiredLowSamples &&
            !warnedCritical
        ) {
            warnedCritical = true
            warnedLow = true
            return Update(displayPct = acceptedPct, warning = Warning.CRITICAL)
        }

        if (pct < lowPct && !warnedLow) {
            warnedLow = true
            return Update(displayPct = acceptedPct, warning = Warning.LOW)
        }

        return Update(displayPct = acceptedPct, warning = null)
    }
}
