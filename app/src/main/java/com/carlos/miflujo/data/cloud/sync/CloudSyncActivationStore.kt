package com.carlos.miflujo.data.cloud.sync

import android.content.Context

interface CloudSyncActivationStore {
    fun isActivated(): Boolean

    fun markActivated()
}

class SharedPreferencesCloudSyncActivationStore(
    context: Context,
) : CloudSyncActivationStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesFileName,
        Context.MODE_PRIVATE,
    )

    override fun isActivated(): Boolean =
        preferences.getBoolean(CloudSyncActivatedKey, false)

    override fun markActivated() {
        if (isActivated()) return

        val persisted = runCatching {
            preferences.edit()
                .putBoolean(CloudSyncActivatedKey, true)
                .commit()
        }.getOrDefault(false)
        if (!persisted) {
            logMiFlujoSyncError("Cloud Sync activation flag could not be persisted.")
        }
    }
}

private const val PreferencesFileName = "miflujo_cloud_sync_preferences"
private const val CloudSyncActivatedKey = "cloud_sync_activated"
