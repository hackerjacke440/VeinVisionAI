package com.eljihani.veinvision.core

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LatencyStatsTest {
    @Test fun warmupSamplesAreDiscarded() {
        val s = LatencyStats(windowSize = 100, warmupFrames = 3)
        repeat(3) { s.add(1000.0) }
        assertTrue(s.snapshot().warmingUp)
        for (i in 1..10) s.add(i.toDouble())
        val snap = s.snapshot()
        assertEquals(10L, snap.measuredFrames)
        assertEquals(5.5, snap.meanMs!!, 1e-9)
    }

    @Test fun nearestRankPercentiles() {
        val s = LatencyStats(windowSize = 100, warmupFrames = 0)
        for (i in 1..100) s.add(i.toDouble())
        val snap = s.snapshot()
        assertEquals(50.0, snap.medianMs!!, 1e-9)
        assertEquals(95.0, snap.p95Ms!!, 1e-9)
    }

    @Test fun windowDropsOldest() {
        val s = LatencyStats(windowSize = 5, warmupFrames = 0)
        for (i in 1..10) s.add(i.toDouble())
        assertEquals(8.0, s.snapshot().meanMs!!, 1e-9)
    }

    @Test fun emptyHasNullStats() {
        assertNull(LatencyStats(10, 5).snapshot().medianMs)
    }

    @Test fun fpsFromTimestamps() {
        val f = FpsMeter()
        assertNull(f.fps())
        for (i in 0..10) f.tick(i * 100_000_000L) // 10 FPS
        assertEquals(10.0, f.fps()!!, 1e-6)
    }

    @Test fun configSummaryReflectsParameters() {
        val c = ProcessingConfig(claheEnabled = false, detectionThreshold = 0.3)
        assertTrue(c.summary().contains("clahe=off"))
        assertTrue(c.summary().contains("thr=0.3"))
    }
}
