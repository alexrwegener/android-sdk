package com.statsig.androidsdk

/**
 * Shared fixtures for the performance suite: a "typical" small initialize response (the standard
 * dummy data used across the unit tests) and generators for large payloads used to measure
 * parse/format and evaluation scaling.
 *
 * Two keying modes are supported via [hashed]:
 *  - hashed = true: keys carry a trailing "!" to match [TestUtil.mockHashing], which maps a
 *    plaintext name to `name + "!"`. Used by the startup suite (which mocks hashing).
 *  - hashed = false: plaintext keys, used by the evaluation suite together with
 *    `StatsigOptions(disableHashing = true)`. This avoids stubbing [Hashing] with mockk, which
 *    would otherwise retain a call record per eval and OOM under tight loops.
 */
internal object PerfPayloads {
    const val LARGE_COUNT = 1_000
    const val SMALL_COUNT = 16

    fun gateName(i: Int) = "perf_gate_$i"
    fun configName(i: Int) = "perf_config_$i"
    fun layerName(i: Int) = "perf_layer_$i"

    private fun key(name: String, hashed: Boolean) = if (hashed) "$name!" else name

    fun gates(count: Int, hashed: Boolean = true): Map<String, APIFeatureGate> = buildMap(count) {
        for (i in 0 until count) {
            val k = key(gateName(i), hashed)
            put(
                k,
                APIFeatureGate(
                    name = k,
                    value = i % 2 == 0,
                    ruleID = "rule_$i",
                    groupName = "group_$i",
                    secondaryExposures = arrayOf()
                )
            )
        }
    }

    fun configs(count: Int, hashed: Boolean = true): Map<String, APIDynamicConfig> =
        buildMap(count) {
            for (i in 0 until count) {
                val k = key(configName(i), hashed)
                put(
                    k,
                    APIDynamicConfig(
                        name = k,
                        value = mapOf(
                            "num" to i,
                            "str" to "value_$i",
                            "flag" to (i % 2 == 0),
                            "nested" to mapOf("a" to i, "b" to "x")
                        ),
                        ruleID = "rule_$i",
                        groupName = "group_$i"
                    )
                )
            }
        }

    fun layers(count: Int, hashed: Boolean = true): Map<String, APIDynamicConfig> =
        buildMap(count) {
            for (i in 0 until count) {
                val k = key(layerName(i), hashed)
                put(
                    k,
                    APIDynamicConfig(
                        name = k,
                        value = mapOf("string" to "value_$i", "number" to i),
                        ruleID = "rule_$i",
                        groupName = "group_$i",
                        isExperimentActive = true,
                        isUserInExperiment = true,
                        explicitParameters = arrayOf("string", "number")
                    )
                )
            }
        }

    /**
     * Serialized initialize response body. With counts of 0 (default) the standard small dummy
     * payload is used; otherwise [gateCount]/[configCount] generated (hashed-key) entries are used.
     */
    fun initializeResponseJson(gateCount: Int = 0, configCount: Int = 0): String {
        val response = if (gateCount == 0 && configCount == 0) {
            TestUtil.makeInitializeResponse()
        } else {
            TestUtil.makeInitializeResponse(
                featureGates = gates(gateCount, hashed = true),
                dynamicConfigs = configs(configCount, hashed = true),
                layerConfigs = emptyMap()
            )
        }
        return StatsigUtil.getOrBuildGson().toJson(response)
    }
}
