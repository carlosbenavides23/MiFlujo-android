package com.carlos.miflujo.data.cloud.sync

import android.content.Context

interface CloudSyncMetadataStore {
    fun getLastSyncTimestamp(): Long?
    fun updateLastSyncTimestamp(timestamp: Long)
}

class SharedPreferencesCloudSyncMetadataStore(
    context: Context,
) : CloudSyncMetadataStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        PreferencesFileName,
        Context.MODE_PRIVATE,
    )

    override fun getLastSyncTimestamp(): Long? {
        val timestamp = preferences.getLong(LastSyncTimestampKey, -1L)
        return if (timestamp == -1L) null else timestamp
    }

    override fun updateLastSyncTimestamp(timestamp: Long) {
        val persisted = runCatching {
            preferences.edit()
                .putLong(LastSyncTimestampKey, timestamp)
                .commit()
        }.getOrDefault(false)
        if (!persisted) {
            logMiFlujoSyncError("Last sync timestamp could not be persisted.")
        }
    }
}

private const val PreferencesFileName = "miflujo_cloud_sync_preferences"
private const val LastSyncTimestampKey = "last_sync_timestamp"
