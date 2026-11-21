package com.carlos.miflujo.data.movementcatalog

import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.carlos.miflujo.data.backup.CatalogMovementBackupStore
import com.carlos.miflujo.data.backup.CatalogRestoreDestination
import com.carlos.miflujo.data.cataloglab.CatalogLabSession
import com.carlos.miflujo.data.local.MIGRATION_1_2
import com.carlos.miflujo.data.local.MIGRATION_2_3
import com.carlos.miflujo.data.local.MiFlujoDatabase
import com.carlos.miflujo.domain.category.LegacyCategoryCatalog
import com.carlos.miflujo.domain.category.isCanonicalCatalogUuid
import com.carlos.miflujo.domain.model.CatalogMovementDraft
import com.carlos.miflujo.domain.model.CatalogMovementSelection
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.Movement
import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementType
import com.carlos.miflujo.domain.report.ReportClassification
import com.carlos.miflujo.domain.model.SyncStatus
import com.carlos.miflujo.domain.model.generateMovementUuid
import com.carlos.miflujo.ui.backup.CatalogBackupJson
import com.carlos.miflujo.ui.backup.BackupJsonSerializer
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import org.json.JSONObject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CatalogMovementMigrationTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext

    @get:Rule
    val helper = MigrationTestHelper(instrumentation, MiFlujoDatabase::class.java)

    @Test
    fun upgradesV3WithAllSeedsAndPreservesIdsFinancialFieldsAndSyncMetadata() = runBlocking {
        val names = listOf(
            "INCOME GENERAL_INCOME" to null,
            "EXPENSE FIXED_COST" to "WATER",
            "EXPENSE FIXED_COST" to "ELECTRICITY",
            "EXPENSE FIXED_COST" to "INTERNET",
            "EXPENSE MAINTENANCE" to null,
            "EXPENSE OTHER" to null,
            "EXPENSE FIXED_COST" to null,
        )
        withDatabaseName { name ->
            val expected = helper.createDatabase(name, 3).use { source ->
                names.mapIndexed { index, (pair, child) ->
                    val (type, category) = pair.split(' ')
                    val row = CatalogMovementEntity(
                        id = index + 1L, uuid = generateMovementUuid(), type = type,
                        amountMinor = Long.MAX_VALUE - index, currency = if (index % 2 == 0) "CORDOBA" else "DOLLAR",
                        dateEpochDay = -100L + index, categoryId = listOf(1L, 2L, 2L, 2L, 3L, 4L, 2L)[index],
                        subcategoryId = child?.let { index.toLong() }, detail = if (index == 1) null else "Pago 📁 \n $index",
                        createdAtEpochMillis = -1234L, updatedAtEpochMillis = 5678L,
                        syncStatus = SyncStatus.entries[index % SyncStatus.entries.size],
                        lastSyncedAt = if (index == 0) null else 4321L,
                        deletedAt = if (index == 3) 5678L else null,
                    )
                    addLegacy(source, 3, row, category, child)
                    row
                }.also {
                    addLegacy(source, 3, it.first().copy(id = 900, uuid = generateMovementUuid()), "GENERAL_INCOME")
                    source.execSQL("DELETE FROM movements WHERE id = 900")
                }
            }

            target(name).useRoom { migrated ->
                val dao = migrated.movements()
                for (row in expected) assertEquals(row, dao.find(row.uuid))
                val sqlite = migrated.openHelper.writableDatabase
                assertEquals(listOf(listOf(expected.size.toString())), query(sqlite, "SELECT count(*) FROM movements"))
                assertTrue(query(sqlite, "PRAGMA foreign_key_check").isEmpty())
                val repo = CatalogMovementRepository(migrated) { 7000L }
                val tombstone = checkNotNull(repo.find(expected[3].uuid))
                assertEquals(5678L, tombstone.deletedAtEpochMillis)
                assertEquals(LegacyCategoryCatalog.subcategories[2].uuid, tombstone.draft.selection.subcategoryUuid)
                rejects<IllegalStateException> { repo.edit(tombstone, tombstone.draft) }
                val synced = checkNotNull(repo.find(expected[2].uuid))
                val edited = repo.edit(synced, synced.draft.copy(detail = "Revisado"))
                assertEquals(SyncStatus.PENDING_UPLOAD, edited.syncStatus)
                assertEquals(synced.lastSyncedAtEpochMillis, edited.lastSyncedAtEpochMillis)
                assertEquals(synced.uuid, edited.uuid)
                assertEquals(901L, dao.insert(expected.first().copy(id = 0, uuid = generateMovementUuid())))
            }
        }
    }

    @Test
    fun opensCompleteRoutesFromV1AndV2() = runBlocking {
        for (version in 1..2) {
            withDatabaseName { name ->
                val row = historicalExpense()
                helper.createDatabase(name, version).use { source -> addLegacy(source, version, row, "FIXED_COST", "WATER") }
                target(name).useRoom { migrated ->
                    val uuid = query(migrated.openHelper.writableDatabase, "SELECT uuid FROM movements").single().single()!!
                    if (version == 1) assertTrue(isCanonicalCatalogUuid(uuid)) else assertEquals(row.uuid, uuid)
                    assertEquals(row.copy(uuid = uuid), migrated.movements().find(uuid))
                }
            }
        }
    }

    @Test
    fun freshRoomSchemaAndSeedsMatchTheMigratedSchema() = runBlocking {
        withDatabaseName { name ->
            helper.createDatabase(name, 3).close()
            target(name).useRoom { migrated ->
                target().useRoom { fresh ->
                    val upgraded = migrated.openHelper.writableDatabase
                    val clean = fresh.openHelper.writableDatabase
                    val tables = listOf("movements", "categories", "subcategories", "catalog_installation")
                    for (table in tables) {
                        assertEquals(query(clean, "PRAGMA table_info($table)"), query(upgraded, "PRAGMA table_info($table)"))
                        fun keys(db: SupportSQLiteDatabase) = query(db, "PRAGMA foreign_key_list($table)")
                            .map { it.drop(1) }.toSet()
                        assertEquals(keys(clean), keys(upgraded))
                    }
                    val indexes = "SELECT name, tbl_name FROM sqlite_master WHERE type = 'index' " +
                        "AND tbl_name IN ('movements','categories','subcategories','catalog_installation') ORDER BY name"
                    assertEquals(query(clean, indexes), query(upgraded, indexes))
                    for ((index) in query(clean, indexes)) {
                        assertEquals(query(clean, "PRAGMA index_info($index)"), query(upgraded, "PRAGMA index_info($index)"))
                    }
                    for (table in tables) {
                        fun indexFlags(db: SupportSQLiteDatabase) = query(db, "PRAGMA index_list($table)")
                            .map { it.drop(1) }.toSet()
                        assertEquals(indexFlags(clean), indexFlags(upgraded))
                    }
                    val triggers = "SELECT name, sql FROM sqlite_master WHERE type = 'trigger' ORDER BY name"
                    assertEquals(query(clean, triggers), query(upgraded, triggers))
                    assertEquals(query(clean, "SELECT * FROM categories ORDER BY id"), query(upgraded, "SELECT * FROM categories ORDER BY id"))
                    assertEquals(query(clean, "SELECT * FROM subcategories ORDER BY id"), query(upgraded, "SELECT * FROM subcategories ORDER BY id"))
                    assertTrue(isCanonicalCatalogUuid(fresh.catalog().actorUuid()))
                    assertTrue(isCanonicalCatalogUuid(migrated.catalog().actorUuid()))
                    assertEquals(listOf(listOf("1")), query(clean, "PRAGMA foreign_keys"))
                    assertEquals(listOf(listOf("1")), query(upgraded, "PRAGMA foreign_keys"))
                }
            }
        }
    }

    @Test
    fun labWorkflowCombinesMigratedHistoryCustomMovementsReportAndPortableRestore() = runBlocking {
        val month = YearMonth.of(2026, 11)
        val day = month.atDay(10)
        withDatabaseName { name ->
            val historical = historicalExpense().copy(dateEpochDay = day.toEpochDay())
            helper.createDatabase(name, 3).use { database ->
                addLegacy(database, 3, historical, "FIXED_COST", "WATER")
            }
            target(name).useRoom { migrated ->
                val session = CatalogLabSession.local(migrated)
                session.addExampleCatalog()
                session.addExampleCatalog()
                val parent = session.categories.categories().single { it.value.name == "Hogar de prueba" }
                val child = session.categories.subcategories(parent.value.uuid).single()
                val draft = CatalogMovementDraft(
                    MovementType.EXPENSE, 1234L, Currency.CORDOBA, day,
                    CatalogMovementSelection(parent.value.uuid, child.value.uuid),
                )
                val original = session.movements.create(draft)
                session.categories.updateCategory(parent.value.edited(archived = true), parent.revision)
                val edited = session.movements.edit(original, draft.copy(detail = "Recibo de prueba"))
                rejects<IllegalArgumentException> { session.movements.create(draft) }
                val uncategorized = session.movements.create(
                    CatalogMovementDraft(MovementType.INCOME, 8000L, Currency.DOLLAR, day),
                )
                val before = session.snapshot()
                val monthly = session.report(before, month)
                assertEquals(1234L, monthly.cordoba.totalExpenseMinor)
                assertEquals(8000L, monthly.dollar.totalIncomeMinor)
                assertEquals(historical.amountMinor, monthly.dollar.totalExpenseMinor)
                val archived = monthly.cordoba.movements.single().classification as ReportClassification.Assigned
                assertTrue(archived.category.archived)
                assertEquals(child.value.uuid, archived.subcategory?.uuid)
                assertEquals(ReportClassification.Uncategorized, monthly.dollar.movements.single {
                    it.movement.uuid == uncategorized.uuid
                }.classification)

                target().useRoom { clean ->
                    val restored = CatalogLabSession.local(clean)
                    val file = session.exportJson()
                    restored.restore(restored.prepareRestore(file))
                    val after = restored.snapshot()
                    assertEquals(before.copy(createdAt = after.createdAt), after)
                    assertEquals(monthly, restored.report(after, month))
                    assertEquals(edited.draft, restored.movements.find(edited.uuid)?.draft)
                    val prepared = restored.prepareRestore(file)
                    clean.movements().update(checkNotNull(clean.movements().find(edited.uuid)).copy(syncStatus = SyncStatus.SYNCED))
                    assertEquals(false, restored.canRestore())
                    rejects<IllegalStateException> { restored.restore(prepared) }
                    assertEquals(after.movements, restored.snapshot().movements)
                }
            }
        }
    }

    @Test
    fun labWorkflowReadsHistoricalJsonAndKeepsCurrentDataAfterInvalidInput() = runBlocking {
        target().useRoom { clean ->
            val session = CatalogLabSession.local(clean)
            val date = LocalDate.of(2026, 11, 10)
            val at = date.atStartOfDay()
            val legacy = Movement(id = 42L, type = MovementType.EXPENSE, amountMinor = 250L,
                currency = Currency.CORDOBA, date = date, category = MovementCategory.OTHER,
                createdAt = at, updatedAt = at)
            val v2 = BackupJsonSerializer.serialize(at, listOf(legacy))
            val v1 = JSONObject(v2).put("schemaVersion", 1)
            v1.getJSONArray("movements").getJSONObject(0).remove("uuid")
            for ((index, file) in listOf(v2, v1.toString()).withIndex()) {
                session.restore(session.prepareRestore(file))
                val snapshot = session.snapshot()
                val movement = snapshot.movements.single()
                assertEquals(index == 0, legacy.uuid == movement.uuid)
                assertEquals(250L, session.report(snapshot, YearMonth.from(date)).cordoba.totalExpenseMinor)
                assertEquals(LegacyCategoryCatalog.categories.last().uuid, movement.categoryUuid)
                rejects<com.carlos.miflujo.ui.backup.InvalidBackupException> { session.prepareRestore("{") }
                assertEquals(snapshot.movements, session.snapshot().movements)
            }
        }
    }

    @Test
    fun movedV3RowsCanBeExportedAndRestoredWithoutChangingFinancialContent() = runBlocking {
        withDatabaseName { name ->
            val historical = historicalExpense()
            helper.createDatabase(name, 3).use { source ->
                addLegacy(source, 3, historical, "FIXED_COST")
            }
            target(name).useRoom { migrated ->
                val time = LocalDateTime.of(2026, 11, 21, 12, 0)
                val store = CatalogMovementBackupStore(migrated)
                val backup = CatalogBackupJson.parse(CatalogBackupJson.serialize(store.snapshot(time)))
                val movement = backup.movements.single()
                assertEquals(historical.uuid, movement.uuid)
                assertEquals(LegacyCategoryCatalog.categories[1].uuid, movement.categoryUuid)
                assertNull(movement.subcategoryUuid)
                store.restore(backup, CatalogRestoreDestination.LOCAL_ONLY)
                val row = checkNotNull(migrated.movements().find(historical.uuid))
                val newCategory = checkNotNull(migrated.catalog().category(checkNotNull(movement.categoryUuid)))
                assertTrue(row.id > historical.id)
                assertEquals(historical.copy(id = row.id, categoryId = newCategory.id, subcategoryId = null), row)
                assertEquals(backup, store.snapshot(time))
            }
        }
    }

    @Test
    fun invalidHistoricalRowsAbortTheWholeUpgradeAndLeaveV3Intact() {
        val malformed = listOf(
            Triple("UNKNOWN", null, 10L), Triple("MAINTENANCE", "WATER", 10L),
            Triple("FIXED_COST", "UNKNOWN", 10L), Triple("GENERAL_INCOME", null, 10L),
            Triple("OTHER", null, 0L),
        )
        for ((category, child, amount) in malformed) {
            withDatabaseName { name ->
                val valid = historicalExpense()
                val invalid = valid.copy(id = 100L, uuid = generateMovementUuid(), amountMinor = amount)
                helper.createDatabase(name, 3).use { source ->
                    addLegacy(source, 3, valid, "FIXED_COST", "WATER")
                    addLegacy(source, 3, invalid, category, child)
                }
                target(name).useRoom { candidate ->
                    val error = rejects<IllegalStateException> { candidate.openHelper.writableDatabase }
                    assertTrue(error.message.orEmpty().contains("100"))
                }
                SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { restored ->
                    assertEquals(3, restored.version)
                    restored.rawQuery("SELECT uuid, category, subcategory, amount_minor FROM movements ORDER BY id", null).use {
                        assertTrue(it.moveToNext())
                        assertEquals(valid.uuid, it.getString(0))
                        assertEquals("FIXED_COST", it.getString(1))
                        assertEquals("WATER", it.getString(2))
                        assertTrue(it.moveToNext())
                        assertEquals(invalid.uuid, it.getString(0))
                        assertEquals(category, it.getString(1))
                        assertEquals(child, it.getString(2))
                        assertEquals(amount, it.getLong(3))
                        assertEquals(false, it.moveToNext())
                    }
                    restored.rawQuery("SELECT name FROM sqlite_master WHERE name IN ('categories','movements_catalog_new')", null)
                        .use { assertEquals(0, it.count) }
                }
            }
        }
    }

    @Test
    fun missingMigrationPathFailsInsteadOfRecreatingTheDatabase() {
        withDatabaseName { name ->
            val row = historicalExpense()
            helper.createDatabase(name, 3).use { addLegacy(it, 3, row, "FIXED_COST", "WATER") }
            Room.databaseBuilder(context, CatalogMovementDatabase::class.java, name).build().useRoom {
                rejects<IllegalStateException> { it.openHelper.writableDatabase }
            }
            SQLiteDatabase.openDatabase(context.getDatabasePath(name).path, null, SQLiteDatabase.OPEN_READONLY).use { source ->
                assertEquals(3, source.version)
                source.rawQuery("SELECT uuid FROM movements", null).use {
                    assertTrue(it.moveToFirst())
                    assertEquals(row.uuid, it.getString(0))
                }
            }
        }
    }

    @Test
    fun transactionsValidatePortableSelectionAndKeepArchivedHistory() = runBlocking {
        target().useRoom { fresh ->
            val repo = CatalogMovementRepository(fresh) { 200L }
            val db = fresh.openHelper.writableDatabase
            val fixed = LegacyCategoryCatalog.categories[1].uuid
            val other = LegacyCategoryCatalog.categories[3].uuid
            val water = LegacyCategoryCatalog.subcategories[0].uuid
            val draft = CatalogMovementDraft(MovementType.EXPENSE, 17, Currency.DOLLAR, LocalDate.ofEpochDay(-3))
            assertEquals(draft, repo.find(repo.create(draft).uuid)?.draft)
            val withCategory = draft.copy(selection = CatalogMovementSelection(fixed))
            assertEquals(withCategory, repo.create(withCategory).draft)
            val withChild = draft.copy(selection = CatalogMovementSelection(fixed, water))
            val historical = repo.create(withChild)
            db.execSQL("UPDATE categories SET archived=1 WHERE uuid=?", arrayOf(fixed))
            db.execSQL("UPDATE subcategories SET archived=1 WHERE uuid=?", arrayOf(water))
            rejects<IllegalArgumentException> { repo.create(withChild) }
            val edited = repo.edit(historical, withChild.copy(detail = "Histórico"))
            assertEquals(historical.createdAtEpochMillis, edited.createdAtEpochMillis)
            assertEquals(historical.updatedAtEpochMillis + 1, edited.updatedAtEpochMillis)
            assertEquals(withChild.selection, edited.draft.selection)
            rejects<IllegalStateException> { repo.edit(historical, draft) }
            rejects<IllegalArgumentException> { repo.edit(edited, withChild.copy(type = MovementType.INCOME)) }
            for (invalid in listOf(
                CatalogMovementSelection(other, water), CatalogMovementSelection(generateMovementUuid()),
                CatalogMovementSelection(other, generateMovementUuid()),
            )) {
                rejects<IllegalArgumentException> { repo.edit(edited, draft.copy(selection = invalid)) }
            }
            assertEquals(edited, repo.find(edited.uuid))
            assertNull(repo.find(generateMovementUuid()))
            rejects<IllegalArgumentException> { CatalogMovementSelection(subcategoryUuid = water) }
            for (change in listOf("category_id=NULL", "category_id=4", "subcategory_id=999", "type='INCOME'")) {
                rejects<SQLiteConstraintException> {
                    db.execSQL("UPDATE movements SET $change WHERE uuid=?", arrayOf(edited.uuid))
                }
            }
            assertEquals(draft, repo.edit(edited, draft).draft)
        }
    }

    private inline fun withDatabaseName(block: (String) -> Unit) {
        val name = "catalog-movement-test-${generateMovementUuid()}.db"
        try { block(name) } finally { context.deleteDatabase(name) }
    }

    private fun target(name: String? = null): CatalogMovementDatabase {
        val builder = if (name == null) Room.inMemoryDatabaseBuilder(context, CatalogMovementDatabase::class.java)
        else Room.databaseBuilder(context, CatalogMovementDatabase::class.java, name)
        return builder.addCallback(CatalogMovementDatabase.initializer)
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3, CATALOG_MOVEMENT_MIGRATION_3_4).build()
    }

    private fun query(db: SupportSQLiteDatabase, sql: String): List<List<String?>> = db.query(sql).use { cursor ->
        buildList {
            while (cursor.moveToNext()) add(List(cursor.columnCount) { cursor.getString(it) })
        }
    }

    private fun historicalExpense() = CatalogMovementEntity(
        id = 8, uuid = generateMovementUuid(), type = "EXPENSE", amountMinor = 42, currency = "DOLLAR",
        dateEpochDay = 567, categoryId = 2, subcategoryId = 1, detail = "Agua\n",
        createdAtEpochMillis = 800, updatedAtEpochMillis = 900,
    )

    private fun addLegacy(
        db: SupportSQLiteDatabase, version: Int, row: CatalogMovementEntity, category: String, child: String? = null,
    ) {
        val fields = mutableListOf("id", "type", "amount_minor", "currency", "date_epoch_day",
            "category", "subcategory", "detail", "created_at_epoch_millis", "updated_at_epoch_millis")
        val args = mutableListOf<Any?>(row.id, row.type, row.amountMinor, row.currency, row.dateEpochDay,
            category, child, row.detail, row.createdAtEpochMillis, row.updatedAtEpochMillis)
        if (version >= 2) {
            fields.add("uuid")
            args.add(row.uuid)
        }
        if (version >= 3) {
            fields.addAll(listOf("sync_status", "last_synced_at_epoch_millis", "deleted_at_epoch_millis"))
            args.addAll(listOf(row.syncStatus.name, row.lastSyncedAt, row.deletedAt))
        }
        db.execSQL("INSERT INTO movements (${fields.joinToString()}) VALUES (${fields.joinToString { "?" }})", args.toTypedArray())
    }

    private inline fun <R> CatalogMovementDatabase.useRoom(block: (CatalogMovementDatabase) -> R): R =
        try { block(this) } finally { close() }

    private inline fun <reified T : Exception> rejects(block: () -> Unit): T {
        val error = runCatching(block).exceptionOrNull()
        assertTrue("Expected ${T::class.java.simpleName} but received $error", error is T)
        return error as T
    }
}
