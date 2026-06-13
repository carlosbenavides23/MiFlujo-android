package com.carlos.miflujo.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ManualCloudSyncResultPresentationTest {
    @Test
    fun `success with no data activity shows everything is up to date`() {
        val presentation = mapManualCloudSyncResultPresentation(
            ManualCloudSyncUiState.Success(
                counts(markedSynced = 11),
            ),
        )

        assertEquals("Sincronización completada.", presentation?.title)
        assertEquals("Todo está al día.", presentation?.detail)
    }

    @Test
    fun `success shows only meaningful non-zero activity counts`() {
        val presentation = mapManualCloudSyncResultPresentation(
            ManualCloudSyncUiState.Success(
                counts(
                    uploaded = 2,
                    downloaded = 1,
                    markedSynced = 11,
                ),
            ),
        )

        assertEquals(
            "Subidos: 2 · Descargados: 1 · Confirmados: 11",
            presentation?.detail,
        )
    }

    @Test
    fun `partial result shows error counts clearly`() {
        val presentation = mapManualCloudSyncResultPresentation(
            ManualCloudSyncUiState.Partial(
                counts(
                    localErrors = 1,
                    remoteErrors = 2,
                ),
            ),
        )

        assertEquals("Sincronización parcial.", presentation?.title)
        assertEquals(
            "Errores locales: 1 · Errores remotos: 2",
            presentation?.detail,
        )
    }

    @Test
    fun `normal success summary omits zero counters`() {
        val detail = mapManualCloudSyncResultPresentation(
            ManualCloudSyncUiState.Success(
                counts(uploaded = 1),
            ),
        )?.detail.orEmpty()

        assertTrue(detail.contains("Subidos: 1"))
        assertFalse(detail.contains(": 0"))
        assertFalse(detail.contains("Descargados"))
        assertFalse(detail.contains("Errores"))
    }

    private fun counts(
        uploaded: Int = 0,
        downloaded: Int = 0,
        updatedLocal: Int = 0,
        markedSynced: Int = 0,
        skippedRemote: Int = 0,
        localErrors: Int = 0,
        remoteErrors: Int = 0,
    ): ManualCloudSyncCounts = ManualCloudSyncCounts(
        uploaded = uploaded,
        downloaded = downloaded,
        updatedLocal = updatedLocal,
        markedSynced = markedSynced,
        skippedRemote = skippedRemote,
        localErrors = localErrors,
        remoteErrors = remoteErrors,
    )
}
