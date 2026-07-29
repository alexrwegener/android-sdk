package com.statsig.androidsdk

import android.app.Application
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Measures the cost of the synchronous evaluation APIs once the SDK is initialized. These are the
 * hot-path calls an app makes on every screen/interaction, so per-call latency matters most here.
 *
 * Each sample times a batch of calls and [PerfHarness] reports per-operation numbers.
 *
 * Hashing is disabled and payloads use plaintext keys so we don't stub [Hashing] with mockk — a
 * mockk stub retains a call record per invocation and would OOM under these tight loops. See
 * [PerfHarness] for how to run and how to read the numbers.
 */
@RunWith(RobolectricTestRunner::class)
class EvaluationPerformanceTest {
    private lateinit var app: Application

    @Before
    internal fun setup() {
        PerfHarness.assumeEnabled()
        app = RuntimeEnvironment.getApplication()
        TestUtil.mockDispatchers()
        TestUtil.setupHttp(app)
    }

    @After
    fun tearDown() {
        TestUtil.reset()
    }

    private fun newInitializedClient(network: StatsigNetwork): StatsigClient {
        val client = StatsigClient()
        client.statsigNetwork = network
        runBlocking {
            client.initialize(
                app,
                "client-perf",
                StatsigUser("perf-eval"),
                StatsigOptions(disableHashing = true)
            )
        }
        return client
    }

    @Test
    fun measureEvaluationPerformance() {
        // Small payload — one entry of each type is queried repeatedly (typical single-flag reads).
        val small = PerfPayloads.SMALL_COUNT
        val client = newInitializedClient(
            TestUtil.mockNetwork(
                featureGates = PerfPayloads.gates(small, hashed = false),
                dynamicConfigs = PerfPayloads.configs(small, hashed = false),
                layerConfigs = PerfPayloads.layers(small, hashed = false)
            )
        )
        val batch = 1_000

        val checkGate = PerfHarness.measure("checkGate", opsPerSample = batch, iterations = 20) {
            repeat(batch) { client.checkGate(PerfPayloads.gateName(0)) }
        }
        val getFeatureGate =
            PerfHarness.measure("getFeatureGate", opsPerSample = batch, iterations = 20) {
                repeat(batch) { client.getFeatureGate(PerfPayloads.gateName(0)) }
            }
        val getConfig = PerfHarness.measure("getConfig", opsPerSample = batch, iterations = 20) {
            repeat(batch) { client.getConfig(PerfPayloads.configName(0)) }
        }
        val getExperiment =
            PerfHarness.measure("getExperiment", opsPerSample = batch, iterations = 20) {
                repeat(batch) { client.getExperiment(PerfPayloads.configName(0)) }
            }
        val getLayer = PerfHarness.measure("getLayer", opsPerSample = batch, iterations = 20) {
            repeat(batch) { client.getLayer(PerfPayloads.layerName(0)) }
        }
        client.shutdown()

        PerfHarness.report(
            "Evaluation performance — standard payload (JVM/Robolectric — relative only)",
            checkGate,
            getFeatureGate,
            getConfig,
            getExperiment,
            getLayer
        )

        // Large payload: evaluated round-robin so no single name is favored by caching/dedupe.
        // Surfaces map-lookup / evaluation scaling.
        val count = PerfPayloads.LARGE_COUNT
        val largeClient = newInitializedClient(
            TestUtil.mockNetwork(
                featureGates = PerfPayloads.gates(count, hashed = false),
                dynamicConfigs = PerfPayloads.configs(count, hashed = false),
                layerConfigs = emptyMap()
            )
        )
        val checkGateLarge =
            PerfHarness.measure("checkGate ($count gates)", opsPerSample = count, iterations = 20) {
                for (i in 0 until count) {
                    largeClient.checkGate(PerfPayloads.gateName(i))
                }
            }
        val getConfigLarge = PerfHarness.measure(
            "getConfig ($count configs)",
            opsPerSample = count,
            iterations = 20
        ) {
            for (i in 0 until count) {
                largeClient.getConfig(PerfPayloads.configName(i))
            }
        }
        largeClient.shutdown()

        PerfHarness.report(
            "Evaluation performance — large payload (JVM/Robolectric — relative only)",
            checkGateLarge,
            getConfigLarge
        )
    }
}
