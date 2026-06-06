package com.carlos.miflujo.ui.backup

import com.carlos.miflujo.domain.model.Movement
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.format.ResolverStyle
import java.time.temporal.ChronoField
import java.util.Locale
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

object BackupJsonSerializer {
    fun serialize(
        createdAt: LocalDateTime,
        movements: List<Movement>,
    ): String {
        requireExportableUuids(movements)

        val serializedMovements = JSONArray().apply {
            movements.forEach { movement ->
                put(movement.toJson())
            }
        }

        return JSONObject()
            .put("schemaVersion", BackupSchemaVersion)
            .put("app", BackupAppName)
            .put("createdAt", createdAt.toIsoString())
            .put("movements", serializedMovements)
            .toString(BackupJsonIndentSpaces)
    }

    private fun Movement.toJson(): JSONObject = JSONObject()
        .put("id", id)
        .put("uuid", uuid)
        .put("type", type.name)
        .put("currency", currency.name)
        .put("category", category.name)
        .put("subcategory", subcategory?.name ?: JSONObject.NULL)
        .put("amountMinor", amountMinor)
        .put("detail", detail ?: JSONObject.NULL)
        .put("date", date.toString())
        .put("createdAt", createdAt.toIsoString())
        .put("updatedAt", updatedAt.toIsoString())

    private fun LocalDateTime.toIsoString(): String =
        format(BackupDateTimeFormatter)

    private fun requireExportableUuids(movements: List<Movement>) {
        val uuids = mutableSetOf<UUID>()
        movements.forEach { movement ->
            val uuid = parseCanonicalUuidOrNull(movement.uuid)
                ?: throw IllegalArgumentException("Movement UUID must use canonical UUID format.")
            require(uuids.add(uuid)) {
                "Movement UUIDs must be unique."
            }
        }
    }
}

internal const val BackupSchemaVersion = 2L
internal const val LegacyBackupSchemaVersion = 1L
internal const val BackupAppName = "MiFlujo"
private const val BackupJsonIndentSpaces = 2
internal val BackupDateTimeFormatter: DateTimeFormatter = DateTimeFormatterBuilder()
    .appendPattern("uuuu-MM-dd'T'HH:mm:ss")
    .appendFraction(ChronoField.NANO_OF_SECOND, 0, 9, true)
    .toFormatter(Locale.ROOT)
    .withResolverStyle(ResolverStyle.STRICT)

internal fun parseCanonicalUuidOrNull(value: String): UUID? =
    try {
        UUID.fromString(value).takeIf { it.toString() == value }
    } catch (_: IllegalArgumentException) {
        null
    }
