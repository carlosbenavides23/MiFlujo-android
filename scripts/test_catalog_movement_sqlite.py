"""Exercise the candidate's SQL on host SQLite. Android tests execute the full Room path."""
import json
import re
import sqlite3
import unittest

from test_catalog_sqlite import BASE, PROTECT, ROOT, install

MIGRATION = (BASE / "data/movementcatalog/CatalogMovementMigration.kt").read_text()
CREATE, COPY = re.findall(r'"""(.*?)"""', MIGRATION, re.S)
INDICES = re.findall(r'db\.execSQL\("(CREATE (?:UNIQUE )?INDEX [^"]+)"\)', MIGRATION)
CANDIDATE = (BASE / "data/movementcatalog/CatalogMovementDatabase.kt").read_text()
GUARD = re.search(
    r'"""(CREATE TRIGGER movement_catalog_relationship_.*?)"""',
    CANDIDATE,
    re.S,
).group(1)
VISIBLE, SYNC_EVIDENCE = re.findall(r'@Query\("""(.*?)"""\)', CANDIDATE, re.S)
CLEAR = re.search(r'@Query\("(DELETE FROM movements)"\)', CANDIDATE).group(1)
RESTORE_HELPER = (BASE / "data/backup/BackupCatalogRows.kt").read_text()
DROP_CATEGORY, DROP_SUBCATEGORY, CLEAR_SUBCATEGORIES, CLEAR_CATEGORIES = re.findall(
    r'sql\.execSQL\("((?:DROP TRIGGER|DELETE FROM) [a-z_]+)"\)', RESTORE_HELPER,
)
V3 = json.loads((ROOT / "app/schemas/com.carlos.miflujo.data.local.MiFlujoDatabase/3.json").read_text())
MOVEMENT = V3["database"]["entities"][0]


class CatalogMovementSqliteTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.db.execute("PRAGMA foreign_keys=ON")
        install(self.db)
        self.db.execute(MOVEMENT["createSql"].replace("${TABLE_NAME}", "movements"))
        for index in MOVEMENT["indices"]:
            self.db.execute(index["createSql"].replace("${TABLE_NAME}", "movements"))
        self.row = (
            8, "7174756a-ab60-4a49-b269-a753df83e875", "EXPENSE", 2**63 - 1, "DOLLAR", -100,
            "FIXED_COST", "WATER", "Detalle \n 🧾", -500, 900, "PENDING_DELETE", 800, 900,
        )
        self.db.execute("INSERT INTO movements VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", self.row)
        self.db.commit()

    def tearDown(self):
        self.db.close()

    def copy(self):
        self.db.execute(CREATE)
        self.db.execute(COPY, (2, 1, 8))
        self.db.execute("DROP TABLE movements")
        self.db.execute("ALTER TABLE movements_catalog_new RENAME TO movements")
        for statement in INDICES:
            self.db.execute(statement)
        for operation in ("INSERT", "UPDATE"):
            self.db.execute(GUARD.replace("${operation.lowercase()}", operation.lower()).replace("$operation", operation))

    def test_copies_financial_values_and_tombstone_with_original_types(self):
        with self.db:
            self.db.execute("BEGIN")
            self.copy()
        row = self.db.execute("SELECT * FROM movements").fetchone()
        self.assertEqual(self.row[:6] + (2, 1) + self.row[8:], row)
        self.assertEqual([], self.db.execute("PRAGMA foreign_key_check").fetchall())

    def test_guards_reject_mismatches_and_allow_empty_or_parent_only_selection(self):
        with self.db:
            self.db.execute("BEGIN")
            self.copy()
        for category, child, movement_type in (
            (None, 1, "EXPENSE"), (3, 1, "EXPENSE"), (289, None, "EXPENSE"),
            (2, 29, "EXPENSE"), (1, None, "EXPENSE"), (None, None, "UNKNOWN"),
        ):
            with self.subTest(category=category, child=child, type=movement_type):
                with self.assertRaises(sqlite3.IntegrityError):
                    self.db.execute(
                        "UPDATE movements SET category_id=?, subcategory_id=?, type=?",
                        (category, child, movement_type),
                    )
                with self.assertRaises(sqlite3.IntegrityError):
                    self.db.execute(
                        "INSERT INTO movements SELECT 9, uuid || 'x', ?, amount_minor, currency, date_epoch_day, "
                        "?, ?, detail, created_at_epoch_millis, updated_at_epoch_millis, sync_status, "
                        "last_synced_at_epoch_millis, deleted_at_epoch_millis FROM movements",
                        (movement_type, category, child),
                    )
        for category in (2, None):
            self.db.execute("UPDATE movements SET category_id=?, subcategory_id=NULL", (category,))
            self.assertEqual((category, None), self.db.execute(
                "SELECT category_id, subcategory_id FROM movements",
            ).fetchone())

    def test_sql_rollback_reinstates_legacy_table_after_rebuild(self):
        with self.assertRaises(sqlite3.IntegrityError), self.db:
            self.db.execute("BEGIN")
            self.copy()
            self.db.execute("UPDATE movements SET category_id=1")
        self.assertEqual(self.row, self.db.execute("SELECT * FROM movements").fetchone())
        self.assertEqual([], self.db.execute(
            "SELECT name FROM sqlite_master WHERE name LIKE 'movement_catalog_%' OR name='movements_catalog_new'",
        ).fetchall())

    def test_backup_excludes_tombstones_and_restore_checks_for_sync_evidence(self):
        with self.db:
            self.db.execute("BEGIN")
            self.copy()
        for status, synced, deleted, visible, evidence in (
            ("LOCAL_ONLY", None, None, True, False),
            ("PENDING_UPLOAD", None, None, True, True),
            ("SYNCED", 800, None, True, True),
            ("LOCAL_ONLY", 800, None, True, True),
            ("LOCAL_ONLY", None, 900, False, True),
            ("PENDING_DELETE", None, None, False, True),
            ("PENDING_DELETE", 800, 900, False, True),
        ):
            with self.subTest(status=status, synced=synced, deleted=deleted):
                self.db.execute(
                    "UPDATE movements SET sync_status=?, last_synced_at_epoch_millis=?, deleted_at_epoch_millis=?",
                    (status, synced, deleted),
                )
                self.assertEqual(visible, bool(self.db.execute(VISIBLE).fetchall()))
                self.assertEqual((int(evidence),), self.db.execute(SYNC_EVIDENCE).fetchone())

    def test_partial_replacement_rolls_back_candidate_rows_and_catalog_ddl(self):
        with self.db:
            self.db.execute("BEGIN")
            self.copy()
            self.db.execute(
                "UPDATE movements SET sync_status='LOCAL_ONLY', last_synced_at_epoch_millis=NULL, "
                "deleted_at_epoch_millis=NULL",
            )
        tables = ("movements", "categories", "subcategories", "catalog_installation", "sqlite_sequence")

        def snapshot():
            return [self.db.execute(f"SELECT * FROM {table}").fetchall() for table in tables]

        before = snapshot()
        with self.assertRaises(sqlite3.IntegrityError), self.db:
            self.db.execute("BEGIN")
            for statement in (DROP_CATEGORY, DROP_SUBCATEGORY, CLEAR, CLEAR_SUBCATEGORIES, CLEAR_CATEGORIES):
                self.db.execute(statement)
            movement = list(before[0][0])
            movement[0] = None
            movement[6:8] = (None, None)
            self.db.execute("INSERT INTO movements VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", movement)
            self.db.execute("INSERT INTO movements VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)", movement)
        self.assertEqual(before, snapshot())
        for statement in PROTECT:
            name = statement.split()[2]
            if name in ("categories_no_delete", "subcategories_no_delete"):
                self.assertIsNotNone(self.db.execute(
                    "SELECT sql FROM sqlite_master WHERE type='trigger' AND name=?", (name,),
                ).fetchone())


if __name__ == "__main__":
    unittest.main()
