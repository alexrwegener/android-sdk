package com.statsig.androidsdk

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UrlConfigTest {
    @Test
    fun fullUrl_isUsedVerbatim_withoutPathConcatenation() {
        val config = UrlConfig(
            Endpoint.Initialize,
            fullUrl = "https://flags.life360.com/v1/initialize"
        )
        assertThat(config.getUrl()).isEqualTo("https://flags.life360.com/v1/initialize")
        assertThat(config.customUrl).isEqualTo("https://flags.life360.com/v1/initialize")
    }

    @Test
    fun fullUrl_takesPrecedenceOverInputApi() {
        val config = UrlConfig(
            Endpoint.Initialize,
            inputApi = "https://x.example.com/",
            fullUrl = "https://y.example.com/v1/initialize"
        )
        assertThat(config.getUrl()).isEqualTo("https://y.example.com/v1/initialize")
    }

    @Test
    fun nullFullUrl_preservesInputApiConcatenation() {
        val config = UrlConfig(
            Endpoint.Initialize,
            inputApi = "https://x.example.com/",
            fullUrl = null
        )
        assertThat(config.getUrl()).isEqualTo("https://x.example.com/initialize")
        assertThat(config.customUrl).isEqualTo("https://x.example.com/initialize")
    }

    @Test
    fun rgstrFullUrl_returnedVerbatim() {
        val config = UrlConfig(
            Endpoint.Rgstr,
            fullUrl = "https://flags.life360.com/v1/l360-rgstr"
        )
        assertThat(config.getUrl()).isEqualTo("https://flags.life360.com/v1/l360-rgstr")
    }

    @Test
    fun userFallbackUrls_remainReadable_whenFullUrlSet() {
        val fallbacks = listOf("fallback.example.com")
        val config = UrlConfig(
            Endpoint.Initialize,
            userFallbackUrls = fallbacks,
            fullUrl = "https://proxy.example.com/v1/initialize"
        )
        assertThat(config.userFallbackUrls).isEqualTo(fallbacks)
        assertThat(config.customUrl).isEqualTo("https://proxy.example.com/v1/initialize")
    }

    @Test
    fun noOverrides_returnsDefaultUrl() {
        val config = UrlConfig(Endpoint.Initialize)
        assertThat(config.customUrl).isNull()
        assertThat(config.getUrl()).isEqualTo("${DEFAULT_INIT_API}initialize")
    }
}
