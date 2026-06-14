package com.carlos.miflujo.data.cloud.firestore

fun interface CloudAuthorizationChecker {
    suspend fun isAuthorized(uid: String): Boolean
}
