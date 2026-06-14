package com.carlos.miflujo.data.cloud.sync

import android.content.Context

/**
 * Manages the local-only cloudSyncEnabled user preference.
 *
 * This flag is independently toggleable on/off and controls whether manual
 * sync UI is active. It does NOT affect the irreversible cloudSyncActivated
 * safety flag used for restore blocking.
 *
 * Default behavior:
 * - If Cloud Sync has never been activated: false.
 * - If Cloud Sync has already been activated: true (safest default to avoid
 *   confusing the user about why their sync stopped).
 */
interface CloudSyncEnabledStore {
    fun isEnabled(): Boolean

    fun setEnabled(enabled: Boolean): Boolean
}

class SharedPreferencesCloudSyncEnabledStore(
    context: Context,
    private val activationStore: CloudSyncActivationStore,
) : CloudSyncEnabledStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesFileName,
        Context.MODE_PRIVATE,
    )

    override fun isEnabled(): Boolean {
        if (!preferences.contains(CloudSyncEnabledKey)) {
            return activationStore.isActivated()
        }
        return preferences.getBoolean(CloudSyncEnabledKey, false)
    }

    override fun setEnabled(enabled: Boolean): Boolean {
        val persisted = runCatching {
            preferences.edit()
                .putBoolean(CloudSyncEnabledKey, enabled)
                .commit()
        }.getOrDefault(false)
        if (!persisted) {
            logMiFlujoSyncError("Cloud Sync enabled preference could not be persisted.")
        }
        return persisted
    }
}

private const val PreferencesFileName = "miflujo_cloud_sync_preferences"
private const val CloudSyncEnabledKey = "cloud_sync_enabled"
