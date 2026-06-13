package com.carlos.miflujo.data.cloud.sync

import androidx.work.ExistingWorkPolicy
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudSyncBackupWorkSchedulerTest {
    @Test
    fun `backup enqueue uses stable unique name and keep policy`() {
        var capturedName: String? = null
        var capturedPolicy: ExistingWorkPolicy? = null
        var callCount = 0
        val scheduler = WorkManagerCloudSyncBackupScheduler { name, policy ->
            capturedName = name
            capturedPolicy = policy
            callCount += 1
        }

        scheduler.enqueueBackup()

        assertEquals(1, callCount)
        assertEquals("cloud_sync_backup", capturedName)
        assertEquals(ExistingWorkPolicy.KEEP, capturedPolicy)
    }
}
