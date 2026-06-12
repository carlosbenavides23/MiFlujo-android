package com.carlos.miflujo.ui.settings

import com.carlos.miflujo.data.cloud.auth.CloudSignInCanceledException
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudAccountFeedbackTest {
    @Test
    fun `fallback cancellation without ID token maps to required visible feedback`() {
        val feedback = fallbackFeedbackForIdToken(null)

        assertEquals(
            CloudAccountFeedback.SignInIncomplete,
            feedback,
        )
        assertEquals(
            "No se completó el inicio de sesión. Intenta nuevamente.",
            feedback.message,
        )
    }

    @Test
    fun `credential manager cancellation requests legacy fallback`() {
        assertEquals(
            true,
            CloudSignInCanceledException(
                IllegalStateException("Credential Manager canceled."),
            ).shouldStartLegacyGoogleSignInFallback(),
        )
    }

    @Test
    fun `credential manager no credential requests legacy fallback`() {
        assertEquals(
            true,
            com.carlos.miflujo.data.cloud.auth.CloudNoGoogleCredentialException(
                IllegalStateException("No credential."),
            ).shouldStartLegacyGoogleSignInFallback(),
        )
    }

    @Test
    fun `other credential failure does not request legacy fallback`() {
        assertEquals(
            false,
            IllegalStateException("Firebase failure.")
                .shouldStartLegacyGoogleSignInFallback(),
        )
    }
}
