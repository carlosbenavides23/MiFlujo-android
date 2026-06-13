package com.carlos.miflujo.ui.settings

import com.carlos.miflujo.data.cloud.sync.CloudSyncScheduledRunExecutor
import com.carlos.miflujo.data.cloud.sync.CloudSyncTriggerReason

class ManualCloudSyncScheduledRunExecutor internal constructor(
    private val syncNow: suspend (String, CloudSyncTriggerReason) -> Unit,
) : CloudSyncScheduledRunExecutor {
    constructor(
        stateHolder: ManualCloudSyncStateHolder,
    ) : this(stateHolder::syncNow)

    override suspend fun runCloudSync(
        requestId: String,
        reason: CloudSyncTriggerReason,
    ) {
        syncNow(requestId, reason)
    }
}
