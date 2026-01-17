package com.entgldb.network.models

import org.junit.Assert.*
import org.junit.Test

class NodeAddressTest {

    @Test
    fun testNodeAddressCreation() {
        val address = NodeAddress("192.168.1.100", 5000)
        assertEquals("192.168.1.100", address.host)
        assertEquals(5000, address.port)
    }

    @Test
    fun testNodeAddressToString() {
        val address = NodeAddress("10.0.0.1", 8080)
        assertEquals("10.0.0.1:8080", address.toString())
    }

    @Test
    fun testNodeAddressParse() {
        val address = NodeAddress.parse("192.168.1.50:3000")
        assertEquals("192.168.1.50", address.host)
        assertEquals(3000, address.port)
    }

    @Test
    fun testNodeAddressParseLocalhost() {
        val address = NodeAddress.parse("localhost:5000")
        assertEquals("localhost", address.host)
        assertEquals(5000, address.port)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testNodeAddressParseInvalidFormat() {
        NodeAddress.parse("invalid-address")
    }

    @Test(expected = NumberFormatException::class)
    fun testNodeAddressParseInvalidPort() {
        NodeAddress.parse("192.168.1.1:abc")
    }
}
