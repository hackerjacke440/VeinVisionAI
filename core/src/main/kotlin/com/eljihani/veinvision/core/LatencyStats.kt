package com.eljihani.veinvision.core

/**
 * Rolling latency statistics. The first [warmupFrames] samples are discarded and not counted.
 * Percentiles use nearest-rank on the retained window.
 */
class LatencyStats(private val windowSize: Int = 300, private val warmupFrames: Int = 30) {
    private val window = ArrayDeque<Double>()
    private var seen = 0L
    var measuredFrames = 0L
        private set
    var lastMs = 0.0
        private set

    @Synchronized
    fun add(ms: Double) {
        seen++
        if (seen <= warmupFrames) return
        measuredFrames++
        lastMs = ms
        window.addLast(ms)
        if (window.size > windowSize) window.removeFirst()
    }

    @Synchronized
    fun snapshot(): Snapshot {
        if (window.isEmpty()) return Snapshot(lastMs, null, null, null, measuredFrames, seen <= warmupFrames)
        val sorted = window.sorted()
        return Snapshot(lastMs, sorted.average(), percentile(sorted, 50.0), percentile(sorted, 95.0), measuredFrames, false)
    }

    @Synchronized
    fun reset() { window.clear(); seen = 0; measuredFrames = 0; lastMs = 0.0 }

    data class Snapshot(
        val lastMs: Double, val meanMs: Double?, val medianMs: Double?, val p95Ms: Double?,
        val measuredFrames: Long, val warmingUp: Boolean,
    )

    companion object {
        /** Nearest-rank percentile on an ascending list. */
        fun percentile(sortedAscending: List<Double>, p: Double): Double {
            require(sortedAscending.isNotEmpty())
            val rank = kotlin.math.ceil(p / 100.0 * sortedAscending.size).toInt().coerceIn(1, sortedAscending.size)
            return sortedAscending[rank - 1]
        }
    }
}

/** Frames-per-second from arrival timestamps over a sliding window (seconds). */
class FpsMeter(private val windowNanos: Long = 2_000_000_000L) {
    private val stamps = ArrayDeque<Long>()

    @Synchronized
    fun tick(nowNanos: Long) {
        stamps.addLast(nowNanos)
        while (stamps.isNotEmpty() && nowNanos - stamps.first() > windowNanos) stamps.removeFirst()
    }

    @Synchronized
    fun fps(): Double? {
        if (stamps.size < 2) return null
        val span = (stamps.last() - stamps.first()) / 1e9
        return if (span > 0) (stamps.size - 1) / span else null
    }
}
