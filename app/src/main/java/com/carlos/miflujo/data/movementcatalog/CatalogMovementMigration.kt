package com.carlos.miflujo.data.movementcatalog

import android.database.Cursor
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteStatement
import com.carlos.miflujo.data.category.CategoryCatalogSchema
import com.carlos.miflujo.domain.category.LegacyCategoryCatalog
import com.carlos.miflujo.domain.model.Currency
import com.carlos.miflujo.domain.model.MovementCategory
import com.carlos.miflujo.domain.model.MovementSubcategory
import com.carlos.miflujo.domain.model.MovementType
import com.carlos.miflujo.domain.model.SyncStatus
import java.time.DateTimeException
import java.time.LocalDate
import java.util.UUID

/** The Room upgrade transaction owns all DDL and data changes. This migration is candidate-only. */
internal val CATALOG_MOVEMENT_MIGRATION_3_4 = Migration(3, 4) { db ->
    val legacySequence = db.query("SELECT seq FROM sqlite_sequence WHERE name = 'movements'").use {
        if (it.moveToFirst()) it.getLong(0) else 0L
    }

    CategoryCatalogSchema.createAndSeed(db)
    db.execSQL(
        """CREATE TABLE movements_catalog_new (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, uuid TEXT NOT NULL, type TEXT NOT NULL,
            amount_minor INTEGER NOT NULL, currency TEXT NOT NULL, date_epoch_day INTEGER NOT NULL,
            category_id INTEGER, subcategory_id INTEGER, detail TEXT, created_at_epoch_millis INTEGER NOT NULL,
            updated_at_epoch_millis INTEGER NOT NULL, sync_status TEXT NOT NULL DEFAULT 'LOCAL_ONLY',
            last_synced_at_epoch_millis INTEGER, deleted_at_epoch_millis INTEGER,
            FOREIGN KEY(category_id) REFERENCES categories(id) ON UPDATE NO ACTION ON DELETE RESTRICT,
            FOREIGN KEY(subcategory_id) REFERENCES subcategories(id) ON UPDATE NO ACTION ON DELETE RESTRICT)""",
    )
    db.compileStatement(
        """INSERT INTO movements_catalog_new (id, uuid, type, amount_minor, currency, date_epoch_day, category_id,
            subcategory_id, detail, created_at_epoch_millis, updated_at_epoch_millis, sync_status,
            last_synced_at_epoch_millis, deleted_at_epoch_millis)
            SELECT id, uuid, type, amount_minor, currency, date_epoch_day, ?, ?, detail, created_at_epoch_millis,
                updated_at_epoch_millis, sync_status, last_synced_at_epoch_millis, deleted_at_epoch_millis
            FROM movements WHERE id = ?""",
    ).use { insert ->
        db.query("SELECT * FROM movements ORDER BY id").use { rows ->
            while (rows.moveToNext()) copyLegacyMovement(rows, insert)
        }
    }
    db.query("SELECT (SELECT count(*) FROM movements) = (SELECT count(*) FROM movements_catalog_new)").use {
        check(it.moveToFirst() && it.getInt(0) == 1) { "Unexpected movement count after migration copy" }
    }
    db.execSQL("DROP TABLE movements")
    db.execSQL("ALTER TABLE movements_catalog_new RENAME TO movements")
    if (legacySequence > 0L) {
        db.execSQL("UPDATE sqlite_sequence SET seq = max(seq, ?) WHERE name = 'movements'", arrayOf(legacySequence))
        db.execSQL(
            "INSERT INTO sqlite_sequence (name, seq) SELECT 'movements', ? WHERE NOT EXISTS " +
                "(SELECT 1 FROM sqlite_sequence WHERE name = 'movements')", arrayOf(legacySequence),
        )
    }
    db.execSQL("CREATE UNIQUE INDEX index_movements_uuid ON movements (uuid)")
    db.execSQL("CREATE INDEX index_movements_category_id ON movements (category_id)")
    db.execSQL("CREATE INDEX index_movements_subcategory_id ON movements (subcategory_id)")
    installMovementRelationshipGuards(db)
    db.query("PRAGMA foreign_key_check").use { check(!it.moveToFirst()) { "Unexpected foreign key violation after migration" } }
}

private fun Cursor.column(name: String) = getColumnIndexOrThrow(name)

private fun copyLegacyMovement(row: Cursor, insert: SupportSQLiteStatement) {
    val id = row.getLong(row.column("id"))
    val selection = try {
        for (column in listOf("amount_minor", "date_epoch_day", "created_at_epoch_millis", "updated_at_epoch_millis")) {
            require(row.getType(row.column(column)) == Cursor.FIELD_TYPE_INTEGER) { "Invalid storage type: $column" }
        }
        for (column in listOf("last_synced_at_epoch_millis", "deleted_at_epoch_millis")) {
            val index = row.column(column)
            require(row.isNull(index) || row.getType(index) == Cursor.FIELD_TYPE_INTEGER) { "Invalid storage type: $column" }
        }
        for (column in listOf("uuid", "type", "currency", "sync_status", "category")) {
            require(row.getType(row.column(column)) == Cursor.FIELD_TYPE_STRING) { "Invalid storage type: $column" }
        }
        for (column in listOf("subcategory", "detail")) {
            val index = row.column(column)
            require(row.isNull(index) || row.getType(index) == Cursor.FIELD_TYPE_STRING) { "Invalid storage type: $column" }
        }
        val uuid = row.getString(row.column("uuid"))
        require(UUID.fromString(uuid).toString() == uuid) { "Noncanonical movement UUID" }
        require(row.getLong(row.column("amount_minor")) > 0L) { "Nonpositive movement amount" }
        Currency.valueOf(row.getString(row.column("currency")))
        SyncStatus.valueOf(row.getString(row.column("sync_status")))
        LocalDate.ofEpochDay(row.getLong(row.column("date_epoch_day")))
        val type = MovementType.valueOf(row.getString(row.column("type")))
        val category = MovementCategory.valueOf(row.getString(row.column("category")))
        val child = row.getString(row.column("subcategory"))?.let(MovementSubcategory::valueOf)
        LegacyCategoryCatalog.selection(category, child).also { resolved ->
            val parent = LegacyCategoryCatalog.categories.single { it.id == resolved.categoryId }
            require(parent.scope.supports(type)) { "Incompatible legacy category scope" }
        }
    } catch (exception: IllegalArgumentException) {
        throw IllegalStateException("Cannot migrate legacy movement at local ID $id", exception)
    } catch (exception: DateTimeException) {
        throw IllegalStateException("Cannot migrate legacy movement date at local ID $id", exception)
    }
    insert.bindLong(1, requireNotNull(selection.categoryId))
    val childId = selection.subcategoryId
    if (childId == null) insert.bindNull(2) else insert.bindLong(2, childId)
    insert.bindLong(3, id)
    insert.executeInsert()
}
