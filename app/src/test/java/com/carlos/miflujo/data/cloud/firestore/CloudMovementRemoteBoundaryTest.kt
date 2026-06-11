package com.carlos.miflujo.data.cloud.firestore

import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory
import com.carlos.miflujo.domain.model.MovementType
import com.carlos.miflujo.domain.sync.InvalidRemoteItemReason
import com.carlos.miflujo.domain.sync.MovementRemoteSnapshot
import com.carlos.miflujo.domain.sync.RemoteMovementInput
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
import org.junit.Test

class CloudMovementRemoteBoundaryTest {
    @Test
    fun `path builder stays under the requested user movement collection`() {
        assertEquals("users/test-uid/movements", CloudMovementPath.collection("test-uid"))
        assertEquals(
            "users/test-uid/movements/$testUuid",
            CloudMovementPath.document("test-uid", testUuid),
        )
    }

    @Test
    fun `path builder rejects nested or empty uid segments`() {
        listOf("", "users/other-user").forEach { invalidUid ->
            assertInvalidWrite {
                CloudMovementPath.collection(invalidUid)
            }
        }
    }

    @Test
    fun `visible write uses uuid as document ID and excludes local-only fields`() {
        val write = prepareRemoteMovementWrite(
            uid = "test-uid",
            documentId = testUuid,
            movement = snapshot(),
            writeType = RemoteMovementWriteType.VISIBLE,
        )

        assertEquals(testUuid, write.documentId)
        assertEquals(testUuid, write.payload.uuid)
        assertEquals("users/test-uid/movements", write.collectionPath)
        assertEquals(RemoteFieldNames, dtoFieldNames())
        assertFalse("id" in dtoFieldNames())
        assertFalse("syncStatus" in dtoFieldNames())
        assertFalse("lastSyncedAt" in dtoFieldNames())
    }

    @Test
    fun `tombstone write uses uuid and includes deletedAt`() {
        val deletedAt = baseTime.plusHours(3)
        val write = prepareRemoteMovementWrite(
            uid = "test-uid",
            documentId = testUuid,
            movement = snapshot(
                updatedAt = deletedAt,
                deletedAt = deletedAt,
            ),
            writeType = RemoteMovementWriteType.TOMBSTONE,
        )

        assertEquals(testUuid, write.documentId)
        assertNotNull(write.payload.deletedAt)
    }

    @Test
    fun `write validation rejects uuid mismatch and invalid tombstone shape`() {
        assertInvalidWrite {
            prepareRemoteMovementWrite(
                uid = "test-uid",
                documentId = secondUuid,
                movement = snapshot(),
                writeType = RemoteMovementWriteType.VISIBLE,
            )
        }
        assertInvalidWrite {
            prepareRemoteMovementWrite(
                uid = "test-uid",
                documentId = testUuid,
                movement = snapshot(),
                writeType = RemoteMovementWriteType.TOMBSTONE,
            )
        }
        assertInvalidWrite {
            prepareRemoteMovementWrite(
                uid = "test-uid",
                documentId = testUuid,
                movement = snapshot(deletedAt = baseTime.plusHours(1)),
                writeType = RemoteMovementWriteType.VISIBLE,
            )
        }
    }

    @Test
    fun `invalid remote document maps to item-level invalid input`() {
        val input = decodeRemoteMovementDocument(
            documentId = testUuid,
            dto = snapshot().toRemoteDto().copy(type = "TRANSFER"),
        )

        assertEquals(
            RemoteMovementInput.Invalid(
                documentId = testUuid,
                reason = InvalidRemoteItemReason.INVALID_DOCUMENT,
            ),
            input,
        )
    }

    @Test
    fun `valid remote document maps to valid snapshot`() {
        val expected = snapshot()

        val input = decodeRemoteMovementDocument(
            documentId = testUuid,
            dto = expected.toRemoteDto(),
        )

        assertEquals(RemoteMovementInput.Valid(expected), input)
    }

    private fun snapshot(
        updatedAt: LocalDateTime = baseTime.plusHours(1),
        deletedAt: LocalDateTime? = null,
    ): MovementRemoteSnapshot = MovementRemoteSnapshot(
        uuid = testUuid,
        type = MovementType.EXPENSE,
        amountMinor = 180_050L,
        currency = Currency.CORDOBA,
        date = LocalDate.of(2026, 6, 11),
        category = MovementCategory.FIXED_COST,
        subcategory = MovementSubcategory.ELECTRICITY,
        detail = "Pago de luz",
        createdAt = baseTime,
        updatedAt = updatedAt,
        deletedAt = deletedAt,
    )

    private fun dtoFieldNames(): Set<String> =
        RemoteMovementDto::class.java.declaredFields
            .map { it.name }
            .filterNot { it.startsWith("$") }
            .toSet()

    private fun assertInvalidWrite(block: () -> Unit) {
        try {
            block()
            fail("Expected InvalidRemoteMovementWriteException.")
        } catch (_: InvalidRemoteMovementWriteException) {
            // Expected.
        }
    }

    private companion object {
        const val testUuid = "3f83ad74-77f1-4625-a525-66d860a86e76"
        const val secondUuid = "bfa01442-30ed-4d90-83ab-cee48d00dfe3"

        val baseTime: LocalDateTime = LocalDateTime.of(2026, 6, 11, 8, 0)

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
