import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, fireEvent, render, screen, waitFor, within } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, expect, it, vi } from "vitest";
import type { Task } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import HistoryPage from "./page";

const session = vi.hoisted(() => ({ userId: "alice-id", displayName: "Alice", busy: false }));
vi.mock("@/components/session-provider", () => ({ useSession: () => ({
  user: { id: session.userId, displayName: session.displayName, demoUserKey: session.userId },
  loading: false, unavailable: false, busy: session.busy,
}) }));
vi.mock("next/link", async () => {
  const { createElement } = await import("react");
  return { default: ({ href, children }: { href: string; children: ReactNode }) => createElement("a", { href }, children) };
});
vi.mock("next/image", async () => {
  const { createElement } = await import("react");
  return { default: (props: { src: string; alt: string; onError?: () => void }) => createElement("img", props) };
});

const initial: Task = {
  id: "task-a", templateId: "template-a", templateTitle: "明信片", status: "RUNNING", stage: "GENERATE",
  createdAt: "2026-10-30T08:00:00Z", startedAt: "2026-10-30T08:00:01Z", finishedAt: null,
  resultUrl: null, failedStage: null, errorCode: null, errorMessage: null, version: 4,
  steps: [
    { stage: "ASSET_CHECK", status: "SUCCEEDED", startedAt: null, finishedAt: null, errorMessage: null },
    { stage: "GENERATE", status: "RUNNING", startedAt: null, finishedAt: null, errorMessage: null },
    { stage: "FINALIZE", status: "PENDING", startedAt: null, finishedAt: null, errorMessage: null },
  ],
};

function renderHistory() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<QueryClientProvider client={client}><HistoryPage /></QueryClientProvider>);
  return client;
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => { resolve = done; });
  return { promise, resolve };
}

afterEach(() => { cleanup(); vi.unstubAllGlobals(); session.userId = "alice-id"; session.displayName = "Alice"; session.busy = false; });

it.each([500, 503])("shows a full request error initially, then preserves the last running snapshot through HTTP %i", async (status) => {
  const done: Task = { ...initial, status: "SUCCEEDED", stage: "FINALIZE", version: 8,
    resultUrl: "/api/assets/result-a/content", steps: initial.steps.map((step) => ({ ...step, status: "SUCCEEDED" })) };
  const fetchMock = vi.fn()
    .mockResolvedValueOnce({ ok: false, status: 503, text: async () => '{"code":"UNAVAILABLE","message":"稍后再试","traceId":"trace"}' })
    .mockResolvedValueOnce({ ok: true, status: 200, json: async () => [initial] })
    .mockResolvedValueOnce({ ok: false, status, text: async () => '{"code":"UNAVAILABLE","message":"稍后再试","traceId":"trace"}' })
    .mockResolvedValueOnce({ ok: true, status: 200, json: async () => [done] });
  vi.stubGlobal("fetch", fetchMock);
  const client = renderHistory();
  await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("历史记录加载失败"));
  expect(screen.queryByText("还没有制作记录")).not.toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "重新查询" }));
  await waitFor(() => expect(screen.getByText("明信片")).toBeInTheDocument());
  await act(async () => { await client.invalidateQueries({ queryKey: queryKeys.userTasks("alice-id") }); });
  await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("保留上次记录"));
  expect(screen.getByText("处理中")).toBeInTheDocument();
  expect(screen.queryByText("制作失败")).not.toBeInTheDocument();
  expect(client.getQueryData<Task[]>(queryKeys.userTasks("alice-id"))?.[0]).toMatchObject({ status: "RUNNING", version: 4 });
  fireEvent.click(screen.getByRole("button", { name: "重新查询" }));
  await waitFor(() => expect(screen.getByText("制作成功")).toBeInTheDocument());
  expect(screen.getByAltText("明信片的制作结果")).toHaveAttribute("src", "/api/assets/result-a/content");
});

it("keeps A success, B running, and C failure in their own cards", async () => {
  const a: Task = { ...initial, id: "task-a", templateTitle: "A 明信片", status: "SUCCEEDED", stage: "FINALIZE", version: 9,
    resultUrl: "/api/assets/result-a/content", steps: initial.steps.map((step) => ({ ...step, status: "SUCCEEDED" })) };
  const b: Task = { ...initial, id: "task-b", templateTitle: "B 海报", status: "RUNNING", stage: "GENERATE", version: 4 };
  const c: Task = { ...initial, id: "task-c", templateTitle: "C 贺卡", status: "FAILED", stage: "FINALIZE", failedStage: "FINALIZE", version: 7,
    errorCode: "SIMULATED_FAILURE", errorMessage: "结果整理失败",
    steps: [
      { ...initial.steps[0], status: "SUCCEEDED" },
      { ...initial.steps[1], status: "SUCCEEDED" },
      { ...initial.steps[2], status: "FAILED" },
    ] };
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => [a, b, c] }));
  renderHistory();
  const cardA = await screen.findByRole("article", { name: "A 明信片，制作成功" });
  const cardB = screen.getByRole("article", { name: "B 海报，处理中" });
  const cardC = screen.getByRole("article", { name: "C 贺卡，制作失败" });
  expect(within(cardA).getByAltText("A 明信片的制作结果")).toHaveAttribute("src", "/api/assets/result-a/content");
  expect(within(cardA).getAllByText("已完成")).toHaveLength(3);
  expect(within(cardA).queryByText("结果整理失败")).not.toBeInTheDocument();
  expect(within(cardB).getByText("当前阶段：模拟生成")).toBeInTheDocument();
  expect(within(cardB).getByText("进行中")).toBeInTheDocument();
  expect(within(cardB).getByText("未开始")).toBeInTheDocument();
  expect(within(cardB).queryByAltText(/制作结果/)).not.toBeInTheDocument();
  expect(within(cardC).getByText("失败阶段：结果整理")).toBeInTheDocument();
  expect(within(cardC).getAllByText("已完成")).toHaveLength(2);
  expect(within(cardC).getByText("失败")).toBeInTheDocument();
  expect(within(cardC).getByText("结果整理失败（SIMULATED_FAILURE）")).toBeInTheDocument();
  expect(within(cardC).queryByAltText(/制作结果/)).not.toBeInTheDocument();
});

it("does not let B's late version 2 response replace its newer version 6", async () => {
  const oldResponse = deferred<{ ok: boolean; status: number; json: () => Promise<Task[]> }>();
  const b: Task = { ...initial, id: "task-b", templateTitle: "B 海报", version: 2 };
  const fetchMock = vi.fn()
    .mockResolvedValueOnce({ ok: true, status: 200, json: async () => [b] })
    .mockImplementationOnce(() => oldResponse.promise);
  vi.stubGlobal("fetch", fetchMock);
  const client = renderHistory();
  await screen.findByText("B 海报");
  const pending = client.refetchQueries({ queryKey: queryKeys.userTasks("alice-id") });
  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
  const newer: Task = { ...b, status: "SUCCEEDED", stage: "FINALIZE", version: 6,
    resultUrl: "/api/assets/result-b/content", steps: b.steps.map((step) => ({ ...step, status: "SUCCEEDED" })) };
  act(() => { client.setQueryData<Task[]>(queryKeys.userTasks("alice-id"), [newer]); });
  await act(async () => {
    oldResponse.resolve({ ok: true, status: 200, json: async () => [b] });
    await pending;
  });
  expect(client.getQueryData<Task[]>(queryKeys.userTasks("alice-id"))?.[0]).toMatchObject({ id: "task-b", status: "SUCCEEDED", version: 6 });
  await waitFor(() => expect(screen.getByRole("article", { name: "B 海报，制作成功" })).toBeInTheDocument());
});

it("aborts Alice's in-flight history on user change and shows only Bob's result", async () => {
  const aliceResponse = deferred<{ ok: boolean; status: number; json: () => Promise<Task[]> }>();
  const observed = { aliceSignal: null as AbortSignal | null };
  const alice: Task = { ...initial, id: "alice-task", templateTitle: "Alice 私有作品", status: "SUCCEEDED", version: 8,
    resultUrl: "/api/assets/alice-result/content" };
  const bob: Task = { ...initial, id: "bob-task", templateTitle: "Bob 私有作品", status: "SUCCEEDED", version: 8,
    resultUrl: "/api/assets/bob-result/content" };
  vi.stubGlobal("fetch", vi.fn((url: string, init: RequestInit) => {
    void url;
    if (session.userId === "alice-id") {
      observed.aliceSignal = init.signal as AbortSignal;
      return aliceResponse.promise;
    }
    return Promise.resolve({ ok: true, status: 200, json: async () => [bob] });
  }));
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  client.setQueryData(queryKeys.userTasks("alice-id"), [alice]);
  const view = <QueryClientProvider client={client}><HistoryPage /></QueryClientProvider>;
  const { rerender } = render(view);
  await waitFor(() => expect(observed.aliceSignal).not.toBeNull());
  expect(screen.getByRole("article", { name: "Alice 私有作品，制作成功" })).toBeInTheDocument();
  session.busy = true;
  rerender(<QueryClientProvider client={client}><HistoryPage /></QueryClientProvider>);
  expect(screen.getByText("正在确认当前用户…")).toBeInTheDocument();
  expect(screen.queryByText("Alice 私有作品")).not.toBeInTheDocument();
  session.userId = "bob-id";
  session.displayName = "Bob";
  session.busy = false;
  rerender(<QueryClientProvider client={client}><HistoryPage /></QueryClientProvider>);
  expect(observed.aliceSignal?.aborted).toBe(true);
  await screen.findByRole("article", { name: "Bob 私有作品，制作成功" });
  await act(async () => {
    aliceResponse.resolve({ ok: true, status: 200, json: async () => [alice] });
    await Promise.resolve();
  });
  expect(screen.queryByText("Alice 私有作品")).not.toBeInTheDocument();
  expect(screen.queryByAltText("Alice 私有作品的制作结果")).not.toBeInTheDocument();
  expect(screen.getByAltText("Bob 私有作品的制作结果")).toHaveAttribute("src", "/api/assets/bob-result/content");
});

it("rejects a late lower-version response and displays Java's failed step without a result", async () => {
  const failed: Task = {
    ...initial, status: "FAILED", stage: "GENERATE", failedStage: "GENERATE", version: 7,
    errorCode: "SIMULATED_FAILURE", errorMessage: "模拟生成失败",
    steps: [initial.steps[0], { ...initial.steps[1], status: "FAILED" }, { ...initial.steps[2], status: "SKIPPED" }],
  };
  const fetchMock = vi.fn()
    .mockResolvedValueOnce({ ok: true, status: 200, json: async () => [failed] })
    .mockResolvedValueOnce({ ok: true, status: 200, json: async () => [initial] });
  vi.stubGlobal("fetch", fetchMock);
  const client = renderHistory();
  await waitFor(() => expect(screen.getByText("模拟生成失败（SIMULATED_FAILURE）")).toBeInTheDocument());
  await act(async () => { await client.invalidateQueries({ queryKey: queryKeys.userTasks("alice-id") }); });
  await waitFor(() => expect(fetchMock).toHaveBeenCalledTimes(2));
  expect(client.getQueryData<Task[]>(queryKeys.userTasks("alice-id"))?.[0]).toMatchObject({ status: "FAILED", version: 7 });
  expect(screen.getByText("已跳过")).toBeInTheDocument();
  expect(screen.queryByAltText(/制作结果/)).not.toBeInTheDocument();
});

it("keeps two task cards separate and lets a failed result image retry", async () => {
  const done: Task = { ...initial, id: "task-b", templateTitle: "祝福卡", status: "SUCCEEDED", stage: "FINALIZE", version: 9,
    resultUrl: "/api/assets/result-b/content", steps: initial.steps.map((step) => ({ ...step, status: "SUCCEEDED" })) };
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => [initial, done] }));
  renderHistory();
  await waitFor(() => expect(screen.getByText("祝福卡")).toBeInTheDocument());
  expect(screen.getByText("明信片")).toBeInTheDocument();
  fireEvent.error(screen.getByAltText("祝福卡的制作结果"));
  expect(screen.getByRole("alert")).toHaveTextContent("结果图片暂时无法加载");
  const retryImage = screen.getByRole("button", { name: "重试图片" });
  retryImage.focus();
  expect(retryImage).toHaveFocus();
  fireEvent.click(retryImage);
  expect(screen.getByAltText("祝福卡的制作结果")).toHaveAttribute("src", "/api/assets/result-b/content?previewRetry=1");
});
