import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, render, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import type { Task, TaskStatus } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import { rememberAcceptedTask } from "@/lib/task-cache";
import { TaskTracker } from "./task-tracker";

vi.mock("@/components/session-provider", () => ({ useSession: () => ({
  user: { id: "alice-id", displayName: "Alice", demoUserKey: "alice" }, busy: false,
}) }));

function task(id: string, version: number, status: TaskStatus): Task {
  return {
    id, templateId: "template", templateTitle: id, status,
    stage: status === "QUEUED" ? null : status === "SUCCEEDED" ? "FINALIZE" : "GENERATE",
    createdAt: "2026-10-30T08:00:00Z", startedAt: null, finishedAt: null,
    resultUrl: status === "SUCCEEDED" ? `/api/assets/${id}/content` : null,
    failedStage: null, errorCode: null, errorMessage: null, version,
    steps: ["ASSET_CHECK", "GENERATE", "FINALIZE"].map((stage) => ({
      stage: stage as Task["steps"][number]["stage"], status: "PENDING" as const,
      startedAt: null, finishedAt: null, errorMessage: null,
    })),
  };
}

function renderTracker() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } } });
  const rendered = render(<QueryClientProvider client={client}><TaskTracker /></QueryClientProvider>);
  return { client, ...rendered };
}

afterEach(() => { cleanup(); vi.useRealTimers(); vi.unstubAllGlobals(); });

it("polls QUEUED and RUNNING, stops at all-terminal, then resumes for a new 202 task", async () => {
  vi.useFakeTimers({ toFake: ["setInterval", "clearInterval"] });
  const queuedA = task("a", 0, "QUEUED");
  const runningA = task("a", 2, "RUNNING");
  const doneA = task("a", 5, "SUCCEEDED");
  const queuedB = task("b", 0, "QUEUED");
  const runningB = task("b", 2, "RUNNING");
  const doneB = task("b", 5, "SUCCEEDED");
  let serverTasks = [queuedA];
  const fetchMock = vi.fn().mockImplementation(() => Promise.resolve({ ok: true, status: 200, json: async () => serverTasks }));
  vi.stubGlobal("fetch", fetchMock);
  const { client } = renderTracker();
  await vi.waitFor(() => expect(client.getQueryData<Task[]>(queryKeys.userTasks("alice-id"))?.[0]?.status).toBe("QUEUED"));
  expect(fetchMock).toHaveBeenCalledTimes(1);
  serverTasks = [runningA];
  await act(async () => { await vi.advanceTimersByTimeAsync(2_000); });
  await vi.waitFor(() => expect(client.getQueryData<Task[]>(queryKeys.userTasks("alice-id"))?.[0]?.status).toBe("RUNNING"));
  expect(fetchMock).toHaveBeenCalledTimes(2);
  serverTasks = [doneA];
  await act(async () => { await vi.advanceTimersByTimeAsync(2_000); });
  await vi.waitFor(() => expect(client.getQueryData<Task[]>(queryKeys.userTasks("alice-id"))?.[0]?.status).toBe("SUCCEEDED"));
  expect(fetchMock).toHaveBeenCalledTimes(3);
  await act(async () => { await vi.advanceTimersByTimeAsync(6_000); });
  expect(fetchMock).toHaveBeenCalledTimes(3);
  act(() => { rememberAcceptedTask(client, "alice-id", queuedB); });
  expect(client.getQueryData<Task[]>(queryKeys.userTasks("alice-id"))).toHaveLength(2);
  serverTasks = [doneA, runningB];
  await act(async () => { await vi.advanceTimersByTimeAsync(2_000); });
  await vi.waitFor(() => expect(client.getQueryData<Task[]>(queryKeys.userTasks("alice-id"))?.find((item) => item.id === "b")?.status).toBe("RUNNING"));
  expect(fetchMock).toHaveBeenCalledTimes(4);
  serverTasks = [doneA, doneB];
  await act(async () => { await vi.advanceTimersByTimeAsync(2_000); });
  await vi.waitFor(() => expect(client.getQueryData<Task[]>(queryKeys.userTasks("alice-id"))?.find((item) => item.id === "b")?.status).toBe("SUCCEEDED"));
  await act(async () => { await vi.advanceTimersByTimeAsync(6_000); });
  expect(fetchMock).toHaveBeenCalledTimes(5);
});

it("makes no periodic request when the first history response is empty", async () => {
  vi.useFakeTimers({ toFake: ["setInterval", "clearInterval"] });
  const fetchMock = vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => [] });
  vi.stubGlobal("fetch", fetchMock);
  const { client } = renderTracker();
  await vi.waitFor(() => expect(client.getQueryData(queryKeys.userTasks("alice-id"))).toEqual([]));
  await act(async () => { await vi.advanceTimersByTimeAsync(6_000); });
  expect(fetchMock).toHaveBeenCalledTimes(1);
});

it("aborts an in-flight task list request when its only observer unmounts", async () => {
  const observed = { signal: null as AbortSignal | null };
  vi.stubGlobal("fetch", vi.fn((path: string, init: RequestInit) => new Promise((resolve, reject) => {
    void path;
    void resolve;
    const requestSignal = init.signal as AbortSignal;
    observed.signal = requestSignal;
    requestSignal.addEventListener("abort", () => reject(new DOMException("cancelled", "AbortError")));
  })));
  const { unmount } = renderTracker();
  await waitFor(() => expect(observed.signal).not.toBeNull());
  await act(async () => { unmount(); });
  expect(observed.signal?.aborted).toBe(true);
});
