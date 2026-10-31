import assert from "node:assert/strict";
import { readFile, writeFile } from "node:fs/promises";

const base = "http://frontend:3000";
const origin = "http://127.0.0.1:3000";
const templateId = "c2222222-2222-4222-8222-222222222222";
const stateFile = "/state/smoke.json";
const stages = ["ASSET_CHECK", "GENERATE", "FINALIZE"];
const zeroDelay = Object.fromEntries(stages.map((stage) => [stage, 0]));

async function request(path, options = {}, expected = 200) {
  const response = await fetch(new URL(path, base), { cache: "no-store", ...options });
  const contentType = response.headers.get("content-type") ?? "";
  const body = contentType.includes("application/json") ? await response.json() : await response.arrayBuffer();
  assert.equal(response.status, expected, `${options.method ?? "GET"} ${path}: ${response.status} ${JSON.stringify(body)}`);
  return { response, body };
}

async function login(key) {
  const { response, body } = await request("/api/session", {
    method: "POST",
    headers: { Origin: origin, "Content-Type": "application/json" },
    body: JSON.stringify({ demoUserKey: key }),
  });
  assert.equal(body.demoUserKey, key);
  const cookie = response.headers.get("set-cookie")?.split(";")[0];
  assert.ok(cookie?.startsWith("material_session="), "session cookie missing");
  return cookie;
}

function auth(cookie) { return { Cookie: cookie }; }

async function upload(cookie) {
  const form = new FormData();
  form.set("templateId", templateId);
  form.set("file", new Blob([await readFile("/samples/coast.png")], { type: "image/png" }), "coast.png");
  const { body } = await request("/api/assets", {
    method: "POST", headers: { Origin: origin, ...auth(cookie) }, body: form,
  }, 201);
  assert.match(body.id, /^[0-9a-f-]{36}$/);
  assert.equal(body.mimeType, "image/png");
  await assertPng(body.previewUrl, cookie);
  return body.id;
}

async function submit(cookie, assetId, failAtStage = null, stageDelayMs = zeroDelay) {
  const { response, body } = await request("/api/tasks", {
    method: "POST",
    headers: { Origin: origin, "Content-Type": "application/json", ...auth(cookie) },
    body: JSON.stringify({ templateId, assetIds: [assetId], simulation: { failAtStage, stageDelayMs } }),
  }, 202);
  assert.equal(response.headers.get("location"), `/api/tasks/${body.id}`);
  assert.equal(body.steps.length, 3);
  return body.id;
}

async function terminal(cookie, id) {
  const deadline = Date.now() + 90_000;
  while (Date.now() < deadline) {
    const { body } = await request(`/api/tasks/${id}`, { headers: auth(cookie) });
    if (body.status === "SUCCEEDED" || body.status === "FAILED") return body;
    await new Promise((resolve) => setTimeout(resolve, 200));
  }
  throw new Error(`task ${id} did not reach a terminal state in 90s`);
}

async function assertPng(path, cookie) {
  const { response, body } = await request(path, { headers: auth(cookie) });
  assert.match(response.headers.get("content-type") ?? "", /^image\/png/);
  const bytes = Buffer.from(body);
  assert.deepEqual([...bytes.subarray(0, 8)], [137, 80, 78, 71, 13, 10, 26, 10]);
  assert.ok(bytes.readUInt32BE(16) > 0 && bytes.readUInt32BE(20) > 0, "invalid PNG dimensions");
}

async function history(cookie) {
  const { body } = await request("/api/tasks", { headers: auth(cookie) });
  assert.ok(Array.isArray(body));
  return body;
}

async function initial() {
  const health = await request("/api/health");
  assert.equal(health.body.status, "UP");
  const users = await request("/api/demo-users");
  assert.deepEqual(new Set(users.body.map((user) => user.demoUserKey)), new Set(["alice", "bob"]));
  const templates = await request("/api/templates");
  assert.ok(templates.body.length >= 3);
  const detail = await request(`/api/templates/${templateId}`);
  assert.equal(detail.body.requiredImageCount, 1);
  const cover = await request(detail.body.coverUrl);
  assert.match(cover.response.headers.get("content-type") ?? "", /^image\/svg\+xml/);
  assert.ok(Buffer.from(cover.body).includes(Buffer.from("<svg")));

  const alice = await login("alice");
  const bob = await login("bob");
  const assetId = await upload(alice);
  const successId = await submit(alice, assetId, null,
    { ASSET_CHECK: 1000, GENERATE: 1000, FINALIZE: 1000 });
  const secondId = await submit(alice, assetId);
  const failures = new Map();
  for (const stage of stages) failures.set(stage, await submit(alice, assetId, stage));

  const success = await terminal(alice, successId);
  assert.equal(success.status, "SUCCEEDED");
  assert.deepEqual(success.steps.map((step) => step.status), ["SUCCEEDED", "SUCCEEDED", "SUCCEEDED"]);
  assert.ok(success.resultUrl);
  await assertPng(success.resultUrl, alice);
  const second = await terminal(alice, secondId);
  assert.equal(second.status, "SUCCEEDED");
  assert.ok(second.resultUrl);

  for (const [stage, id] of failures) {
    const failed = await terminal(alice, id);
    assert.equal(failed.status, "FAILED");
    assert.equal(failed.failedStage, stage);
    assert.ok(failed.errorCode && failed.errorMessage);
    assert.equal(failed.resultUrl, null);
    const index = stages.indexOf(stage);
    assert.deepEqual(failed.steps.map((step) => step.status),
      stages.map((_, i) => i < index ? "SUCCEEDED" : i === index ? "FAILED" : "SKIPPED"));
  }

  const aliceHistory = await history(alice);
  for (const id of [successId, secondId, ...failures.values()]) {
    assert.ok(aliceHistory.some((task) => task.id === id), `Alice history missing ${id}`);
  }
  const bobHistory = await history(bob);
  assert.ok(!bobHistory.some((task) => task.id === successId));
  await request(`/api/tasks/${successId}`, { headers: auth(bob) }, 404);
  await request(`/api/assets/${assetId}/content`, { headers: auth(bob) }, 404);
  await request(success.resultUrl, { headers: auth(bob) }, 404);

  await writeFile(stateFile, JSON.stringify({ successId, secondId, assetId,
    resultUrl: success.resultUrl, failureIds: [...failures.values()] }));
  console.log("PASS initial: seed, cover, upload, 202, two successes, three staged failures, history, Bob 404");
}

async function persistence() {
  const saved = JSON.parse(await readFile(stateFile, "utf8"));
  const alice = await login("alice");
  const bob = await login("bob");
  const tasks = await history(alice);
  for (const id of [saved.successId, saved.secondId]) {
    assert.equal(tasks.find((task) => task.id === id)?.status, "SUCCEEDED");
  }
  for (const id of saved.failureIds) {
    assert.equal(tasks.find((task) => task.id === id)?.status, "FAILED");
  }
  await assertPng(`/api/assets/${saved.assetId}/content`, alice);
  await assertPng(saved.resultUrl, alice);
  await request(saved.resultUrl, { headers: auth(bob) }, 404);
  console.log("PASS persistence: task history, uploaded file, result file retained after compose down/up; Bob remains isolated");
}

if (process.env.SMOKE_MODE === "persist") await persistence();
else if (process.env.SMOKE_MODE === "initial") await initial();
else throw new Error(`unknown SMOKE_MODE: ${process.env.SMOKE_MODE}`);
