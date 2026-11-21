package com.carlos.miflujo.data.backup

import com.carlos.miflujo.domain.backup.BackupMovement
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.MovementType
import com.carlos.miflujo.domain.model.SyncStatus
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class CatalogMovementBackupMappingTest {
    private val movement = BackupMovement(
        uuid = "10000000-0000-4000-8000-000000000001", type = MovementType.EXPENSE,
        amountMinor = Long.MAX_VALUE, currency = Currency.DOLLAR, date = LocalDate.of(1969, 12, 31),
        categoryUuid = null, subcategoryUuid = null, detail = "Pago",
        createdAt = LocalDateTime.parse("1969-12-31T23:59:59.999999999"),
        updatedAt = LocalDateTime.parse("1970-01-01T00:00:01.234999999"),
    )

    @Test fun `financial row uses exact amounts and legacy UTC millisecond conversion`() {
        val row = movement.toCatalogMovementRow()
        assertEquals(movement.uuid, row.uuid)
        assertEquals("EXPENSE", row.type)
        assertEquals(Long.MAX_VALUE, row.amountMinor)
        assertEquals("DOLLAR", row.currency)
        assertEquals(-1L, row.dateEpochDay)
        assertEquals(-1L, row.createdAtEpochMillis)
        assertEquals(1234L, row.updatedAtEpochMillis)
        assertEquals("Pago", row.detail)
        assertEquals(0L, row.id)
        assertEquals(SyncStatus.LOCAL_ONLY, row.syncStatus)
        assertNull(row.lastSyncedAt)
        assertNull(row.deletedAt)
    }

    @Test fun `timestamps outside the storage range cannot be prepared`() {
        assertThrows(ArithmeticException::class.java) {
            movement.copy(createdAt = LocalDateTime.MAX).toCatalogMovementRow()
        }
    }
}
