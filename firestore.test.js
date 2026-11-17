const {
  initializeTestEnvironment,
  assertSucceeds,
  assertFails,
} = require("@firebase/rules-unit-testing");
const { test, before, after, beforeEach } = require("node:test");
const fs = require("node:fs");

const PROJECT_ID = process.env.GCP_PROJECT || "demo-no-project";
const [emulatorHost, emulatorPortStr] = (
  process.env.FIRESTORE_EMULATOR_HOST || "127.0.0.1:8085"
).split(":");
const emulatorPort = parseInt(emulatorPortStr, 10);

let testEnv;

before(async () => {
  const rules = fs.readFileSync("./firestore.rules", "utf8");
  testEnv = await initializeTestEnvironment({
    projectId: PROJECT_ID,
    firestore: {
      host: emulatorHost,
      port: emulatorPort,
      rules,
    },
  });
});

after(async () => {
  if (testEnv) await testEnv.cleanup();
});

beforeEach(async () => {
  if (testEnv) await testEnv.clearFirestore();
});

test("User vault allows owner read/create/merge-update and denies other users and anonymous", async () => {
  const aliceDb = testEnv
    .authenticatedContext("alice_uid", { email: "alice@example.com" })
    .firestore();
  const bobDb = testEnv
    .authenticatedContext("bob_uid", { email: "bob@example.com" })
    .firestore();
  const anonDb = testEnv.unauthenticatedContext().firestore();

  const aliceRef = aliceDb.collection("users").doc("alice_uid");

  await assertSucceeds(
    aliceRef.set({
      uid: "alice_uid",
      email: "alice@example.com",
      displayName: "Alice Dev",
      photoUrl: "",
      themeMode: "DARK",
      defaultProviderId: "coarena",
      defaultModelId: "gpt-4o-mini",
      systemPrompt: "Helpful engineer.",
      githubToken: "ghp_testtoken123",
      githubDefaultOwner: "alicedev",
      githubUserProfileJson: "{}",
      updatedAt: 1700000000000,
    })
  );

  await assertSucceeds(aliceRef.get());
  await assertSucceeds(
    aliceRef.set(
      {
        githubToken: "ghp_updated456",
        updatedAt: 1700000005000,
      },
      { merge: true }
    )
  );

  await assertFails(bobDb.collection("users").doc("alice_uid").get());
  await assertFails(
    bobDb.collection("users").doc("alice_uid").set({
      uid: "alice_uid",
      email: "bob@example.com",
      displayName: "Bob",
      updatedAt: 1,
    })
  );
  await assertFails(anonDb.collection("users").doc("alice_uid").get());

  // Listing /users collection is denied
  await assertFails(aliceDb.collection("users").get());

  // Tampering UID or adding unexpected fields fails
  await assertFails(
    aliceRef.set({
      uid: "bob_uid",
      email: "alice@example.com",
      displayName: "Alice Dev",
      updatedAt: 2,
    })
  );
  await assertFails(
    aliceRef.set({
      uid: "alice_uid",
      email: "alice@example.com",
      displayName: "Alice Dev",
      extraAdminFlag: true,
      updatedAt: 2,
    })
  );
});

test("Provider API keys, custom providers, MCP servers, GitHub projects, and session configs enforce owner isolation and validation", async () => {
  const aliceDb = testEnv.authenticatedContext("alice_uid").firestore();
  const bobDb = testEnv.authenticatedContext("bob_uid").firestore();

  const keyRef = aliceDb
    .collection("users")
    .doc("alice_uid")
    .collection("providerKeys")
    .doc("openai");
  await assertSucceeds(
    keyRef.set({
      providerId: "openai",
      apiKey: "sk-test-1234",
      baseUrl: "https://api.openai.com/v1",
      selectedModel: "gpt-4o",
      enabled: true,
      accountId: "",
      deploymentName: "",
      fetchedModels: ["gpt-4o", "gpt-4o-mini"],
      updatedAt: 1700000001000,
    })
  );
  await assertSucceeds(
    aliceDb.collection("users").doc("alice_uid").collection("providerKeys").get()
  );
  await assertFails(
    bobDb
      .collection("users")
      .doc("alice_uid")
      .collection("providerKeys")
      .doc("openai")
      .get()
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc("alice_uid")
      .collection("providerKeys")
      .doc("openai")
      .set({
        providerId: "wrong_id",
        apiKey: "sk-1",
        updatedAt: 1,
      })
  );

  const customRef = aliceDb
    .collection("users")
    .doc("alice_uid")
    .collection("customProviders")
    .doc("custom_cerebras");
  await assertSucceeds(
    customRef.set({
      id: "custom_cerebras",
      displayName: "Cerebras",
      defaultBaseUrl: "https://api.cerebras.ai/v1",
      signupUrl: "https://cloud.cerebras.ai",
      defaultModel: "llama-3.3-70b",
      requiresApiKey: true,
      description: "Fast inference",
      updatedAt: 1700000002000,
    })
  );
  await assertSucceeds(customRef.delete());

  const mcpRef = aliceDb
    .collection("users")
    .doc("alice_uid")
    .collection("mcpServers")
    .doc("srv_1");
  await assertSucceeds(
    mcpRef.set({
      id: "srv_1",
      name: "Context7",
      endpointUrl: "https://mcp.context7.com/mcp",
      transport: "AUTO",
      authHeaderName: "Authorization",
      enabled: true,
      authToken: "bearer_secret",
      updatedAt: 1700000003000,
    })
  );
  await assertFails(
    bobDb.collection("users").doc("alice_uid").collection("mcpServers").get()
  );

  const ghRef = aliceDb
    .collection("users")
    .doc("alice_uid")
    .collection("githubProjects")
    .doc("proj_1");
  await assertSucceeds(
    ghRef.set({
      projectId: "proj_1",
      owner: "alicedev",
      repo: "ib-app",
      branch: "main",
      isPrivate: true,
      createIfMissing: true,
      includeApkWorkflow: true,
      autoSyncEnabled: true,
      autoFixWorkflowBugs: true,
      directGitHubMode: false,
      updatedAt: 1700000004000,
    })
  );

  const sessRef = aliceDb
    .collection("users")
    .doc("alice_uid")
    .collection("sessionConfigs")
    .doc("sess_1");
  await assertSucceeds(
    sessRef.set({
      sessionId: "sess_1",
      mode: "SPECIAL",
      presetId: "fullstack_web",
      presetTitle: "Full-Stack Web",
      systemPrompt: "Build modern web apps.",
      updatedAt: 1700000005000,
    })
  );
  await assertFails(
    aliceDb
      .collection("users")
      .doc("alice_uid")
      .collection("sessionConfigs")
      .doc("sess_2")
      .set({
        sessionId: "sess_2",
        mode: "INVALID_MODE",
        systemPrompt: "x",
        updatedAt: 1,
      })
  );
});
