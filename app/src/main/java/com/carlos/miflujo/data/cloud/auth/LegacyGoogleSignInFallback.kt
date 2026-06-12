@file:Suppress("DEPRECATION")

package com.carlos.miflujo.data.cloud.auth

import android.app.Activity
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

class LegacyGoogleSignInFallback(
    activity: Activity,
    googleWebClientId: String,
) {
    private val client = GoogleSignIn.getClient(
        activity,
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(googleWebClientId)
            .requestEmail()
            .build(),
    )

    fun signInIntent(): Intent = client.signInIntent

    fun extractIdToken(data: Intent?): String? {
        return try {
            GoogleSignIn.getSignedInAccountFromIntent(data)
                .getResult(ApiException::class.java)
                .idToken
        } catch (exception: ApiException) {
            Log.e(
                MiFlujoAuthLogTag,
                "GoogleSignInClient fallback ApiException: " +
                    "statusCode=${exception.statusCode}, " +
                    "statusMessage=${exception.statusMessage}.",
            )
            null
        } catch (_: Exception) {
            null
        }
    }
}
