package com.carlos.miflujo.data.model

import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.Movement
import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory
import com.carlos.miflujo.domain.model.MovementType
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset

fun Movement.toEntity(): MovementEntity = MovementEntity(
    id = id,
    type = type.name,
    amountMinor = amountMinor,
    currency = currency.name,
    dateEpochDay = date.toEpochDay(),
    category = category.name,
    subcategory = subcategory?.name,
    detail = detail,
    createdAtEpochMillis = createdAt.toEpochMillis(),
    updatedAtEpochMillis = updatedAt.toEpochMillis(),
)

fun MovementEntity.toDomain(): Movement = Movement(
    id = id,
    type = MovementType.valueOf(type),
    amountMinor = amountMinor,
    currency = Currency.valueOf(currency),
    date = LocalDate.ofEpochDay(dateEpochDay),
    category = MovementCategory.valueOf(category),
    subcategory = subcategory?.let(MovementSubcategory::valueOf),
    detail = detail,
    createdAt = createdAtEpochMillis.toLocalDateTime(),
    updatedAt = updatedAtEpochMillis.toLocalDateTime(),
)

private fun LocalDateTime.toEpochMillis(): Long =
    toInstant(ZoneOffset.UTC).toEpochMilli()

private fun Long.toLocalDateTime(): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneOffset.UTC)
