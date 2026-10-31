import { act, cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import type { Asset, Template } from "@/lib/api";
import { UploadSlot } from "./upload-slot";

const mocks = vi.hoisted(() => ({ uploadAsset: vi.fn() }));
vi.mock("@/lib/api", async (load) => ({ ...(await load<typeof import("@/lib/api")>()), uploadAsset: mocks.uploadAsset }));
vi.mock("next/image", async () => {
  const { createElement } = await import("react");
  return { default: (props: { src: string; alt: string }) => createElement("img", props) };
});

const template: Template = {
  id: "template", title: "示例", category: "图片", coverUrl: "/cover.svg", description: "描述",
  requiredImageCount: 1, acceptedMimeTypes: ["image/png", "image/jpeg"], maxFileSizeBytes: 1024,
};

function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((done) => { resolve = done; });
  return { promise, resolve };
}

afterEach(() => {
  cleanup();
  mocks.uploadAsset.mockReset();
  Reflect.deleteProperty(URL, "createObjectURL");
  Reflect.deleteProperty(URL, "revokeObjectURL");
});

it("offers a focusable file button with a named input", () => {
  const { container } = render(<UploadSlot template={template} index={0} onChange={vi.fn()} />);
  const input = container.querySelector('input[type="file"]') as HTMLInputElement;
  const openFileDialog = vi.spyOn(input, "click").mockImplementation(() => {});
  const button = screen.getByRole("button", { name: "选择图片" });
  button.focus();
  expect(button).toHaveFocus();
  expect(button).toHaveClass("focus-visible:outline-2");
  expect(input).toHaveAccessibleName("为素材 1 选择图片");
  expect(input).toHaveAttribute("tabindex", "-1");
  fireEvent.click(button);
  expect(openFileDialog).toHaveBeenCalledOnce();
});

it("removing an uploading file aborts it, releases the preview, and ignores a late success", async () => {
  const revoke = vi.fn();
  Object.defineProperty(URL, "createObjectURL", { configurable: true, value: vi.fn(() => "blob:pending") });
  Object.defineProperty(URL, "revokeObjectURL", { configurable: true, value: revoke });
  const pending = deferred<Asset>();
  const abort = vi.fn();
  mocks.uploadAsset.mockReturnValue({ promise: pending.promise, abort });
  const onChange = vi.fn();
  const { container } = render(<UploadSlot template={template} index={0} onChange={onChange} />);
  fireEvent.change(container.querySelector('input[type="file"]')!, {
    target: { files: [new File(["a"], "pending.png", { type: "image/png" })] },
  });
  expect(screen.getByRole("progressbar")).toBeInTheDocument();
  fireEvent.click(screen.getByRole("button", { name: "移除素材 1" }));
  expect(abort).toHaveBeenCalledOnce();
  expect(revoke).toHaveBeenCalledWith("blob:pending");
  await act(async () => { pending.resolve({ id: "late", previewUrl: "/api/assets/late/content", mimeType: "image/png", sizeBytes: 1, width: 1, height: 1 }); });
  expect(onChange).toHaveBeenLastCalledWith(0, { phase: "empty", assetId: null });
  expect(screen.queryByAltText("素材 1 预览")).not.toBeInTheDocument();
});

it("aborts the old upload, ignores its late response and releases object URLs", async () => {
  let nextUrl = 0;
  const revoke = vi.fn();
  Object.defineProperty(URL, "createObjectURL", { configurable: true, value: vi.fn(() => `blob:local-${++nextUrl}`) });
  Object.defineProperty(URL, "revokeObjectURL", { configurable: true, value: revoke });
  const first = deferred<Asset>();
  const second = deferred<Asset>();
  const abortFirst = vi.fn();
  let progressFirst!: (percent: number) => void;
  mocks.uploadAsset.mockImplementationOnce((templateId: string, file: File, onProgress: (percent: number) => void) => {
    void templateId;
    void file;
    progressFirst = onProgress;
    return { promise: first.promise, abort: abortFirst };
  })
    .mockReturnValueOnce({ promise: second.promise, abort: vi.fn() });
  const onChange = vi.fn();
  const { container, unmount } = render(<UploadSlot template={template} index={0} onChange={onChange} />);
  const input = container.querySelector('input[type="file"]') as HTMLInputElement;
  fireEvent.change(input, { target: { files: [new File(["a"], "first.png", { type: "image/png" })] } });
  expect(onChange).toHaveBeenLastCalledWith(0, { phase: "uploading", assetId: null });
  act(() => progressFirst(42));
  expect(screen.getByRole("progressbar")).toHaveAttribute("aria-valuenow", "42");
  fireEvent.change(input, { target: { files: [new File(["b"], "second.png", { type: "image/png" })] } });
  expect(abortFirst).toHaveBeenCalledOnce();
  expect(revoke).toHaveBeenCalledWith("blob:local-1");
  await act(async () => {
    first.resolve({ id: "old", previewUrl: "/api/assets/old/content", mimeType: "image/png", sizeBytes: 1, width: 1, height: 1 });
    second.resolve({ id: "new", previewUrl: "/api/assets/new/content", mimeType: "image/png", sizeBytes: 1, width: 1, height: 1 });
  });
  await waitFor(() => expect(onChange).toHaveBeenLastCalledWith(0, { phase: "ready", assetId: "new" }));
  expect(onChange).not.toHaveBeenCalledWith(0, { phase: "ready", assetId: "old" });
  expect(screen.getByAltText("素材 1 预览")).toHaveAttribute("src", "/api/assets/new/content");
  expect(revoke).toHaveBeenCalledWith("blob:local-2");
  fireEvent.click(screen.getByRole("button", { name: "移除素材 1" }));
  expect(onChange).toHaveBeenLastCalledWith(0, { phase: "empty", assetId: null });
  unmount();
});

it("rejects a local over-limit selection without uploading", () => {
  Object.defineProperty(URL, "createObjectURL", { configurable: true, value: vi.fn(() => "blob:large") });
  Object.defineProperty(URL, "revokeObjectURL", { configurable: true, value: vi.fn() });
  const onChange = vi.fn();
  const { container } = render(<UploadSlot template={template} index={0} onChange={onChange} />);
  fireEvent.change(container.querySelector('input[type="file"]')!, {
    target: { files: [new File(["x".repeat(1025)], "large.png", { type: "image/png" })] },
  });
  expect(screen.getByRole("alert")).toHaveTextContent("不超过");
  expect(mocks.uploadAsset).not.toHaveBeenCalled();
});
