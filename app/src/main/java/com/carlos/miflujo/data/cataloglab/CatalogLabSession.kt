package com.carlos.miflujo.data.cataloglab

import com.carlos.miflujo.data.backup.CatalogMovementBackupStore
import com.carlos.miflujo.data.backup.CatalogRestoreDestination
import com.carlos.miflujo.data.category.RoomCategoryRepository
import com.carlos.miflujo.data.cloud.catalog.EmulatorMovementAccount
import com.carlos.miflujo.data.movementcatalog.CatalogMovementDatabase
import com.carlos.miflujo.data.movementcatalog.CatalogMovementRepository
import com.carlos.miflujo.domain.backup.CatalogBackup
import com.carlos.miflujo.domain.category.Category
import com.carlos.miflujo.domain.category.CategoryRepository
import com.carlos.miflujo.domain.category.CategoryScope
import com.carlos.miflujo.domain.category.ReportGroup
import com.carlos.miflujo.domain.category.Subcategory
import com.carlos.miflujo.domain.report.CalculateFlexibleMonthlyReport
import com.carlos.miflujo.domain.report.fromCatalogSnapshot
import com.carlos.miflujo.ui.backup.CatalogBackupJson
import java.time.LocalDateTime
import java.time.YearMonth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The restore capability belongs to the local candidate. An account session never gets that store. */
internal class CatalogLabSession private constructor(
    val movements: CatalogMovementRepository,
    val categories: CategoryRepository,
    private val snapshotSource: suspend (LocalDateTime) -> CatalogBackup,
    private val backupStore: CatalogMovementBackupStore?,
    private val account: EmulatorMovementAccount?,
) {
    val accountUid: String? get() = account?.local?.uid

    suspend fun snapshot() = snapshotSource(LocalDateTime.now())

    suspend fun canRestore() = backupStore?.canRestore() == true

    suspend fun prepareRestore(json: String): CatalogBackup {
        check(canRestore()) { "La restauración requiere una base local sin historial de sincronización." }
        return withContext(Dispatchers.Default) { CatalogBackupJson.parse(json) }
    }

    suspend fun restore(backup: CatalogBackup) {
        val store = checkNotNull(backupStore) { "La base de una cuenta no admite restauración." }
        store.restore(backup, CatalogRestoreDestination.LOCAL_ONLY)
    }

    suspend fun exportJson(): String {
        val data = snapshot()
        return withContext(Dispatchers.Default) { CatalogBackupJson.serialize(data) }
    }

    fun report(snapshot: CatalogBackup, month: YearMonth) =
        CalculateFlexibleMonthlyReport().fromCatalogSnapshot(snapshot, month)

    suspend fun sync() = checkNotNull(account).sync.sync()

    /** Small fixture for exercising the selector without adding a catalog administration screen. */
    suspend fun addExampleCatalog() {
        val parent = categories.category(EXAMPLE_CATEGORY) ?: categories.createCategory(
            Category(uuid = EXAMPLE_CATEGORY, name = "Hogar de prueba", scope = CategoryScope.EXPENSE,
                reportGroup = ReportGroup.FIXED_COST, iconKey = "home"),
        )
        if (!parent.value.archived && categories.subcategory(EXAMPLE_SUBCATEGORY) == null) {
            categories.createSubcategory(Subcategory(
                uuid = EXAMPLE_SUBCATEGORY, categoryId = parent.value.id, name = "Servicio de prueba", iconKey = "water",
            ))
        }
    }

    companion object {
        private const val EXAMPLE_CATEGORY = "290d8ee5-4c5e-4d26-9c8d-70879c88e26a"
        private const val EXAMPLE_SUBCATEGORY = "f4f8894d-518d-48c3-a2c5-3bed1069dc50"

        fun local(database: CatalogMovementDatabase): CatalogLabSession {
            val store = CatalogMovementBackupStore(database)
            return CatalogLabSession(
                CatalogMovementRepository(database), RoomCategoryRepository(database, database.catalog()),
                store::snapshot, store, null,
            )
        }

        fun account(account: EmulatorMovementAccount) = CatalogLabSession(
            account.local.movementRepository, account.local.categories, account.local::snapshot, null, account,
        )
    }
}
