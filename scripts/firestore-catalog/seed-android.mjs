// Fixture only for ScopedCatalogSyncTest against the local Auth and Firestore emulators.
import assert from "node:assert/strict";

const project = "demo-miflujo-catalog";
const endpoint = name => {
    const value = process.env[name];
    assert.match(value ?? "", /^127\.0\.0\.1:\d+$/);
    return `http://${value}`;
};
const auth = endpoint("FIREBASE_AUTH_EMULATOR_HOST");
const firestore = endpoint("FIRESTORE_EMULATOR_HOST");
const credentials = {
    email: "catalog-android@example.test",
    password: "catalog-emulator-only",
    returnSecureToken: true,
};
const authenticate = async action => {
    const response = await fetch(`${auth}/identitytoolkit.googleapis.com/v1/accounts:${action}?key=fake-demo-api-key`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(credentials),
    });
    return response.json();
};
let account = await authenticate("signUp");
if (account.error?.message === "EMAIL_EXISTS") account = await authenticate("signInWithPassword");
assert.ok(account.idToken && account.localId, "Could not set up the Auth Emulator fixture");
const claims = JSON.parse(Buffer.from(account.idToken.split(".")[1], "base64url").toString("utf8"));
assert.equal(claims.aud, project);
assert.equal(claims.sub, account.localId);
const fixture = await fetch(
    `${firestore}/v1/projects/${project}/databases/(default)/documents/authorizedUsers/${encodeURIComponent(account.localId)}`,
    {
        method: "PATCH",
        headers: { Authorization: "Bearer owner", "Content-Type": "application/json" },
        body: JSON.stringify({ fields: { enabled: { booleanValue: true } } }),
    },
);
assert.ok(fixture.ok, `Firestore Emulator fixture setup failed: ${fixture.status}`);
if (process.argv.includes("--movement-v2")) {
    const marker = await fetch(
        `${firestore}/v1/projects/${project}/databases/(default)/documents/users/${encodeURIComponent(account.localId)}/metadata/movementProtocol`,
        {
            method: "PATCH",
            headers: { Authorization: "Bearer owner", "Content-Type": "application/json" },
            body: JSON.stringify({ fields: { version: { integerValue: "2" } } }),
        },
    );
    assert.ok(marker.ok, `Firestore Emulator protocol fixture setup failed: ${marker.status}`);
}
console.log("Demo catalog Android fixture is ready.");
