package com.statsig.androidsdk

import android.app.Application
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Measures SDK start-up cost across the paths that dominate perceived latency:
 *  - online cold start (network hydration via [MockWebServer]),
 *  - offline warm start (values served from the on-disk cache),
 *  - updateUser (re-fetch + re-evaluate for a new user),
 *  - online cold start with a large payload (parse/format scaling).
 *
 * See [PerfHarness] for how to run and how to read the numbers.
 */
@RunWith(RobolectricTestRunner::class)
class StartupPerformanceTest {
    private lateinit var app: Application
    private lateinit var mockWebServer: MockWebServer

    // Response body served for /initialize; swapped per scenario.
    @Volatile private var initializeResponseBody: String = ""
    private val userCounter = AtomicInteger(0)

    @Before
    internal fun setup() {
        PerfHarness.assumeEnabled()
        app = RuntimeEnvironment.getApplication()
        TestUtil.mockDispatchers()
        TestUtil.mockHashing()

        mockWebServer = MockWebServer()
        mockWebServer.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse =
                if (request.path?.contains("initialize") == true) {
                    MockResponse().setResponseCode(200).setBody(initializeResponseBody)
                } else {
                    // Event-logging / diagnostics endpoints — acknowledge and move on.
                    MockResponse().setResponseCode(200).setBody("{\"success\":true}")
                }
        }
        mockWebServer.start()
        TestUtil.setupHttp(app)

        initializeResponseBody = PerfPayloads.initializeResponseJson()
    }

    @After
    fun tearDown() {
        if (PerfHarness.isEnabled()) {
            mockWebServer.shutdown()
        }
        TestUtil.reset()
    }

    private fun apiUrl() = mockWebServer.url("/v1").toString()

    @Test
    fun measureStartupPerformance() {
        val online = PerfHarness.measureFresh(
            name = "online cold start (network)",
            setup = { StatsigClient() },
            teardown = { it.shutdown() },
            block = { client ->
                // Fresh user each iteration => no cached values => true network cold start.
                val user = StatsigUser("perf-online-${userCounter.getAndIncrement()}")
                runBlocking {
                    client.initialize(app, "client-perf", user, StatsigOptions(api = apiUrl()))
                }
            }
        )

        // Offline warm start reads from the on-disk cache. Hydrate the cache once for a fixed
        // user, then measure repeated offline initializes against it.
        val offlineUser = StatsigUser("perf-offline")
        runBlocking {
            val warm = StatsigClient()
            warm.initialize(app, "client-perf", offlineUser, StatsigOptions(api = apiUrl()))
            warm.shutdownSuspend()
        }
        val offline = PerfHarness.measureFresh(
            name = "offline warm start (cache)",
            setup = { StatsigClient() },
            teardown = { it.shutdown() },
            block = { client ->
                runBlocking {
                    client.initialize(
                        app,
                        "client-perf",
                        offlineUser,
                        StatsigOptions(api = apiUrl(), initializeOffline = true)
                    )
                }
            }
        )

        // updateUser: fetch + re-evaluate for a brand-new user on an already-initialized client.
        val updateClient = StatsigClient()
        runBlocking {
            updateClient.initialize(
                app,
                "client-perf",
                StatsigUser("perf-update-seed"),
                StatsigOptions(api = apiUrl())
            )
        }
        val updateUser = PerfHarness.measure(
            name = "updateUser (network)",
            warmup = 3,
            iterations = 12
        ) {
            runBlocking {
                updateClient.updateUser(StatsigUser("perf-update-${userCounter.getAndIncrement()}"))
            }
        }
        updateClient.shutdown()

        // Large payload: 1,000 gates + 1,000 configs. Isolates parse/format scaling on cold start.
        initializeResponseBody = PerfPayloads.initializeResponseJson(
            gateCount = PerfPayloads.LARGE_COUNT,
            configCount = PerfPayloads.LARGE_COUNT
        )
        val largeOnline = PerfHarness.measureFresh(
            name = "online cold start (${PerfPayloads.LARGE_COUNT} gates + configs)",
            warmup = 2,
            iterations = 8,
            setup = { StatsigClient() },
            teardown = { it.shutdown() },
            block = { client ->
                val user = StatsigUser("perf-large-${userCounter.getAndIncrement()}")
                runBlocking {
                    client.initialize(app, "client-perf", user, StatsigOptions(api = apiUrl()))
                }
            }
        )

        PerfHarness.report(
            "Startup performance (JVM/Robolectric — relative only)",
            online,
            offline,
            updateUser,
            largeOnline
        )
    }
}
