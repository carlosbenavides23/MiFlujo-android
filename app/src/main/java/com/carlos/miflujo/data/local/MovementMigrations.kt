package com.carlos.miflujo.data.local

import androidx.room.migration.Migration
import com.carlos.miflujo.domain.model.generateMovementUuid

val MIGRATION_1_2 = Migration(1, 2) { database ->
    val generatedUuids = mutableSetOf<String>()

    database.execSQL(
        """
        CREATE TABLE IF NOT EXISTS `movements_new` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `uuid` TEXT NOT NULL,
            `type` TEXT NOT NULL,
            `amount_minor` INTEGER NOT NULL,
            `currency` TEXT NOT NULL,
            `date_epoch_day` INTEGER NOT NULL,
            `category` TEXT NOT NULL,
            `subcategory` TEXT,
            `detail` TEXT,
            `created_at_epoch_millis` INTEGER NOT NULL,
            `updated_at_epoch_millis` INTEGER NOT NULL
        )
        """.trimIndent(),
    )

    database.compileStatement(
        """
        INSERT INTO `movements_new` (
            `id`,
            `uuid`,
            `type`,
            `amount_minor`,
            `currency`,
            `date_epoch_day`,
            `category`,
            `subcategory`,
            `detail`,
            `created_at_epoch_millis`,
            `updated_at_epoch_millis`
        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """.trimIndent(),
    ).use { insertStatement ->
        database.query(
            """
            SELECT
                `id`,
                `type`,
                `amount_minor`,
                `currency`,
                `date_epoch_day`,
                `category`,
                `subcategory`,
                `detail`,
                `created_at_epoch_millis`,
                `updated_at_epoch_millis`
            FROM `movements`
            ORDER BY `id`
            """.trimIndent(),
        ).use { cursor ->
            while (cursor.moveToNext()) {
                insertStatement.bindLong(1, cursor.getLong(0))
                insertStatement.bindString(2, generateUniqueMovementUuid(generatedUuids))
                insertStatement.bindString(3, cursor.getString(1))
                insertStatement.bindLong(4, cursor.getLong(2))
                insertStatement.bindString(5, cursor.getString(3))
                insertStatement.bindLong(6, cursor.getLong(4))
                insertStatement.bindString(7, cursor.getString(5))
                insertStatement.bindNullableString(8, cursor, 6)
                insertStatement.bindNullableString(9, cursor, 7)
                insertStatement.bindLong(10, cursor.getLong(8))
                insertStatement.bindLong(11, cursor.getLong(9))
                insertStatement.executeInsert()
                insertStatement.clearBindings()
            }
        }
    }

    database.execSQL("DROP TABLE `movements`")
    database.execSQL("ALTER TABLE `movements_new` RENAME TO `movements`")
    database.execSQL(
        "CREATE UNIQUE INDEX IF NOT EXISTS `index_movements_uuid` ON `movements` (`uuid`)",
    )
}

private fun generateUniqueMovementUuid(generatedUuids: MutableSet<String>): String {
    while (true) {
        val uuid = generateMovementUuid()
        if (generatedUuids.add(uuid)) return uuid
    }
}

private fun androidx.sqlite.db.SupportSQLiteStatement.bindNullableString(
    bindIndex: Int,
    cursor: android.database.Cursor,
    cursorIndex: Int,
) {
    if (cursor.isNull(cursorIndex)) {
        bindNull(bindIndex)
    } else {
        bindString(bindIndex, cursor.getString(cursorIndex))
    }
}
