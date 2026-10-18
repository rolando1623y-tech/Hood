package com.panahoops.next

/** Counter semantics for Android's TYPE_STEP_COUNTER. No motion is inferred. */
data class FitSampleState(
    val boot: Long,
    val day: String,
    val raw: Long,
    val steps: Long,
    val timestampMillis: Long
)

object FitCounter {
    /**
     * The first sample establishes a baseline, not historical steps.
     * A new local day starts a new baseline: steps between midnight and the first
     * sample cannot be accurately assigned to that day without another source.
     */
    @JvmStatic
    fun accept(previous: FitSampleState?, boot: Long, day: String, raw: Long, time: Long): FitSampleState {
        require(raw >= 0 && time >= 0 && day.isNotBlank())
        if (previous == null || previous.day != day)
            return FitSampleState(boot, day, raw, 0L, time)
        if (previous.boot != boot || raw < previous.raw)
            return FitSampleState(boot, day, raw, previous.steps, time)
        if (time < previous.timestampMillis) return previous // stale timestamp
        val increment = raw - previous.raw
        // A single impossibly large jump is rejected instead of displaying a fake result.
        if (increment > 100_000L) return FitSampleState(boot, day, raw, previous.steps, time)
        return FitSampleState(boot, day, raw, previous.steps + increment, time)
    }

    /** Keeps the hardware baseline; never claims to reset Android's physical sensor. */
    @JvmStatic
    fun reset(previous: FitSampleState?): FitSampleState? = previous?.copy(steps = 0L)
}
