package com.carlos.miflujo.data.category

import androidx.sqlite.db.SupportSQLiteDatabase
import com.carlos.miflujo.domain.category.LegacyCategoryCatalog
import com.carlos.miflujo.domain.category.newCatalogUuid

/** Extra SQL protections are installed both after Room's fresh DDL and during migration. */
internal object CategoryCatalogSchema {
    val createStatements = listOf(
        """CREATE TABLE `categories` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `uuid` TEXT NOT NULL, `name` TEXT NOT NULL, `scope` TEXT NOT NULL,
            `reportGroup` TEXT NOT NULL, `iconKey` TEXT NOT NULL,
            `archived` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL,
            `revisionCounter` INTEGER NOT NULL, `revisionActorUuid` TEXT NOT NULL,
            `syncState` TEXT NOT NULL)""",
        """CREATE UNIQUE INDEX `index_categories_uuid` ON `categories` (`uuid`)""",
        """CREATE TABLE `subcategories` (
            `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
            `uuid` TEXT NOT NULL, `categoryId` INTEGER NOT NULL, `name` TEXT NOT NULL,
            `iconKey` TEXT NOT NULL, `archived` INTEGER NOT NULL, `sortOrder` INTEGER NOT NULL,
            `revisionCounter` INTEGER NOT NULL, `revisionActorUuid` TEXT NOT NULL,
            `syncState` TEXT NOT NULL,
            FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE RESTRICT)""",
        """CREATE UNIQUE INDEX `index_subcategories_uuid` ON `subcategories` (`uuid`)""",
        """CREATE INDEX `index_subcategories_categoryId` ON `subcategories` (`categoryId`)""",
        """CREATE TABLE `catalog_installation` (`id` INTEGER NOT NULL, `actorUuid` TEXT NOT NULL, PRIMARY KEY(`id`))""",
        """CREATE UNIQUE INDEX `index_catalog_installation_actorUuid` ON `catalog_installation` (`actorUuid`)""",
    )
    val protectionStatements = listOf(
        """CREATE TRIGGER categories_structure BEFORE UPDATE ON categories
            WHEN NEW.id != OLD.id OR NEW.uuid != OLD.uuid OR NEW.scope != OLD.scope OR NEW.reportGroup != OLD.reportGroup
            BEGIN SELECT RAISE(ABORT, 'Immutable category structure'); END""",
        """CREATE TRIGGER subcategories_structure BEFORE UPDATE ON subcategories
            WHEN NEW.id != OLD.id OR NEW.uuid != OLD.uuid OR NEW.categoryId != OLD.categoryId
            BEGIN SELECT RAISE(ABORT, 'Immutable subcategory structure'); END""",
        """CREATE TRIGGER categories_no_delete BEFORE DELETE ON categories
            BEGIN SELECT RAISE(ABORT, 'Archive categories instead'); END""",
        """CREATE TRIGGER subcategories_no_delete BEFORE DELETE ON subcategories
            BEGIN SELECT RAISE(ABORT, 'Archive subcategories instead'); END""",
        """CREATE TRIGGER categories_metadata_insert BEFORE INSERT ON categories
            WHEN NEW.revisionCounter < 0 OR NEW.syncState NOT IN ('PENDING', 'SYNCED', 'ERROR') OR NEW.archived NOT IN (0,1)
            BEGIN SELECT RAISE(ABORT, 'Invalid category metadata'); END""",
        """CREATE TRIGGER categories_metadata_update BEFORE UPDATE ON categories
            WHEN NEW.revisionCounter < 0 OR NEW.syncState NOT IN ('PENDING', 'SYNCED', 'ERROR') OR NEW.archived NOT IN (0,1)
            BEGIN SELECT RAISE(ABORT, 'Invalid category metadata'); END""",
        """CREATE TRIGGER subcategories_metadata_insert BEFORE INSERT ON subcategories
            WHEN NEW.revisionCounter < 0 OR NEW.syncState NOT IN ('PENDING', 'SYNCED', 'ERROR') OR NEW.archived NOT IN (0,1)
            BEGIN SELECT RAISE(ABORT, 'Invalid subcategory metadata'); END""",
        """CREATE TRIGGER subcategories_metadata_update BEFORE UPDATE ON subcategories
            WHEN NEW.revisionCounter < 0 OR NEW.syncState NOT IN ('PENDING', 'SYNCED', 'ERROR') OR NEW.archived NOT IN (0,1)
            BEGIN SELECT RAISE(ABORT, 'Invalid subcategory metadata'); END""",
        """CREATE TRIGGER installation_singleton BEFORE INSERT ON catalog_installation WHEN NEW.id != 1
            BEGIN SELECT RAISE(ABORT, 'Installation is a singleton'); END""",
        """CREATE TRIGGER installation_no_update BEFORE UPDATE ON catalog_installation
            BEGIN SELECT RAISE(ABORT, 'Immutable installation'); END""",
        """CREATE TRIGGER installation_no_delete BEFORE DELETE ON catalog_installation
            BEGIN SELECT RAISE(ABORT, 'Immutable installation'); END""",
    )

    fun createAndSeed(db: SupportSQLiteDatabase) {
        createStatements.forEach { db.execSQL(it) }
        initialize(db)
    }
    fun initialize(db: SupportSQLiteDatabase) {
        protectionStatements.forEach { db.execSQL(it) }
        db.execSQL("INSERT INTO catalog_installation (id, actorUuid) VALUES (1, ?)", arrayOf(newCatalogUuid()))
        val revision = LegacyCategoryCatalog.seedRevision
        LegacyCategoryCatalog.categories.forEach { c ->
            db.execSQL("INSERT INTO categories (id, uuid, name, scope, reportGroup, iconKey, archived, sortOrder, revisionCounter, revisionActorUuid, syncState) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(c.id, c.uuid, c.name, c.scope.name, c.reportGroup.name, c.iconKey,
                    if (c.archived) 1 else 0, c.sortOrder, revision.counter, revision.actorUuid, "PENDING"))
        }
        LegacyCategoryCatalog.subcategories.forEach { s ->
            db.execSQL("INSERT INTO subcategories (id, uuid, categoryId, name, iconKey, archived, sortOrder, revisionCounter, revisionActorUuid, syncState) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                arrayOf<Any?>(s.id, s.uuid, s.categoryId, s.name, s.iconKey,
                    if (s.archived) 1 else 0, s.sortOrder, revision.counter, revision.actorUuid, "PENDING"))
        }
    }
}
