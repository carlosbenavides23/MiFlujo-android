package com.carlos.miflujo.ui.backup

import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.Movement
import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory
import com.carlos.miflujo.domain.model.MovementType
import java.time.LocalDate
import java.time.LocalDateTime
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupJsonSerializerTest {
    @Test
    fun serializesStableSchemaAndAllRealMovementFields() {
        val createdAt = LocalDateTime.of(2026, 6, 3, 16, 50)
        val movements = listOf(
            Movement(
                id = 41L,
                type = MovementType.EXPENSE,
                amountMinor = 180_050L,
                currency = Currency.CORDOBA,
                date = LocalDate.of(2026, 6, 3),
                category = MovementCategory.FIXED_COST,
                subcategory = MovementSubcategory.ELECTRICITY,
                detail = "",
                createdAt = LocalDateTime.of(2026, 6, 3, 9, 15, 30),
                updatedAt = LocalDateTime.of(2026, 6, 3, 10, 20, 45),
            ),
            Movement(
                id = 42L,
                type = MovementType.INCOME,
                amountMinor = 10_000L,
                currency = Currency.DOLLAR,
                date = LocalDate.of(2026, 6, 4),
                category = MovementCategory.GENERAL_INCOME,
                subcategory = null,
                detail = null,
                createdAt = LocalDateTime.of(2026, 6, 4, 8, 0),
                updatedAt = LocalDateTime.of(2026, 6, 4, 8, 0),
            ),
            movement(
                id = 43L,
                type = MovementType.EXPENSE,
                currency = Currency.CORDOBA,
                category = MovementCategory.FIXED_COST,
                subcategory = MovementSubcategory.WATER,
            ),
            movement(
                id = 44L,
                type = MovementType.EXPENSE,
                currency = Currency.DOLLAR,
                category = MovementCategory.FIXED_COST,
                subcategory = MovementSubcategory.INTERNET,
            ),
            movement(
                id = 45L,
                type = MovementType.EXPENSE,
                currency = Currency.DOLLAR,
                category = MovementCategory.MAINTENANCE,
                amountMinor = 9_876_543_210_00L,
            ),
        )

        val backup = JSONObject(
            BackupJsonSerializer.serialize(
                createdAt = createdAt,
                movements = movements,
            ),
        )
        val serializedMovements = backup.getJSONArray("movements")
        val expense = serializedMovements.getJSONObject(0)
        val income = serializedMovements.getJSONObject(1)

        assertEquals(1, backup.getInt("schemaVersion"))
        assertEquals("MiFlujo", backup.getString("app"))
        assertEquals("2026-06-03T16:50:00", backup.getString("createdAt"))
        assertEquals(5, serializedMovements.length())
        assertEquals(
            setOf("schemaVersion", "app", "createdAt", "movements"),
            backup.keys().asSequence().toSet(),
        )

        assertEquals(41L, expense.getLong("id"))
        assertEquals("EXPENSE", expense.getString("type"))
        assertEquals("CORDOBA", expense.getString("currency"))
        assertEquals("FIXED_COST", expense.getString("category"))
        assertEquals("ELECTRICITY", expense.getString("subcategory"))
        assertEquals(180_050L, expense.getLong("amountMinor"))
        assertEquals("", expense.getString("detail"))
        assertEquals("2026-06-03", expense.getString("date"))
        assertEquals("2026-06-03T09:15:30", expense.getString("createdAt"))
        assertEquals("2026-06-03T10:20:45", expense.getString("updatedAt"))

        assertEquals("INCOME", income.getString("type"))
        assertEquals("DOLLAR", income.getString("currency"))
        assertTrue(income.isNull("subcategory"))
        assertTrue(income.isNull("detail"))

        serializedMovements.forEachObject { movement ->
            assertEquals(
                MovementJsonKeys,
                movement.keys().asSequence().toSet(),
            )
        }
        assertEquals(
            setOf("WATER", "ELECTRICITY", "INTERNET"),
            serializedMovements.objects()
                .filterNot { it.isNull("subcategory") }
                .map { it.getString("subcategory") }
                .toSet(),
        )
        assertEquals(
            setOf("CORDOBA", "DOLLAR"),
            serializedMovements.objects().map { it.getString("currency") }.toSet(),
        )
        assertEquals(
            setOf("INCOME", "EXPENSE"),
            serializedMovements.objects().map { it.getString("type") }.toSet(),
        )
        assertTrue(
            serializedMovements.objects().any { it.getLong("amountMinor") == 9_876_543_210_00L },
        )
    }

    private fun movement(
        id: Long,
        type: MovementType,
        currency: Currency,
        category: MovementCategory,
        subcategory: MovementSubcategory? = null,
        amountMinor: Long = 10_000L,
    ): Movement {
        val timestamp = LocalDateTime.of(2026, 6, 4, 8, 0)
        return Movement(
            id = id,
            type = type,
            amountMinor = amountMinor,
            currency = currency,
            date = LocalDate.of(2026, 6, 4),
            category = category,
            subcategory = subcategory,
            detail = "Prueba",
            createdAt = timestamp,
            updatedAt = timestamp,
        )
    }
}

private val MovementJsonKeys = setOf(
    "id",
    "type",
    "currency",
    "category",
    "subcategory",
    "amountMinor",
    "detail",
    "date",
    "createdAt",
    "updatedAt",
)

private fun org.json.JSONArray.objects(): List<JSONObject> =
    List(length()) { index -> getJSONObject(index) }

private inline fun org.json.JSONArray.forEachObject(action: (JSONObject) -> Unit) {
    objects().forEach(action)
}
