package com.carlos.miflujo.catalogpreview

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import androidx.room.withTransaction
import com.carlos.miflujo.cataloglab.categoriesByRecentUsage
import com.carlos.miflujo.data.cataloglab.CatalogLabSession
import com.carlos.miflujo.data.movementcatalog.CatalogMovementDatabase
import com.carlos.miflujo.domain.backup.BackupCategory
import com.carlos.miflujo.domain.backup.CatalogBackup
import com.carlos.miflujo.domain.model.CatalogMovement
import com.carlos.miflujo.domain.model.CatalogMovementDraft
import com.carlos.miflujo.ui.theme.MiFlujoTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class CatalogPreviewActivity : ComponentActivity() {
    private val model: CatalogPreviewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { MiFlujoTheme { CatalogPreviewApp(model) } }
    }
}

internal data class CatalogPreviewSnapshot(
    val catalog: CatalogBackup,
    val categoriesByUsage: List<BackupCategory>,
)

internal data class CatalogPreviewRead(
    val lastValid: CatalogPreviewSnapshot? = null,
    val latest: Result<CatalogPreviewSnapshot>? = null,
) {
    fun completed(result: Result<CatalogPreviewSnapshot>) =
        CatalogPreviewRead(result.getOrNull() ?: lastValid, result)
}

// Main-thread callers start the write once. A cancelled UI waiter does not own the write.
internal class CatalogPreviewSave(private val scope: CoroutineScope) {
    private var active: Pair<String, Deferred<Boolean>>? = null

    fun start(id: String, write: suspend () -> Unit): Boolean {
        if (active?.let { it.first == id || it.second.isActive } == true) return false
        val result = scope.async(start = CoroutineStart.LAZY) {
            try {
                write()
                true
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (_: Exception) {
                false
            }
        }
        active = id to result
        result.start()
        return true
    }

    suspend fun await(id: String, reconcile: suspend () -> Boolean): Boolean =
        active?.takeIf { it.first == id }?.second?.await() ?: reconcile()
}

internal class CatalogPreviewModel(application: Application) : AndroidViewModel(application) {
    private val database = Room.databaseBuilder(
        application, CatalogMovementDatabase::class.java, "catalog-preview.db",
    ).addCallback(CatalogMovementDatabase.initializer).build()
    private val session = CatalogLabSession.local(database)
    val categories get() = session.categories
    val quickEntrySave = CatalogPreviewSave(viewModelScope)
    private var editedMovement: CatalogMovement? = null

    private val refreshes = MutableStateFlow(0)
    private var lastRead = CatalogPreviewRead()
    val snapshot = database.invalidationTracker.createFlow("movements", "categories", "subcategories")
        .combine(refreshes) { _, _ -> Unit }
        .map {
            val result = try {
                val catalog = session.snapshot()
                val categoriesByUsage = withContext(Dispatchers.Default) { catalog.categoriesByRecentUsage() }
                Result.success(CatalogPreviewSnapshot(catalog, categoriesByUsage))
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                Result.failure(error)
            }
            lastRead.completed(result).also { lastRead = it }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), lastRead)

    fun refresh() {
        refreshes.update { it + 1 }
    }

    suspend fun loadEditor(uuid: String): CatalogMovement? =
        editedMovement?.takeIf { it.uuid == uuid }
            ?: session.movements.find(uuid)?.also { editedMovement = it }

    fun closeEditor() {
        editedMovement = null
    }

    suspend fun wasSaved(entries: List<Pair<String, CatalogMovementDraft>>): Boolean = database.withTransaction {
        matchesSavedMovements(entries, session.movements::find)
    }

    suspend fun saveNew(entries: List<Pair<String, CatalogMovementDraft>>) {
        database.withTransaction {
            for ((uuid, draft) in entries) {
                val saved = session.movements.find(uuid)
                if (saved == null) session.movements.create(draft, uuid)
                else check(saved.draft == draft)
            }
        }
    }

    suspend fun edit(expected: CatalogMovement, draft: CatalogMovementDraft) {
        session.movements.edit(expected, draft)
    }

    suspend fun delete(uuid: String) {
        check(database.movements().deleteLocal(uuid) == 1)
    }

    override fun onCleared() {
        database.close()
    }
}

internal suspend fun matchesSavedMovements(
    entries: List<Pair<String, CatalogMovementDraft>>,
    find: suspend (String) -> CatalogMovement?,
): Boolean = entries.all { (uuid, draft) ->
    val saved = find(uuid)
    saved != null && saved.deletedAtEpochMillis == null && saved.draft == draft
}
