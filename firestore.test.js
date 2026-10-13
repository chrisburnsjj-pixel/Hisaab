const {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

let testEnv;
const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const ALICE_UID = "alice_123";
const BOB_UID = "bob_456";

const [emulatorHost, emulatorPortStr] = (process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085").split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      rules,
      host: emulatorHost,
      port: emulatorPort,
    },
  });
});

after(async () => {
  if (testEnv) {
    await testEnv.cleanup();
  }
});

beforeEach(async () => {
  if (testEnv) {
    await testEnv.clearFirestore();
  }
});

test("Unauthenticated user: cannot read transactions", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(unauthDb.collection("users").doc(ALICE_UID).collection("transactions").get());
});

test("Unauthenticated user: cannot write transaction", async () => {
  const unauthDb = testEnv.unauthenticatedContext().firestore();
  await assertFails(
    unauthDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_1").set({
      userId: ALICE_UID,
      title: "Salary",
      amount: 5000,
      type: "INCOME",
      category: "Salary",
      date: "2026-10-12",
      createdAt: new Date(),
    })
  );
});

test("Authenticated Alice: can create valid transaction under her account", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertSucceeds(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_1").set({
      userId: ALICE_UID,
      title: "Salary",
      amount: 5000,
      type: "INCOME",
      category: "Salary",
      date: "2026-10-12",
      createdAt: new Date(),
    })
  );
});

test("Authenticated Alice: rejects invalid transaction amount (<= 0)", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_invalid_amount").set({
      userId: ALICE_UID,
      title: "Invalid",
      amount: -50,
      type: "EXPENSE",
      category: "Food",
      date: "2026-10-12",
      createdAt: new Date(),
    })
  );
});

test("Authenticated Alice: rejects invalid transaction type", async () => {
  const aliceDb = testEnv.authenticatedContext(ALICE_UID).firestore();
  await assertFails(
    aliceDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_invalid_type").set({
      userId: ALICE_UID,
      title: "Invalid",
      amount: 100,
      type: "UNKNOWN",
      category: "Food",
      date: "2026-10-12",
      createdAt: new Date(),
    })
  );
});

test("Cross-user isolation: Bob cannot read Alice's transactions", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).collection("transactions").doc("tx_alice").set({
      userId: ALICE_UID,
      title: "Private Expense",
      amount: 250,
      type: "EXPENSE",
      category: "Shopping",
      date: "2026-10-12",
      createdAt: new Date(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_alice").get()
  );
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("transactions").get()
  );
});

test("Cross-user isolation: Bob cannot mutate Alice's transaction", async () => {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    await context.firestore().collection("users").doc(ALICE_UID).collection("transactions").doc("tx_alice").set({
      userId: ALICE_UID,
      title: "Alice Rent",
      amount: 1200,
      type: "EXPENSE",
      category: "Bills",
      date: "2026-10-12",
      createdAt: new Date(),
    });
  });

  const bobDb = testEnv.authenticatedContext(BOB_UID).firestore();
  await assertFails(
    bobDb.collection("users").doc(ALICE_UID).collection("transactions").doc("tx_alice").delete()
  );
});
