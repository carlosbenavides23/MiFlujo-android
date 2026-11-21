"""Host SQLite maintenance/relationship checks; does NOT run the Kotlin restorer or Room.
The staging table is a test fixture, not a claim about Room-generated DDL equivalence.
"""
import re
import sqlite3
import unittest
import sys
sys.dont_write_bytecode = True
from test_catalog_sqlite import BASE, PROTECT, install

SOURCE = (BASE / "data/backup/BackupSandboxDatabase.kt").read_text()
RELATIONSHIPS = re.findall(r'"""(.*?)"""', SOURCE, re.S)
DELETE_NAMES = ("categories_no_delete", "subcategories_no_delete")


class BackupSqliteTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.db.execute("PRAGMA foreign_keys=ON")
        install(self.db)
        self.db.execute("""CREATE TABLE backup_movements (
            id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, uuid TEXT NOT NULL UNIQUE,
            type TEXT NOT NULL, amountMinor INTEGER NOT NULL, currency TEXT NOT NULL,
            date TEXT NOT NULL, categoryId INTEGER, subcategoryId INTEGER, detail TEXT,
            createdAt TEXT NOT NULL, updatedAt TEXT NOT NULL, syncStatus TEXT NOT NULL,
            FOREIGN KEY(categoryId) REFERENCES categories(id) ON DELETE RESTRICT,
            FOREIGN KEY(subcategoryId) REFERENCES subcategories(id) ON DELETE RESTRICT)""")
        for sql in RELATIONSHIPS:
            self.db.execute(sql)
        self.insert("old", 2, 1)
        self.db.commit()

    def tearDown(self):
        self.db.close()

    def insert(self, uuid, parent, child):
        self.db.execute("INSERT INTO backup_movements (uuid,type,amountMinor,currency,date,categoryId,subcategoryId,detail,createdAt,updatedAt,syncStatus) VALUES (?, 'EXPENSE', ?, 'DOLLAR', '2024-02-29', ?, ?, 'detalle', ?, ?, 'LOCAL_ONLY')",
                        (uuid, 9223372036854775807, parent, child, "2026-11-16T11:12:13.123456789", "2026-11-16T11:12:13.123456790"))

    def test_unknown_or_mismatched_relationships_are_rejected(self):
        for parent, child in [(None, 1), (4, 1), (999, None), (2, 999)]:
            with self.subTest(parent=parent, child=child), self.assertRaises(sqlite3.IntegrityError):
                self.insert("invalid", parent, child)

    def test_failure_rolls_back_catalog_data_movements_and_trigger_ddl(self):
        before = {table: self.db.execute(f"SELECT * FROM {table}").fetchall()
                  for table in ("categories", "subcategories", "backup_movements", "catalog_installation")}
        with self.assertRaises(sqlite3.IntegrityError):
            with self.db:
                # Python's context manager does not BEGIN for DDL; Room does before its block.
                self.db.execute("BEGIN")
                for name in DELETE_NAMES:
                    self.db.execute(f"DROP TRIGGER {name}")
                self.db.execute("DELETE FROM backup_movements")
                self.db.execute("DELETE FROM subcategories")
                self.db.execute("DELETE FROM categories")
                self.insert("failure-after-clear", 999, None)
        for table, expected in before.items():
            self.assertEqual(expected, self.db.execute(f"SELECT * FROM {table}").fetchall())
        for name in DELETE_NAMES:
            self.assertEqual(1, self.db.execute("SELECT count(*) FROM sqlite_master WHERE type='trigger' AND name=?", (name,)).fetchone()[0])

    def test_financial_integer_date_and_nanoseconds_are_exact(self):
        row = self.db.execute("SELECT amountMinor,currency,date,createdAt,updatedAt FROM backup_movements").fetchone()
        self.assertEqual((9223372036854775807, "DOLLAR", "2024-02-29", "2026-11-16T11:12:13.123456789", "2026-11-16T11:12:13.123456790"), row)

    def test_replacement_can_remap_local_ids_without_changing_uuid(self):
        parent = self.db.execute("SELECT * FROM categories WHERE id=2").fetchone()
        child = self.db.execute("SELECT * FROM subcategories WHERE id=1").fetchone()
        actor = self.db.execute("SELECT actorUuid FROM catalog_installation").fetchone()[0]
        with self.db:
            self.db.execute("BEGIN")
            for name in DELETE_NAMES:
                self.db.execute(f"DROP TRIGGER {name}")
            self.db.execute("DELETE FROM backup_movements")
            self.db.execute("DELETE FROM subcategories")
            self.db.execute("DELETE FROM categories")
            parent_id = self.db.execute("INSERT INTO categories (uuid,name,scope,reportGroup,iconKey,archived,sortOrder,revisionCounter,revisionActorUuid,syncState) VALUES (?,?,?,?,?,?,?,?,?,?)", parent[1:]).lastrowid
            values = list(child[1:]); values[1] = parent_id
            child_id = self.db.execute("INSERT INTO subcategories (uuid,categoryId,name,iconKey,archived,sortOrder,revisionCounter,revisionActorUuid,syncState) VALUES (?,?,?,?,?,?,?,?,?)", values).lastrowid
            self.insert("old", parent_id, child_id)
            for name in DELETE_NAMES:
                self.db.execute(next(sql for sql in PROTECT if sql.startswith(f"CREATE TRIGGER {name} ")))
        self.assertNotEqual(2, parent_id)
        self.assertNotEqual(1, child_id)
        self.assertEqual((parent[1], child[1]), self.db.execute("SELECT c.uuid,s.uuid FROM backup_movements m JOIN categories c ON c.id=m.categoryId JOIN subcategories s ON s.id=m.subcategoryId").fetchone())
        self.assertEqual(actor, self.db.execute("SELECT actorUuid FROM catalog_installation").fetchone()[0])
        self.assertEqual([], self.db.execute("PRAGMA foreign_key_check").fetchall())


if __name__ == "__main__":
    unittest.main()
