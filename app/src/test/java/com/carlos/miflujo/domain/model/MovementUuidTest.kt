package com.carlos.miflujo.domain.model

import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MovementUuidTest {
    @Test
    fun `new movements receive distinct valid UUIDs`() {
        val first = movement()
        val second = movement()

        assertEquals(first.uuid, UUID.fromString(first.uuid).toString())
        assertEquals(second.uuid, UUID.fromString(second.uuid).toString())
        assertNotEquals(first.uuid, second.uuid)
    }

    @Test
    fun `editing a movement with copy preserves its UUID`() {
        val movement = movement()

        val edited = movement.copy(
            amountMinor = 25_000L,
            detail = "Detalle actualizado",
            updatedAt = movement.updatedAt.plusHours(1),
        )

        assertEquals(movement.uuid, edited.uuid)
    }

    private fun movement(): Movement {
        val timestamp = LocalDateTime.of(2026, 6, 6, 8, 0)
        return Movement(
            type = MovementType.EXPENSE,
            amountMinor = 10_000L,
            currency = Currency.CORDOBA,
            date = LocalDate.of(2026, 6, 6),
            category = MovementCategory.OTHER,
            detail = "Prueba",
            createdAt = timestamp,
            updatedAt = timestamp,
        )
    }
}
