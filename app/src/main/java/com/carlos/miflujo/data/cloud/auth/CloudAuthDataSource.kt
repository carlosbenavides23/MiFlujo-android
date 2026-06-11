package com.carlos.miflujo.data.cloud.auth

import android.content.Context

interface CloudAuthDataSource {
    fun currentAccount(): CloudAccount?

    suspend fun signInWithGoogle(context: Context): CloudAccount

    suspend fun signOut(context: Context)
}
