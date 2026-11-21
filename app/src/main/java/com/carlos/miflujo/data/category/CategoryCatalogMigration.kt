package com.carlos.miflujo.data.category

import androidx.room.migration.Migration

/** Candidate only. Does not touch movements and is NOT registered in MiFlujoDatabase. */
val CATEGORY_CATALOG_MIGRATION_3_4 = Migration(3, 4) { db ->
    CategoryCatalogSchema.createAndSeed(db)
}
