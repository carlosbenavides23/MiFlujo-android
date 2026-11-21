package com.carlos.miflujo.data.category

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase

/** Experimental catalog ONLY. Not injected into MiFlujoAppContainer or used by movements. */
@Database(
    entities = [CategoryEntity::class, SubcategoryEntity::class, CatalogInstallationEntity::class],
    version = 4,
    exportSchema = true,
)
abstract class CategoryCatalogDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao

    companion object {
        val initializer = object : Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) { CategoryCatalogSchema.initialize(db) }
        }

        /** Fixed distinct name prevents accidentally opening the financial database. */
        fun openIsolated(context: Context): CategoryCatalogDatabase = Room.databaseBuilder(
            context.applicationContext, CategoryCatalogDatabase::class.java, "category-catalog-experiment.db",
        ).addCallback(initializer).addMigrations(CATEGORY_CATALOG_MIGRATION_3_4).build()
    }
}
