package com.carlos.miflujo

import android.content.Context
import androidx.room.Room
import com.carlos.miflujo.data.cloud.auth.CloudAccountRepository
import com.carlos.miflujo.data.cloud.auth.DefaultCloudAccountRepository
import com.carlos.miflujo.data.cloud.auth.FirebaseCloudAuthDataSource
import com.carlos.miflujo.data.cloud.firestore.FirestoreCloudMovementRemoteDataSource
import com.carlos.miflujo.data.cloud.firestore.FirestoreCloudAuthorizationChecker
import com.carlos.miflujo.data.cloud.sync.CloudSyncEngine
import com.carlos.miflujo.data.cloud.sync.CloudSyncActivationStore
import com.carlos.miflujo.data.cloud.sync.CloudSyncEnabledStore
import com.carlos.miflujo.data.cloud.sync.CloudSyncPendingChangesProvider
import com.carlos.miflujo.data.cloud.sync.RoomCloudSyncLocalDataSource
import com.carlos.miflujo.data.cloud.sync.RoomCloudSyncPendingChangesProvider
import com.carlos.miflujo.data.cloud.sync.SharedPreferencesCloudSyncActivationStore
import com.carlos.miflujo.data.cloud.sync.SharedPreferencesCloudSyncEnabledStore
import com.carlos.miflujo.data.cloud.sync.CloudSyncMetadataStore
import com.carlos.miflujo.data.cloud.sync.SharedPreferencesCloudSyncMetadataStore
import com.carlos.miflujo.data.local.MiFlujoDatabase
import com.carlos.miflujo.data.local.MIGRATION_1_2
import com.carlos.miflujo.data.local.MIGRATION_2_3
import com.carlos.miflujo.data.repository.MovementRepository
import com.carlos.miflujo.data.repository.RoomMovementRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

interface MiFlujoAppContainer {
    val database: MiFlujoDatabase
    val movementRepository: MovementRepository
    val cloudAccountRepository: CloudAccountRepository
    val cloudSyncEngine: CloudSyncEngine
    val cloudSyncActivationStore: CloudSyncActivationStore
    val cloudSyncEnabledStore: CloudSyncEnabledStore
    val cloudSyncMetadataStore: CloudSyncMetadataStore
    val cloudSyncPendingChangesProvider: CloudSyncPendingChangesProvider
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
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()
    }

    override val movementRepository: MovementRepository by lazy {
        RoomMovementRepository(database.movementDao())
    }

    override val cloudAccountRepository: CloudAccountRepository by lazy {
        DefaultCloudAccountRepository(
            authDataSource = FirebaseCloudAuthDataSource(
                firebaseAuth = FirebaseAuth.getInstance(),
                googleWebClientId = applicationContext.getString(R.string.default_web_client_id),
                googleWebClientIdSource = "generated resource default_web_client_id",
            ),
            authorizationChecker = FirestoreCloudAuthorizationChecker(
                firestore = FirebaseFirestore.getInstance(),
            ),
        )
    }

    override val cloudSyncEngine: CloudSyncEngine by lazy {
        CloudSyncEngine(
            cloudAccountRepository = cloudAccountRepository,
            localDataSource = RoomCloudSyncLocalDataSource(database.movementDao()),
            remoteDataSource = FirestoreCloudMovementRemoteDataSource(
                firestore = FirebaseFirestore.getInstance(),
            ),
        )
    }

    override val cloudSyncActivationStore: CloudSyncActivationStore by lazy {
        SharedPreferencesCloudSyncActivationStore(applicationContext)
    }

    override val cloudSyncEnabledStore: CloudSyncEnabledStore by lazy {
        SharedPreferencesCloudSyncEnabledStore(
            context = applicationContext,
            activationStore = cloudSyncActivationStore,
        )
    }

    override val cloudSyncMetadataStore: CloudSyncMetadataStore by lazy {
        SharedPreferencesCloudSyncMetadataStore(applicationContext)
    }

    override val cloudSyncPendingChangesProvider: CloudSyncPendingChangesProvider by lazy {
        RoomCloudSyncPendingChangesProvider(database.movementDao())
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

    fun cloudSyncEngine(context: Context): CloudSyncEngine {
        return container(context).cloudSyncEngine
    }

    fun cloudSyncActivationStore(context: Context): CloudSyncActivationStore {
        return container(context).cloudSyncActivationStore
    }

    fun cloudSyncEnabledStore(context: Context): CloudSyncEnabledStore {
        return container(context).cloudSyncEnabledStore
    }

    fun cloudSyncMetadataStore(context: Context): CloudSyncMetadataStore {
        return container(context).cloudSyncMetadataStore
    }

    fun cloudSyncPendingChangesProvider(context: Context): CloudSyncPendingChangesProvider {
        return container(context).cloudSyncPendingChangesProvider
    }
}
