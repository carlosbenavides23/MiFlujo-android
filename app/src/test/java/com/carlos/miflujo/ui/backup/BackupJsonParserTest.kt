package com.carlos.miflujo.ui.backup

import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory
import com.carlos.miflujo.domain.model.MovementType
import com.carlos.miflujo.domain.model.SyncStatus
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class BackupJsonParserTest {
    @Test
    fun parsesSchemaVersion1AndGeneratesNewUuids() {
        val backup = validBackup(
            validMovement(
                id = 1L,
                type = "INCOME",
                category = "GENERAL_INCOME",
                subcategory = JSONObject.NULL,
                detail = JSONObject.NULL,
            ),
            validMovement(
                id = 2L,
                type = "EXPENSE",
                category = "FIXED_COST",
                subcategory = "WATER",
                detail = "",
            ),
            validMovement(
                id = 3L,
                type = "EXPENSE",
                category = "FIXED_COST",
                subcategory = "ELECTRICITY",
            ),
            validMovement(
                id = 4L,
                type = "EXPENSE",
                category = "FIXED_COST",
                subcategory = "INTERNET",
            ),
        ).put("unknownRootField", true)
        backup.getJSONArray("movements").getJSONObject(0).put("unknownMovementField", 42)

        val parsed = BackupJsonParser.parse(backup.toString())

        assertEquals(LocalDateTime.of(2026, 6, 4, 12, 30, 45), parsed.createdAt)
        assertEquals(4, parsed.movements.size)
        assertEquals(4, parsed.movements.map { it.uuid }.toSet().size)
        parsed.movements.forEach { movement ->
            assertEquals(movement.uuid, UUID.fromString(movement.uuid).toString())
        }

        val income = parsed.movements[0]
        assertEquals(1L, income.id)
        assertEquals(MovementType.INCOME, income.type)
        assertEquals(12_345L, income.amountMinor)
        assertEquals(Currency.CORDOBA, income.currency)
        assertEquals(LocalDate.of(2026, 6, 3), income.date)
        assertEquals(MovementCategory.GENERAL_INCOME, income.category)
        assertNull(income.subcategory)
        assertNull(income.detail)
        assertEquals(LocalDateTime.of(2026, 6, 3, 9, 15, 30), income.createdAt)
        assertEquals(LocalDateTime.of(2026, 6, 3, 10, 20, 45, 123_000_000), income.updatedAt)
        assertEquals(SyncStatus.LOCAL_ONLY, income.syncStatus)
        assertNull(income.lastSyncedAt)
        assertNull(income.deletedAt)

        assertEquals("", parsed.movements[1].detail)
        assertEquals(
            listOf(
                MovementSubcategory.WATER,
                MovementSubcategory.ELECTRICITY,
                MovementSubcategory.INTERNET,
            ),
            parsed.movements.drop(1).map { it.subcategory },
        )
    }

    @Test
    fun parsesSchemaVersion2AndPreservesMovementUuid() {
        val uuid = "3f83ad74-77f1-4625-a525-66d860a86e76"
        val parsed = BackupJsonParser.parse(
            validBackup(
                validMovement(uuid = uuid),
                schemaVersion = 2,
            ).toString(),
        )

        assertEquals(uuid, parsed.movements.single().uuid)
    }

    @Test
    fun acceptsEmptyMovementsArray() {
        val parsed = BackupJsonParser.parse(validBackup().toString())

        assertEquals(emptyList<Any>(), parsed.movements)
    }

    @Test
    fun rejectsInvalidSchemaVersion() {
        assertInvalid(validBackup(validMovement()).put("schemaVersion", 3))
    }

    @Test
    fun rejectsMissingUuidInSchemaVersion2() {
        assertInvalid(
            validBackup(
                validMovement(),
                schemaVersion = 2,
            ),
        )
    }

    @Test
    fun rejectsInvalidUuidInSchemaVersion2() {
        assertInvalid(
            validBackup(
                validMovement(uuid = "not-a-uuid"),
                schemaVersion = 2,
            ),
        )
    }

    @Test
    fun rejectsDuplicateUuidInSchemaVersion2() {
        val uuid = "3f83ad74-77f1-4625-a525-66d860a86e76"
        assertInvalid(
            validBackup(
                validMovement(id = 1L, uuid = uuid),
                validMovement(id = 2L, uuid = uuid),
                schemaVersion = 2,
            ),
        )
    }

    @Test
    fun rejectsWrongAppName() {
        assertInvalid(validBackup(validMovement()).put("app", "OtraApp"))
    }

    @Test
    fun rejectsMissingMovementsArray() {
        assertInvalid(validBackup(validMovement()).apply { remove("movements") })
    }

    @Test
    fun rejectsInvalidRootCreatedAtTimestamp() {
        assertInvalid(validBackup(validMovement()).put("createdAt", "June 4, 2026"))
    }

    @Test
    fun rejectsMissingMovementFields() {
        MovementJsonKeys.forEach { missingKey ->
            val movement = validMovement().apply { remove(missingKey) }
            assertInvalid(validBackup(movement))
        }
    }

    @Test
    fun rejectsUnknownEnum() {
        listOf(
            validMovement().put("type", "TRANSFER"),
            validMovement().put("currency", "EURO"),
            validMovement().put("category", "TAX"),
            validMovement().put("subcategory", "PHONE"),
        ).forEach { movement ->
            assertInvalid(validBackup(movement))
        }
    }

    @Test
    fun rejectsInvalidDate() {
        assertInvalid(validBackup(validMovement().put("date", "03/06/26")))
    }

    @Test
    fun rejectsInvalidMovementTimestamp() {
        assertInvalid(validBackup(validMovement().put("updatedAt", "2026-06-03")))
    }

    @Test
    fun rejectsNonPositiveOrNonIntegerAmount() {
        listOf(0, -1, 12.5).forEach { invalidAmount ->
            assertInvalid(validBackup(validMovement().put("amountMinor", invalidAmount)))
        }
    }

    @Test
    fun rejectsNonPositiveOrNonIntegerId() {
        listOf(0, -1, 1.5).forEach { invalidId ->
            assertInvalid(validBackup(validMovement().put("id", invalidId)))
        }
    }

    @Test
    fun rejectsDuplicateMovementId() {
        assertInvalid(
            validBackup(
                validMovement(id = 7L),
                validMovement(id = 7L),
            ),
        )
    }

    @Test
    fun rejectsInvalidNullableFieldTypes() {
        assertInvalid(validBackup(validMovement().put("subcategory", 1)))
        assertInvalid(validBackup(validMovement().put("detail", true)))
    }

    @Test
    fun rejectsInvalidMovementBusinessRuleCombinations() {
        val invalidMovements = listOf(
            validMovement(type = "INCOME", category = "FIXED_COST", subcategory = "WATER"),
            validMovement(type = "INCOME", category = "GENERAL_INCOME", subcategory = "WATER"),
            validMovement(type = "EXPENSE", category = "GENERAL_INCOME"),
            validMovement(type = "EXPENSE", category = "FIXED_COST"),
            validMovement(type = "EXPENSE", category = "MAINTENANCE", subcategory = "WATER"),
            validMovement(type = "EXPENSE", category = "OTHER", subcategory = "INTERNET"),
        )

        invalidMovements.forEach { movement ->
            assertInvalid(validBackup(movement))
        }
    }

    private fun assertInvalid(backup: JSONObject) {
        try {
            BackupJsonParser.parse(backup.toString())
            fail("Expected InvalidBackupException for $backup")
        } catch (_: InvalidBackupException) {
            // Expected.
        }
    }
}

private fun validBackup(
    vararg movements: JSONObject,
    schemaVersion: Int = 1,
): JSONObject =
    JSONObject()
        .put("schemaVersion", schemaVersion)
        .put("app", "MiFlujo")
        .put("createdAt", "2026-06-04T12:30:45")
        .put("movements", JSONArray().apply { movements.forEach(::put) })

private fun validMovement(
    id: Long = 1L,
    type: String = "EXPENSE",
    category: String = "OTHER",
    subcategory: Any = JSONObject.NULL,
    detail: Any = "Detalle exacto",
    uuid: String? = null,
): JSONObject = JSONObject()
    .apply {
        put("id", id)
        uuid?.let { put("uuid", it) }
        put("type", type)
        put("currency", "CORDOBA")
        put("category", category)
        put("subcategory", subcategory)
        put("amountMinor", 12_345L)
        put("detail", detail)
        put("date", "2026-06-03")
        put("createdAt", "2026-06-03T09:15:30")
        put("updatedAt", "2026-06-03T10:20:45.123")
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
