package com.carlos.miflujo.data.category

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

// The backup sandbox and the per-UID movement store use their own version series.
internal val CATALOG_ICON_COLOR_4_5 = Migration(4, 5, ::addIconColors)
internal val CATALOG_ICON_COLOR_1_2 = Migration(1, 2, ::addIconColors)

private fun addIconColors(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE categories ADD COLUMN iconColor TEXT")
    db.execSQL("ALTER TABLE subcategories ADD COLUMN iconColor TEXT")
}
