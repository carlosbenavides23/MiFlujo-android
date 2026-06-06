package com.carlos.miflujo.domain.model

import java.time.LocalDate
import java.time.LocalDateTime

data class Movement(
    val id: Long = 0,
    val uuid: String = generateMovementUuid(),
    val type: MovementType,
    val amountMinor: Long,
    val currency: Currency,
    val date: LocalDate,
    val category: MovementCategory,
    val subcategory: MovementSubcategory? = null,
    val detail: String? = null,
    val createdAt: LocalDateTime,
    val updatedAt: LocalDateTime
)
