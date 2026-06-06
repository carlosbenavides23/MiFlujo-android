package com.carlos.miflujo.ui.backup

import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.Movement
import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory
import com.carlos.miflujo.domain.model.MovementType
import com.carlos.miflujo.domain.validation.MovementBusinessRuleValidator
import java.math.BigInteger
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

data class ParsedBackup(
    val createdAt: LocalDateTime,
    val movements: List<Movement>,
)

class InvalidBackupException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

object BackupJsonParser {
    fun parse(json: String): ParsedBackup {
        try {
            val root = JSONObject(json)
            val schemaVersion = root.requiredPositiveLong("schemaVersion")
            if (schemaVersion != BackupSchemaVersion) {
                invalidBackup("Unsupported backup schema version.")
            }
            if (root.requiredString("app") != BackupAppName) {
                invalidBackup("Backup belongs to another app.")
            }

            val createdAt = root.requiredTimestamp("createdAt")
            val serializedMovements = root.requiredArray("movements")
            val movements = serializedMovements.parseMovements()

            return ParsedBackup(
                createdAt = createdAt,
                movements = movements,
            )
        } catch (exception: InvalidBackupException) {
            throw exception
        } catch (exception: JSONException) {
            throw InvalidBackupException("Invalid backup JSON.", exception)
        }
    }

    private fun JSONArray.parseMovements(): List<Movement> {
        val ids = mutableSetOf<Long>()
        return List(length()) { index ->
            val serializedMovement = opt(index) as? JSONObject
                ?: invalidBackup("Movement at index $index is not an object.")
            serializedMovement.toMovement().also { movement ->
                if (!ids.add(movement.id)) {
                    invalidBackup("Movement IDs must be unique.")
                }
            }
        }
    }

    private fun JSONObject.toMovement(): Movement {
        requireKeys(MovementJsonKeys)

        val movement = Movement(
            id = requiredPositiveLong("id"),
            type = requiredEnum<MovementType>("type"),
            amountMinor = requiredLong("amountMinor"),
            currency = requiredEnum<Currency>("currency"),
            date = requiredDate("date"),
            category = requiredEnum<MovementCategory>("category"),
            subcategory = nullableEnum<MovementSubcategory>("subcategory"),
            detail = nullableString("detail"),
            createdAt = requiredTimestamp("createdAt"),
            updatedAt = requiredTimestamp("updatedAt"),
        )
        if (
            MovementBusinessRuleValidator.validate(
                amountMinor = movement.amountMinor,
                type = movement.type,
                category = movement.category,
                subcategory = movement.subcategory,
            ).isNotEmpty()
        ) {
            invalidBackup("Movement violates business rules.")
        }
        return movement
    }

    private fun JSONObject.requireKeys(keys: Set<String>) {
        keys.forEach { key ->
            if (!has(key)) {
                invalidBackup("Missing required movement field: $key.")
            }
        }
    }

    private fun JSONObject.requiredArray(key: String): JSONArray =
        requiredValue(key) as? JSONArray
            ?: invalidBackup("$key must be an array.")

    private fun JSONObject.requiredString(key: String): String =
        requiredValue(key) as? String
            ?: invalidBackup("$key must be a string.")

    private fun JSONObject.nullableString(key: String): String? {
        val value = requiredValueAllowingNull(key)
        return when (value) {
            JSONObject.NULL -> null
            is String -> value
            else -> invalidBackup("$key must be a string or null.")
        }
    }

    private fun JSONObject.requiredPositiveLong(key: String): Long {
        val parsed = requiredLong(key)
        if (parsed <= 0L) {
            invalidBackup("$key must be positive.")
        }
        return parsed
    }

    private fun JSONObject.requiredLong(key: String): Long {
        return when (val value = requiredValue(key)) {
            is Byte -> value.toLong()
            is Short -> value.toLong()
            is Int -> value.toLong()
            is Long -> value
            is BigInteger -> try {
                value.longValueExact()
            } catch (exception: ArithmeticException) {
                invalidBackup("$key must fit in a Long.")
            }
            else -> invalidBackup("$key must be an integer.")
        }
    }

    private inline fun <reified T : Enum<T>> JSONObject.requiredEnum(key: String): T =
        enumValueOrNull<T>(requiredString(key))
            ?: invalidBackup("Unknown $key value.")

    private inline fun <reified T : Enum<T>> JSONObject.nullableEnum(key: String): T? {
        val value = requiredValueAllowingNull(key)
        if (value == JSONObject.NULL) return null
        val serializedValue = value as? String
            ?: invalidBackup("$key must be a string or null.")
        return enumValueOrNull<T>(serializedValue)
            ?: invalidBackup("Unknown $key value.")
    }

    private fun JSONObject.requiredDate(key: String): LocalDate =
        parseDate(requiredString(key), key)

    private fun JSONObject.requiredTimestamp(key: String): LocalDateTime =
        parseTimestamp(requiredString(key), key)

    private fun JSONObject.requiredValue(key: String): Any {
        val value = requiredValueAllowingNull(key)
        if (value == JSONObject.NULL) {
            invalidBackup("$key cannot be null.")
        }
        return value
    }

    private fun JSONObject.requiredValueAllowingNull(key: String): Any {
        if (!has(key)) {
            invalidBackup("Missing required field: $key.")
        }
        return get(key)
    }

    private fun parseDate(value: String, key: String): LocalDate =
        try {
            LocalDate.parse(value)
        } catch (exception: DateTimeException) {
            invalidBackup("$key must be an ISO date.", exception)
        }

    private fun parseTimestamp(value: String, key: String): LocalDateTime =
        try {
            LocalDateTime.parse(value, BackupDateTimeFormatter)
        } catch (exception: DateTimeException) {
            invalidBackup("$key must be an ISO local timestamp.", exception)
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

private inline fun <reified T : Enum<T>> enumValueOrNull(value: String): T? =
    enumValues<T>().firstOrNull { it.name == value }

private fun invalidBackup(
    message: String,
    cause: Throwable? = null,
): Nothing = throw InvalidBackupException(message, cause)
