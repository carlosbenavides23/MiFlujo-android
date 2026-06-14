package com.carlos.miflujo.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncNetworkAvailabilityTest {
    @Test
    fun `internet validated wifi is usable`() {
        assertTrue(usable(hasWifi = true))
    }

    @Test
    fun `internet validated cellular is usable`() {
        assertTrue(usable(hasCellular = true))
    }

    @Test
    fun `internet validated ethernet is usable`() {
        assertTrue(usable(hasEthernet = true))
    }

    @Test
    fun `internet validated vpn is usable`() {
        assertTrue(usable(hasVpn = true))
    }

    @Test
    fun `internet validated bluetooth only is not usable`() {
        assertFalse(usable(hasBluetooth = true))
    }

    @Test
    fun `internet unvalidated wifi is not usable`() {
        assertFalse(usable(isValidated = false, hasWifi = true))
    }

    @Test
    fun `validated wifi without internet capability is not usable`() {
        assertFalse(usable(hasInternet = false, hasWifi = true))
    }

    @Test
    fun `internet validated without accepted transport is not usable`() {
        assertFalse(usable())
    }

    private fun usable(
        hasInternet: Boolean = true,
        isValidated: Boolean = true,
        hasWifi: Boolean = false,
        hasCellular: Boolean = false,
        hasEthernet: Boolean = false,
        hasVpn: Boolean = false,
        hasBluetooth: Boolean = false,
    ): Boolean = isUsableCloudSyncNetwork(
        hasInternet = hasInternet,
        isValidated = isValidated,
        hasWifi = hasWifi,
        hasCellular = hasCellular,
        hasEthernet = hasEthernet,
        hasVpn = hasVpn,
        hasBluetooth = hasBluetooth,
    )
}
