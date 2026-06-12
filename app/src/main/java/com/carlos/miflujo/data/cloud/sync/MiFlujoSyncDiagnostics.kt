package com.carlos.miflujo.data.cloud.sync

import android.util.Log

const val MiFlujoSyncLogTag = "MiFlujoSync"

fun logMiFlujoSyncDebug(message: String) {
    runCatching { Log.d(MiFlujoSyncLogTag, message) }
}

fun logMiFlujoSyncError(message: String) {
    runCatching { Log.e(MiFlujoSyncLogTag, message) }
}

fun CloudSyncResult.toSafeSyncLogMessage(prefix: String): String =
    "$prefix: status=$status, uploaded=$uploaded, downloaded=$downloaded, " +
        "updatedLocal=$updatedLocal, markedSynced=$markedSynced, " +
        "skippedRemote=$skippedRemote, localErrors=$localErrors, remoteErrors=$remoteErrors."
