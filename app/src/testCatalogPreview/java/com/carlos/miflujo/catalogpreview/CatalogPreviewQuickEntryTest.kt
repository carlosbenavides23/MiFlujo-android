package com.carlos.miflujo.catalogpreview

import com.carlos.miflujo.domain.backup.CatalogBackup
import com.carlos.miflujo.domain.model.CatalogMovement
import com.carlos.miflujo.domain.model.CatalogMovementDraft
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.MovementType
import com.carlos.miflujo.domain.model.SyncStatus
import com.carlos.miflujo.ui.movement.PendingEntry
import com.carlos.miflujo.ui.movement.QuickEntryDraft
import com.carlos.miflujo.ui.movement.QuickEntrySavePhase
import com.carlos.miflujo.ui.movement.QuickEntryState
import com.carlos.miflujo.ui.movement.catalogSubmissionEntries
import com.carlos.miflujo.ui.movement.quickEntrySavePhase
import java.time.LocalDate
import java.time.LocalDateTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CatalogPreviewQuickEntryTest {
    @Test
    fun `only a successful missing editor lookup clears its requested UUID`() {
        val lookupFailure = Result.failure<CatalogMovement?>(IllegalStateException("Reading movement failed"))
        assertEquals(movement.uuid, lookupFailure.editorUuidAfterLookup(movement.uuid))
        assertEquals(movement.uuid, Result.success(movement).editorUuidAfterLookup(movement.uuid))
        assertNull(Result.success<CatalogMovement?>(null).editorUuidAfterLookup(movement.uuid))
    }

    @Test
    fun `failed reconciliation permits leaving with the frozen attempt available for another query`() = runBlocking {
        val save = CatalogPreviewSave(this)
        val attempt = "restored"
        val draft = QuickEntryDraft(amount = "12,50")
        val state = QuickEntryState(pending = listOf(PendingEntry(movement.uuid, draft)))
        val submitted = state.catalogSubmissionEntries("unused", editingStored = false)
        val failure = IllegalStateException("Reading Room failed")

        assertEquals(QuickEntrySavePhase.RECONCILING, quickEntrySavePhase(attempt, false, save.retains(attempt)))
        val error = runCatching { save.await(attempt) { throw failure } }.exceptionOrNull()
        assertSame(failure, error)
        val waiting = quickEntrySavePhase(attempt, true, save.retains(attempt))
        assertEquals(QuickEntrySavePhase.READ_FAILED, waiting)
        assertTrue(waiting.frozen)
        assertTrue(waiting.canLeave)
        assertFalse(save.retains(attempt))

        assertEquals(submitted, state.catalogSubmissionEntries("unused", editingStored = false))
        assertTrue(save.await(attempt) {
            matchesSavedMovements(submitted.map { it.id to movement.draft }) { movement }
        })
    }

    @Test
    fun `a known negative result can leave the original capture editable`() = runBlocking {
        val save = CatalogPreviewSave(this)
        val attempt = "restored"
        assertTrue(quickEntrySavePhase(attempt, false, save.retains(attempt)).frozen)
        assertFalse(save.await(attempt) { matchesSavedMovements(listOf(movement.uuid to movement.draft)) { null } })
        assertEquals(QuickEntrySavePhase.EDITABLE, quickEntrySavePhase(null, false, false))
    }

    @Test
    fun `the write survives replacement of the UI waiter and blocks repeated starts`() = runBlocking {
        val save = CatalogPreviewSave(this)
        val commit = CompletableDeferred<Unit>()
        var writes = 0
        assertTrue(save.start("attempt") {
            writes++
            commit.await()
        })
        val oldUi = launch(start = CoroutineStart.UNDISPATCHED) {
            save.await("attempt") { error("Should use the retained write") }
        }
        assertEquals(QuickEntrySavePhase.WRITING, quickEntrySavePhase("attempt", false, save.retains("attempt")))
        assertFalse(QuickEntrySavePhase.WRITING.canLeave)
        oldUi.cancelAndJoin()
        assertFalse(save.start("attempt") { error("Duplicate attempt") })
        assertFalse(save.start("other") { error("A write is already in progress") })
        commit.complete(Unit)

        assertTrue(save.await("attempt") { error("Should use the retained result") })
        assertFalse(save.start("attempt") { error("Already confirmed") })
        assertEquals(1, writes)
    }

    @Test
    fun `failed write returns failure and permits a retry with the same movement identity`() = runBlocking {
        val save = CatalogPreviewSave(this)
        val entries = listOf(movement.uuid to movement.draft)
        assertTrue(save.start("first attempt") { error("Room rolled back") })
        assertFalse(save.await("first attempt") { error("Should use the retained result") })

        val stored = mutableMapOf<String, CatalogMovement>()
        assertTrue(save.start("second attempt") {
            val (uuid, draft) = entries.single()
            stored[uuid] = movement.copy(uuid = uuid, draft = draft)
        })
        assertTrue(save.await("second attempt") { error("Should use the retained result") })
        assertEquals(setOf(movement.uuid), stored.keys)
    }

    @Test
    fun `restoration requires every original UUID to have the exact submitted draft`() = runBlocking {
        val save = CatalogPreviewSave(this)
        val other = movement.copy(uuid = "other")
        val entries = listOf(movement.uuid to movement.draft, other.uuid to other.draft)
        val stored = mutableMapOf<String, CatalogMovement>()
        suspend fun reconcile() = matchesSavedMovements(entries, stored::get)

        assertFalse(save.await("restored", ::reconcile))
        stored[movement.uuid] = movement
        assertFalse(save.await("restored", ::reconcile))
        stored[other.uuid] = other.copy(draft = other.draft.copy(detail = "Conflicto"))
        assertFalse(save.await("restored", ::reconcile))
        stored[other.uuid] = other.copy(deletedAtEpochMillis = 2)
        assertFalse(save.await("restored", ::reconcile))
        stored[other.uuid] = other
        assertTrue(save.await("restored", ::reconcile))
        assertEquals(2, stored.size)
    }

    @Test
    fun `a read failure is reported and keeps the last catalog available`() {
        val failure = IllegalStateException("Read failed")
        val firstFailure = CatalogPreviewRead().completed(Result.failure(failure))
        assertNull(firstFailure.lastValid)
        assertSame(failure, firstFailure.latest?.exceptionOrNull())

        val available = snapshot()
        val failedRefresh = firstFailure.completed(Result.success(available)).completed(Result.failure(failure))
        assertSame(available, failedRefresh.lastValid)
        assertSame(failure, failedRefresh.latest?.exceptionOrNull())

        val refreshed = snapshot()
        val recovered = failedRefresh.completed(Result.success(refreshed))
        assertSame(refreshed, recovered.lastValid)
        assertTrue(recovered.latest?.isSuccess == true)
    }

    private fun snapshot() =
        CatalogPreviewSnapshot(CatalogBackup(LocalDateTime.of(2026, 11, 23, 0, 0), emptyList(), emptyList(), emptyList()), emptyList())

    private val movement = CatalogMovement(
        "movement",
        CatalogMovementDraft(MovementType.EXPENSE, 1_250, Currency.CORDOBA, LocalDate.of(2026, 11, 23)),
        1, 1, SyncStatus.LOCAL_ONLY, null, null,
    )
}
