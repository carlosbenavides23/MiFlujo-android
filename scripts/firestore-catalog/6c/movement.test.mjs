import test, { after, before, beforeEach } from 'node:test';
import assert from 'node:assert/strict';
import { readFile } from 'node:fs/promises';
import { initializeTestEnvironment, assertFails, assertSucceeds } from '@firebase/rules-unit-testing';
import { doc, getDoc, setDoc, deleteDoc, runTransaction, writeBatch, Timestamp } from 'firebase/firestore';

const endpoint = process.env.FIRESTORE_EMULATOR_HOST ?? '';
assert.match(endpoint, /^127\.0\.0\.1:[0-9]+$/, 'Use only a loopback Firestore emulator');
const projectId = 'demo-miflujo-catalog';
let env;
const a = 'demo-alice';
const b = 'demo-bob';
const id = '10000000-0000-4000-8000-000000000001';
const another = '10000000-0000-4000-8000-000000000002';
const cat = '20000000-0000-4000-8000-000000000001';
const otherCat = '20000000-0000-4000-8000-000000000002';
const child = '30000000-0000-4000-8000-000000000001';
const actor = '40000000-0000-4000-8000-000000000001';
const now = Timestamp.fromMillis(1_700_000_000_001);
const dir = (uid, collection, uuid) => `users/${uid}/${collection}/${uuid}`;
const movement = (db, uuid = id, uid = a) => doc(db, dir(uid, 'movements', uuid));
const protocol = (db, uid = a) => doc(db, dir(uid, 'metadata', 'movementProtocol'));
const base = { uuid: id, type: 'EXPENSE', amountMinor: 123, currency: 'DOLLAR', date: '2026-11-21',
  detail: null, createdAt: now, updatedAt: now, deletedAt: null };
const legacy = { ...base, category: 'OTHER', subcategory: null, schemaVersion: 1 };
const v2 = { ...base, categoryUuid: null, subcategoryUuid: null, schemaVersion: 2, revision: 1 };
const metadata = { schemaVersion: 1, name: 'Hogar', iconKey: 'home', sortOrder: 0, archived: true,
  revisionCounter: 1, revisionActorUuid: actor };
const parent = { ...metadata, uuid: cat, scope: 'EXPENSE', reportGroup: 'OTHER' };
const subcategory = { ...metadata, uuid: child, categoryUuid: cat };
const client = uid => env.authenticatedContext(uid).firestore();
const admin = block => env.withSecurityRulesDisabled(context => block(context.firestore()));
const phase = (version, uid = a) => admin(db => setDoc(protocol(db, uid), { version }));

before(async () => {
  const [host, port] = endpoint.split(':');
  env = await initializeTestEnvironment({ projectId, firestore: {
    host, port: Number(port), rules: await readFile(new URL('./firestore.rules', import.meta.url), 'utf8'),
  } });
});
beforeEach(async () => {
  await env.clearFirestore();
  await admin(async db => {
    await setDoc(doc(db, 'authorizedUsers', a), { enabled: true });
    await setDoc(doc(db, 'authorizedUsers', b), { enabled: true });
  });
});
after(async () => await env?.cleanup());

test('server gates creates, updates, tombstones and metadata through the account cut', async () => {
  const db = client(a);
  await assertSucceeds(setDoc(movement(db), legacy));
  await assertFails(setDoc(movement(db, another), { ...legacy, uuid: another, schemaVersion: 2 }));
  await assertFails(setDoc(movement(db, another), { ...v2, uuid: another }));
  await assertFails(setDoc(protocol(db), { version: 2 }));
  await admin(db => setDoc(protocol(db), { version: 1, unexpected: true }));
  await assertFails(setDoc(movement(db, another), { ...legacy, uuid: another }));
  await phase(0);
  await assertFails(setDoc(movement(db), { ...legacy, deletedAt: now }));
  await assertFails(setDoc(movement(db, another), { ...v2, uuid: another }));
  await assertFails(setDoc(doc(db, dir(a, 'categories', cat)), parent));
  await phase(2);
  await assertFails(setDoc(movement(db), v2));
  await phase(0);
  await admin(db => setDoc(movement(db), v2));
  await phase(2);
  await assertFails(setDoc(movement(db), legacy));
  await assertFails(setDoc(movement(db, another), { ...legacy, uuid: another }));
  await assertFails(setDoc(doc(db, dir(a, 'metadata', 'sync')), { schemaVersion: 1, updatedAt: now }));
  await assertSucceeds(setDoc(movement(db), { ...v2, detail: 'v2', revision: 2 }));
  const bob = client(b);
  await assertSucceeds(setDoc(movement(bob, id, b), legacy));
});

test('UID isolation covers protocol, catalog, movement, and unauthenticated operations', async () => {
  await phase(2);
  const alice = client(a);
  const other = client(b);
  const guest = env.unauthenticatedContext().firestore();
  const denied = client('disabled');
  await assertSucceeds(setDoc(movement(alice), v2));
  for (const db of [other, guest, denied]) {
    await assertFails(getDoc(movement(db)));
    await assertFails(getDoc(protocol(db)));
    await assertFails(setDoc(movement(db), { ...v2, detail: 'denied', revision: 2 }));
    await assertFails(setDoc(doc(db, dir(a, 'categories', cat)), parent));
  }
  await assertFails(deleteDoc(protocol(alice)));
});

test('nullable, archived, and atomic parent/child links respect scope and immutable identity', async () => {
  await phase(2);
  const db = client(a);
  const linked = { ...v2, categoryUuid: cat, subcategoryUuid: child };
  await assertFails(setDoc(movement(db), linked));
  await assertFails(setDoc(movement(db), { ...v2, subcategoryUuid: child }));
  const batch = writeBatch(db);
  batch.set(doc(db, dir(a, 'categories', cat)), parent);
  batch.set(doc(db, dir(a, 'subcategories', child)), subcategory);
  batch.set(movement(db), linked);
  await assertSucceeds(batch.commit());
  await assertSucceeds(setDoc(doc(db, dir(a, 'categories', otherCat)), { ...parent, uuid: otherCat }));
  await assertFails(setDoc(movement(db), { ...linked, revision: 2, categoryUuid: otherCat }));
  await assertFails(setDoc(movement(db), { ...linked, revision: 2, type: 'INCOME' }));
  await assertFails(setDoc(doc(db, dir(a, 'subcategories', child)), { ...subcategory, categoryUuid: otherCat, revisionCounter: 2 }));
  await assertFails(deleteDoc(doc(db, dir(a, 'categories', cat))));
  await assertFails(setDoc(movement(db), { ...linked, revision: 2, id: 321 }));
  await assertFails(setDoc(movement(db), { ...linked, revision: 2, amountMinor: 1.5 }));
});

test('competing devices against the same revision can commit one edit; retry is idempotent', async () => {
  await phase(2);
  const device1 = client(a);
  const device2 = client(a);
  await assertSucceeds(setDoc(movement(device1), v2));
  const edit = (db, detail) => runTransaction(db, async tx => {
    const ref = movement(db);
    const current = (await tx.get(ref)).data();
    if (current.revision !== 1) return false;
    tx.set(ref, { ...current, revision: 2, detail });
    return true;
  });
  const outcomes = await Promise.allSettled([edit(device1, 'a'), edit(device2, 'b')]);
  assert.equal(outcomes.filter(result => result.status === 'fulfilled' && result.value === true).length, 1);
  for (const result of outcomes.filter(result => result.status === 'rejected')) {
    // A stale candidate can be denied by rules before the SDK's transaction retry.
    assert.equal(result.reason.code, 'permission-denied');
  }
  assert.equal(await edit(device2, 'retry on stale base'), false);
  const winner = (await getDoc(movement(device1))).data();
  assert.equal(winner.revision, 2);
  await assertSucceeds(setDoc(movement(device2), winner));
  await assertFails(setDoc(movement(device2), { ...winner, detail: 'stale same revision' }));
  await assertFails(setDoc(movement(device2), { ...winner, revision: 4 }));
});

test('tombstones cannot be deleted, recreated as visible, or altered after confirmation', async () => {
  await phase(2);
  const db = client(a);
  await assertSucceeds(setDoc(movement(db), v2));
  const tombstone = { ...v2, revision: 2, deletedAt: now };
  await assertSucceeds(setDoc(movement(db), tombstone));
  await assertSucceeds(setDoc(movement(db), tombstone));
  await assertFails(setDoc(movement(db), { ...tombstone, revision: 3, deletedAt: null }));
  await assertFails(setDoc(movement(db), { ...tombstone, revision: 3, detail: 'changed' }));
  await assertFails(deleteDoc(movement(db)));
  await assertFails(setDoc(movement(db, another), { ...v2, uuid: another, revision: 9 }));
});

test('catalog edits retain the deterministic winner under concurrent transactions', async () => {
  await phase(2);
  const left = client(a);
  const right = client(a);
  const ref = db => doc(db, dir(a, 'categories', cat));
  await assertSucceeds(setDoc(ref(left), parent));
  const highActor = '40000000-0000-4000-8000-000000000002';
  const propose = (db, name, revisionActorUuid) => runTransaction(db, async tx => {
    const row = (await tx.get(ref(db))).data();
    if (row.revisionCounter === 2 && row.revisionActorUuid >= revisionActorUuid) return;
    tx.set(ref(db), { ...row, name, revisionCounter: 2, revisionActorUuid });
  });
  const outcomes = await Promise.allSettled([propose(left, 'Bajo', actor), propose(right, 'Alto', highActor)]);
  for (const result of outcomes.filter(result => result.status === 'rejected')) {
    assert.equal(result.reason.code, 'permission-denied');
  }
  await propose(right, 'Alto', highActor);
  await propose(left, 'Bajo', actor);
  const winner = (await getDoc(ref(left))).data();
  assert.equal(winner.name, 'Alto');
  await assertFails(setDoc(ref(left), { ...winner, name: 'Misma revisión' }));
});

test('color protocol upgrades catalog v1 without allowing old writes or invalid colors', async () => {
  const db = client(a);
  const parentRef = doc(db, dir(a, 'categories', cat));
  const childRef = doc(db, dir(a, 'subcategories', child));
  const colorParent = { ...parent, schemaVersion: 2, iconColor: 'blue', revisionCounter: 2 };
  await phase(2);
  await assertSucceeds(setDoc(parentRef, parent));
  await assertFails(setDoc(parentRef, colorParent));
  await phase(3);
  await assertFails(setDoc(parentRef, { ...parent, revisionCounter: 2 }));
  await assertFails(setDoc(parentRef, { ...colorParent, revisionCounter: 1 }));
  await assertFails(setDoc(parentRef, { ...colorParent, iconColor: '#ffffff' }));
  const { iconColor, ...missingColor } = colorParent;
  await assertFails(setDoc(parentRef, missingColor));
  await assertSucceeds(setDoc(parentRef, colorParent));
  await assertSucceeds(setDoc(childRef, { ...subcategory, schemaVersion: 2, iconColor: 'blue' }));
  await assertSucceeds(setDoc(movement(db), { ...v2, categoryUuid: cat, subcategoryUuid: child }));
  await assertFails(setDoc(parentRef, { ...parent, revisionCounter: 3 }));
  await assertSucceeds(setDoc(parentRef, { ...colorParent, iconColor: null, revisionCounter: 3 }));
  assert.equal((await getDoc(parentRef)).data().iconColor, null);
  assert.equal((await getDoc(childRef)).data().iconColor, 'blue');
  await assertFails(setDoc(doc(client(b), dir(a, 'categories', cat)), colorParent));
});
