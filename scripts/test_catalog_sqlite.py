"""Host SQLite checks of the catalog's actual SQL strings. Does NOT execute Room/KSP.
Run: python3 scripts/test_catalog_sqlite.py
"""
from pathlib import Path
import json
import re
import sqlite3
import unittest

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / "app/src/main/java/com/carlos/miflujo"
SCHEMA = (BASE / "data/category/CategoryCatalogSchema.kt").read_text()
CREATE = re.findall(r'"""(.*?)"""', SCHEMA.split("val createStatements =", 1)[1].split("val protectionStatements =", 1)[0], re.S)
PROTECT = re.findall(r'"""(.*?)"""', SCHEMA.split("val protectionStatements =", 1)[1].split("fun createAndSeed", 1)[0], re.S)
SEEDS = (BASE / "domain/category/LegacyCategoryCatalog.kt").read_text()
ACTOR = "00000000-0000-4000-8000-000000000000"
QUERIES = dict((name, sql) for sql, name in re.findall(r'@Query\("([^"\n]+)"\)\s+suspend fun (\w+)', (BASE / "data/category/CategoryDao.kt").read_text()))


def install(db):
    for sql in CREATE + PROTECT:
        db.execute(sql)
    db.execute("INSERT INTO catalog_installation VALUES (1, ?)", (ACTOR,))
    for row in re.findall(r'Category\((\d+), "([^"]+)", "([^"]+)", CategoryScope\.(\w+), ReportGroup\.(\w+), "([^"]+)", sortOrder = (\d+)\)', SEEDS):
        a = list(row); a[0] = int(a[0]); a[-1] = int(a[-1]); a.insert(6, 0)
        db.execute("INSERT INTO categories VALUES (?,?,?,?,?,?,?,?,?,?,?)", (*a, 0, ACTOR, "PENDING"))
    for row in re.findall(r'Subcategory\((\d+), "([^"]+)", (\d+), "([^"]+)", "([^"]+)", sortOrder = (\d+)\)', SEEDS):
        a = list(row); a[0] = int(a[0]); a[2] = int(a[2]); a[-1] = int(a[-1]); a.insert(5, 0)
        db.execute("INSERT INTO subcategories VALUES (?,?,?,?,?,?,?,?,?,?)", (*a, 0, ACTOR, "PENDING"))
    db.commit()


class CatalogSqliteTest(unittest.TestCase):
    def setUp(self):
        self.db = sqlite3.connect(":memory:")
        self.db.execute("PRAGMA foreign_keys=ON")
        install(self.db)

    def tearDown(self):
        self.db.close()

    def test_color_columns_preserve_rows_and_allow_reuse_and_null(self):
        migration = (BASE / "data/category/CatalogIconColorMigration.kt").read_text()
        statements = re.findall(r'db.execSQL\("(ALTER TABLE .*?)"\)', migration)
        self.assertEqual(2, len(statements))
        for table in ("categories", "subcategories"):
            self.db.execute(f"UPDATE {table} SET archived = 1 WHERE id = 1")
        original = {
            table: self.db.execute(f"SELECT * FROM {table} ORDER BY id").fetchall()
            for table in ("categories", "subcategories")
        }
        for sql in statements:
            self.db.execute(sql)
        for table, rows in original.items():
            self.assertEqual([row + (None,) for row in rows],
                             self.db.execute(f"SELECT * FROM {table} ORDER BY id").fetchall())
            self.db.execute(f"UPDATE {table} SET iconColor = 'mint' WHERE id IN (1, 2)")
            self.db.execute(f"UPDATE {table} SET archived = 0 WHERE id = 1")
            self.assertEqual([(0, "mint"), (0, "mint")],
                             self.db.execute(f"SELECT archived, iconColor FROM {table} WHERE id IN (1, 2) ORDER BY id").fetchall())
        self.assertEqual([], self.db.execute("PRAGMA foreign_key_check").fetchall())

    def test_seed_and_foreign_keys(self):
        self.assertEqual(4, self.db.execute("SELECT count(*) FROM categories").fetchone()[0])
        self.assertEqual(3, self.db.execute("SELECT count(*) FROM subcategories").fetchone()[0])
        self.assertEqual([], self.db.execute("PRAGMA foreign_key_check").fetchall())
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("INSERT INTO subcategories SELECT 99, uuid || 'x', 999, name, iconKey, archived, sortOrder, revisionCounter, revisionActorUuid, syncState FROM subcategories WHERE id=1")

    def test_duplicate_portable_identities(self):
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("INSERT INTO categories SELECT 99, uuid, name, scope, reportGroup, iconKey, archived, sortOrder, revisionCounter, revisionActorUuid, syncState FROM categories WHERE id=1")
        with self.assertRaises(sqlite3.IntegrityError):
            self.db.execute("INSERT INTO subcategories SELECT 99, uuid, categoryId, name, iconKey, archived, sortOrder, revisionCounter, revisionActorUuid, syncState FROM subcategories WHERE id=1")

    def test_sql_structural_guards(self):
        for sql in ["UPDATE categories SET uuid='replacement' WHERE id=2",
                    "UPDATE categories SET scope='BOTH' WHERE id=2",
                    "UPDATE categories SET reportGroup='OTHER' WHERE id=2",
                    "UPDATE subcategories SET categoryId=4 WHERE id=1",
                    "DELETE FROM categories WHERE id=2", "DELETE FROM subcategories WHERE id=1",
                    "UPDATE catalog_installation SET actorUuid='replacement' WHERE id=1"]:
            with self.subTest(sql=sql), self.assertRaises(sqlite3.IntegrityError):
                self.db.execute(sql)

    def test_archive_preserves_children(self):
        self.db.execute("UPDATE categories SET archived=1, revisionCounter=1 WHERE id=2")
        self.assertEqual(3, self.db.execute("SELECT count(*) FROM subcategories WHERE categoryId=2").fetchone()[0])
        self.assertEqual([], self.db.execute("PRAGMA foreign_key_check").fetchall())

    def test_real_dao_orders_and_scope(self):
        # Equal ranks; the UUID order differs from the local ID order.
        self.db.execute("UPDATE categories SET sortOrder=0")
        rows = self.db.execute(QUERIES["categories"], {"scope": "EXPENSE", "includeArchived": False}).fetchall()
        self.assertEqual([2, 3, 4], [row[0] for row in rows])
        children = self.db.execute(QUERIES["subcategories"], {"parentId": 2, "includeArchived": False}).fetchall()
        self.assertEqual([1, 2, 3], [row[0] for row in children])
        self.db.execute("UPDATE subcategories SET sortOrder=0")
        rows = self.db.execute(QUERIES["subcategories"], {"parentId": 2, "includeArchived": False}).fetchall()
        self.assertEqual([2, 1, 3], [row[0] for row in rows])

    def test_exact_revision_ack_does_not_clear_newer_edit(self):
        uuid = self.db.execute("SELECT uuid FROM categories WHERE id=2").fetchone()[0]
        self.db.execute("UPDATE categories SET name='Casa', revisionCounter=1, syncState='PENDING' WHERE id=2")
        params = {"uuid": uuid, "counter": 0, "actor": ACTOR, "state": "SYNCED"}
        self.assertEqual(0, self.db.execute(QUERIES["setCategorySyncState"], params).rowcount)
        params["counter"] = 1
        self.assertEqual(1, self.db.execute(QUERIES["setCategorySyncState"], params).rowcount)
        self.assertEqual("SYNCED", self.db.execute("SELECT syncState FROM categories WHERE id=2").fetchone()[0])
        params["state"] = "ERROR"
        self.assertEqual(0, self.db.execute(QUERIES["setCategorySyncState"], params).rowcount)

    def test_metadata_constraints(self):
        for update in ["revisionCounter=-1", "syncState='unknown'", "archived=7"]:
            with self.subTest(update=update), self.assertRaises(sqlite3.IntegrityError):
                self.db.execute(f"UPDATE categories SET {update} WHERE id=2")

    def test_rollback_on_later_invalid_write(self):
        before = self.db.execute("SELECT * FROM categories ORDER BY id").fetchall()
        with self.assertRaises(sqlite3.IntegrityError):
            with self.db:
                self.db.execute("UPDATE categories SET sortOrder=99, revisionCounter=1 WHERE id=2")
                self.db.execute("UPDATE subcategories SET categoryId=4 WHERE id=1")
        self.assertEqual(before, self.db.execute("SELECT * FROM categories ORDER BY id").fetchall())

    def test_candidate_sql_keeps_actual_v3_movements_unchanged(self):
        schema = json.loads(next((ROOT / "app/schemas").glob("com.carlos.miflujo.data.local.MiFlujoDatabase/3.json")).read_text())["database"]
        with sqlite3.connect(":memory:") as migrated:
            migrated.execute("PRAGMA foreign_keys=ON")
            migrated.execute(schema["entities"][0]["createSql"].replace("${TABLE_NAME}", "movements"))
            migrated.execute("INSERT INTO movements VALUES (7, ?, 'EXPENSE', 12345, 'CORDOBA', 20610, 'FIXED_COST', 'WATER', 'sentinel', 1, 2, 'PENDING_DELETE', 3, 4)", (ACTOR,))
            before = migrated.execute("SELECT * FROM movements").fetchall()
            install(migrated)
            self.assertEqual(before, migrated.execute("SELECT * FROM movements").fetchall())
            def signature(db):
                return db.execute("SELECT type,name,tbl_name,sql FROM sqlite_master WHERE tbl_name != 'movements' ORDER BY type,name").fetchall()
            self.assertEqual(signature(self.db), signature(migrated))
        # This compares shared SQL routes only, not Room-generated fresh DDL.


if __name__ == "__main__":
    unittest.main()
