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
import kotlinx.coroutines.Dispatchers
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

internal class CatalogPreviewModel(application: Application) : AndroidViewModel(application) {
    private val database = Room.databaseBuilder(
        application, CatalogMovementDatabase::class.java, "catalog-preview.db",
    ).addCallback(CatalogMovementDatabase.initializer).build()
    private val session = CatalogLabSession.local(database)
    val categories get() = session.categories

    private val refreshes = MutableStateFlow(0)
    val snapshot = database.invalidationTracker.createFlow("movements", "categories", "subcategories")
        .combine(refreshes) { _, _ -> Unit }
        .map<_, Result<CatalogPreviewSnapshot>?> {
            try {
                val catalog = session.snapshot()
                val categoriesByUsage = withContext(Dispatchers.Default) { catalog.categoriesByRecentUsage() }
                Result.success(CatalogPreviewSnapshot(catalog, categoriesByUsage))
            } catch (cancel: CancellationException) {
                throw cancel
            } catch (error: Exception) {
                Result.failure(error)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refresh() {
        refreshes.update { it + 1 }
    }

    suspend fun find(uuid: String): CatalogMovement? = session.movements.find(uuid)

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
