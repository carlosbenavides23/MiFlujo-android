package com.carlos.miflujo.data.cloud.catalog

import org.junit.Assert.assertThrows
import org.junit.Test

class EmulatorCatalogClientTest {
    @Test
    fun acceptsHostsForAvdAndUsbReverse() {
        EmulatorCatalogClient.validateHost("10.0.2.2")
        EmulatorCatalogClient.validateHost("127.0.0.1")
    }

    @Test
    fun rejectsIncompleteAndUnsupportedHosts() {
        for (host in listOf("", " ", "localhost", "192.168.1.2", "8.8.8.8", "127.0.0.1:8080", "http://127.0.0.1")) {
            assertThrows("Unexpected valid host: '$host'", IllegalArgumentException::class.java) {
                EmulatorCatalogClient.validateHost(host)
            }
        }
    }
}
