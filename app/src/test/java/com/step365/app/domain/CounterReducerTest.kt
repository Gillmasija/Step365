package com.step365.app.domain

import org.junit.Assert.*
import org.junit.Test

class CounterReducerTest {
    private val old = CounterCheckpoint(40_000, 10_000, "2026-11-22", "Africa/Nairobi", 8)
    private fun sample(raw: Long = 40_010, time: Long = 20_000, date: String = old.date,
                       zone: String = old.zoneId, boot: Int = old.bootCount) =
        CounterSample(raw, time, date, zone, boot)

    @Test fun `first cumulative total is baseline not today's steps`() {
        val result = CounterReducer.reduce(null, sample())
        assertEquals(0L, result.delta)
        assertEquals(40_010L, result.checkpoint!!.raw)
        assertTrue(result.gap)
        assertEquals(CounterReason.BASELINE, result.reason)
    }

    @Test fun `normal sample adds difference only`() {
        val result = CounterReducer.reduce(old, sample())
        assertEquals(10L, result.delta)
        assertFalse(result.gap)
        assertEquals(CounterReason.ADVANCED, result.reason)
    }

    @Test fun `duplicate cannot count twice`() {
        val first = CounterReducer.reduce(old, sample())
        val duplicate = CounterReducer.reduce(first.checkpoint, sample())
        assertEquals(0L, duplicate.delta)
        assertEquals(CounterReason.DUPLICATE, duplicate.reason)
        assertEquals(first.checkpoint, duplicate.checkpoint)
    }

    @Test fun `stale sample cannot move checkpoint backward`() {
        val stale = CounterReducer.reduce(old, sample(raw = 39_000, time = 9_000))
        assertEquals(old, stale.checkpoint)
        assertEquals(0L, stale.delta)
        assertEquals(CounterReason.OUT_OF_ORDER, stale.reason)
        assertEquals(10L, CounterReducer.reduce(stale.checkpoint, sample()).delta)
    }

    @Test fun `unchanged sensor value adds zero without marking gap`() {
        val result = CounterReducer.reduce(old, sample(raw = old.raw))
        assertEquals(0L, result.delta)
        assertFalse(result.gap)
        assertEquals(20_000L, result.checkpoint!!.elapsedNanos)
    }

    @Test fun `reboot resets baseline even if raw total grew`() {
        val result = CounterReducer.reduce(old, sample(raw = 50_000, time = 2_000, boot = 9))
        assertEquals(0L, result.delta)
        assertTrue(result.gap)
        assertEquals(CounterReason.REBOOT, result.reason)
        assertEquals(9, result.checkpoint!!.bootCount)
    }

    @Test fun `sensor reset never produces negative or fabricated steps`() {
        val reset = CounterReducer.reduce(old, sample(raw = 12))
        assertEquals(0L, reset.delta)
        assertEquals(CounterReason.RESET, reset.reason)
        assertTrue(reset.gap)
        assertEquals(3L, CounterReducer.reduce(reset.checkpoint, sample(raw = 15, time = 30_000)).delta)
    }

    @Test fun `ambiguous midnight delta is discarded`() {
        val result = CounterReducer.reduce(old, sample(date = "2026-11-23"))
        assertEquals(0L, result.delta)
        assertTrue(result.gap)
        assertEquals(CounterReason.DATE_OR_ZONE_CHANGE, result.reason)
        assertEquals("2026-11-23", result.checkpoint!!.date)
    }

    @Test fun `timezone change in same date rebases conservatively`() {
        val result = CounterReducer.reduce(old, sample(zone = "Europe/London"))
        assertEquals(0L, result.delta)
        assertTrue(result.gap)
        assertEquals(CounterReason.DATE_OR_ZONE_CHANGE, result.reason)
    }

    @Test fun `manual date reversal rebases without backdating delta`() {
        val result = CounterReducer.reduce(old, sample(date = "2026-11-21"))
        assertEquals(0L, result.delta)
        assertTrue(result.gap)
    }

    @Test fun `invalid samples preserve baseline`() {
        listOf(sample(raw = -1), sample(time = -1), sample(date = "bogus"), sample(zone = "bogus"))
            .forEach {
                val result = CounterReducer.reduce(old, it)
                assertEquals(old, result.checkpoint)
                assertEquals(0L, result.delta)
                assertTrue(result.gap)
                assertEquals(CounterReason.INVALID, result.reason)
            }
    }

    @Test fun `persisted checkpoint restores same day across app restart`() {
        val beforeClose = CounterReducer.reduce(old, sample()).checkpoint
        val afterRestart = CounterReducer.reduce(beforeClose, sample(raw = 40_032, time = 30_000))
        assertEquals(22L, afterRestart.delta)
        assertFalse(afterRestart.gap)
    }
}
