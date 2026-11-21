package com.carlos.miflujo.data.category

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.carlos.miflujo.domain.category.*
import com.carlos.miflujo.domain.model.MovementType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Room tests: require generated Room implementation and an Android test runtime. */
@RunWith(AndroidJUnit4::class)
class CategoryCatalogRoomTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private lateinit var db: CategoryCatalogDatabase
    private lateinit var repo: CategoryRepository
    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(context, CategoryCatalogDatabase::class.java)
            .addCallback(CategoryCatalogDatabase.initializer).build()
        repo = RoomCategoryRepository(db)
    }
    @After fun close() { db.close(); context.deleteDatabase(MigrationTestFile) }
    private fun draft(name: String = "Casa") = Category(name = name, scope = CategoryScope.EXPENSE, reportGroup = ReportGroup.OTHER)
    private suspend fun rejected(block: suspend () -> Unit) {
        try { block(); fail("Expected rejected catalog write") } catch (_: Exception) { }
    }
    @Test fun createReadUpdateAndRevisionAcknowledgements() = runBlocking {
        val first = repo.createCategory(draft())
        assertEquals(first, repo.category(first.value.uuid))
        assertEquals(1L, first.revision.counter)
        assertEquals(db.categoryDao().actorUuid(), first.revision.actorUuid)
        assertEquals(CatalogSyncState.PENDING, first.syncState)
        assertTrue(repo.acknowledgeCategory(first.value.uuid, first.revision))
        val noOp = repo.updateCategory(first.value, first.revision)
        assertEquals(CatalogSyncState.SYNCED, noOp.syncState)
        val edited = repo.updateCategory(first.value.edited(name = "Hogar", iconKey = "home"), first.revision)
        assertEquals(2L, edited.revision.counter)
        assertEquals(CatalogSyncState.PENDING, edited.syncState)
        assertFalse(repo.acknowledgeCategory(first.value.uuid, first.revision))
        rejected { repo.updateCategory(first.value, first.revision) }
        assertTrue(repo.failCategory(edited.value.uuid, edited.revision))
        assertTrue(repo.pendingCategories().any { it.value.uuid == edited.value.uuid && it.syncState == CatalogSyncState.ERROR })
        assertTrue(repo.acknowledgeCategory(edited.value.uuid, edited.revision))
        assertFalse(repo.pendingCategories().any { it.value.uuid == edited.value.uuid })
        assertFalse(repo.failCategory(edited.value.uuid, edited.revision))
    }
    @Test fun childrenRequireParentAndRetainHistoryAfterArchive() = runBlocking {
        val parent = repo.createCategory(draft())
        val child = repo.createSubcategory(Subcategory(categoryId = parent.value.id, name = "Agua"))
        rejected { repo.createSubcategory(Subcategory(categoryId = Long.MAX_VALUE, name = "Unknown")) }
        rejected { repo.updateSubcategory(child.value.copy(categoryId = 1), child.revision) }
        val archived = repo.updateCategory(parent.value.edited(archived = true), parent.revision)
        assertTrue(repo.subcategories(parent.value.uuid).isEmpty())
        assertEquals(child, repo.subcategory(child.value.uuid))
        assertEquals(listOf(child), repo.subcategories(parent.value.uuid, includeArchived = true))
        assertTrue(repo.categories(includeArchived = true).contains(archived))
        rejected { repo.createSubcategory(Subcategory(categoryId = parent.value.id, name = "New")) }
        val renamed = repo.updateSubcategory(child.value.edited(name = "Agua potable", archived = true), child.revision)
        assertEquals(2L, renamed.revision.counter)
        assertFalse(repo.acknowledgeSubcategory(child.value.uuid, child.revision))
        assertTrue(repo.failSubcategory(renamed.value.uuid, renamed.revision))
        assertTrue(repo.pendingSubcategories().any { it.value.uuid == child.value.uuid })
        assertTrue(repo.acknowledgeSubcategory(renamed.value.uuid, renamed.revision))
    }
    @Test fun forbiddenStructureAndDuplicateUuidAreRejected() = runBlocking {
        val item = repo.createCategory(draft())
        rejected { repo.createCategory(item.value.copy(id = 0)) }
        rejected { repo.updateCategory(item.value.copy(uuid = newCatalogUuid()), item.revision) }
        rejected { repo.updateCategory(item.value.copy(scope = CategoryScope.BOTH), item.revision) }
        rejected { repo.updateCategory(item.value.copy(reportGroup = ReportGroup.MAINTENANCE), item.revision) }
        assertEquals(item, repo.category(item.value.uuid))
        val child = repo.createSubcategory(Subcategory(categoryId = item.value.id, name = "Child"))
        rejected { db.categoryDao().insertSubcategory(child.value.copy(id = 0).toEntity(child.revision, CatalogSyncState.PENDING)) }
    }
    @Test fun portableOrderingAndTypeFiltering() = runBlocking {
        val a = repo.createCategory(draft().copy(uuid = "11111111-1111-4111-8111-111111111111", sortOrder = -1))
        val b = repo.createCategory(draft().copy(uuid = "10000000-1111-4111-8111-111111111111", sortOrder = -1))
        val both = repo.createCategory(draft().copy(scope = CategoryScope.BOTH))
        val ordered = repo.categories(MovementType.EXPENSE)
        assertEquals(listOf(b.value.uuid, a.value.uuid), ordered.take(2).map { it.value.uuid })
        assertTrue(repo.categories(MovementType.INCOME).any { it.value.uuid == both.value.uuid })
        assertFalse(repo.categories(MovementType.INCOME).any { it.value.uuid == a.value.uuid })
        repo.reorderCategories(listOf(a.value.uuid, b.value.uuid))
        assertEquals(1, repo.category(b.value.uuid)!!.value.sortOrder)
        assertEquals(2L, repo.category(b.value.uuid)!!.revision.counter)
    }
    @Test fun invalidCompoundOperationRollsBackEarlierChanges() = runBlocking {
        val item = repo.createCategory(draft().copy(sortOrder = 10))
        rejected { repo.reorderCategories(listOf(item.value.uuid, newCatalogUuid())) }
        assertEquals(item, repo.category(item.value.uuid))
        val child = repo.createSubcategory(Subcategory(categoryId = item.value.id, name = "First", sortOrder = 5))
        val wrongParentChild = LegacyCategoryCatalog.subcategories.first()
        rejected { repo.reorderSubcategories(item.value.uuid, listOf(child.value.uuid, wrongParentChild.uuid)) }
        assertEquals(child, repo.subcategory(child.value.uuid))
        rejected { repo.reorderCategories(listOf(item.value.uuid, item.value.uuid)) }
    }
    @Test fun sqlAlsoProtectsStructureAndParentReferences() = runBlocking {
        val sql = db.openHelper.writableDatabase
        val before = repo.categories(includeArchived = true)
        rejected { sql.execSQL("UPDATE categories SET scope = 'BOTH' WHERE id = 2") }
        rejected { sql.execSQL("UPDATE subcategories SET categoryId = 4 WHERE id = 1") }
        rejected { sql.execSQL("DELETE FROM categories WHERE id = 2") }
        rejected { sql.execSQL("UPDATE categories SET revisionCounter = -1 WHERE id = 2") }
        rejected { sql.execSQL("UPDATE categories SET syncState = 'INVALID' WHERE id = 2") }
        assertEquals(before, repo.categories(includeArchived = true))
    }
    @Test fun freshRoomAndCandidateMigrationHaveEquivalentCatalogSchema() = runBlocking {
        // Hand-create a version-3 source, preserving an unrelated financial sentinel table.
        context.openOrCreateDatabase(MigrationTestFile, 0, null).use { old ->
            old.execSQL("CREATE TABLE movements (id INTEGER PRIMARY KEY, amount_minor INTEGER NOT NULL)")
            old.execSQL("INSERT INTO movements VALUES (7, 12345)")
            old.version = 3
        }
        val migrated = Room.databaseBuilder(context, CategoryCatalogDatabase::class.java, MigrationTestFile)
            .addMigrations(CATEGORY_CATALOG_MIGRATION_3_4, CATALOG_ICON_COLOR_4_5).addCallback(CategoryCatalogDatabase.initializer).build()
        try {
            val oldSql = migrated.openHelper.writableDatabase // Room validates the catalog tables here.
            val freshSql = db.openHelper.writableDatabase
            fun signature(sql: androidx.sqlite.db.SupportSQLiteDatabase): List<String> = buildList {
                listOf("categories", "subcategories", "catalog_installation").forEach { table ->
                    listOf("table_info", "foreign_key_list", "index_list").forEach { pragma ->
                        sql.query("PRAGMA $pragma('$table')").use { cursor ->
                            while (cursor.moveToNext()) add("$table/$pragma:" + (0 until cursor.columnCount).joinToString { cursor.getString(it).orEmpty() })
                        }
                    }
                }
                sql.query("SELECT name, sql FROM sqlite_master WHERE type = 'trigger' AND name NOT LIKE 'room_%' ORDER BY name").use { cursor ->
                    while (cursor.moveToNext()) add(cursor.getString(0) + cursor.getString(1))
                }
            }.sorted()
            assertEquals(signature(freshSql), signature(oldSql))
            assertEquals(repo.categories(includeArchived = true), RoomCategoryRepository(migrated).categories(includeArchived = true))
            oldSql.query("SELECT amount_minor FROM movements WHERE id = 7").use { assertTrue(it.moveToFirst()); assertEquals(12345L, it.getLong(0)) }
            oldSql.query("PRAGMA foreign_key_check").use { assertFalse(it.moveToFirst()) }
            val actor = migrated.categoryDao().actorUuid()
            migrated.close()
            val reopened = Room.databaseBuilder(context, CategoryCatalogDatabase::class.java, MigrationTestFile).build()
            try { assertEquals(actor, reopened.categoryDao().actorUuid()) } finally { reopened.close() }
        } finally { migrated.close() }
    }
    companion object { private const val MigrationTestFile = "catalog-migration-instrumented-test.db" }
}
