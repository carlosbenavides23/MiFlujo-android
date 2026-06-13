package com.carlos.miflujo.data.cloud.sync

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncSchedulerCoordinatorTest {
    @Test
    fun `run decision executes exactly once with generated request id and reason`() = runBlocking {
        val executor = RecordingExecutor()
        val coordinator = coordinator(executor)

        val action = coordinator.requestSync(
            runnableInput(reason = CloudSyncTriggerReason.APP_FOREGROUND),
        )

        assertEquals(CloudSyncSchedulerAction.RUN, action)
        assertEquals(1, executor.calls.size)
        assertEquals("test-request-id", executor.calls.single().requestId)
        assertEquals(CloudSyncTriggerReason.APP_FOREGROUND, executor.calls.single().reason)
    }

    @Test
    fun `disabled decision skips executor`() = runBlocking {
        assertSkipped(
            expected = CloudSyncSchedulerAction.SKIP_DISABLED,
            input = runnableInput().copy(cloudSyncEnabled = false),
        )
    }

    @Test
    fun `not activated decision skips executor`() = runBlocking {
        assertSkipped(
            expected = CloudSyncSchedulerAction.SKIP_NOT_ACTIVATED,
            input = runnableInput(
                reason = CloudSyncTriggerReason.CONNECTIVITY_RECOVERED,
            ).copy(cloudSyncActivated = false),
        )
    }

    @Test
    fun `no pending changes decision skips executor`() = runBlocking {
        assertSkipped(
            expected = CloudSyncSchedulerAction.SKIP_NO_PENDING_CHANGES,
            input = runnableInput(
                reason = CloudSyncTriggerReason.FOREGROUND_PENDING_TIMER,
            ).copy(hasPendingLocalChanges = false),
        )
    }

    @Test
    fun `coordinator uses request id provider for executor call`() = runBlocking {
        val executor = RecordingExecutor()
        val coordinator = CloudSyncSchedulerCoordinator(
            executor = executor,
            requestIdProvider = { "deterministic-id" },
        )

        coordinator.requestSync(runnableInput())

        assertEquals("deterministic-id", executor.calls.single().requestId)
    }

    @Test
    fun `coordinator preserves trigger reason for executor call`() = runBlocking {
        val executor = RecordingExecutor()
        val coordinator = coordinator(executor)

        coordinator.requestSync(
            runnableInput(reason = CloudSyncTriggerReason.WORK_MANAGER_BACKUP),
        )

        assertEquals(
            CloudSyncTriggerReason.WORK_MANAGER_BACKUP,
            executor.calls.single().reason,
        )
    }

    private suspend fun assertSkipped(
        expected: CloudSyncSchedulerAction,
        input: CloudSyncSchedulerDecisionInput,
    ) {
        val executor = RecordingExecutor()

        val action = coordinator(executor).requestSync(input)

        assertEquals(expected, action)
        assertTrue(executor.calls.isEmpty())
    }

    private fun coordinator(
        executor: RecordingExecutor,
    ): CloudSyncSchedulerCoordinator = CloudSyncSchedulerCoordinator(
        executor = executor,
        requestIdProvider = { "test-request-id" },
    )

    private fun runnableInput(
        reason: CloudSyncTriggerReason = CloudSyncTriggerReason.MANUAL_SETTINGS,
    ): CloudSyncSchedulerDecisionInput = CloudSyncSchedulerDecisionInput(
        reason = reason,
        cloudSyncEnabled = true,
        cloudSyncActivated = true,
        networkAvailable = true,
        accountAuthorized = true,
        alreadyRunning = false,
        accountOperationRunning = false,
        hasPendingLocalChanges = true,
    )

    private class RecordingExecutor : CloudSyncScheduledRunExecutor {
        val calls = mutableListOf<RunCall>()

        override suspend fun runCloudSync(
            requestId: String,
            reason: CloudSyncTriggerReason,
        ) {
            calls += RunCall(requestId, reason)
        }
    }

    private data class RunCall(
        val requestId: String,
        val reason: CloudSyncTriggerReason,
    )
}
