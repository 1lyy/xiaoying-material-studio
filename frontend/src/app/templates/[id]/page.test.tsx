import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, expect, it, vi } from "vitest";
import type { Task, Template } from "@/lib/api";
import type { SlotValue } from "@/components/upload-slot";
import { queryKeys } from "@/lib/query-keys";
import TemplateDetailPage from "./page";

vi.mock("next/navigation", () => ({ useParams: () => ({ id: "template-1" }) }));
vi.mock("@/components/session-provider", () => ({ useSession: () => ({
  user: { id: "alice-id", displayName: "Alice", demoUserKey: "alice" },
  identityEpoch: 0, unavailable: false, busy: false,
}) }));
vi.mock("next/link", async () => {
  const { createElement } = await import("react");
  return { default: ({ href, children }: { href: string; children: ReactNode }) => createElement("a", { href }, children) };
});
vi.mock("next/image", async () => {
  const { createElement } = await import("react");
  return { default: (props: { src: string; alt: string }) => createElement("img", props) };
});
vi.mock("@/components/upload-slot", async () => {
  const { createElement } = await import("react");
  return { UploadSlot: ({ index, onChange }: { index: number; onChange: (index: number, value: SlotValue) => void }) =>
    createElement("div", null,
      createElement("button", { type: "button", onClick: () => onChange(index, { phase: "uploading", assetId: null }) }, `素材 ${index + 1} 上传中`),
      createElement("button", { type: "button", onClick: () => onChange(index, { phase: "ready", assetId: `asset-${index + 1}` }) }, `素材 ${index + 1} 就绪`),
    ) };
});

const template: Template = {
  id: "template-1", title: "双图明信片", category: "创意图片", coverUrl: "/api/templates/template-1/cover",
  description: "上传两张照片", requiredImageCount: 2, acceptedMimeTypes: ["image/png"], maxFileSizeBytes: 1024,
};
const accepted: Task = {
  id: "task-1", templateId: template.id, templateTitle: template.title, status: "QUEUED", stage: null,
  createdAt: "2026-10-30T08:00:00Z", startedAt: null, finishedAt: null, resultUrl: null,
  failedStage: null, errorCode: null, errorMessage: null, version: 0,
  steps: ["ASSET_CHECK", "GENERATE", "FINALIZE"].map((stage) => ({
    stage: stage as Task["steps"][number]["stage"], status: "PENDING" as const,
    startedAt: null, finishedAt: null, errorMessage: null,
  })),
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => { resolve = done; });
  return { promise, resolve };
}

function renderDetail() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  render(<QueryClientProvider client={client}><TemplateDetailPage /></QueryClientProvider>);
  return client;
}

afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

it("disables submission while an upload or POST is in flight, then caches the 202 task and resets", async () => {
  const post = deferred<{ ok: boolean; status: number; json: () => Promise<Task> }>();
  const fetchMock = vi.fn((url: string, init: RequestInit) => url === "/api/tasks" && init.method === "POST"
    ? post.promise
    : Promise.resolve({ ok: true, status: 200, json: async () => template }));
  vi.stubGlobal("fetch", fetchMock);
  const client = renderDetail();
  await screen.findByText("双图明信片");
  const submit = screen.getByRole("button", { name: "提交制作任务" });
  fireEvent.click(screen.getByRole("button", { name: "素材 1 就绪" }));
  fireEvent.click(screen.getByRole("button", { name: "素材 2 上传中" }));
  expect(submit).toBeDisabled();
  fireEvent.click(screen.getByRole("button", { name: "素材 2 就绪" }));
  expect(submit).toBeEnabled();
  submit.focus();
  expect(submit).toHaveFocus();
  fireEvent.click(submit);
  expect(screen.getByRole("button", { name: "正在提交请求…" })).toBeDisabled();
  fireEvent.click(screen.getByRole("button", { name: "正在提交请求…" }));
  expect(fetchMock.mock.calls.filter(([url]) => url === "/api/tasks")).toHaveLength(1);
  await act(async () => { post.resolve({ ok: true, status: 202, json: async () => accepted }); });
  await waitFor(() => expect(screen.getByText("制作任务已受理")).toBeInTheDocument());
  expect(screen.getByRole("button", { name: "提交制作任务" })).toBeDisabled();
  expect(screen.getByRole("link", { name: "查看历史和处理进度" })).toHaveAttribute("href", "/history");
  expect(client.getQueryData<Task[]>(queryKeys.userTasks("alice-id"))?.[0]).toMatchObject({ id: "task-1", status: "QUEUED" });
});

it("shows a not-found template separately from an empty detail response", async () => {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false, status: 404, text: async () => '{"code":"TEMPLATE_NOT_FOUND","message":"模板不存在","traceId":"x"}' }));
  renderDetail();
  await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("找不到这个模板"));
  expect(screen.getByRole("button", { name: "重试" })).toBeEnabled();
  cleanup();
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true, status: 200, json: async () => null }));
  renderDetail();
  await waitFor(() => expect(screen.getByText("模板内容为空")).toBeInTheDocument());
  expect(screen.getByRole("button", { name: "重新加载" })).toBeEnabled();
});

it("retries an empty detail response and shows the recovered template", async () => {
  const fetchMock = vi.fn()
    .mockResolvedValueOnce({ ok: true, status: 200, json: async () => null })
    .mockResolvedValue({ ok: true, status: 200, json: async () => template });
  vi.stubGlobal("fetch", fetchMock);
  renderDetail();
  await screen.findByText("模板内容为空");
  fireEvent.click(screen.getByRole("button", { name: "重新加载" }));
  await screen.findByText("双图明信片");
  expect(fetchMock).toHaveBeenCalledTimes(2);
});
