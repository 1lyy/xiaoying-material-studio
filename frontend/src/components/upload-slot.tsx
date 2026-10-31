"use client";

import Image from "next/image";
import { useEffect, useRef, useState } from "react";
import { ApiRequestError, errorMessage, uploadAsset, type Asset, type Template, type UploadRequest } from "@/lib/api";

export type SlotPhase = "empty" | "validating" | "uploading" | "ready" | "error";
export type SlotValue = { phase: SlotPhase; assetId: string | null };
type SlotView = { phase: SlotPhase; previewUrl: string | null; fileName: string | null; progress: number; message: string | null; asset: Asset | null };
const EMPTY: SlotView = { phase: "empty", previewUrl: null, fileName: null, progress: 0, message: null, asset: null };

function allowedExtension(file: File): boolean {
  const lower = file.name.toLowerCase();
  return file.type === "image/png" ? lower.endsWith(".png")
    : file.type === "image/jpeg" && (lower.endsWith(".jpg") || lower.endsWith(".jpeg"));
}

export function UploadSlot({ template, index, onChange, onUnauthorized, disabled = false }: {
  template: Template;
  index: number;
  onChange: (index: number, value: SlotValue) => void;
  onUnauthorized?: () => void;
  disabled?: boolean;
}) {
  const [view, setView] = useState<SlotView>(EMPTY);
  const request = useRef<UploadRequest | null>(null);
  const generation = useRef(0);
  const localUrl = useRef<string | null>(null);
  const input = useRef<HTMLInputElement | null>(null);
  const inputId = `upload-${template.id}-${index}`;
  const maxSize = Math.min(template.maxFileSizeBytes, 5 * 1024 * 1024);

  function clearPrevious() {
    generation.current += 1;
    request.current?.abort();
    request.current = null;
    if (localUrl.current) URL.revokeObjectURL(localUrl.current);
    localUrl.current = null;
    if (input.current) input.current.value = "";
  }

  useEffect(() => () => {
    generation.current += 1;
    request.current?.abort();
    if (localUrl.current) URL.revokeObjectURL(localUrl.current);
  }, []);

  function remove() {
    clearPrevious();
    setView(EMPTY);
    onChange(index, { phase: "empty", assetId: null });
  }

  function select(file: File | undefined) {
    if (!file) return;
    clearPrevious();
    const current = generation.current;
    setView({ phase: "validating", previewUrl: null, fileName: file.name, progress: 0, message: null, asset: null });
    onChange(index, { phase: "validating", assetId: null });
    if (!template.acceptedMimeTypes.includes(file.type) || !allowedExtension(file)) {
      setView({ phase: "error", previewUrl: null, fileName: file.name, progress: 0, message: "请选择 JPG 或 PNG 图片，并检查文件扩展名", asset: null });
      onChange(index, { phase: "error", assetId: null });
      return;
    }
    if (file.size === 0 || file.size > maxSize) {
      setView({ phase: "error", previewUrl: null, fileName: file.name, progress: 0, message: `图片必须非空，且不超过 ${(maxSize / 1024 / 1024).toFixed(1)} MiB`, asset: null });
      onChange(index, { phase: "error", assetId: null });
      return;
    }
    const preview = URL.createObjectURL(file);
    localUrl.current = preview;
    setView({ phase: "uploading", previewUrl: preview, fileName: file.name, progress: 0, message: null, asset: null });
    onChange(index, { phase: "uploading", assetId: null });
    const upload = uploadAsset(template.id, file, (progress) => {
      if (generation.current === current) setView((previous) => ({ ...previous, progress }));
    });
    request.current = upload;
    void upload.promise.then((asset) => {
      if (generation.current !== current) return;
      request.current = null;
      if (localUrl.current) URL.revokeObjectURL(localUrl.current);
      localUrl.current = null;
      setView({ phase: "ready", previewUrl: asset.previewUrl, fileName: file.name, progress: 100, message: null, asset });
      onChange(index, { phase: "ready", assetId: asset.id });
    }).catch((error: unknown) => {
      if (generation.current !== current || (error instanceof Error && error.name === "AbortError")) return;
      request.current = null;
      if (error instanceof ApiRequestError && error.isUnauthorized) onUnauthorized?.();
      setView((previous) => ({ ...previous, phase: "error", message: errorMessage(error, "上传失败，请替换图片重试") }));
      onChange(index, { phase: "error", assetId: null });
    });
  }

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-4 shadow-sm" aria-labelledby={`${inputId}-title`}>
      <div className="mb-3 flex items-center justify-between gap-3">
        <h3 id={`${inputId}-title`} className="font-semibold text-slate-950">素材 {String(index + 1).padStart(2, "0")}</h3>
        <span className={`rounded-full px-2.5 py-1 text-xs font-semibold ${view.phase === "ready" ? "bg-emerald-50 text-emerald-700" : view.phase === "error" ? "bg-red-50 text-red-700" : "bg-slate-100 text-slate-500"}`}>
          {{ empty: "待添加", validating: "检查中", uploading: "上传中", ready: "已就绪", error: "需要处理" }[view.phase]}
        </span>
      </div>
      <div className="relative flex min-h-48 items-center justify-center overflow-hidden rounded-xl border border-dashed border-slate-300 bg-[#f4f7f7] focus-within:outline-2 focus-within:outline-offset-2 focus-within:outline-sky-700">
        {view.previewUrl ? <Image src={view.previewUrl} alt={`素材 ${index + 1} 预览`} fill unoptimized sizes="(max-width: 1024px) 100vw, 40vw" className="object-contain" /> : <div className="px-4 text-center"><span className="block text-4xl text-[#8aa9b5]" aria-hidden="true">＋</span><p className="mt-2 text-sm text-slate-600">选择一张图片</p></div>}
        <input ref={input} id={inputId} type="file" accept={template.acceptedMimeTypes.join(",")} disabled={disabled} tabIndex={-1}
          aria-label={`为素材 ${index + 1} 选择图片`}
          className="sr-only" aria-describedby={`${inputId}-help ${inputId}-status`}
          onChange={(event) => select(event.currentTarget.files?.[0])} />
      </div>
      <p id={`${inputId}-help`} className="mt-3 text-xs leading-5 text-slate-500">JPG / PNG · 单张不超过 {(maxSize / 1024 / 1024).toFixed(1)} MiB</p>
      <p id={`${inputId}-status`} className={`mt-2 min-h-5 text-xs ${view.phase === "error" ? "text-red-700" : "text-slate-600"}`} role={view.phase === "error" ? "alert" : "status"}>
        {view.message ?? (view.phase === "uploading" ? view.progress < 100 ? `正在上传 ${view.progress}%` : "正在等待服务器校验…" : view.fileName ?? "尚未选择图片")}
      </p>
      {view.phase === "uploading" ? <div className="mt-2 h-1.5 overflow-hidden rounded-full bg-slate-100" role="progressbar" aria-label={`素材 ${index + 1} 上传进度`} aria-valuenow={view.progress} aria-valuemin={0} aria-valuemax={100}><div className="h-full bg-[#1c7295] transition-all" style={{ width: `${view.progress}%` }} /></div> : null}
      <div className="mt-4 flex gap-2">
        <button type="button" disabled={disabled} onClick={() => input.current?.click()}
          className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold text-slate-800 hover:bg-slate-50 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700 disabled:cursor-not-allowed disabled:opacity-50">{view.phase === "empty" ? "选择图片" : "替换图片"}</button>
        {view.phase !== "empty" ? <button type="button" onClick={remove} aria-label={`移除素材 ${index + 1}`} className="rounded-lg px-3 py-2 text-sm font-medium text-slate-600 hover:bg-slate-100 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700">移除</button> : null}
      </div>
    </section>
  );
}
