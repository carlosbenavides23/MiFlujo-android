package com.carlos.miflujo.data.cloud.sync

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncEnabledStoreTest {
    @Test
    fun `default is false when Cloud Sync has never been activated`() {
        val activationStore = FakeCloudSyncActivationStore(initialValue = false)
        val enabledStore = FakeCloudSyncEnabledStore(activationStore = activationStore)

        assertFalse(enabledStore.isEnabled())
    }

    @Test
    fun `default is true when Cloud Sync has already been activated`() {
        val activationStore = FakeCloudSyncActivationStore(initialValue = true)
        val enabledStore = FakeCloudSyncEnabledStore(activationStore = activationStore)

        assertTrue(enabledStore.isEnabled())
    }

    @Test
    fun `toggle on persists enabled`() {
        val activationStore = FakeCloudSyncActivationStore(initialValue = false)
        val enabledStore = FakeCloudSyncEnabledStore(activationStore = activationStore)

        enabledStore.setEnabled(true)

        assertTrue(enabledStore.isEnabled())
    }

    @Test
    fun `toggle off persists disabled`() {
        val activationStore = FakeCloudSyncActivationStore(initialValue = false)
        val enabledStore = FakeCloudSyncEnabledStore(activationStore = activationStore)

        enabledStore.setEnabled(true)
        enabledStore.setEnabled(false)

        assertFalse(enabledStore.isEnabled())
    }

    @Test
    fun `toggling enabled does not affect activated`() {
        val activationStore = FakeCloudSyncActivationStore(initialValue = false)
        val enabledStore = FakeCloudSyncEnabledStore(activationStore = activationStore)

        enabledStore.setEnabled(true)

        assertFalse(activationStore.isActivated())
    }

    @Test
    fun `activated remains independent when enabled is toggled off`() {
        val activationStore = FakeCloudSyncActivationStore(initialValue = true)
        val enabledStore = FakeCloudSyncEnabledStore(activationStore = activationStore)

        enabledStore.setEnabled(false)

        assertTrue(activationStore.isActivated())
        assertFalse(enabledStore.isEnabled())
    }

    @Test
    fun `explicit value overrides activation-based default`() {
        val activationStore = FakeCloudSyncActivationStore(initialValue = true)
        val enabledStore = FakeCloudSyncEnabledStore(activationStore = activationStore)

        assertTrue(enabledStore.isEnabled())

        enabledStore.setEnabled(false)
        assertFalse(enabledStore.isEnabled())

        enabledStore.setEnabled(true)
        assertTrue(enabledStore.isEnabled())
    }
}

/**
 * In-memory fake that mirrors the default logic of
 * SharedPreferencesCloudSyncEnabledStore without requiring Context.
 */
private class FakeCloudSyncEnabledStore(
    private val activationStore: CloudSyncActivationStore,
) : CloudSyncEnabledStore {
    private var explicitValue: Boolean? = null

    override fun isEnabled(): Boolean =
        explicitValue ?: activationStore.isActivated()

    override fun setEnabled(enabled: Boolean): Boolean {
        explicitValue = enabled
        return true
    }
}

private class FakeCloudSyncActivationStore(
    initialValue: Boolean = false,
) : CloudSyncActivationStore {
    private var activated = initialValue

    override fun isActivated(): Boolean = activated

    override fun markActivated() {
        activated = true
    }
}
