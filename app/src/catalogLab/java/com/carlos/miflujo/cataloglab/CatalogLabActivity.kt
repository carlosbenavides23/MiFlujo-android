package com.carlos.miflujo.cataloglab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.lifecycleScope
import androidx.room.Room
import com.carlos.miflujo.data.cataloglab.CatalogLabSession
import com.carlos.miflujo.data.cloud.catalog.EmulatorCatalogClient
import com.carlos.miflujo.data.cloud.catalog.EmulatorMovementAccount
import com.carlos.miflujo.data.cloud.catalog.awaitCatalogTask
import com.carlos.miflujo.data.movementcatalog.CatalogMovementDatabase
import com.carlos.miflujo.ui.theme.MiFlujoTheme
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CatalogLabActivity : ComponentActivity() {
    private var client: EmulatorCatalogClient? = null
    private var account: EmulatorMovementAccount? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val database = Room.databaseBuilder(applicationContext, CatalogMovementDatabase::class.java, "catalog-lab-local.db")
            .addCallback(CatalogMovementDatabase.initializer)
            .build()
        val local = CatalogLabSession.local(database)
        lifecycleScope.launch {
            try {
                awaitCancellation()
            } finally {
                withContext(NonCancellable) {
                    try { disconnect() } finally { database.close() }
                }
            }
        }
        setContent {
            MiFlujoTheme {
                CatalogLabScreen(local, onConnect = ::connect, onDisconnect = ::disconnect)
            }
        }
    }

    private suspend fun connect(host: String): CatalogLabSession {
        check(client == null && account == null)
        val candidate = EmulatorCatalogClient.open(applicationContext, host)
        try {
            candidate.auth.signInWithEmailAndPassword(
                "catalog-android@example.test", "catalog-emulator-only",
            ).awaitCatalogTask()
            val connected = candidate.coordinatedSync(applicationContext)
            account = connected
            client = candidate
            return CatalogLabSession.account(connected)
        } catch (error: Exception) {
            withContext(NonCancellable) { candidate.close() }
            throw error
        }
    }

    private suspend fun disconnect() {
        val previous = client
        client = null
        account?.close()
        account = null
        previous?.close()
    }
}
