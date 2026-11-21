package com.carlos.miflujo.cataloglab

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.room.Room
import com.carlos.miflujo.data.cataloglab.CatalogLabSession
import com.carlos.miflujo.data.cloud.catalog.EmulatorCatalogClient
import com.carlos.miflujo.data.cloud.catalog.EmulatorMovementAccount
import com.carlos.miflujo.data.cloud.catalog.awaitCatalogTask
import com.carlos.miflujo.data.movementcatalog.CatalogMovementDatabase
import com.carlos.miflujo.domain.model.CatalogMovement
import com.carlos.miflujo.ui.theme.MiFlujoTheme
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CatalogLabActivity : ComponentActivity() {
    private val model: CatalogLabModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MiFlujoTheme { CatalogLabScreen(model) }
        }
    }
}

internal class CatalogLabModel(
    application: Application,
    private val database: CatalogMovementDatabase,
) : AndroidViewModel(application) {
    constructor(application: Application) : this(
        application,
        Room.databaseBuilder(application, CatalogMovementDatabase::class.java, "catalog-lab-local.db")
            .addCallback(CatalogMovementDatabase.initializer).build(),
    )
    private val local = CatalogLabSession.local(database)
    private var client: EmulatorCatalogClient? = null
    private var account: EmulatorMovementAccount? = null
    var session by mutableStateOf(local)
        private set
    var editingMovement by mutableStateOf<CatalogMovement?>(null)

    override fun onCleared() {
        val closing = client
        account?.close()
        database.close()
        if (closing != null) CoroutineScope(Dispatchers.Main).launch { closing.close() }
    }

    suspend fun connect(host: String) {
        check(client == null && account == null)
        val candidate = EmulatorCatalogClient.open(getApplication<Application>(), host)
        try {
            candidate.auth.signInWithEmailAndPassword(
                "catalog-android@example.test", "catalog-emulator-only",
            ).awaitCatalogTask()
            val connected = candidate.coordinatedSync(getApplication<Application>())
            account = connected
            client = candidate
            session = CatalogLabSession.account(connected)
        } catch (error: Exception) {
            withContext(NonCancellable) { candidate.close() }
            throw error
        }
    }

    suspend fun disconnect() {
        session = local
        val previous = client
        client = null
        account?.close()
        account = null
        previous?.close()
    }
}
