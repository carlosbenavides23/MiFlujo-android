package com.carlos.miflujo.data.cloud.catalog

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.carlos.miflujo.data.category.ScopedCatalogStore
import com.carlos.miflujo.data.category.scopedCatalogDatabaseName
import com.carlos.miflujo.domain.category.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class ScopedCatalogSyncTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun accountFilesPersistPendingsAndKeepTheActorOnReopen() = runBlocking {
        val alice = "catalog-test-${UUID.randomUUID()}"
        val bob = "catalog-test-${UUID.randomUUID()}"
        try {
            val created = ScopedCatalogStore.open(context, alice).use {
                val saved = it.repository.createCategory(draft())
                it.markFailed(saved.toRemoteDto())
                saved
            }
            ScopedCatalogStore.open(context, bob).use {
                assertNull(it.repository.category(created.value.uuid))
                assertFalse(it.pending().entries.any { entry -> entry.uuid == created.value.uuid })
            }
            ScopedCatalogStore.open(context, alice).use {
                val previous = checkNotNull(it.repository.category(created.value.uuid))
                assertEquals(CatalogSyncState.ERROR, previous.syncState)
                assertEquals(created.revision, previous.revision)
                assertTrue(it.pending().entries.contains(previous.toRemoteDto()))
                val edit = it.repository.updateCategory(previous.value.edited(name = "Casa nueva"), previous.revision)
                assertEquals(created.revision.counter + 1, edit.revision.counter)
                assertEquals(created.revision.actorUuid, edit.revision.actorUuid)
                assertEquals(CatalogSyncState.PENDING, edit.syncState)
            }
        } finally {
            context.deleteDatabase(scopedCatalogDatabaseName(alice))
            context.deleteDatabase(scopedCatalogDatabaseName(bob))
        }
    }

    @Test fun conditionalApplyPreservesAnEditAndResolvesRemoteParentToLocalId() = runBlocking {
        val uid = "catalog-test-${UUID.randomUUID()}"
        try {
            ScopedCatalogStore.open(context, uid).use { store ->
                val saved = store.repository.createCategory(draft())
                val sent = saved.toRemoteDto()
                val renamed = store.repository.updateCategory(saved.value.edited(name = "Nueva"), saved.revision)
                assertEquals(CatalogApplyResult.LOCAL_CHANGED, store.apply(sent, sent))
                assertEquals(renamed, store.repository.category(saved.value.uuid))
                val archive = renamed.toRemoteDto().copy(
                    archived = true, revision = renamed.revision.nextFor(newCatalogUuid()),
                )
                val child = RemoteSubcategoryDto(
                    newCatalogUuid(), archive.uuid, "Agua", "water", false, 0, archive.revision,
                )
                assertEquals(CatalogApplyResult.MISSING_PARENT, store.apply(child.copy(categoryUuid = newCatalogUuid())))
                assertEquals(CatalogApplyResult.APPLIED, store.apply(archive))
                assertEquals(CatalogApplyResult.APPLIED, store.apply(child))
                val parent = checkNotNull(store.repository.category(archive.uuid))
                val stored = checkNotNull(store.repository.subcategory(child.uuid))
                assertEquals(saved.value.id, parent.value.id)
                assertTrue(parent.value.archived)
                assertEquals(parent.value.id, stored.value.categoryId)
                assertEquals(CatalogSyncState.SYNCED, stored.syncState)
                assertEquals(CatalogApplyResult.CONFLICT, store.apply(archive.copy(scope = CategoryScope.BOTH)))
            }
        } finally {
            context.deleteDatabase(scopedCatalogDatabaseName(uid))
        }
    }

    @Test fun emulatorRoundTripAndServerWinner() = runBlocking {
        val arguments = InstrumentationRegistry.getArguments()
        org.junit.Assume.assumeTrue(arguments.getString("catalogEmulator") == "true")
        val host = arguments.getString("catalogEmulatorHost") ?: "10.0.2.2"
        val client = EmulatorCatalogClient.open(context, host = host)
        try {
            val uid = checkNotNull(client.auth.signInWithEmailAndPassword(
                "catalog-android@example.test", "catalog-emulator-only",
            ).awaitCatalogTask().user?.uid)
            // Isolated UID file for the emulator fixture. Rows already present from an interrupted test are harmless.
            ScopedCatalogStore.open(context, uid).use { store ->
                val parent = store.repository.createCategory(draft())
                val child = store.repository.createSubcategory(Subcategory(categoryId = parent.value.id, name = "Agua"))
                val engine = CatalogSyncEngine(store, client, client)
                val first = engine.sync()
                assertTrue(first.issues.toString(), first.successful)
                val remote = client.fetch(uid).entries.associateBy { it.uuid }
                assertEquals(parent.toRemoteDto(), remote[parent.value.uuid])
                assertEquals(child.toRemoteDto(parent.value), remote[child.value.uuid])
                val sent = parent.toRemoteDto()
                val winner = sent.copy(archived = true, revision = sent.revision.nextFor(newCatalogUuid()))
                assertEquals(winner, client.publish(uid, winner))
                assertEquals(winner, client.publish(uid, sent))
                val second = engine.sync()
                assertTrue(second.issues.toString(), second.successful)
                assertTrue(checkNotNull(store.repository.category(sent.uuid)).value.archived)
                assertEquals(CatalogSyncState.SYNCED, checkNotNull(store.repository.subcategory(child.value.uuid)).syncState)
                client.auth.signOut()
                assertEquals(CatalogIssueKind.UNAUTHENTICATED, engine.sync().issues.single().kind)
            }
        } finally {
            client.close()
        }
    }

    private fun draft() = Category(name = "Casa", scope = CategoryScope.EXPENSE, reportGroup = ReportGroup.OTHER)
}
