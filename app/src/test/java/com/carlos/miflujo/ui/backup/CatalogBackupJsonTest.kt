package com.carlos.miflujo.ui.backup

import com.carlos.miflujo.domain.backup.*
import com.carlos.miflujo.domain.category.*
import com.carlos.miflujo.domain.model.*
import java.math.BigInteger
import java.time.LocalDate
import java.time.LocalDateTime
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class CatalogBackupJsonTest {
    private val time = LocalDateTime.parse("2026-11-16T11:12:13.123456789")
    private val stamp = CatalogRevision(7, newCatalogUuid())
    private val parent = Category(name = "Hogar", scope = CategoryScope.BOTH, reportGroup = ReportGroup.OTHER).backup(stamp)
    private val child = Subcategory(categoryId = 1, name = "Agua potable").backup(parent.uuid, stamp)
    private val movement = BackupMovement(newCatalogUuid(), MovementType.EXPENSE, 12345, Currency.DOLLAR,
        LocalDate.of(2024, 2, 29), parent.uuid, child.uuid, "Detalle áéñ", time, time.plusNanos(1))
    private fun sample() = CatalogBackup(time, listOf(parent), listOf(child), listOf(movement))
    private fun root() = JSONObject(CatalogBackupJson.serialize(sample()))
    private fun invalid(root: JSONObject) = invalidJson(root.toString())
    private fun invalidJson(json: String) {
        try { CatalogBackupJson.parse(json); fail("Expected rejection of complete backup") }
        catch (_: InvalidBackupException) { }
    }
    private fun oldMovement() = Movement(id = 99, type = MovementType.EXPENSE, amountMinor = 12345,
        currency = Currency.DOLLAR, date = movement.date, category = MovementCategory.FIXED_COST,
        subcategory = MovementSubcategory.WATER, detail = movement.detail, createdAt = time, updatedAt = time.plusNanos(1))

    @Test fun `v3 round trip preserves exact content and revision without local sending metadata`() {
        val input = sample()
        val json = CatalogBackupJson.serialize(input)
        assertEquals(input, CatalogBackupJson.parse(json))
        assertFalse(json.contains("\"id\"")); assertFalse(json.contains("categoryId"))
        listOf("PENDING", "SYNCED", "ERROR", "syncState", "catalog_installation", "lastSyncedAt").forEach { assertFalse(json.contains(it)) }
    }
    @Test fun `v2 preserves financial values and movement identity using frozen seed UUIDs`() {
        val old = oldMovement()
        val converted = CatalogBackupJson.parse(BackupJsonSerializer.serialize(time, listOf(old)))
        val result = converted.movements.single()
        assertEquals(old.uuid, result.uuid); assertEquals(old.amountMinor, result.amountMinor)
        assertEquals(old.currency, result.currency); assertEquals(old.date, result.date)
        assertEquals(old.detail, result.detail); assertEquals(old.createdAt, result.createdAt); assertEquals(old.updatedAt, result.updatedAt)
        assertEquals("299690db-dcc7-380d-850f-535c74bd8827", result.categoryUuid)
        assertEquals("919bb4b6-da9e-3857-b442-d682d5d80c2d", result.subcategoryUuid)
        assertEquals(4, converted.categories.size); assertEquals(3, converted.subcategories.size)
        assertEquals(converted, CatalogBackupJson.parse(CatalogBackupJson.serialize(converted)))
    }
    @Test fun `v1 retains its documented generation of movement UUIDs`() {
        val old = oldMovement()
        val json = JSONObject(BackupJsonSerializer.serialize(time, listOf(old))).put("schemaVersion", 1)
        json.getJSONArray("movements").getJSONObject(0).remove("uuid")
        val a = CatalogBackupJson.parse(json.toString()).movements.single()
        val b = CatalogBackupJson.parse(json.toString()).movements.single()
        assertNotEquals(a.uuid, b.uuid); assertTrue(isCanonicalCatalogUuid(a.uuid))
        assertEquals(old.amountMinor, a.amountMinor); assertEquals(old.type, a.type)
        assertEquals(old.currency, a.currency); assertEquals(old.date, a.date)
        assertEquals(old.detail, a.detail); assertEquals(old.createdAt, a.createdAt); assertEquals(old.updatedAt, a.updatedAt)
    }
    @Test fun `archived parents and children are valid historical content`() {
        val backup = sample().copy(categories = listOf(parent.copy(archived = true)), subcategories = listOf(child.copy(archived = true)))
        assertEquals(backup, CatalogBackupJson.parse(CatalogBackupJson.serialize(backup)))
    }
    @Test fun `explicit uncategorized and category without child are both valid`() {
        val input = sample().copy(movements = listOf(
            movement.copy(categoryUuid = null, subcategoryUuid = null),
            movement.copy(uuid = newCatalogUuid(), subcategoryUuid = null),
        ))
        assertEquals(input, CatalogBackupJson.parse(CatalogBackupJson.serialize(input)))
    }
    @Test fun `unknown references are never converted to uncategorized`() {
        listOf("categoryUuid", "subcategoryUuid").forEach { field ->
            invalid(root().also { it.getJSONArray("movements").getJSONObject(0).put(field, newCatalogUuid()) })
        }
        invalid(root().also { it.getJSONArray("subcategories").getJSONObject(0).put("categoryUuid", newCatalogUuid()) })
        invalid(root().also { it.getJSONArray("movements").getJSONObject(0).put("categoryUuid", JSONObject.NULL) })
    }
    @Test fun `child of another existing category is rejected`() {
        val json = root()
        val other = Category(name = "Otra", scope = CategoryScope.EXPENSE, reportGroup = ReportGroup.OTHER).backup(stamp)
        val otherJson = JSONObject(CatalogBackupJson.serialize(sample().copy(categories = listOf(other), subcategories = emptyList(), movements = emptyList())))
        json.getJSONArray("categories").put(otherJson.getJSONArray("categories").getJSONObject(0))
        json.getJSONArray("subcategories").getJSONObject(0).put("categoryUuid", other.uuid)
        invalid(json)
    }
    @Test fun `duplicate UUID in each collection invalidates the entire file`() {
        listOf("categories", "subcategories", "movements").forEach { key ->
            invalid(root().also { json -> val a = json.getJSONArray(key); a.put(JSONObject(a.getJSONObject(0).toString())) })
        }
    }
    @Test fun `category and child cannot share a catalog identity`() {
        val json = root()
        json.getJSONArray("subcategories").getJSONObject(0).put("uuid", parent.uuid)
        json.getJSONArray("movements").getJSONObject(0).put("subcategoryUuid", parent.uuid)
        invalid(json)
    }
    @Test fun `a known catalog identity cannot change kind during replacement`() {
        val replacement = sample().copy(categories = listOf(parent.copy(uuid = newCatalogUuid())), subcategories = emptyList(), movements = emptyList())
        val input = replacement.copy(subcategories = listOf(child.copy(uuid = parent.uuid, categoryUuid = replacement.categories.single().uuid))).validatedCopy()
        try { input.requireCompatibleWith(sample()); fail("Identity changed from parent to child") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun `equal names never merge distinct category identities`() {
        val other = parent.copy(uuid = newCatalogUuid())
        val backup = sample().copy(categories = listOf(parent, other))
        assertEquals(2, CatalogBackupJson.parse(CatalogBackupJson.serialize(backup)).categories.size)
    }
    @Test fun `invalid scope structure and repurposed seed identities are rejected`() {
        invalid(root().also { it.getJSONArray("categories").getJSONObject(0).put("scope", "INCOME").put("reportGroup", "INCOME") })
        invalid(root().also { it.getJSONArray("categories").getJSONObject(0).put("scope", "EXPENSE").put("reportGroup", "INCOME") })
        invalid(root().also { it.getJSONArray("categories").getJSONObject(0).put("uuid", LegacyCategoryCatalog.categories[0].uuid) })
        invalid(root().also { it.getJSONArray("categories").getJSONObject(0).getJSONObject("revision").put("counter", -1) })
    }
    @Test fun `financial numbers require positive exact Long integers`() {
        listOf<Any>(0, -1, 1.5, "12", BigInteger("9223372036854775808")).forEach { amount ->
            invalid(root().also { it.getJSONArray("movements").getJSONObject(0).put("amountMinor", amount) })
        }
        val backup = sample().copy(movements = listOf(movement.copy(amountMinor = Long.MAX_VALUE)))
        assertEquals(Long.MAX_VALUE, CatalogBackupJson.parse(CatalogBackupJson.serialize(backup)).movements.single().amountMinor)
    }
    @Test fun `unknown incomplete and locally decorated formats are rejected`() {
        invalid(root().put("schemaVersion", 4))
        // JSONObject normalizes a Double 3.0 to the literal 3 when serializing; exercise wire input.
        invalidJson(root().toString().replace("\"schemaVersion\":3", "\"schemaVersion\":3.0"))
        invalidJson(root().toString() + "{}")
        invalid(root().put("app", "Other")); invalid(root().also { it.remove("categories") })
        invalid(root().also { it.getJSONArray("movements").getJSONObject(0).remove("subcategoryUuid") })
        invalid(root().also { it.getJSONArray("categories").getJSONObject(0).put("id", 7) })
        invalid(root().also { it.getJSONArray("categories").getJSONObject(0).put("syncState", "SYNCED") })
        invalid(root().also { it.getJSONArray("movements").getJSONObject(0).put("date", "2024-02-30") })
    }
    @Test fun `legacy malformed references remain invalid and production still rejects v3`() {
        val legacy = JSONObject(BackupJsonSerializer.serialize(time, listOf(oldMovement())))
        legacy.getJSONArray("movements").getJSONObject(0).put("category", "UNKNOWN")
        invalid(legacy)
        try { BackupJsonParser.parse(root().toString()); fail("Production parser must remain v1-v2") }
        catch (_: InvalidBackupException) { }
        assertEquals(2L, BackupSchemaVersion)
    }
    @Test fun `structural conflicts with an existing catalog are not silently replaced`() {
        try { sample().copy(categories = listOf(parent.copy(reportGroup = ReportGroup.MAINTENANCE))).requireCompatibleWith(sample()); fail("Must reject repurposed UUID") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun `income can have optional custom categories and children`() {
        val input = sample().copy(movements = listOf(movement.copy(type = MovementType.INCOME)))
        assertEquals(input, CatalogBackupJson.parse(CatalogBackupJson.serialize(input)))
    }
    @Test fun `seed structure cannot be repurposed even without movement references`() {
        val invalid = sample().copy(categories = listOf(parent.copy(uuid = LegacyCategoryCatalog.categories[0].uuid)), subcategories = emptyList(), movements = emptyList())
        try { invalid.validatedCopy(); fail("Reserved identity must retain its structure") }
        catch (_: IllegalArgumentException) { }
    }
    @Test fun `empty full replacement is representable`() {
        val empty = CatalogBackup(time, emptyList(), emptyList(), emptyList())
        assertEquals(empty, CatalogBackupJson.parse(CatalogBackupJson.serialize(empty)))
    }

    @Test fun `ambiguous and non JSON amounts are rejected before a runtime can coerce them`() {
        val json = root().toString()
        val amount = "\"amountMinor\":12345"
        assertTrue(json.contains(amount))
        listOf(
            "\"amountMinor\":1,$amount",
            "\"\\u0061mountMinor\":1,$amount",
            "\"amountMinor\":012345",
            "\"amountMinor\":0x3039",
            "\"amountMinor\":12345e0",
            "\"amountMinor\":+12345",
            "\"amountMinor\":12345/*comment*/",
        ).forEach { invalidJson(json.replace(amount, it)) }
        invalidJson(json + '\u0000')
        invalidJson(json.replaceFirst("\"schemaVersion\":3", "\"schemaVersion\":2,\"schemaVersion\":3"))
    }

    @Test fun `strict reading accepts escaped text without interpreting punctuation as structure`() {
        val detail = "Servicio: {\"importe\": 41}, [1, 2]\t\n📁 \\ \""
        val backup = sample().copy(movements = listOf(movement.copy(detail = detail)))
        assertEquals(backup, CatalogBackupJson.parse(CatalogBackupJson.serialize(backup)))
    }

    @Test fun `missing legacy categories and incomplete fixed costs are rejected while other has no child`() {
        val old = oldMovement().copy(category = MovementCategory.OTHER, subcategory = null)
        val json = BackupJsonSerializer.serialize(time, listOf(old))
        val converted = CatalogBackupJson.parse(json).movements.single()
        assertEquals(LegacyCategoryCatalog.categories.last().uuid, converted.categoryUuid)
        assertNull(converted.subcategoryUuid)

        invalid(JSONObject(json).also { root ->
            root.getJSONArray("movements").getJSONObject(0).put("category", JSONObject.NULL)
        })
        invalid(JSONObject(json).also { root ->
            root.getJSONArray("movements").getJSONObject(0).put("category", MovementCategory.FIXED_COST.name)
        })
    }
}
