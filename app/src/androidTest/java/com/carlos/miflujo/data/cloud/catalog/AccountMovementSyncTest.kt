package com.carlos.miflujo.data.cloud.catalog

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.carlos.miflujo.data.backup.CatalogMovementBackupStore
import com.carlos.miflujo.data.backup.CatalogRestoreDestination
import com.carlos.miflujo.data.cataloglab.CatalogLabSession
import com.carlos.miflujo.data.cloud.catalogmovement.AccountMovementSyncStore
import com.carlos.miflujo.data.cloud.catalogmovement.RemoteCatalogMovement
import com.carlos.miflujo.data.cloud.catalogmovement.accountMovementDatabaseName
import com.carlos.miflujo.data.movementcatalog.CatalogMovementDatabase
import com.carlos.miflujo.domain.backup.BackupMovement
import com.carlos.miflujo.domain.backup.CatalogBackup
import com.carlos.miflujo.domain.category.Category
import com.carlos.miflujo.domain.category.CategoryScope
import com.carlos.miflujo.domain.category.ReportGroup
import com.carlos.miflujo.domain.category.Subcategory
import com.carlos.miflujo.domain.model.CatalogMovementDraft
import com.carlos.miflujo.domain.model.CatalogMovementSelection
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.MovementType
import com.carlos.miflujo.domain.model.SyncStatus
import com.carlos.miflujo.domain.model.generateMovementUuid
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import com.carlos.miflujo.ui.backup.CatalogBackupJson
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AccountMovementSyncTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context = instrumentation.targetContext
    private val draft = CatalogMovementDraft(MovementType.EXPENSE, Long.MAX_VALUE, Currency.DOLLAR, LocalDate.of(2026, 11, 21))

    @Test
    fun restoredLocalContentAndOtherAccountPendingRowsAreIneligible() = runBlocking {
        val first = "fixture-${generateMovementUuid()}"
        val other = "fixture-${generateMovementUuid()}"
        var active = first
        val session = CatalogSession { active }
        val db = Room.inMemoryDatabaseBuilder(context, CatalogMovementDatabase::class.java)
            .addCallback(CatalogMovementDatabase.initializer).build()
        try {
            val time = LocalDateTime.of(2026, 11, 21, 12, 0)
            val restored = BackupMovement(generateMovementUuid(), draft.type, draft.amountMinor, draft.currency,
                draft.date, null, null, null, time, time)
            val backup = CatalogBackup(time, emptyList(), emptyList(), listOf(restored))
            CatalogMovementBackupStore(db).restore(backup, CatalogRestoreDestination.LOCAL_ONLY)
            AccountMovementSyncStore.open(context, session).use { alice ->
                assertTrue(alice.pendingMovements().entries.isEmpty())
                assertNull(alice.movementRepository.find(restored.uuid))
                val pending = alice.movementRepository.create(draft)
                assertEquals(pending.uuid, alice.pendingMovements().entries.single().content.uuid)
                active = other
                val rejection = runCatching { alice.pendingMovements() }.exceptionOrNull() as? CatalogSyncException
                assertEquals(CatalogIssueKind.ACCOUNT_CHANGED, rejection?.kind)
                AccountMovementSyncStore.open(context, session).use { bob ->
                    assertTrue(bob.pendingMovements().entries.isEmpty())
                    assertNull(bob.movementRepository.find(pending.uuid))
                }
            }
        } finally {
            context.deleteDatabase(accountMovementDatabaseName(first))
            context.deleteDatabase(accountMovementDatabaseName(other))
            db.close()
        }
    }

    @Test
    fun acknowledgedRevisionAndConcurrentLocalEditCommitTogetherInRoom() = runBlocking {
        val uid = "fixture-${generateMovementUuid()}"
        try {
            AccountMovementSyncStore.open(context, CatalogSession { uid }).use { local ->
                val first = local.movementRepository.create(draft)
                val sent = local.pendingMovements().entries.single()
                val newer = local.movementRepository.edit(first, draft.copy(detail = "Edición posterior"))
                assertEquals(CatalogIssueKind.LOCAL_CHANGED, local.applyMovement(RemoteCatalogMovement(sent.content, 1), sent).issue)
                val pending = local.pendingMovements().entries.single()
                assertEquals(1L, pending.baseRevision)
                assertEquals(newer.draft, pending.content.draft)
                assertEquals(CatalogIssueKind.REVISION_CONFLICT, local.applyMovement(
                    RemoteCatalogMovement(sent.content.copy(deletedAt = 1_800_000_000_000L), 2),
                ).issue)
                assertEquals(pending, local.pendingMovements().entries.single())
            }
        } finally {
            context.deleteDatabase(accountMovementDatabaseName(uid))
        }
    }

    @Test
    fun androidSdkSyncsArchivedReferencesAndTombstoneThroughTheDemoEmulators() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        assumeTrue(arguments.getString("catalogMovementEmulator") == "true")
        val host = arguments.getString("catalogMovementEmulatorHost") ?: "10.0.2.2"
        val client = EmulatorCatalogClient.open(context, host = host)
        try {
            val auth = client.auth.signInWithEmailAndPassword(
                "catalog-android@example.test", "catalog-emulator-only",
            ).awaitCatalogTask()
            val uid = checkNotNull(auth.user).uid
            val path = accountMovementDatabaseName(uid)
            context.deleteDatabase(path)
            try {
                client.coordinatedSync(context).use { account ->
                    val session = CatalogLabSession.account(account)
                    val catalog = session.categories
                    val parent = catalog.createCategory(Category(name = "Hogar", scope = CategoryScope.EXPENSE, reportGroup = ReportGroup.OTHER))
                    val child = catalog.createSubcategory(Subcategory(categoryId = parent.value.id, name = "Agua"))
                    val selection = CatalogMovementSelection(parent.value.uuid, child.value.uuid)
                    val movement = session.movements.create(draft.copy(selection = selection))
                    catalog.updateCategory(parent.value.edited(archived = true), parent.revision)
                    assertTrue(session.sync().successful)
                    val synchronized = checkNotNull(account.local.movementRepository.find(movement.uuid))
                    assertEquals(Long.MAX_VALUE, synchronized.draft.amountMinor)
                    assertEquals(selection, synchronized.draft.selection)
                    assertEquals(SyncStatus.SYNCED, synchronized.syncStatus)
                    val snapshot = session.snapshot()
                    val line = session.report(snapshot, YearMonth.from(draft.date)).dollar.movements
                        .single { it.movement.uuid == movement.uuid }
                    assertEquals(Long.MAX_VALUE, line.movement.amountMinor)
                    val exported = CatalogBackupJson.parse(session.exportJson())
                    assertTrue(exported.movements.any { it.uuid == movement.uuid && it.subcategoryUuid == child.value.uuid })
                    assertFalse(session.canRestore())
                    assertTrue(runCatching { session.restore(exported) }.exceptionOrNull() is IllegalStateException)
                    session.movements.tombstone(synchronized)
                    assertTrue(session.sync().successful)
                    val deleted = checkNotNull(account.local.movementRepository.find(movement.uuid))
                    assertEquals(SyncStatus.SYNCED, deleted.syncStatus)
                    assertNotNull(deleted.deletedAtEpochMillis)
                    assertTrue(account.local.pendingMovements().entries.isEmpty())
                    assertFalse(CatalogBackupJson.parse(session.exportJson()).movements.any { it.uuid == movement.uuid })
                    assertTrue(session.sync().successful)
                    client.auth.signOut()
                    assertFalse(session.sync().successful)
                    assertTrue(runCatching { session.snapshot() }.exceptionOrNull() is CatalogSyncException)
                }
            } finally {
                context.deleteDatabase(path)
            }
        } finally {
            client.close()
        }
    }
}
