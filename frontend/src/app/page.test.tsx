import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { afterEach, expect, it, vi } from "vitest";
import type { Template } from "@/lib/api";
import Home from "./page";

vi.mock("next/image", async () => {
  const { createElement } = await import("react");
  return { default: (props: { src: string; alt: string }) => createElement("img", props) };
});
vi.mock("next/link", async () => {
  const { createElement } = await import("react");
  return { default: ({ href, children, className }: { href: string; children: ReactNode; className?: string }) => createElement("a", { href, className }, children) };
});

const templates: Template[] = [
  { id: "a", title: "明信片", category: "创意图片", coverUrl: "/api/templates/a/cover", description: "旅行主题", requiredImageCount: 2, acceptedMimeTypes: ["image/png"], maxFileSizeBytes: 1000 },
  { id: "b", title: "祝福卡", category: "节日贺卡", coverUrl: "/api/templates/b/cover", description: "节日主题", requiredImageCount: 1, acceptedMimeTypes: ["image/png"], maxFileSizeBytes: 1000 },
];

function renderHome() {
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  return render(<QueryClientProvider client={client}><Home /></QueryClientProvider>);
}

afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

it("gets categories and cards from Java and requests a selected category", async () => {
  const fetchMock = vi.fn((url: string) => Promise.resolve({
    ok: true, status: 200,
    json: async () => url.includes("category=") ? [templates[0]] : templates,
  }));
  vi.stubGlobal("fetch", fetchMock);
  renderHome();
  expect(screen.getByRole("status")).toHaveTextContent("正在加载");
  await waitFor(() => expect(screen.getByRole("link", { name: /明信片/ })).toBeInTheDocument());
  expect(screen.getByRole("button", { name: "节日贺卡" })).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "创意图片" }));
  await waitFor(() => expect(fetchMock).toHaveBeenCalledWith(
    "/api/templates?category=%E5%88%9B%E6%84%8F%E5%9B%BE%E7%89%87", expect.objectContaining({ cache: "no-store" }),
  ));
  expect(screen.getByRole("button", { name: "创意图片" })).toHaveAttribute("aria-pressed", "true");
});

it("distinguishes an empty result from a failed request and offers retry", async () => {
  const fetchMock = vi.fn().mockResolvedValueOnce({ ok: false, status: 503, text: async () => '{"code":"UNAVAILABLE","message":"稍后重试","traceId":"t"}' })
    .mockResolvedValueOnce({ ok: true, status: 200, json: async () => [] });
  vi.stubGlobal("fetch", fetchMock);
  renderHome();
  await waitFor(() => expect(screen.getByRole("alert")).toHaveTextContent("模板加载失败"));
  fireEvent.click(screen.getByRole("button", { name: "重试加载" }));
  await waitFor(() => expect(screen.getByText("这里暂时没有模板")).toBeInTheDocument());
  expect(screen.queryByRole("alert")).not.toBeInTheDocument();
});
