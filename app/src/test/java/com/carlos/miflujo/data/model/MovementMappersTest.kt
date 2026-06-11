package com.carlos.miflujo.data.model

import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.Movement
import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory
import com.carlos.miflujo.domain.model.MovementType
import com.carlos.miflujo.domain.model.SyncStatus
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MovementMappersTest {
    @Test
    fun `entity mapping preserves UUID and all movement fields`() {
        val movement = Movement(
            id = 27L,
            uuid = "9fc6cd49-7c38-4bd1-bfa6-ae047394f87d",
            type = MovementType.EXPENSE,
            amountMinor = 180_050L,
            currency = Currency.CORDOBA,
            date = LocalDate.of(2026, 6, 6),
            category = MovementCategory.FIXED_COST,
            subcategory = MovementSubcategory.ELECTRICITY,
            detail = "Pago de luz",
            createdAt = LocalDateTime.of(2026, 6, 6, 8, 30),
            updatedAt = LocalDateTime.of(2026, 6, 6, 9, 45),
            syncStatus = SyncStatus.SYNCED,
            lastSyncedAt = LocalDateTime.of(2026, 6, 6, 9, 50),
            deletedAt = LocalDateTime.of(2026, 6, 6, 10, 0),
        )

        val restored = movement.toEntity().toDomain()

        assertEquals(movement, restored)
    }

    @Test
    fun `new movement defaults to local only without sync timestamps`() {
        val movement = Movement(
            type = MovementType.INCOME,
            amountMinor = 10_000L,
            currency = Currency.CORDOBA,
            date = LocalDate.of(2026, 6, 11),
            category = MovementCategory.GENERAL_INCOME,
            createdAt = LocalDateTime.of(2026, 6, 11, 8, 30),
            updatedAt = LocalDateTime.of(2026, 6, 11, 8, 30),
        )

        assertEquals(SyncStatus.LOCAL_ONLY, movement.syncStatus)
        assertNull(movement.lastSyncedAt)
        assertNull(movement.deletedAt)
    }
}
