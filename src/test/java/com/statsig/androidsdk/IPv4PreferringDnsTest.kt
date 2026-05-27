package com.statsig.androidsdk

import com.google.common.truth.Truth.assertThat
import java.net.Inet4Address
import java.net.Inet6Address
import java.net.InetAddress
import okhttp3.Dns
import org.junit.Test

class IPv4PreferringDnsTest {
    private val v4a: InetAddress = Inet4Address.getByName("1.1.1.1")
    private val v4b: InetAddress = Inet4Address.getByName("2.2.2.2")
    private val v6a: InetAddress = Inet6Address.getByName("2001:db8::1")
    private val v6b: InetAddress = Inet6Address.getByName("2001:db8::2")

    private fun stub(addresses: List<InetAddress>) = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> = addresses
    }

    @Test
    fun mixedAddresses_v4First_preservingRelativeOrder() {
        val dns = IPv4PreferringDns(stub(listOf(v6a, v4a, v6b, v4b)))
        assertThat(dns.lookup("example.com")).containsExactly(v4a, v4b, v6a, v6b).inOrder()
    }

    @Test
    fun v4Only_unchanged() {
        val input = listOf(v4a, v4b)
        val dns = IPv4PreferringDns(stub(input))
        assertThat(dns.lookup("example.com")).containsExactlyElementsIn(input).inOrder()
    }

    @Test
    fun v6Only_unchanged() {
        val input = listOf(v6a, v6b)
        val dns = IPv4PreferringDns(stub(input))
        assertThat(dns.lookup("example.com")).containsExactlyElementsIn(input).inOrder()
    }

    @Test
    fun emptyInput_empty() {
        val dns = IPv4PreferringDns(stub(emptyList()))
        assertThat(dns.lookup("example.com")).isEmpty()
    }

    @Test(expected = RuntimeException::class)
    fun delegateThrows_propagates() {
        val dns = IPv4PreferringDns(object : Dns {
            override fun lookup(hostname: String): List<InetAddress> =
                throw RuntimeException("boom")
        })
        dns.lookup("example.com")
    }
}
