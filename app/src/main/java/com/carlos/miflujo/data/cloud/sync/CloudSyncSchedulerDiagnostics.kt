package com.carlos.miflujo.data.cloud.sync

import java.util.UUID

enum class CloudSyncTriggerReason {
    MANUAL_SETTINGS,
    APP_FOREGROUND,
    CONNECTIVITY_RECOVERED,
    FOREGROUND_PENDING_TIMER,
    WORK_MANAGER_BACKUP,
}

enum class CloudSyncSchedulerAction {
    RUN,
    SKIP_DISABLED,
    SKIP_NOT_ACTIVATED,
    SKIP_OFFLINE,
    SKIP_NO_PENDING_CHANGES,
    SKIP_ALREADY_RUNNING,
    SKIP_ACCOUNT_NOT_AUTHORIZED,
    SKIP_ACCOUNT_OPERATION_RUNNING,
}

fun generateCloudSyncRequestId(): String = UUID.randomUUID().toString().take(8)

fun logCloudSyncRequest(id: String, reason: CloudSyncTriggerReason) {
    logMiFlujoSyncDebug("CloudSync request: id=$id, reason=$reason")
}

fun logCloudSyncDecision(
    id: String,
    reason: CloudSyncTriggerReason,
    action: CloudSyncSchedulerAction,
    cloudSyncEnabled: Boolean? = null,
    cloudSyncActivated: Boolean? = null,
    networkAvailable: Boolean? = null,
    accountAuthorized: Boolean? = null,
    alreadyRunning: Boolean? = null,
    accountOperationRunning: Boolean? = null,
    hasPendingLocalChanges: Boolean? = null,
) {
    val details = buildString {
        append("CloudSync decision: id=$id, reason=$reason, action=$action")
        cloudSyncEnabled?.let { append(", cloudSyncEnabled=$it") }
        cloudSyncActivated?.let { append(", cloudSyncActivated=$it") }
        networkAvailable?.let { append(", networkAvailable=$it") }
        accountAuthorized?.let { append(", accountAuthorized=$it") }
        alreadyRunning?.let { append(", alreadyRunning=$it") }
        accountOperationRunning?.let { append(", accountOperationRunning=$it") }
        hasPendingLocalChanges?.let { append(", hasPendingLocalChanges=$it") }
    }
    logMiFlujoSyncDebug(details.trim())
}

fun logCloudSyncRunStarted(id: String, reason: CloudSyncTriggerReason) {
    logMiFlujoSyncDebug("CloudSync run started: id=$id, reason=$reason")
}

fun logCloudSyncRunFinished(
    id: String,
    reason: CloudSyncTriggerReason,
    result: CloudSyncResult,
) {
    logMiFlujoSyncDebug(
        "CloudSync run finished: id=$id, reason=$reason, status=${result.status}, " +
            "uploaded=${result.uploaded}, downloaded=${result.downloaded}, markedSynced=${result.markedSynced}",
    )
}

fun logCloudSyncMetadataUpdate(
    id: String,
    activatedUpdated: Boolean,
    lastSyncUpdated: Boolean,
) {
    logMiFlujoSyncDebug(
        "CloudSync metadata update: id=$id, activatedUpdated=$activatedUpdated, lastSyncUpdated=$lastSyncUpdated",
    )
}
