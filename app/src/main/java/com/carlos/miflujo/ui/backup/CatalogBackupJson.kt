package com.carlos.miflujo.ui.backup

import com.carlos.miflujo.domain.backup.*
import com.carlos.miflujo.domain.category.*
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.MovementType
import java.math.BigInteger
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Preparatory v3 codec. Not wired into BackupExporter, BackupImporter or SettingsViewModel. */
object CatalogBackupJson {
    const val Version = 3L

    fun parse(json: String): CatalogBackup = try {
        validateCatalogBackupJsonSyntax(json)
        val root = JSONObject(json)
        when (root.integer("schemaVersion")) {
            1L, 2L -> legacy(BackupJsonParser.parse(json)).validatedCopy()
            Version -> parseV3(root).validatedCopy()
            else -> error("Unsupported backup version")
        }
    } catch (e: InvalidBackupException) { throw e
    } catch (e: JSONException) { throw InvalidBackupException("Invalid catalog backup JSON", e)
    } catch (e: IllegalArgumentException) { throw InvalidBackupException("Invalid catalog backup", e)
    } catch (e: IllegalStateException) { throw InvalidBackupException("Invalid catalog backup", e)
    } catch (e: ArithmeticException) { throw InvalidBackupException("Backup integer out of range", e)
    } catch (e: DateTimeException) { throw InvalidBackupException("Invalid backup date", e) }

    fun serialize(backup: CatalogBackup): String {
        val snapshot = backup.validatedCopy()
        return JSONObject().put("schemaVersion", Version).put("app", BackupAppName)
            .put("createdAt", snapshot.createdAt.format(BackupDateTimeFormatter))
            .put("categories", array(snapshot.categories.map { c ->
                JSONObject().put("uuid", c.uuid).put("name", c.name).put("scope", c.scope.name)
                    .put("reportGroup", c.reportGroup.name).put("iconKey", c.iconKey)
                    .put("archived", c.archived).put("sortOrder", c.sortOrder).put("revision", revision(c.revision))
            }))
            .put("subcategories", array(snapshot.subcategories.map { s ->
                JSONObject().put("uuid", s.uuid).put("categoryUuid", s.categoryUuid).put("name", s.name)
                    .put("iconKey", s.iconKey).put("archived", s.archived).put("sortOrder", s.sortOrder)
                    .put("revision", revision(s.revision))
            }))
            .put("movements", array(snapshot.movements.map { m ->
                JSONObject().put("uuid", m.uuid).put("type", m.type.name).put("amountMinor", m.amountMinor)
                    .put("currency", m.currency.name).put("date", m.date.toString())
                    .put("categoryUuid", m.categoryUuid ?: JSONObject.NULL).put("subcategoryUuid", m.subcategoryUuid ?: JSONObject.NULL)
                    .put("detail", m.detail ?: JSONObject.NULL)
                    .put("createdAt", m.createdAt.format(BackupDateTimeFormatter)).put("updatedAt", m.updatedAt.format(BackupDateTimeFormatter))
            })).toString(2)
    }

    private fun parseV3(root: JSONObject): CatalogBackup {
        root.shape("schemaVersion", "app", "createdAt", "categories", "subcategories", "movements")
        require(root.string("app") == BackupAppName)
        return CatalogBackup(root.time("createdAt"), root.objects("categories").map { c ->
            c.shape("uuid", "name", "scope", "reportGroup", "iconKey", "archived", "sortOrder", "revision")
            BackupCategory(c.string("uuid"), c.string("name"), CategoryScope.valueOf(c.string("scope")),
                ReportGroup.valueOf(c.string("reportGroup")), c.string("iconKey"), c.boolean("archived"), c.position(), c.revision())
        }, root.objects("subcategories").map { s ->
            s.shape("uuid", "categoryUuid", "name", "iconKey", "archived", "sortOrder", "revision")
            BackupSubcategory(s.string("uuid"), s.string("categoryUuid"), s.string("name"), s.string("iconKey"),
                s.boolean("archived"), s.position(), s.revision())
        }, root.objects("movements").map { m ->
            m.shape("uuid", "type", "amountMinor", "currency", "date", "categoryUuid", "subcategoryUuid", "detail", "createdAt", "updatedAt")
            BackupMovement(m.string("uuid"), MovementType.valueOf(m.string("type")), m.integer("amountMinor"),
                Currency.valueOf(m.string("currency")), LocalDate.parse(m.string("date")), m.nullable("categoryUuid"),
                m.nullable("subcategoryUuid"), m.nullable("detail"), m.time("createdAt"), m.time("updatedAt"))
        })
    }
    private fun legacy(old: ParsedBackup): CatalogBackup {
        val stamp = LegacyCategoryCatalog.seedRevision
        val categories = LegacyCategoryCatalog.categories.associateBy { it.id }
        val subcategories = LegacyCategoryCatalog.subcategories.associateBy { it.id }
        return CatalogBackup(
            old.createdAt,
            categories.values.map { it.backup(stamp) },
            subcategories.values.map { child ->
                child.backup(categories.getValue(child.categoryId).uuid, stamp)
            },
            old.movements.map { movement ->
                val selection = LegacyCategoryCatalog.selection(movement.category, movement.subcategory)
                BackupMovement(
                    movement.uuid, movement.type, movement.amountMinor, movement.currency, movement.date,
                    categories.getValue(requireNotNull(selection.categoryId)).uuid,
                    selection.subcategoryId?.let { subcategories.getValue(it).uuid },
                    movement.detail, movement.createdAt, movement.updatedAt,
                )
            },
        )
    }

    private fun revision(r: CatalogRevision) = JSONObject().put("counter", r.counter).put("actorUuid", r.actorUuid)
    private fun JSONObject.revision(): CatalogRevision {
        val r = get("revision") as? JSONObject ?: error("Missing revision")
        r.shape("counter", "actorUuid")
        return CatalogRevision(r.integer("counter"), r.string("actorUuid"))
    }
    private fun array(values: List<JSONObject>) = JSONArray().also { a -> values.forEach { a.put(it) } }
    private fun JSONObject.shape(vararg keys: String) { require(this.keys().asSequence().toSet() == keys.toSet()) { "Incomplete or unknown fields" } }
    private fun JSONObject.objects(key: String): List<JSONObject> {
        val a = get(key) as? JSONArray ?: error("Expected array")
        return List(a.length()) { a.get(it) as? JSONObject ?: error("Expected object") }
    }
    private fun JSONObject.string(key: String) = get(key) as? String ?: error("Expected string")
    private fun JSONObject.nullable(key: String): String? = if (get(key) == JSONObject.NULL) null else string(key)
    private fun JSONObject.boolean(key: String) = get(key) as? Boolean ?: error("Expected boolean")
    private fun JSONObject.time(key: String) = LocalDateTime.parse(string(key), BackupDateTimeFormatter)
    private fun JSONObject.position(): Int = integer("sortOrder").also { require(it in Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()) }.toInt()
    private fun JSONObject.integer(key: String): Long = when (val n = get(key)) {
        is Byte -> n.toLong(); is Short -> n.toLong(); is Int -> n.toLong(); is Long -> n
        is BigInteger -> n.longValueExact()
        else -> error("Expected integer")
    }
}
