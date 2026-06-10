package com.carlos.miflujo

import android.content.Context
import androidx.room.Room
import com.carlos.miflujo.data.cloud.auth.CloudAccountRepository
import com.carlos.miflujo.data.cloud.auth.DefaultCloudAccountRepository
import com.carlos.miflujo.data.cloud.auth.FirebaseCloudAuthDataSource
import com.carlos.miflujo.data.cloud.firestore.FirestoreCloudAuthorizationChecker
import com.carlos.miflujo.data.local.MiFlujoDatabase
import com.carlos.miflujo.data.local.MIGRATION_1_2
import com.carlos.miflujo.data.repository.MovementRepository
import com.carlos.miflujo.data.repository.RoomMovementRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

interface MiFlujoAppContainer {
    val database: MiFlujoDatabase
    val movementRepository: MovementRepository
    val cloudAccountRepository: CloudAccountRepository
}

class DefaultMiFlujoAppContainer(
    context: Context,
) : MiFlujoAppContainer {
    private val applicationContext = context.applicationContext

    override val database: MiFlujoDatabase by lazy {
        Room.databaseBuilder(
            context = applicationContext,
            klass = MiFlujoDatabase::class.java,
            name = DATABASE_NAME,
        )
            .addMigrations(MIGRATION_1_2)
            .build()
    }

    override val movementRepository: MovementRepository by lazy {
        RoomMovementRepository(database.movementDao())
    }

    override val cloudAccountRepository: CloudAccountRepository by lazy {
        DefaultCloudAccountRepository(
            authDataSource = FirebaseCloudAuthDataSource(
                firebaseAuth = FirebaseAuth.getInstance(),
                googleWebClientId = applicationContext.getString(R.string.google_web_client_id),
            ),
            authorizationChecker = FirestoreCloudAuthorizationChecker(
                firestore = FirebaseFirestore.getInstance(),
            ),
        )
    }

    private companion object {
        const val DATABASE_NAME = "miflujo.db"
    }
}

object MiFlujoAppProvider {
    @Volatile
    private var appContainer: MiFlujoAppContainer? = null

    fun container(context: Context): MiFlujoAppContainer {
        return appContainer ?: synchronized(this) {
            appContainer ?: DefaultMiFlujoAppContainer(context).also { appContainer = it }
        }
    }

    fun movementRepository(context: Context): MovementRepository {
        return container(context).movementRepository
    }

    fun cloudAccountRepository(context: Context): CloudAccountRepository {
        return container(context).cloudAccountRepository
    }
}
