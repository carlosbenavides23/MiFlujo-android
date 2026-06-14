package com.carlos.miflujo.data.cloud.firestore

import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.Movement
import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory
import com.carlos.miflujo.domain.model.MovementType
import com.carlos.miflujo.domain.model.SyncStatus
import com.carlos.miflujo.domain.sync.MovementRemoteSchemaVersion
import com.carlos.miflujo.domain.sync.MovementRemoteSnapshot
import com.carlos.miflujo.domain.sync.toRemoteSnapshot
import com.carlos.miflujo.domain.validation.MovementBusinessRuleValidator
import com.google.firebase.Timestamp
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

class InvalidRemoteMovementException(
    message: String,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause)

fun Movement.toRemoteDto(): RemoteMovementDto {
    return toRemoteSnapshot().toRemoteDto()
}

fun MovementRemoteSnapshot.toRemoteDto(): RemoteMovementDto {
    requireCanonicalUuid(uuid)
    if (schemaVersion != MovementRemoteSchemaVersion) {
        invalidRemoteMovement("Unsupported remote movement schema version.")
    }
    requireValidBusinessRules(
        amountMinor = amountMinor,
        type = type,
        category = category,
        subcategory = subcategory,
    )

    return RemoteMovementDto(
        uuid = uuid,
        type = type.name,
        amountMinor = amountMinor,
        currency = currency.name,
        date = date.toString(),
        category = category.name,
        subcategory = subcategory?.name,
        detail = detail,
        createdAt = createdAt.toFirestoreTimestamp(),
        updatedAt = updatedAt.toFirestoreTimestamp(),
        deletedAt = deletedAt?.toFirestoreTimestamp(),
        schemaVersion = schemaVersion,
    )
}

fun RemoteMovementDto.toDomain(documentId: String): Movement {
    val parsedUuid = required("uuid", uuid)
    requireCanonicalUuid(parsedUuid)
    if (parsedUuid != documentId) {
        invalidRemoteMovement("Remote movement UUID must match its document ID.")
    }

    val parsedSchemaVersion = required("schemaVersion", schemaVersion)
    if (parsedSchemaVersion != MovementRemoteSchemaVersion) {
        invalidRemoteMovement("Unsupported remote movement schema version.")
    }

    val parsedType = requiredEnum<MovementType>("type", type)
    val parsedAmountMinor = required("amountMinor", amountMinor)
    val parsedCurrency = requiredEnum<Currency>("currency", currency)
    val parsedDate = requiredDate(date)
    val parsedCategory = requiredEnum<MovementCategory>("category", category)
    val parsedSubcategory = optionalEnum<MovementSubcategory>("subcategory", subcategory)
    requireValidBusinessRules(
        amountMinor = parsedAmountMinor,
        type = parsedType,
        category = parsedCategory,
        subcategory = parsedSubcategory,
    )

    return Movement(
        uuid = parsedUuid,
        type = parsedType,
        amountMinor = parsedAmountMinor,
        currency = parsedCurrency,
        date = parsedDate,
        category = parsedCategory,
        subcategory = parsedSubcategory,
        detail = detail,
        createdAt = required("createdAt", createdAt).toLocalDateTimeUtc(),
        updatedAt = required("updatedAt", updatedAt).toLocalDateTimeUtc(),
        syncStatus = SyncStatus.SYNCED,
        lastSyncedAt = null,
        deletedAt = deletedAt?.toLocalDateTimeUtc(),
    )
}

fun RemoteMovementDto.toRemoteSnapshot(documentId: String): MovementRemoteSnapshot =
    toDomain(documentId).toRemoteSnapshot()

private fun requireCanonicalUuid(value: String) {
    val canonicalUuid = try {
        UUID.fromString(value).toString()
    } catch (exception: IllegalArgumentException) {
        invalidRemoteMovement("Remote movement UUID must use canonical UUID format.", exception)
    }
    if (canonicalUuid != value) {
        invalidRemoteMovement("Remote movement UUID must use canonical UUID format.")
    }
}

private fun requireValidBusinessRules(
    amountMinor: Long,
    type: MovementType,
    category: MovementCategory,
    subcategory: MovementSubcategory?,
) {
    if (
        MovementBusinessRuleValidator.validate(
            amountMinor = amountMinor,
            type = type,
            category = category,
            subcategory = subcategory,
        ).isNotEmpty()
    ) {
        invalidRemoteMovement("Remote movement violates business rules.")
    }
}

private fun requiredDate(value: String?): LocalDate {
    val date = required("date", value)
    return try {
        LocalDate.parse(date)
    } catch (exception: DateTimeException) {
        invalidRemoteMovement("Remote movement date must use ISO YYYY-MM-DD format.", exception)
    }
}

private inline fun <reified T : Enum<T>> requiredEnum(
    fieldName: String,
    value: String?,
): T {
    val serializedValue = required(fieldName, value)
    return enumValues<T>().firstOrNull { it.name == serializedValue }
        ?: invalidRemoteMovement("Unknown remote movement $fieldName value.")
}

private inline fun <reified T : Enum<T>> optionalEnum(
    fieldName: String,
    value: String?,
): T? = value?.let { serializedValue ->
    enumValues<T>().firstOrNull { it.name == serializedValue }
        ?: invalidRemoteMovement("Unknown remote movement $fieldName value.")
}

private fun <T> required(fieldName: String, value: T?): T =
    value ?: invalidRemoteMovement("Missing required remote movement field: $fieldName.")

private fun LocalDateTime.toFirestoreTimestamp(): Timestamp {
    val instant = toInstant(ZoneOffset.UTC)
    return Timestamp(instant.epochSecond, instant.nano)
}

private fun Timestamp.toLocalDateTimeUtc(): LocalDateTime =
    LocalDateTime.ofEpochSecond(seconds, nanoseconds, ZoneOffset.UTC)

private fun invalidRemoteMovement(
    message: String,
    cause: Throwable? = null,
): Nothing = throw InvalidRemoteMovementException(message, cause)
