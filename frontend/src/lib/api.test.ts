import { afterEach, expect, it, vi } from "vitest";
import { api, ApiRequestError, errorMessage } from "./api";
import { isPrivateKey, queryKeys } from "./query-keys";

afterEach(() => vi.unstubAllGlobals());

it("keeps 401 identity loss distinct from temporary 503 and network errors", async () => {
  vi.stubGlobal("fetch", vi.fn()
    .mockResolvedValueOnce({ ok: false, status: 401, text: async () => JSON.stringify({ code: "UNAUTHENTICATED", message: "请登录", traceId: "a" }) })
    .mockResolvedValueOnce({ ok: false, status: 503, text: async () => JSON.stringify({ code: "SERVICE_UNAVAILABLE", message: "稍后重试", traceId: "b" }) })
    .mockRejectedValueOnce(new TypeError("network")));
  const unauthorized = await api.session().catch((error: unknown) => error);
  const unavailable = await api.session().catch((error: unknown) => error);
  const network = await api.session().catch((error: unknown) => error);
  expect(unauthorized).toBeInstanceOf(ApiRequestError);
  expect(unavailable).toBeInstanceOf(ApiRequestError);
  expect((unauthorized as ApiRequestError).isUnauthorized).toBe(true);
  expect((unavailable as ApiRequestError).isUnavailable).toBe(true);
  expect((network as ApiRequestError).isUnavailable).toBe(true);
  expect(errorMessage(unauthorized)).toContain("会话已失效");
  expect(errorMessage(unavailable)).toContain("服务暂时不可用");
  expect((unauthorized as ApiRequestError).traceId).toBe("a");
});

it("keeps public template keys apart from each user's private keys", () => {
  expect(isPrivateKey(queryKeys.templates)).toBe(false);
  expect(isPrivateKey(queryKeys.template("template"))).toBe(false);
  expect(isPrivateKey(queryKeys.userTasks("alice"))).toBe(true);
  expect(queryKeys.userTasks("alice")).not.toEqual(queryKeys.userTasks("bob"));
  expect(queryKeys.userTask("alice", "first")).not.toEqual(queryKeys.userTask("alice", "second"));
});

it("uses the frozen paths and sends only asset IDs when accepting a task", async () => {
  const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 202, json: async () => ({ id: "task", status: "QUEUED" }) });
  vi.stubGlobal("fetch", fetchMock);
  await api.createTask("template", ["asset-1", "asset-2"]);
  const [url, init] = fetchMock.mock.calls[0] as [string, RequestInit];
  expect(url).toBe("/api/tasks");
  expect(init.credentials).toBe("same-origin");
  expect(init.cache).toBe("no-store");
  expect(JSON.parse(init.body as string)).toEqual({ templateId: "template", assetIds: ["asset-1", "asset-2"] });
});

it("treats a timed-out POST as unknown acceptance and never sends it again", async () => {
  vi.useFakeTimers();
  try {
    const fetchMock = vi.fn((path: string, init: RequestInit) => new Promise((resolve, reject) => {
      void path;
      void resolve;
      init.signal?.addEventListener("abort", () => reject(new DOMException("aborted", "AbortError")));
    }));
    vi.stubGlobal("fetch", fetchMock);
    const pending = api.createTask("template", ["asset"]);
    const assertion = expect(pending).rejects.toMatchObject({ code: "REQUEST_TIMEOUT", status: 0 });
    await vi.advanceTimersByTimeAsync(10_000);
    await assertion;
    expect(fetchMock).toHaveBeenCalledTimes(1);
  } finally {
    vi.useRealTimers();
  }
});
