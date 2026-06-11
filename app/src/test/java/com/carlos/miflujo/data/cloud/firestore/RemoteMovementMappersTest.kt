package com.carlos.miflujo.data.cloud.firestore

import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.Movement
import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory
import com.carlos.miflujo.domain.model.MovementType
import com.carlos.miflujo.domain.model.SyncStatus
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

class RemoteMovementMappersTest {
    @Test
    fun `remote DTO contains only the documented Firestore fields`() {
        val fieldNames = RemoteMovementDto::class.java.declaredFields
            .map { it.name }
            .filterNot { it.startsWith("$") }
            .toSet()

        assertEquals(RemoteFieldNames, fieldNames)
        assertFalse("id" in fieldNames)
        assertFalse("syncStatus" in fieldNames)
        assertFalse("lastSyncedAt" in fieldNames)
    }

    @Test
    fun `movement maps to remote DTO without local-only metadata`() {
        val movement = testMovement(
            deletedAt = LocalDateTime.of(2026, 6, 11, 12, 45, 30, 123_456_789),
        )

        val remote = movement.toRemoteDto()

        assertEquals(movement.uuid, remote.uuid)
        assertEquals(RemoteMovementSchemaVersion, remote.schemaVersion)
        assertEquals("2026-06-11", remote.date)
        assertEquals(123_456_789, remote.deletedAt?.nanoseconds)
    }

    @Test
    fun `round trip preserves all supported remote movement fields`() {
        val original = testMovement(
            deletedAt = LocalDateTime.of(2026, 6, 11, 12, 45, 30, 123_456_789),
        )

        val restored = original.toRemoteDto().toDomain(documentId = original.uuid)

        assertEquals(0L, restored.id)
        assertEquals(original.uuid, restored.uuid)
        assertEquals(original.type, restored.type)
        assertEquals(original.amountMinor, restored.amountMinor)
        assertEquals(original.currency, restored.currency)
        assertEquals(original.date, restored.date)
        assertEquals(original.category, restored.category)
        assertEquals(original.subcategory, restored.subcategory)
        assertEquals(original.detail, restored.detail)
        assertEquals(original.createdAt, restored.createdAt)
        assertEquals(original.updatedAt, restored.updatedAt)
        assertEquals(original.deletedAt, restored.deletedAt)
        assertEquals(SyncStatus.SYNCED, restored.syncStatus)
        assertNull(restored.lastSyncedAt)
    }

    @Test
    fun `missing invalid or mismatched remote fields fail safely`() {
        val valid = testMovement().toRemoteDto()

        listOf(
            valid.copy(uuid = null),
            valid.copy(type = "TRANSFER"),
            valid.copy(amountMinor = 0),
            valid.copy(currency = "EURO"),
            valid.copy(date = "11/06/2026"),
            valid.copy(category = "GENERAL_INCOME"),
            valid.copy(subcategory = "PHONE"),
            valid.copy(createdAt = null),
            valid.copy(updatedAt = null),
            valid.copy(schemaVersion = 0),
            valid.copy(schemaVersion = RemoteMovementSchemaVersion + 1),
        ).forEach { remote ->
            assertInvalidRemoteMovement {
                remote.toDomain(documentId = testUuid)
            }
        }

        assertInvalidRemoteMovement {
            valid.toDomain(documentId = "bfa01442-30ed-4d90-83ab-cee48d00dfe3")
        }
    }

    private fun testMovement(
        deletedAt: LocalDateTime? = null,
    ): Movement = Movement(
        id = 42L,
        uuid = testUuid,
        type = MovementType.EXPENSE,
        amountMinor = 180_050L,
        currency = Currency.CORDOBA,
        date = LocalDate.of(2026, 6, 11),
        category = MovementCategory.FIXED_COST,
        subcategory = MovementSubcategory.ELECTRICITY,
        detail = "Pago de luz",
        createdAt = LocalDateTime.of(2026, 6, 11, 8, 30, 15, 987_654_321),
        updatedAt = LocalDateTime.of(2026, 6, 11, 9, 45, 20, 456_789_123),
        syncStatus = SyncStatus.PENDING_DELETE,
        lastSyncedAt = LocalDateTime.of(2026, 6, 11, 9, 0),
        deletedAt = deletedAt,
    )

    private fun assertInvalidRemoteMovement(block: () -> Unit) {
        try {
            block()
            fail("Expected InvalidRemoteMovementException.")
        } catch (_: InvalidRemoteMovementException) {
            // Expected.
        }
    }

    private companion object {
        const val testUuid = "3f83ad74-77f1-4625-a525-66d860a86e76"

        val RemoteFieldNames = setOf(
            "uuid",
            "type",
            "amountMinor",
            "currency",
            "date",
            "category",
            "subcategory",
            "detail",
            "createdAt",
            "updatedAt",
            "deletedAt",
            "schemaVersion",
        )
    }
}
