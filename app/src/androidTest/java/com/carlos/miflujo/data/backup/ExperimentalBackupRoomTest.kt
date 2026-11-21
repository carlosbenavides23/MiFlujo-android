package com.carlos.miflujo.data.backup

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.carlos.miflujo.domain.backup.*
import com.carlos.miflujo.domain.category.*
import com.carlos.miflujo.domain.model.*
import com.carlos.miflujo.ui.backup.CatalogBackupJson
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.LocalDateTime

/** Requires generated Room classes and Android; no access to production or catalog v4 files. */
@RunWith(AndroidJUnit4::class)
class ExperimentalBackupRoomTest {
    private lateinit var db: BackupSandboxDatabase
    private lateinit var store: ExperimentalBackupStore
    private val time = LocalDateTime.parse("2026-11-16T11:12:13.123456789")
    private val revision = CatalogRevision(8, newCatalogUuid())
    private val parent = Category(name = "Hogar", scope = CategoryScope.BOTH, reportGroup = ReportGroup.OTHER).backup(revision)
    private val child = Subcategory(categoryId = 1, name = "Agua").backup(parent.uuid, revision)
    private val movement = BackupMovement(newCatalogUuid(), MovementType.EXPENSE, 12345, Currency.DOLLAR,
        LocalDate.of(2024, 2, 29), parent.uuid, child.uuid, "Detalle", time, time.plusNanos(1))
    private fun sample() = CatalogBackup(time, listOf(parent), listOf(child), listOf(movement))
    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(InstrumentationRegistry.getInstrumentation().targetContext, BackupSandboxDatabase::class.java)
            .addCallback(BackupSandboxDatabase.initializer).build()
        store = ExperimentalBackupStore(db)
    }
    @After fun close() { db.close() }
    private suspend fun rejected(block: suspend () -> Unit) {
        try { block(); fail("Expected rejected replacement") } catch (_: Exception) { }
    }
    @Test fun restoreRemapsRelationshipsPreservesFinancialValuesAndResetsOnlyLocalSendingState() = runBlocking {
        val actor = db.categories().actorUuid()
        val backup = sample().copy(categories = listOf(parent.copy(archived = true)), subcategories = listOf(child.copy(archived = true)))
        store.restore(CatalogBackupJson.parse(CatalogBackupJson.serialize(backup)))
        assertEquals(backup, store.snapshot(time))
        val parentRow = db.categories().category(parent.uuid)!!
        val childRow = db.categories().subcategory(child.uuid)!!
        val row = db.movements().all().single()
        assertEquals(parentRow.id, childRow.categoryId)
        assertEquals(parentRow.id, row.categoryId)
        assertEquals(childRow.id, row.subcategoryId)
        assertEquals("LOCAL_ONLY", row.syncStatus)
        assertEquals("PENDING", parentRow.syncState)
        assertEquals(revision.counter, parentRow.revisionCounter)
        assertEquals(revision.actorUuid, parentRow.revisionActorUuid)
        assertEquals(actor, db.categories().actorUuid())
        store.restore(backup)
        assertNotEquals(parentRow.id, db.categories().category(parent.uuid)!!.id)
        assertEquals(backup, store.snapshot(time))
    }
    @Test fun failureAfterCatalogAndFirstMovementRollsBackRowsAndProtectionTriggers() = runBlocking {
        store.restore(sample())
        val old = store.snapshot(time)
        val oldRows = db.movements().all()
        db.openHelper.writableDatabase.execSQL("""CREATE TRIGGER test_failure BEFORE INSERT ON backup_movements
            WHEN NEW.detail = 'fail' BEGIN SELECT RAISE(ABORT, 'Injected failure'); END""")
        val replacement = sample().copy(categories = listOf(parent.copy(name = "Renombrado")), movements = listOf(
            movement.copy(uuid = newCatalogUuid()), movement.copy(uuid = newCatalogUuid(), detail = "fail"),
        ))
        rejected { store.restore(replacement) }
        assertEquals(old, store.snapshot(time))
        assertEquals(oldRows, db.movements().all())
        db.openHelper.writableDatabase.query("SELECT count(*) FROM sqlite_master WHERE type='trigger' AND name IN ('categories_no_delete','subcategories_no_delete')").use {
            assertTrue(it.moveToFirst()); assertEquals(2, it.getInt(0))
        }
    }
    @Test fun invalidReferencesAndRepurposedStructuresLeaveTargetIntact() = runBlocking {
        store.restore(sample())
        rejected { store.restore(sample().copy(movements = listOf(movement.copy(categoryUuid = newCatalogUuid())))) }
        rejected { store.restore(sample().copy(categories = listOf(parent.copy(reportGroup = ReportGroup.MAINTENANCE)))) }
        assertEquals(sample(), store.snapshot(time))
    }
    @Test fun fullReplacementAllowsEmptyCatalogAndUncategorizedMovements() = runBlocking {
        val backup = CatalogBackup(time, emptyList(), emptyList(), listOf(movement.copy(categoryUuid = null, subcategoryUuid = null, amountMinor = Long.MAX_VALUE)))
        store.restore(backup)
        assertEquals(backup, store.snapshot(time))
        store.restore(CatalogBackup(time, emptyList(), emptyList(), emptyList()))
        assertTrue(store.snapshot(time).movements.isEmpty())
        assertTrue(store.snapshot(time).categories.isEmpty())
    }
    @Test fun databaseRejectsChildWithoutMatchingParent() = runBlocking {
        val other = Category(name = "Otro", scope = CategoryScope.EXPENSE, reportGroup = ReportGroup.OTHER).backup(revision)
        store.restore(sample().copy(categories = listOf(parent, other)))
        val otherId = db.categories().category(other.uuid)!!.id
        val childId = db.categories().subcategory(child.uuid)!!.id
        rejected {
            db.movements().insert(BackupMovementEntity(uuid = newCatalogUuid(), type = "EXPENSE", amountMinor = 1,
                currency = "CORDOBA", date = "2024-01-01", categoryId = otherId, subcategoryId = childId,
                detail = null, createdAt = time.toString(), updatedAt = time.toString()))
        }
        assertEquals(listOf(movement), store.snapshot(time).movements)
    }
}
