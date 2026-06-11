package com.carlos.miflujo.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.carlos.miflujo.domain.model.generateMovementUuid
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MiFlujoDatabaseMigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MiFlujoDatabase::class.java,
    )

    @Test
    fun migrationFrom1To2PreservesRowsAndAddsUniqueUUIDs() {
        helper.createDatabase(TEST_DATABASE, 1).apply {
            execSQL(
                """
                INSERT INTO `movements` (
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
                ) VALUES (
                    7,
                    'INCOME',
                    500000,
                    'CORDOBA',
                    20610,
                    'GENERAL_INCOME',
                    NULL,
                    NULL,
                    1780734600000,
                    1780734600000
                )
                """.trimIndent(),
            )
            execSQL(
                """
                INSERT INTO `movements` (
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
                ) VALUES (
                    11,
                    'EXPENSE',
                    180050,
                    'DOLLAR',
                    20611,
                    'FIXED_COST',
                    'ELECTRICITY',
                    'Pago de luz',
                    1780821000000,
                    1780824600000
                )
                """.trimIndent(),
            )
            close()
        }

        val migratedDatabase = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            2,
            true,
            MIGRATION_1_2,
        )

        migratedDatabase.query(
            """
            SELECT
                `id`,
                `uuid`,
                `type`,
                `amount_minor`,
                `currency`,
                `category`,
                `subcategory`,
                `detail`
            FROM `movements`
            ORDER BY `id`
            """.trimIndent(),
        ).use { cursor ->
            assertEquals(2, cursor.count)

            cursor.moveToFirst()
            assertEquals(7L, cursor.getLong(0))
            val firstUuid = cursor.getString(1)
            assertEquals(firstUuid, UUID.fromString(firstUuid).toString())
            assertEquals("INCOME", cursor.getString(2))
            assertEquals(500_000L, cursor.getLong(3))
            assertEquals("CORDOBA", cursor.getString(4))
            assertEquals("GENERAL_INCOME", cursor.getString(5))
            assertEquals(true, cursor.isNull(6))
            assertEquals(true, cursor.isNull(7))

            cursor.moveToNext()
            assertEquals(11L, cursor.getLong(0))
            val secondUuid = cursor.getString(1)
            assertEquals(secondUuid, UUID.fromString(secondUuid).toString())
            assertNotEquals(firstUuid, secondUuid)
            assertEquals("EXPENSE", cursor.getString(2))
            assertEquals(180_050L, cursor.getLong(3))
            assertEquals("DOLLAR", cursor.getString(4))
            assertEquals("FIXED_COST", cursor.getString(5))
            assertEquals("ELECTRICITY", cursor.getString(6))
            assertEquals("Pago de luz", cursor.getString(7))
        }

        val duplicateUuid = generateMovementUuid()
        migratedDatabase.execSQL(
            """
            INSERT INTO `movements` (
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
            ) VALUES (?, 'EXPENSE', 100, 'CORDOBA', 20612, 'OTHER', NULL, NULL, 1, 1)
            """.trimIndent(),
            arrayOf(duplicateUuid),
        )

        try {
            migratedDatabase.execSQL(
                """
                INSERT INTO `movements` (
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
                ) VALUES (?, 'EXPENSE', 200, 'CORDOBA', 20613, 'OTHER', NULL, NULL, 2, 2)
                """.trimIndent(),
                arrayOf(duplicateUuid),
            )
            throw AssertionError("Expected duplicate UUID insertion to fail.")
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
            // Expected: the UUID index is unique.
        }
    }

    @Test
    fun migrationFrom2To3AddsLocalSyncMetadataWithSafeDefaults() {
        helper.createDatabase(TEST_DATABASE, 2).apply {
            execSQL(
                """
                INSERT INTO `movements` (
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
                ) VALUES (
                    7,
                    '3f83ad74-77f1-4625-a525-66d860a86e76',
                    'INCOME',
                    500000,
                    'CORDOBA',
                    20610,
                    'GENERAL_INCOME',
                    NULL,
                    NULL,
                    1780734600000,
                    1780734600000
                )
                """.trimIndent(),
            )
            close()
        }

        val migratedDatabase = helper.runMigrationsAndValidate(
            TEST_DATABASE,
            3,
            true,
            MIGRATION_2_3,
        )

        migratedDatabase.query(
            """
            SELECT
                `id`,
                `sync_status`,
                `last_synced_at_epoch_millis`,
                `deleted_at_epoch_millis`
            FROM `movements`
            """.trimIndent(),
        ).use { cursor ->
            assertEquals(1, cursor.count)
            cursor.moveToFirst()
            assertEquals(7L, cursor.getLong(0))
            assertEquals("LOCAL_ONLY", cursor.getString(1))
            assertEquals(true, cursor.isNull(2))
            assertEquals(true, cursor.isNull(3))
        }
    }

    private companion object {
        const val TEST_DATABASE = "miflujo-migration-test"
    }
}
