package com.carlos.miflujo.ui.report.export

import org.junit.Assert.assertEquals
import org.junit.Test

class PdfTextComparisonTest {
    @Test
    fun recognizesWordsAndAmountsSplitAtApiBlockBoundaries() {
        val page = PdfTextComparison(listOf(
            "INICIODE", "TALLE", "LI", "NEA0", "01", "Servicio con descripcio", "\u0301n detallada",
            "C$ 92,233,720,368,547,7", "58.07", "FINDE", "TALLE",
        ))
        assertEquals(listOf("INICIODETALLE", "LINEA001", "FINDETALLE"), page.markers)
        assertEquals(emptyList<String>(), page.incompleteLines("Servicio con descripción detallada"))
        assertEquals(1, page.count("C$ 92,233,720,368,547,758.07"))
    }

    @Test
    fun preservesWrongOrderDuplicatesAndIncompleteDescriptionForVerification() {
        val page = PdfTextComparison(listOf(
            "LINEA002 Servicio con descripción\r\ndetallada\r\n" +
                "LINEA001 Servicio con descripción\nLINEA001 Servicio con descripción detalladaX",
        ))
        assertEquals(listOf("LINEA002", "LINEA001", "LINEA001"), page.markers)
        assertEquals(listOf("LINEA001", "LINEA001"), page.incompleteLines("Servicio con descripción detallada"))
    }

    @Test
    fun rejectsRawWhitespaceOrInvisibleCharactersInsideMarkersAndAmounts() {
        val page = PdfTextComparison(listOf(
            "LI NEA001 LI\u200bNEA002 LI\u0000NEA003 LINEA0003 XXLINEA001 C$ 9 2.07 C$ 92.071",
        ))
        assertEquals(emptyList<String>(), page.markers)
        assertEquals(0, page.count("C$ 92.07"))
    }

    @Test
    fun escapesRawControlAndInvisibleCharacters() {
        assertEquals("LI\\u0020NEA\\r\\n\\t\\u200b\\u0000", "LI NEA\r\n\t\u200b\u0000".escapePdfSample())
    }
}
