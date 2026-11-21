package com.carlos.miflujo.data.backup

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.carlos.miflujo.data.backup.CatalogRestoreDestination.ACCOUNT_BOUND
import com.carlos.miflujo.data.backup.CatalogRestoreDestination.LOCAL_ONLY
import com.carlos.miflujo.data.movementcatalog.CatalogMovementDatabase
import com.carlos.miflujo.data.movementcatalog.CatalogMovementRepository
import com.carlos.miflujo.domain.backup.BackupMovement
import com.carlos.miflujo.domain.backup.CatalogBackup
import com.carlos.miflujo.domain.backup.backup
import com.carlos.miflujo.domain.category.CatalogRevision
import com.carlos.miflujo.domain.category.Category
import com.carlos.miflujo.domain.category.CategoryScope
import com.carlos.miflujo.domain.category.LegacyCategoryCatalog
import com.carlos.miflujo.domain.category.ReportGroup
import com.carlos.miflujo.domain.category.Subcategory
import com.carlos.miflujo.domain.category.newCatalogUuid
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.Movement
import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory
import com.carlos.miflujo.domain.model.MovementType
import com.carlos.miflujo.domain.model.SyncStatus
import com.carlos.miflujo.ui.backup.BackupJsonSerializer
import com.carlos.miflujo.ui.backup.CatalogBackupJson
import com.carlos.miflujo.ui.backup.InvalidBackupException
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogMovementBackupRoomTest {
    private lateinit var db: CatalogMovementDatabase
    private lateinit var store: CatalogMovementBackupStore
    private val time = LocalDateTime.parse("2026-11-16T11:12:13.123")
    private val revision = CatalogRevision(7, newCatalogUuid())
    private val parent = Category(
        name = "Hogar", scope = CategoryScope.BOTH, reportGroup = ReportGroup.OTHER, archived = true,
    ).backup(revision)
    private val child = Subcategory(categoryId = 1, name = "Agua", archived = true).backup(parent.uuid, revision)
    private val expense = BackupMovement(
        "10000000-0000-4000-8000-000000000001", MovementType.EXPENSE, Long.MAX_VALUE, Currency.DOLLAR,
        LocalDate.of(2024, 2, 29), parent.uuid, child.uuid, "Pago 📁\n", time, time.plusNanos(1_000_000),
    )
    private val income = expense.copy(
        uuid = "10000000-0000-4000-8000-000000000002", type = MovementType.INCOME,
        amountMinor = 9_007_199_254_740_993L, currency = Currency.CORDOBA,
        categoryUuid = null, subcategoryUuid = null, detail = null,
    )
    private val sample = CatalogBackup(time, listOf(parent), listOf(child), listOf(expense, income))
    private val empty = CatalogBackup(time, emptyList(), emptyList(), emptyList())

    @Before
    fun open() {
        db = newDatabase()
        store = CatalogMovementBackupStore(db)
    }

    @After
    fun close() {
        db.close()
    }

    @Test
    fun v3RoundTripAcrossTwoDatabasesRemapsIdsAndPreservesExactContent() = runBlocking {
        store.restore(CatalogBackupJson.parse(CatalogBackupJson.serialize(sample)), LOCAL_ONLY)
        val exported = CatalogBackupJson.serialize(store.snapshot(time))
        val decoded = CatalogBackupJson.parse(exported)
        assertEquals(sample, decoded)

        val target = newDatabase()
        try {
            val actor = target.catalog().actorUuid()
            val restored = CatalogMovementBackupStore(target)
            // Advance independently generated IDs in the target without manufacturing IDs in JSON.
            restored.restore(sample, LOCAL_ONLY)
            restored.restore(decoded, LOCAL_ONLY)
            assertEquals(sample, restored.snapshot(time))
            val parentRow = checkNotNull(target.catalog().category(parent.uuid))
            val childRow = checkNotNull(target.catalog().subcategory(child.uuid))
            val movement = checkNotNull(target.movements().find(expense.uuid))
            assertNotEquals(db.catalog().category(parent.uuid)?.id, parentRow.id)
            assertNotEquals(db.catalog().subcategory(child.uuid)?.id, childRow.id)
            assertNotEquals(db.movements().find(expense.uuid)?.id, movement.id)
            assertEquals(parentRow.id, childRow.categoryId)
            assertEquals(parentRow.id, movement.categoryId)
            assertEquals(childRow.id, movement.subcategoryId)
            assertEquals(revision.counter, parentRow.revisionCounter)
            assertEquals(revision.actorUuid, childRow.revisionActorUuid)
            assertEquals("PENDING", parentRow.syncState)
            assertEquals(SyncStatus.LOCAL_ONLY, movement.syncStatus)
            assertNull(movement.lastSyncedAt)
            assertNull(movement.deletedAt)
            assertEquals(actor, target.catalog().actorUuid())
            val historical = checkNotNull(CatalogMovementRepository(target).find(expense.uuid))
            assertEquals(child.uuid, historical.draft.selection.subcategoryUuid)
            assertEquals(expense.amountMinor, historical.draft.amountMinor)
            target.openHelper.writableDatabase.query("PRAGMA foreign_key_check").use {
                assertEquals(0, it.count)
            }
        } finally {
            target.close()
        }
    }

    @Test
    fun historicalV1AndV2ReplaceContentUsingStableSeedIdentities() = runBlocking {
        store.restore(sample, LOCAL_ONLY)
        val old = Movement(
            id = 999, uuid = expense.uuid, type = MovementType.EXPENSE, amountMinor = expense.amountMinor,
            currency = expense.currency, date = expense.date, category = MovementCategory.FIXED_COST,
            subcategory = MovementSubcategory.WATER, detail = expense.detail,
            createdAt = time, updatedAt = expense.updatedAt,
        )
        val json = BackupJsonSerializer.serialize(time, listOf(old))
        val v1 = JSONObject(json).put("schemaVersion", 1)
        v1.getJSONArray("movements").getJSONObject(0).remove("uuid")
        for ((version, file) in listOf(1 to v1.toString(), 2 to json)) {
            val parsed = CatalogBackupJson.parse(file)
            store.restore(parsed, LOCAL_ONLY)
            val restored = store.snapshot(time)
            assertEquals(parsed, restored)
            val movement = restored.movements.single()
            if (version == 1) assertNotEquals(old.uuid, movement.uuid) else assertEquals(old.uuid, movement.uuid)
            assertEquals(LegacyCategoryCatalog.categories[1].uuid, movement.categoryUuid)
            assertEquals(LegacyCategoryCatalog.subcategories[0].uuid, movement.subcategoryUuid)
            assertNotEquals(old.id, db.movements().find(movement.uuid)?.id)
            assertNull(db.catalog().category(parent.uuid))
        }
        store.restore(empty, LOCAL_ONLY)
        assertEquals(empty, store.snapshot(time))
    }

    @Test
    fun corruptInputAndIncompatibleLocalIdentitiesLeaveExistingContentIntact() = runBlocking {
        store.restore(sample, LOCAL_ONLY)
        val json = CatalogBackupJson.serialize(sample)
        val other = parent.copy(uuid = newCatalogUuid())
        val wrongChild = sample.copy(
            categories = listOf(parent, other),
            subcategories = listOf(child.copy(categoryUuid = other.uuid)),
        )
        rejects<IllegalArgumentException> { store.restore(wrongChild, LOCAL_ONLY) }
        // Internally consistent file, but this child UUID already belongs to another parent locally.
        val reparented = wrongChild.copy(movements = listOf(expense.copy(categoryUuid = other.uuid)))
        rejects<IllegalArgumentException> { store.restore(reparented, LOCAL_ONLY) }
        rejects<IllegalArgumentException> {
            store.restore(sample.copy(categories = listOf(parent.copy(reportGroup = ReportGroup.MAINTENANCE))), LOCAL_ONLY)
        }
        rejects<IllegalArgumentException> {
            store.restore(sample.copy(movements = listOf(expense, expense)), LOCAL_ONLY)
        }
        rejects<InvalidBackupException> {
            val invalid = JSONObject(json)
            invalid.getJSONArray("movements").getJSONObject(0).remove("amountMinor")
            store.restore(CatalogBackupJson.parse(invalid.toString()), LOCAL_ONLY)
        }
        val duplicateMember = json.replaceFirst("\"amountMinor\"", "\"amountMinor\": 1, \"amountMinor\"")
        assertNotEquals(json, duplicateMember)
        rejects<InvalidBackupException> { store.restore(CatalogBackupJson.parse(duplicateMember), LOCAL_ONLY) }
        rejects<ArithmeticException> {
            store.restore(sample.copy(movements = listOf(expense.copy(createdAt = LocalDateTime.MAX))), LOCAL_ONLY)
        }
        assertEquals(sample, store.snapshot(time))
    }

    @Test
    fun sqliteFailureAfterFirstMovementRestoresRowsAndCatalogGuards() = runBlocking {
        store.restore(sample, LOCAL_ONLY)
        val original = db.movements().visibleForBackup()
        val parentRow = db.catalog().category(parent.uuid)
        val childRow = db.catalog().subcategory(child.uuid)
        val sql = db.openHelper.writableDatabase
        sql.execSQL("""
            CREATE TRIGGER injected_restore_failure BEFORE INSERT ON movements
            WHEN NEW.detail = 'fail' BEGIN SELECT RAISE(ABORT, 'Injected failure'); END
        """.trimIndent())
        val replacement = sample.copy(
            categories = listOf(parent.copy(name = "Renombrada")),
            movements = listOf(expense.copy(detail = "Primera fila"), income.copy(detail = "fail")),
        )
        rejects<SQLiteConstraintException> { store.restore(replacement, LOCAL_ONLY) }
        assertEquals(original, db.movements().visibleForBackup())
        assertEquals(parentRow, db.catalog().category(parent.uuid))
        assertEquals(childRow, db.catalog().subcategory(child.uuid))
        assertEquals(sample, store.snapshot(time))
        rejects<SQLiteConstraintException> {
            sql.execSQL("DELETE FROM categories WHERE id = 0 OR uuid = ?", arrayOf(parent.uuid))
        }
        sql.query("""
            SELECT name FROM sqlite_master
            WHERE type = 'trigger' AND name IN ('categories_no_delete', 'subcategories_no_delete')
        """.trimIndent()).use { assertEquals(2, it.count) }
        sql.execSQL("DROP TRIGGER injected_restore_failure")
        store.restore(empty, LOCAL_ONLY)
        assertEquals(empty, store.snapshot(time))
    }

    @Test
    fun accountDestinationAndCloudMetadataBlockReplacementWhileExportOmitsTombstones() = runBlocking {
        store.restore(sample, LOCAL_ONLY)
        rejects<IllegalStateException> { store.restore(empty, ACCOUNT_BOUND) }
        assertEquals(sample, store.snapshot(time))

        val original = checkNotNull(db.movements().find(expense.uuid))
        val synchronized = original.copy(syncStatus = SyncStatus.SYNCED, lastSyncedAt = 6)
        assertEquals(1, db.movements().update(synchronized))
        assertEquals(sample, store.snapshot(time))
        rejects<IllegalStateException> { store.restore(empty, LOCAL_ONLY) }

        val tombstone = synchronized.copy(syncStatus = SyncStatus.PENDING_DELETE, deletedAt = 8)
        assertEquals(1, db.movements().update(tombstone))
        assertEquals(sample.copy(movements = listOf(income)), store.snapshot(time))
        // The older file contains the same live UUID. It cannot revive or discard the tombstone.
        rejects<IllegalStateException> { store.restore(sample, LOCAL_ONLY) }
        rejects<IllegalStateException> { store.restore(empty, LOCAL_ONLY) }
        assertEquals(tombstone, db.movements().find(expense.uuid))

        assertEquals(1, db.movements().update(original))
        val catalogRow = checkNotNull(db.catalog().category(parent.uuid))
        db.catalog().updateCategory(catalogRow.copy(syncState = "SYNCED"))
        rejects<IllegalStateException> { store.restore(empty, LOCAL_ONLY) }
        assertEquals("SYNCED", db.catalog().category(parent.uuid)?.syncState)
        assertEquals(sample, store.snapshot(time))
    }

    @Test
    fun backupPreservesMissingFixedCostChildFromCandidateAndNormalizesSubmillisecondTimestamps() = runBlocking {
        val fixed = LegacyCategoryCatalog.categories[1].backup(LegacyCategoryCatalog.seedRevision)
        val withoutChild = expense.copy(
            categoryUuid = fixed.uuid, subcategoryUuid = null, createdAt = time.plusNanos(999_999),
        )
        val portable = sample.copy(categories = listOf(fixed), subcategories = emptyList(), movements = listOf(withoutChild))
        val parsed = CatalogBackupJson.parse(CatalogBackupJson.serialize(portable))
        assertEquals(portable, parsed)
        store.restore(parsed, LOCAL_ONLY)
        assertEquals(portable.copy(movements = listOf(withoutChild.copy(createdAt = time))), store.snapshot(time))
        assertTrue(db.movements().visibleForBackup().single().categoryId != null)
        assertNull(db.movements().visibleForBackup().single().subcategoryId)
    }

    private fun newDatabase(): CatalogMovementDatabase =
        Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext, CatalogMovementDatabase::class.java,
        ).addCallback(CatalogMovementDatabase.initializer).build()

    private suspend inline fun <reified T : Exception> rejects(block: () -> Unit) {
        try {
            block()
        } catch (exception: Exception) {
            if (exception !is T) throw exception
            return
        }
        fail("Expected ${T::class.java.simpleName}")
    }
}
