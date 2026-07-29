package com.statsig.androidsdk

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class StatsigOptionsTest {
    @Test
    fun autoValueUpdateInterval_valueLessThanMinimum_enforcesMinimum() {
        val options = StatsigOptions(autoValueUpdateIntervalMinutes = 0.5)
        assertThat(options.autoValueUpdateIntervalMinutes).isEqualTo(
            AUTO_VALUE_UPDATE_INTERVAL_MINIMUM_VALUE
        )

        options.autoValueUpdateIntervalMinutes = 1.5
        assertThat(options.autoValueUpdateIntervalMinutes).isEqualTo(1.5)
    }

    @Test
    fun setTier_writesLowerCaseEnvironmentVariable() {
        val options = StatsigOptions()
        options.setTier(Tier.PRODUCTION)

        assertThat(options.getEnvironment()?.values).contains(
            Tier.PRODUCTION.toString().lowercase()
        )
        assertThat(
            options.getLoggingCopy()["environment"] as Map<String, String>
        ).containsEntry("tier", Tier.PRODUCTION.toString().lowercase())
    }

    @Test
    fun setTierString_writesLowerCaseEnvironmentVariable() {
        val customTier = "CUSTOMER-DEFINED-TIER"
        val options = StatsigOptions()
        options.setTier(customTier)

        assertThat(options.getEnvironment()?.values).contains(customTier.lowercase())
        assertThat(
            options.getLoggingCopy()["environment"] as Map<String, String>
        ).containsEntry("tier", customTier.lowercase())
    }

    @Test
    fun urlOverrides_defaultToNull() {
        val options = StatsigOptions()
        assertThat(options.initializeURL).isNull()
        assertThat(options.eventLoggingURL).isNull()
    }

    @Test
    fun loggingCopy_surfacesUrlOverrides() {
        val options = StatsigOptions(
            initializeURL = "https://flags.life360.com/v1/initialize",
            eventLoggingURL = "https://flags.life360.com/v1/l360-rgstr"
        )
        val copy = options.getLoggingCopy()
        assertThat(copy).containsEntry(
            "initializeURL",
            "https://flags.life360.com/v1/initialize"
        )
        assertThat(copy).containsEntry(
            "eventLoggingURL",
            "https://flags.life360.com/v1/l360-rgstr"
        )
    }
}
