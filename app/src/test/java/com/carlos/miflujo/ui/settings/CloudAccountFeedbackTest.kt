package com.carlos.miflujo.ui.settings

import com.carlos.miflujo.data.cloud.auth.LegacyGoogleSignInResult
import org.junit.Assert.assertEquals
import org.junit.Test

class CloudAccountFeedbackTest {
    @Test
    fun `fallback cancellation maps to required visible feedback`() {
        val feedback = fallbackFeedbackForResult(LegacyGoogleSignInResult.Canceled)

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
    fun `fallback missing ID token maps to required visible failure`() {
        val feedback = fallbackFeedbackForResult(LegacyGoogleSignInResult.MissingIdToken)

        assertEquals(CloudAccountFeedback.SignInFailed, feedback)
        assertEquals(
            "No se pudo iniciar sesión con Google. Intenta nuevamente.",
            feedback.message,
        )
    }

    @Test
    fun `fallback developer error maps to visible configuration feedback`() {
        val feedback = fallbackFeedbackForResult(LegacyGoogleSignInResult.ConfigurationError)

        assertEquals(CloudAccountFeedback.GoogleSignInConfigurationError, feedback)
        assertEquals(
            "No se pudo iniciar sesión porque la configuración de Google no está completa. MiFlujo continúa en modo local.",
            feedback.message,
        )
    }

    @Test
    fun `firebase failure maps to safe visible failure`() {
        val feedback = IllegalStateException("Firebase failure.").toCloudAccountFeedback()

        assertEquals(CloudAccountFeedback.SignInFailed, feedback)
    }
}
