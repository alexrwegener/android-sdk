package com.statsig.androidsdk

import org.junit.Assume

/**
 * Lightweight micro-benchmark harness for the SDK's performance test suite.
 *
 * These are JVM/Robolectric measurements, not on-device benchmarks: the absolute numbers are
 * NOT representative of a real handset (no ART, no real IO scheduler, localhost sockets). They
 * are useful for detecting *relative* regressions run-over-run on the same machine and for
 * comparing code paths against each other.
 *
 * The suite is skipped by default so it never slows down or destabilizes the normal unit-test
 * job. Enable it with the `statsig.perf` system property, e.g.
 *
 *   ./gradlew testDebugUnitTest -PstatsigPerf --tests "*PerformanceTest"
 *
 * (the `statsigPerf` project property wires `-Dstatsig.perf=true` and single-forks the JVM; see
 * build.gradle.kts).
 */
internal object PerfHarness {
    const val ENABLE_PROPERTY = "statsig.perf"

    fun isEnabled(): Boolean =
        System.getProperty(ENABLE_PROPERTY)?.lowercase() in setOf("1", "true", "yes", "on")

    /** Skips the calling test unless perf runs are explicitly enabled. */
    fun assumeEnabled() {
        Assume.assumeTrue(
            "Performance tests are skipped unless -Dstatsig.perf=true (run gradle with -PstatsigPerf)",
            isEnabled()
        )
    }

    /**
     * A set of timing samples for a single named scenario.
     *
     * @param opsPerSample how many logical operations each raw sample represents. For per-call
     * latency of a fast operation, measure a batch of N calls per sample and set this to N so the
     * reported numbers are per-operation.
     */
    class Stats(val name: String, opsPerSample: Int, rawSamplesNanos: LongArray) {
        val samples: Int = rawSamplesNanos.size

        // Per-operation durations in nanoseconds, ascending.
        private val perOpNanos: DoubleArray =
            rawSamplesNanos.map { it.toDouble() / opsPerSample }.sorted().toDoubleArray()

        val minNanos: Double get() = if (perOpNanos.isEmpty()) 0.0 else perOpNanos.first()
        val maxNanos: Double get() = if (perOpNanos.isEmpty()) 0.0 else perOpNanos.last()
        val meanNanos: Double get() = if (perOpNanos.isEmpty()) 0.0 else perOpNanos.average()
        val medianNanos: Double get() = percentileNanos(50.0)
        val p90Nanos: Double get() = percentileNanos(90.0)
        val p99Nanos: Double get() = percentileNanos(99.0)

        private fun percentileNanos(p: Double): Double {
            if (perOpNanos.isEmpty()) return 0.0
            val rank = Math.ceil(p / 100.0 * perOpNanos.size).toInt().coerceIn(1, perOpNanos.size)
            return perOpNanos[rank - 1]
        }
    }

    /**
     * Runs [block] [warmup] times (untimed) then [iterations] times (timed), returning per-sample
     * timings. Use when every iteration measures the same in-place work (e.g. an eval batch).
     */
    fun measure(
        name: String,
        opsPerSample: Int = 1,
        warmup: Int = 5,
        iterations: Int = 25,
        block: () -> Unit
    ): Stats {
        repeat(warmup) { block() }
        val raw = LongArray(iterations)
        for (i in 0 until iterations) {
            val start = System.nanoTime()
            block()
            raw[i] = System.nanoTime() - start
        }
        return Stats(name, opsPerSample, raw)
    }

    /**
     * Like [measure] but re-runs [setup] before each iteration and [teardown] after, timing only
     * [block]. Use when each iteration needs fresh, untimed state (e.g. a fresh client per
     * cold-start measurement).
     */
    fun <S> measureFresh(
        name: String,
        warmup: Int = 3,
        iterations: Int = 12,
        setup: () -> S,
        teardown: (S) -> Unit = {},
        block: (S) -> Unit
    ): Stats {
        repeat(warmup) {
            val state = setup()
            try {
                block(state)
            } finally {
                teardown(state)
            }
        }
        val raw = LongArray(iterations)
        for (i in 0 until iterations) {
            val state = setup()
            try {
                val start = System.nanoTime()
                block(state)
                raw[i] = System.nanoTime() - start
            } finally {
                teardown(state)
            }
        }
        return Stats(name, 1, raw)
    }

    /** Prints an aligned table of the given [stats] to stdout. */
    fun report(title: String, vararg stats: Stats) {
        val header = listOf("scenario", "n", "mean", "p50", "p90", "p99", "min", "max")
        val rows = stats.map { s ->
            val unitDivisor: Double
            val unit: String
            if (s.medianNanos < 1_000_000.0) {
                unitDivisor = 1_000.0
                unit = "us"
            } else {
                unitDivisor = 1_000_000.0
                unit = "ms"
            }
            fun fmt(nanos: Double) = String.format("%.3f%s", nanos / unitDivisor, unit)
            listOf(
                s.name,
                s.samples.toString(),
                fmt(s.meanNanos),
                fmt(s.medianNanos),
                fmt(s.p90Nanos),
                fmt(s.p99Nanos),
                fmt(s.minNanos),
                fmt(s.maxNanos)
            )
        }
        val widths = header.indices.map { col ->
            (rows.map { it[col].length } + header[col].length).max()
        }
        val sb = StringBuilder()
        sb.append('\n').append("=== ").append(title).append(" ===").append('\n')
        fun line(cells: List<String>) {
            sb.append(
                cells.mapIndexed { i, c -> c.padEnd(widths[i]) }.joinToString("  ")
            ).append('\n')
        }
        line(header)
        line(widths.map { "-".repeat(it) })
        rows.forEach { line(it) }
        // Single println keeps the table intact in Gradle's parallel test output.
        println(sb.toString())
    }
}
