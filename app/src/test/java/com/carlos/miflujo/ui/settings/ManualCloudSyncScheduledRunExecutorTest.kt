package com.carlos.miflujo.ui.settings

import com.carlos.miflujo.data.cloud.sync.CloudSyncResult
import com.carlos.miflujo.data.cloud.sync.CloudSyncRunOutcome
import com.carlos.miflujo.data.cloud.sync.CloudSyncStatus
import com.carlos.miflujo.data.cloud.sync.CloudSyncTriggerReason
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ManualCloudSyncScheduledRunExecutorTest {
    @Test
    fun `adapter delegates exactly once with same request id and reason`() = runBlocking {
        val calls = mutableListOf<RunCall>()
        val adapter = ManualCloudSyncScheduledRunExecutor { requestId, reason ->
            calls += RunCall(requestId, reason)
            CloudSyncRunOutcome.Completed(CloudSyncResult(CloudSyncStatus.SUCCESS))
        }

        val outcome = adapter.runCloudSync(
            requestId = "scheduled-request-id",
            reason = CloudSyncTriggerReason.APP_FOREGROUND,
        )

        assertEquals(1, calls.size)
        assertEquals("scheduled-request-id", calls.single().requestId)
        assertEquals(CloudSyncTriggerReason.APP_FOREGROUND, calls.single().reason)
        assertEquals(
            CloudSyncRunOutcome.Completed(CloudSyncResult(CloudSyncStatus.SUCCESS)),
            outcome,
        )
    }

    private data class RunCall(
        val requestId: String,
        val reason: CloudSyncTriggerReason,
    )
}
