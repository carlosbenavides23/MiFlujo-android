package com.carlos.miflujo.data.cloud.catalogmovement

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.Upsert
import androidx.room.withTransaction
import com.carlos.miflujo.data.backup.readCatalogMovementSnapshot
import com.carlos.miflujo.data.category.CatalogInstallationEntity
import com.carlos.miflujo.data.category.CategoryDao
import com.carlos.miflujo.data.category.CategoryEntity
import com.carlos.miflujo.data.category.RoomCatalogSyncSource
import com.carlos.miflujo.data.category.RoomCategoryRepository
import com.carlos.miflujo.data.category.SubcategoryEntity
import com.carlos.miflujo.data.category.catalogAccountHash
import com.carlos.miflujo.data.cloud.catalog.CatalogIssueKind
import com.carlos.miflujo.data.cloud.catalog.CatalogSession
import com.carlos.miflujo.data.cloud.catalog.CatalogSyncException
import com.carlos.miflujo.data.local.SyncStatusConverters
import com.carlos.miflujo.data.movementcatalog.CatalogMovementDao
import com.carlos.miflujo.data.movementcatalog.CatalogMovementDatabase
import com.carlos.miflujo.data.movementcatalog.CatalogMovementEntity
import com.carlos.miflujo.data.movementcatalog.CatalogMovementRepository
import com.carlos.miflujo.domain.category.CategoryScope
import com.carlos.miflujo.domain.model.CatalogMovementDraft
import com.carlos.miflujo.domain.model.CatalogMovementSelection
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.MovementType
import com.carlos.miflujo.domain.model.SyncStatus
import java.time.DateTimeException
import java.time.LocalDate
import java.time.LocalDateTime

@Entity(
    tableName = "movement_sync_baselines",
    foreignKeys = [ForeignKey(CatalogMovementEntity::class, ["uuid"], ["uuid"], onDelete = ForeignKey.RESTRICT)],
)
internal data class MovementSyncBaseline(@PrimaryKey val uuid: String, val revision: Long)

@Dao
internal interface MovementSyncBaselineDao {
    @Query("SELECT revision FROM movement_sync_baselines WHERE uuid = :uuid")
    suspend fun revision(uuid: String): Long?

    @Upsert
    suspend fun remember(baseline: MovementSyncBaseline)
}

@Database(
    entities = [CatalogMovementEntity::class, CategoryEntity::class, SubcategoryEntity::class,
        CatalogInstallationEntity::class, MovementSyncBaseline::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(SyncStatusConverters::class)
internal abstract class AccountMovementSyncDatabase : RoomDatabase() {
    abstract fun catalog(): CategoryDao
    abstract fun movements(): CatalogMovementDao
    abstract fun baselines(): MovementSyncBaselineDao
}

/** Room partition has no import or upgrade path from unscoped/restored databases. */
internal class AccountMovementSyncStore private constructor(
    override val uid: String,
    private val database: AccountMovementSyncDatabase,
    private val session: CatalogSession,
) : CatalogMovementSyncLocalSource, AutoCloseable {
    private val catalog = database.catalog()
    private val movements = database.movements()
    private val baselines = database.baselines()

    val categories = RoomCategoryRepository(database, catalog)
    val movementRepository = CatalogMovementRepository(database, movements, catalog, SyncStatus.PENDING_UPLOAD)
    val catalogSync = RoomCatalogSyncSource(uid, database, catalog, ::requireSession)

    suspend fun snapshot(createdAt: LocalDateTime) = database.withTransaction {
        requireSession()
        database.readCatalogMovementSnapshot(catalog, movements, createdAt).also { requireSession() }
    }

    override suspend fun pendingMovements(): MovementSyncSnapshot<PendingCatalogMovement> = database.withTransaction {
        requireSession()
        val entries = mutableListOf<PendingCatalogMovement>()
        val invalid = mutableSetOf<String>()
        for (row in movements.pendingForSync()) {
            try {
                require(row.syncStatus != SyncStatus.PENDING_DELETE || row.deletedAt != null)
                entries += PendingCatalogMovement(row.content(), baselines.revision(row.uuid))
            } catch (_: IllegalArgumentException) {
                invalid += row.uuid
            } catch (_: DateTimeException) {
                invalid += row.uuid
            }
        }
        requireSession()
        MovementSyncSnapshot(entries, invalid)
    }

    override suspend fun applyMovement(remote: RemoteCatalogMovement, sent: PendingCatalogMovement?): MovementApplyResult =
        database.withTransaction {
            requireSession()
            val content = remote.content
            val current = movements.find(content.uuid)
            val base = baselines.revision(content.uuid)
            if (current != null) {
                if (current.syncStatus == SyncStatus.LOCAL_ONLY) return@withTransaction MovementApplyResult(issue = CatalogIssueKind.INVALID_LOCAL)
                val local = try {
                    current.content()
                } catch (_: IllegalArgumentException) {
                    return@withTransaction MovementApplyResult(issue = CatalogIssueKind.INVALID_LOCAL)
                } catch (_: DateTimeException) {
                    return@withTransaction MovementApplyResult(issue = CatalogIssueKind.INVALID_LOCAL)
                }
                if (local.createdAt != content.createdAt || (base != null && remote.revision < base) ||
                    (local.deletedAt != null && base != null && current.syncStatus == SyncStatus.SYNCED &&
                        content != local)
                ) return@withTransaction MovementApplyResult(issue = CatalogIssueKind.REVISION_CONFLICT)
                if (sent != null && (sent.baseRevision != base || sent.content != content)) {
                    return@withTransaction MovementApplyResult(issue = CatalogIssueKind.LOCAL_CHANGED)
                }
                if (local != content) {
                    if (current.syncStatus != SyncStatus.SYNCED) {
                        if (sent != null) {
                            // Our exact write won; an edit made while it was in flight stays pending on the new base.
                            baselines.remember(MovementSyncBaseline(content.uuid, remote.revision))
                            requireSession()
                            return@withTransaction MovementApplyResult(issue = CatalogIssueKind.LOCAL_CHANGED)
                        }
                        return@withTransaction if (base == remote.revision) MovementApplyResult()
                        else MovementApplyResult(issue = CatalogIssueKind.REVISION_CONFLICT)
                    }
                    if (base == null || remote.revision == base) return@withTransaction MovementApplyResult(issue = CatalogIssueKind.REVISION_CONFLICT)
                } else if (current.syncStatus == SyncStatus.SYNCED && base == remote.revision) {
                    return@withTransaction MovementApplyResult()
                }
            } else if (sent != null || base != null) {
                return@withTransaction MovementApplyResult(issue = CatalogIssueKind.LOCAL_CHANGED)
            }

            val draft = content.draft
            val selection = draft.selection
            val parent = selection.categoryUuid?.let {
                catalog.category(it) ?: return@withTransaction MovementApplyResult(issue = CatalogIssueKind.MISSING_PARENT)
            }
            val child = selection.subcategoryUuid?.let {
                catalog.subcategory(it) ?: return@withTransaction MovementApplyResult(issue = CatalogIssueKind.MISSING_PARENT)
            }
            if (parent != null && !CategoryScope.valueOf(parent.scope).supports(draft.type) ||
                child != null && child.categoryId != parent?.id
            ) return@withTransaction MovementApplyResult(issue = CatalogIssueKind.INVALID_REMOTE)
            val row = CatalogMovementEntity(
                id = current?.id ?: 0, uuid = content.uuid, type = draft.type.name, amountMinor = draft.amountMinor,
                currency = draft.currency.name, dateEpochDay = draft.date.toEpochDay(), categoryId = parent?.id,
                subcategoryId = child?.id, detail = draft.detail, createdAtEpochMillis = content.createdAt,
                updatedAtEpochMillis = content.updatedAt, deletedAt = content.deletedAt,
                syncStatus = SyncStatus.SYNCED, lastSyncedAt = System.currentTimeMillis(),
            )
            if (current == null) movements.insert(row) else check(movements.update(row) == 1)
            baselines.remember(MovementSyncBaseline(content.uuid, remote.revision))
            requireSession()
            MovementApplyResult(changed = true)
        }

    private suspend fun CatalogMovementEntity.content() = MovementContent(
        uuid,
        CatalogMovementDraft(MovementType.valueOf(type), amountMinor, Currency.valueOf(currency), LocalDate.ofEpochDay(dateEpochDay),
            CatalogMovementSelection(categoryId?.let { requireNotNull(catalog.categoryById(it)).uuid },
                subcategoryId?.let { requireNotNull(catalog.subcategoryById(it)).uuid }), detail),
        createdAtEpochMillis, updatedAtEpochMillis, deletedAt,
    )

    private fun requireSession() {
        if (session.currentUid() != uid) throw CatalogSyncException(CatalogIssueKind.ACCOUNT_CHANGED)
    }

    override fun close() = database.close()

    companion object {
        fun open(context: Context, session: CatalogSession): AccountMovementSyncStore {
            val uid = checkNotNull(session.currentUid())
            val name = accountMovementDatabaseName(uid)
            val database = Room.databaseBuilder(context.applicationContext, AccountMovementSyncDatabase::class.java, name)
                .addCallback(CatalogMovementDatabase.initializer).build()
            return AccountMovementSyncStore(uid, database, session)
        }
    }
}

internal fun accountMovementDatabaseName(uid: String) = "catalog-movement-sync-${catalogAccountHash(uid)}.db"
