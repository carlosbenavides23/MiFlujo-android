package com.carlos.miflujo.data.category

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import com.carlos.miflujo.data.cloud.catalog.*
import com.carlos.miflujo.domain.category.Category
import com.carlos.miflujo.domain.category.CatalogSyncState
import com.carlos.miflujo.domain.category.Subcategory
import java.security.MessageDigest

/** The existing unscoped experimental file is never used as a sync source. */
class ScopedCatalogStore private constructor(
    override val uid: String,
    private val database: CategoryCatalogDatabase,
) : CatalogSyncLocalSource by RoomCatalogSyncSource(uid, database, database.categoryDao()), AutoCloseable {
    val repository = RoomCategoryRepository(database)

    override fun close() = database.close()

    companion object {
        fun open(context: Context, uid: String): ScopedCatalogStore {
            val name = scopedCatalogDatabaseName(uid)
            val db = Room.databaseBuilder(context.applicationContext, CategoryCatalogDatabase::class.java, name)
                .addCallback(CategoryCatalogDatabase.initializer)
                .build()
            return ScopedCatalogStore(uid, db)
        }
    }
}

internal class RoomCatalogSyncSource(
    override val uid: String,
    private val database: RoomDatabase,
    private val dao: CategoryDao,
    private val checkSession: () -> Unit = {},
) : CatalogSyncLocalSource {
    private val repository = RoomCategoryRepository(database, dao)

    override suspend fun pending(): CatalogSnapshot = inSessionTransaction {
        val entries = mutableListOf<RemoteCatalogEntry>()
        val invalid = mutableSetOf<String>()
        fun add(uuid: String, read: () -> RemoteCatalogEntry) {
            try {
                entries += read()
            } catch (_: IllegalArgumentException) {
                invalid += uuid
            }
        }
        dao.pendingCategories().forEach { row ->
            if (dao.subcategory(row.uuid) != null) invalid += row.uuid
            else add(row.uuid) { row.stored().toRemoteDto() }
        }
        dao.pendingSubcategories().forEach { row ->
            val parent = dao.categoryById(row.categoryId)
            if (dao.category(row.uuid) != null) invalid += row.uuid
            else add(row.uuid) { row.stored().toRemoteDto(requireNotNull(parent).toDomain()) }
        }
        CatalogSnapshot(entries, invalid)
    }

    override suspend fun apply(
        entry: RemoteCatalogEntry,
        expectedSent: RemoteCatalogEntry?,
    ): CatalogApplyResult = inSessionTransaction {
        val category = dao.category(entry.uuid)?.stored()
        val child = dao.subcategory(entry.uuid)?.stored()
        if ((entry is RemoteCategoryDto && child != null) || (entry is RemoteSubcategoryDto && category != null)) {
            return@inSessionTransaction CatalogApplyResult.CONFLICT
        }
        val parent = if (entry is RemoteSubcategoryDto) {
            if (dao.subcategory(entry.categoryUuid) != null) return@inSessionTransaction CatalogApplyResult.CONFLICT
            dao.category(entry.categoryUuid)?.toDomain() ?: return@inSessionTransaction CatalogApplyResult.MISSING_PARENT
        } else null
        if (child != null && child.value.categoryId != parent?.id) {
            return@inSessionTransaction CatalogApplyResult.CONFLICT
        }
        val current = try {
            category?.toRemoteDto() ?: child?.toRemoteDto(requireNotNull(parent))
        } catch (_: IllegalArgumentException) {
            return@inSessionTransaction CatalogApplyResult.INVALID_LOCAL
        }
        if (expectedSent != null && current != expectedSent) return@inSessionTransaction CatalogApplyResult.LOCAL_CHANGED
        val decision = current?.let { reconcileCatalog(it, entry) }
        when (decision) {
            CatalogReconciliation.INCOMPATIBLE_STRUCTURE, CatalogReconciliation.REVISION_CONTENT_MISMATCH ->
                CatalogApplyResult.CONFLICT
            CatalogReconciliation.LOCAL_WINS -> CatalogApplyResult.LOCAL_NEWER
            CatalogReconciliation.IDENTICAL -> {
                if ((category?.syncState ?: child?.syncState) == CatalogSyncState.SYNCED) {
                    CatalogApplyResult.UNCHANGED
                } else {
                    val marked = when (entry) {
                        is RemoteCategoryDto -> repository.acknowledgeCategory(entry.uuid, entry.revision)
                        is RemoteSubcategoryDto -> repository.acknowledgeSubcategory(entry.uuid, entry.revision)
                    }
                    check(marked)
                    CatalogApplyResult.ACKNOWLEDGED
                }
            }
            null, CatalogReconciliation.REMOTE_WINS -> {
                when (entry) {
                    is RemoteCategoryDto -> {
                        val value = Category(
                            id = category?.value?.id ?: 0, uuid = entry.uuid, name = entry.name,
                            scope = entry.scope, reportGroup = entry.reportGroup, iconKey = entry.iconKey,
                            archived = entry.archived, sortOrder = entry.sortOrder,
                        )
                        val row = value.toEntity(entry.revision, CatalogSyncState.SYNCED)
                        if (category == null) dao.insertCategory(row) else check(dao.updateCategory(row) == 1)
                    }
                    is RemoteSubcategoryDto -> {
                        val value = Subcategory(
                            id = child?.value?.id ?: 0, uuid = entry.uuid, categoryId = requireNotNull(parent).id,
                            name = entry.name, iconKey = entry.iconKey, archived = entry.archived, sortOrder = entry.sortOrder,
                        )
                        val row = value.toEntity(entry.revision, CatalogSyncState.SYNCED)
                        if (child == null) dao.insertSubcategory(row) else check(dao.updateSubcategory(row) == 1)
                    }
                }
                CatalogApplyResult.APPLIED
            }
        }
    }

    override suspend fun markFailed(entry: RemoteCatalogEntry): Unit = inSessionTransaction {
        when (entry) {
            is RemoteCategoryDto -> repository.failCategory(entry.uuid, entry.revision)
            is RemoteSubcategoryDto -> repository.failSubcategory(entry.uuid, entry.revision)
        }
    }

    private suspend fun <T> inSessionTransaction(block: suspend () -> T): T = database.withTransaction {
        checkSession()
        block().also { checkSession() }
    }
}

internal fun scopedCatalogDatabaseName(uid: String): String {
    return "category-catalog-sync-${catalogAccountHash(uid)}.db"
}

internal fun catalogAccountHash(uid: String): String {
    require(isValidCatalogUid(uid))
    val hash = MessageDigest.getInstance("SHA-256").digest(uid.toByteArray(Charsets.UTF_8))
    return hash.toHexString()
}
