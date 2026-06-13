package com.carlos.miflujo.data.cloud.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

fun interface CloudSyncBackupWorkScheduler {
    fun enqueueBackup()
}

class WorkManagerCloudSyncBackupScheduler internal constructor(
    private val enqueueUniqueWork: (String, ExistingWorkPolicy) -> Unit,
) : CloudSyncBackupWorkScheduler {
    constructor(context: Context) : this(
        enqueueUniqueWork = WorkManager.getInstance(context.applicationContext).let { workManager ->
            { uniqueWorkName, existingWorkPolicy ->
                workManager.enqueueUniqueWork(
                    uniqueWorkName,
                    existingWorkPolicy,
                    cloudSyncBackupWorkRequest(),
                )
            }
        },
    )

    override fun enqueueBackup() {
        enqueueUniqueWork(UNIQUE_WORK_NAME, ExistingWorkPolicy.KEEP)
    }

    companion object {
        const val UNIQUE_WORK_NAME = "cloud_sync_backup"
    }
}

private fun cloudSyncBackupWorkRequest() =
    OneTimeWorkRequestBuilder<CloudSyncBackupWorker>()
        .setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build(),
        )
        .setBackoffCriteria(
            BackoffPolicy.EXPONENTIAL,
            30L,
            TimeUnit.SECONDS,
        )
        .build()
