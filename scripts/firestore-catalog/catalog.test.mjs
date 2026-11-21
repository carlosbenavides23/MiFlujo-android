import assert from "node:assert/strict";
import { readFile } from "node:fs/promises";
import { after, before, beforeEach, test } from "node:test";
import { assertFails, assertSucceeds, initializeTestEnvironment } from "@firebase/rules-unit-testing";
import { collection, deleteDoc, doc, getDoc, getDocs, setDoc, Timestamp, writeBatch } from "firebase/firestore";

const projectId = "demo-miflujo-catalog";
const endpoint = /^(127\.0\.0\.1|localhost):(\d+)$/.exec(process.env.FIRESTORE_EMULATOR_HOST ?? "");
assert.ok(endpoint, "Run against a localhost Firestore Emulator");
let env;
const parentUuid = "11111111-1111-4111-8111-111111111111";
const childUuid = "22222222-2222-4222-8222-222222222222";
const actor = "33333333-3333-4333-8333-333333333333";
const nextActor = "44444444-4444-4444-8444-444444444444";
const movementUuid = "55555555-5555-4555-8555-555555555555";
const fixedUuid = "299690db-dcc7-380d-850f-535c74bd8827";
const waterUuid = "919bb4b6-da9e-3857-b442-d682d5d80c2d";

const common = uuid => ({
    schemaVersion: 1, uuid, name: "Hogar", iconKey: "home",
    archived: false, sortOrder: -2, revisionCounter: 1, revisionActorUuid: actor,
});
const category = (uuid = parentUuid) => ({ ...common(uuid), scope: "EXPENSE", reportGroup: "OTHER" });
const child = (uuid = childUuid, categoryUuid = parentUuid) => ({ ...common(uuid), categoryUuid });
const client = (uid = "alice") => env.authenticatedContext(uid).firestore();
const catDoc = (db, uuid = parentUuid, uid = "alice") => doc(db, "users", uid, "categories", uuid);
const subDoc = (db, uuid = childUuid, uid = "alice") => doc(db, "users", uid, "subcategories", uuid);

before(async () => {
    env = await initializeTestEnvironment({
        projectId,
        firestore: {
            host: endpoint[1], port: Number(endpoint[2]),
            rules: await readFile(new URL("./firestore.rules", import.meta.url), "utf8"),
        },
    });
});
beforeEach(async () => {
    await env.clearFirestore();
    await env.withSecurityRulesDisabled(async ctx => {
        const db = ctx.firestore();
        for (const [uid, enabled] of [["alice", true], ["bob", true], ["disabled", false]]) {
            await setDoc(doc(db, "authorizedUsers", uid), { enabled });
        }
    });
});
after(async () => { if (env) await env.cleanup(); });

test("only the authenticated and enabled owner can read, list or write the catalog", async () => {
    const db = client();
    await assertSucceeds(setDoc(catDoc(db), category()));
    await assertSucceeds(setDoc(subDoc(db), child()));
    await assertSucceeds(getDocs(collection(db, "users/alice/categories")));
    await assertSucceeds(getDocs(collection(db, "users/alice/subcategories")));
    await assertFails(getDocs(collection(client("bob"), "users/alice/categories")));
    await assertFails(getDoc(subDoc(client("bob"))));
    await assertFails(setDoc(catDoc(client("bob")), category()));
    await assertFails(setDoc(subDoc(client("bob")), child()));
    await assertSucceeds(setDoc(catDoc(client("bob"), parentUuid, "bob"), category()));
    for (const uid of ["disabled", "missing"]) {
        await assertFails(setDoc(catDoc(client(uid), parentUuid, uid), category()));
        await assertFails(getDoc(catDoc(client(uid), parentUuid, uid)));
    }
    const anon = env.unauthenticatedContext().firestore();
    await assertFails(getDoc(catDoc(anon)));
    await assertFails(setDoc(catDoc(anon), category()));
});

test("shape, types, UTF-8 size, identity, owner injection and seed structure", async () => {
    const db = client();
    const valid = category();
    const { iconKey: omitted, ...incomplete } = valid;
    for (const payload of [
        incomplete, { ...valid, id: 1 }, { ...valid, ownerUid: "alice" }, { ...valid, uid: "alice" },
        { ...valid, uuid: childUuid }, { ...valid, schemaVersion: 2 }, { ...valid, schemaVersion: 1.5 },
        { ...valid, archived: "false" }, { ...valid, sortOrder: 2 ** 31 },
        { ...valid, revisionCounter: -1 }, { ...valid, revisionActorUuid: "malformed" },
        { ...valid, name: "é".repeat(241) }, { ...valid, iconKey: "k".repeat(129) },
        { ...valid, name: "  " }, { ...valid, scope: "INCOME" },
    ]) await assertFails(setDoc(catDoc(db), payload));
    await assertFails(setDoc(catDoc(db, "not-a-uuid"), category("not-a-uuid")));
    await assertFails(setDoc(catDoc(db, fixedUuid), category(fixedUuid)));
    await assertFails(setDoc(catDoc(db, waterUuid), category(waterUuid)));
    await assertSucceeds(setDoc(catDoc(db, fixedUuid), { ...category(fixedUuid), reportGroup: "FIXED_COST" }));
    await assertSucceeds(setDoc(catDoc(db), { ...valid, name: "é".repeat(240) }));
    await assertFails(setDoc(subDoc(db, waterUuid), child(waterUuid)));
    await assertSucceeds(setDoc(subDoc(db, waterUuid), child(waterUuid, fixedUuid)));
});

test("child writes require a same-UID parent and disjoint UUIDs, including in batches", async () => {
    const db = client();
    await assertFails(setDoc(subDoc(db), child()));
    await assertSucceeds(setDoc(catDoc(client("bob"), parentUuid, "bob"), category()));
    await assertFails(setDoc(subDoc(db), child()));
    const batch = writeBatch(db);
    batch.set(subDoc(db), child());
    batch.set(catDoc(db), category());
    await assertSucceeds(batch.commit());
    await assertFails(setDoc(subDoc(db, nextActor), { ...child(nextActor), scope: "EXPENSE" }));
    await assertFails(setDoc(catDoc(db, childUuid), category(childUuid)));
    const collision = writeBatch(db);
    collision.set(catDoc(db, nextActor), category(nextActor));
    collision.set(subDoc(db, nextActor), child(nextActor));
    await assertFails(collision.commit());
});

test("revisions are monotonic, retries are idempotent and archive preserves both documents", async () => {
    const db = client();
    const first = category();
    await assertSucceeds(setDoc(catDoc(db), first));
    await assertSucceeds(setDoc(subDoc(db), child()));
    await assertFails(setDoc(catDoc(db), { ...first, name: "Conflict" }));
    const archived = { ...first, archived: true, revisionActorUuid: nextActor };
    await assertSucceeds(setDoc(catDoc(db), archived));
    await assertSucceeds(setDoc(catDoc(db), archived));
    await assertFails(setDoc(catDoc(db), first));
    await assertFails(setDoc(catDoc(db), { ...archived, scope: "BOTH", revisionCounter: 2 }));
    await assertFails(setDoc(catDoc(db), { ...archived, reportGroup: "MAINTENANCE", revisionCounter: 2 }));
    await assertSucceeds(setDoc(catDoc(db, nextActor), category(nextActor)));
    await assertFails(setDoc(subDoc(db), { ...child(), categoryUuid: nextActor, revisionCounter: 2 }));
    await assertSucceeds(setDoc(subDoc(db), { ...child(), archived: true, revisionCounter: 2 }));
    await assertFails(deleteDoc(catDoc(db)));
    await assertFails(deleteDoc(subDoc(db)));
    assert.equal((await assertSucceeds(getDoc(catDoc(db)))).data().archived, true);
    assert.equal((await assertSucceeds(getDoc(subDoc(db)))).data().categoryUuid, parentUuid);
});

test("legacy movement and tombstone stay compatible while UUID movements remain gated", async () => {
    const db = client();
    const now = Timestamp.fromMillis(1700000000000);
    const ref = doc(db, "users/alice/movements", movementUuid);
    const legacy = {
        uuid: movementUuid, type: "EXPENSE", amountMinor: 1234, currency: "CORDOBA",
        date: "2024-01-01", category: "FIXED_COST", subcategory: "WATER", detail: null,
        createdAt: now, updatedAt: now, deletedAt: null, schemaVersion: 1,
    };
    await assertSucceeds(setDoc(ref, legacy));
    await assertSucceeds(setDoc(catDoc(db), category()));
    const { category: oldCategory, subcategory: oldChild, ...fields } = legacy;
    const future = { ...fields, schemaVersion: 2, categoryUuid: parentUuid, subcategoryUuid: null };
    await assertFails(setDoc(ref, future));
    assert.deepEqual((await assertSucceeds(getDoc(ref))).data(), legacy);
    await assertSucceeds(setDoc(ref, { ...legacy, deletedAt: now }));
    await assertFails(deleteDoc(ref));
    await assertFails(getDoc(doc(client("bob"), "users/alice/movements", movementUuid)));
});
