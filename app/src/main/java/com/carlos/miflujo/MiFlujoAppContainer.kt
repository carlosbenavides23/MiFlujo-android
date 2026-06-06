package com.carlos.miflujo

import android.content.Context
import androidx.room.Room
import com.carlos.miflujo.data.local.MiFlujoDatabase
import com.carlos.miflujo.data.local.MIGRATION_1_2
import com.carlos.miflujo.data.repository.MovementRepository
import com.carlos.miflujo.data.repository.RoomMovementRepository

interface MiFlujoAppContainer {
    val database: MiFlujoDatabase
    val movementRepository: MovementRepository
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
}
