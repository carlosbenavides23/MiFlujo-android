package com.carlos.miflujo.data.cloud.auth

import android.util.Log

const val MiFlujoAuthLogTag = "MiFlujoAuth"

fun logMiFlujoAuthDebug(message: String) {
    runCatching { Log.d(MiFlujoAuthLogTag, message) }
}

fun logMiFlujoAuthError(message: String) {
    runCatching { Log.e(MiFlujoAuthLogTag, message) }
}
