"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import Image from "next/image";
import Link from "next/link";
import { useParams } from "next/navigation";
import { useCallback, useEffect, useRef, useState } from "react";
import { useSession } from "@/components/session-provider";
import { UploadSlot, type SlotValue } from "@/components/upload-slot";
import { api, ApiRequestError, errorMessage, type Task, type Template } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import { rememberAcceptedTask } from "@/lib/task-cache";

function Studio({ template }: { template: Template }) {
  const { user, identityEpoch, unavailable, busy } = useSession();
  return <StudioForIdentity key={`${user?.id ?? "anonymous"}:${identityEpoch}:${template.id}`} template={template} userId={user?.id ?? null} sessionUnavailable={unavailable} sessionSwitching={busy} />;
}

function StudioForIdentity({ template, userId, sessionUnavailable, sessionSwitching }: {
  template: Template; userId: string | null; sessionUnavailable: boolean; sessionSwitching: boolean;
}) {
  const client = useQueryClient();
  const [slots, setSlots] = useState<SlotValue[]>(() => Array.from({ length: template.requiredImageCount }, () => ({ phase: "empty", assetId: null })));
  const [slotGeneration, setSlotGeneration] = useState(0);
  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<unknown>(null);
  const [accepted, setAccepted] = useState<Task | null>(null);
  const submitController = useRef<AbortController | null>(null);
  const inFlight = useRef(false);
  useEffect(() => () => submitController.current?.abort(), []);
  useEffect(() => { if (sessionSwitching) submitController.current?.abort(); }, [sessionSwitching]);

  const onSlotChange = useCallback((index: number, value: SlotValue) => {
    setSlots((previous) => previous.map((slot, position) => position === index ? value : slot));
  }, []);
  const ready = slots.length === template.requiredImageCount && slots.every((slot) => slot.phase === "ready" && slot.assetId);
  const busyUpload = slots.some((slot) => slot.phase === "validating" || slot.phase === "uploading");

  async function submit() {
    if (inFlight.current || !ready || !userId || sessionUnavailable || sessionSwitching) return;
    const assetIds = slots.map((slot) => slot.assetId).filter((id): id is string => id !== null);
    if (assetIds.length !== template.requiredImageCount || new Set(assetIds).size !== assetIds.length) {
      setSubmitError(new Error("请为每个上传位选择不同的图片"));
      return;
    }
    inFlight.current = true;
    setSubmitting(true);
    setSubmitError(null);
    const controller = new AbortController();
    submitController.current = controller;
    try {
      const task = await api.createTask(template.id, assetIds, controller.signal);
      if (controller.signal.aborted) return;
      rememberAcceptedTask(client, userId, task);
      setAccepted(task);
      setSlots(Array.from({ length: template.requiredImageCount }, () => ({ phase: "empty", assetId: null })));
      setSlotGeneration((value) => value + 1);
      void client.invalidateQueries({ queryKey: queryKeys.userTasks(userId) });
    } catch (error) {
      if (controller.signal.aborted) return;
      setSubmitError(error);
      if (error instanceof ApiRequestError && error.isUnauthorized) {
        void client.invalidateQueries({ queryKey: queryKeys.session });
      }
    } finally {
      inFlight.current = false;
      submitController.current = null;
      if (!controller.signal.aborted) setSubmitting(false);
    }
  }

  return (
    <div className="rounded-3xl border border-slate-200 bg-[#fcfcfa] p-5 shadow-[0_18px_50px_rgba(35,54,64,0.07)] sm:p-8">
      <div className="flex flex-wrap items-start justify-between gap-4 border-b border-slate-200 pb-6">
        <div><p className="text-xs font-bold tracking-[0.2em] text-[#41738a]">YOUR MATERIALS</p><h2 className="mt-2 text-2xl font-bold tracking-tight">上传你的图片</h2><p className="mt-2 text-sm leading-6 text-slate-600">按顺序填满 {template.requiredImageCount} 个素材位。每张图片上传成功后才可提交。</p></div>
        <span className="rounded-full bg-[#e6f0f2] px-4 py-2 text-sm font-semibold text-[#245c73]">{slots.filter((slot) => slot.phase === "ready").length} / {template.requiredImageCount} 已就绪</span>
      </div>
      <div className="mt-6 grid gap-4 sm:grid-cols-2 xl:grid-cols-1 2xl:grid-cols-2">
        {slots.map((_, index) => <UploadSlot key={`${slotGeneration}-${index}`} template={template} index={index} onChange={onSlotChange} onUnauthorized={() => void client.invalidateQueries({ queryKey: queryKeys.session })} disabled={!userId || sessionUnavailable || sessionSwitching} />)}
      </div>
      <div className="mt-7 rounded-xl bg-[#eff4f3] p-4 text-sm leading-6 text-slate-700">
        <strong className="text-slate-900">素材要求</strong>
        <p className="mt-1">支持 {template.acceptedMimeTypes.map((mime) => mime === "image/jpeg" ? "JPG" : mime === "image/png" ? "PNG" : mime).join(" / ")}；每张不超过 {(Math.min(template.maxFileSizeBytes, 5 * 1024 * 1024) / 1024 / 1024).toFixed(1)} MiB。服务器会再次检查图片内容。</p>
      </div>
      {!userId ? <p className="mt-5 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900" role="status">请先在顶栏选择演示用户，再上传和提交。</p> : null}
      {sessionUnavailable ? <p className="mt-5 rounded-xl border border-amber-200 bg-amber-50 p-4 text-sm text-amber-900" role="alert">会话服务暂不可用，请在顶栏重试。已选择的素材会保留在当前页面。</p> : null}
      {sessionSwitching ? <p className="mt-5 rounded-xl border border-slate-200 bg-slate-50 p-4 text-sm text-slate-700" role="status">正在切换用户，上传和提交已暂停。</p> : null}
      {accepted ? <div className="mt-5 rounded-xl border border-emerald-200 bg-emerald-50 p-4" role="status"><p className="font-semibold text-emerald-900">制作任务已受理</p><p className="mt-1 break-all text-sm text-emerald-800">任务编号：{accepted.id} · 当前状态：{accepted.status === "QUEUED" ? "等待处理" : accepted.status === "RUNNING" ? "后台处理中" : accepted.status === "SUCCEEDED" ? "已完成" : "制作失败"}</p><p className="mt-1 text-xs text-emerald-800">你可以继续上传图片并创建下一项任务。</p><Link href="/history" className="mt-2 inline-block rounded font-semibold text-emerald-900 underline underline-offset-2 focus-visible:outline-2">查看历史和处理进度</Link></div> : null}
      {submitError ? <div className="mt-5 rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800" role="alert"><p>{submitError instanceof ApiRequestError && submitError.code === "REQUEST_TIMEOUT" ? "提交请求超时，无法确认服务端是否已受理。" : errorMessage(submitError, submitError instanceof Error ? submitError.message : "提交失败")}</p>{submitError instanceof ApiRequestError && submitError.isUnavailable ? <p className="mt-1 text-xs">请先<Link href="/history" className="mx-1 font-semibold underline">刷新历史记录</Link>核对是否已创建；本页不会自动重发提交。</p> : null}</div> : null}
      <button type="button" onClick={() => void submit()} disabled={!ready || busyUpload || submitting || !userId || sessionUnavailable || sessionSwitching}
        className="mt-6 w-full rounded-xl bg-[#155678] px-6 py-4 text-base font-bold text-white shadow-sm transition hover:bg-[#0e405c] focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-sky-700 disabled:cursor-not-allowed disabled:bg-slate-300 disabled:text-slate-600">
        {submitting ? "正在提交请求…" : busyUpload ? "等待图片上传完成" : "提交制作任务"}
      </button>
    </div>
  );
}

export default function TemplateDetailPage() {
  const { id } = useParams<{ id: string }>();
  // Treat an unexpected empty 200 response as an empty state that can be retried.
  const detail = useQuery<Template | null>({ queryKey: queryKeys.template(id), queryFn: ({ signal }) => api.template(id, signal), enabled: Boolean(id) });
  return (
    <main className="mx-auto max-w-[1320px] px-5 py-8 sm:px-8 lg:px-12 lg:py-12">
      <nav aria-label="面包屑" className="mb-7 text-sm text-slate-500"><Link href="/" className="rounded hover:text-sky-800 focus-visible:outline-2">模板列表</Link><span className="mx-2" aria-hidden="true">/</span><span className="text-slate-800">模板详情</span></nav>
      {detail.isPending ? <div className="rounded-2xl border border-slate-200 bg-white p-12 text-slate-600" role="status">正在加载模板要求…</div> : null}
      {detail.isError ? <div className="rounded-2xl border border-amber-200 bg-amber-50 p-8" role="alert"><h1 className="text-2xl font-bold">{detail.error instanceof ApiRequestError && detail.error.status === 404 ? "找不到这个模板" : detail.error instanceof ApiRequestError && detail.error.status === 400 ? "模板链接无效" : "模板加载失败"}</h1><p className="mt-2 text-sm text-slate-700">{errorMessage(detail.error)}</p><div className="mt-5 flex gap-4"><button type="button" onClick={() => void detail.refetch()} className="rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white focus-visible:outline-2">重试</button><Link href="/" className="rounded-lg border border-slate-300 px-4 py-2 text-sm font-semibold focus-visible:outline-2">返回模板列表</Link></div></div> : null}
      {!detail.isPending && !detail.isError && !detail.data ? <div className="rounded-2xl border border-slate-200 bg-white p-8" role="status"><h1 className="text-xl font-bold">模板内容为空</h1><button type="button" onClick={() => void detail.refetch()} className="mt-4 rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white focus-visible:outline-2">重新加载</button></div> : null}
      {detail.data ? <div className="grid gap-10 lg:grid-cols-[minmax(0,0.9fr)_minmax(0,1.1fr)] lg:items-start">
        <div className="lg:sticky lg:top-8"><div className="relative aspect-[4/3] overflow-hidden rounded-3xl bg-[#dfe9e9] shadow-[0_18px_60px_rgba(24,49,58,0.12)]"><Image src={detail.data.coverUrl} alt={`${detail.data.title}的示例效果`} fill unoptimized sizes="(max-width: 1024px) 100vw, 45vw" className="object-cover" /></div><div className="mt-8"><span className="rounded-full bg-[#e4eff1] px-3 py-1.5 text-xs font-bold text-[#235a73]">{detail.data.category}</span><h1 className="mt-5 text-3xl font-bold tracking-tight sm:text-4xl">{detail.data.title}</h1><p className="mt-4 max-w-xl text-base leading-8 text-slate-600">{detail.data.description}</p><div className="mt-7 grid grid-cols-2 gap-3"><div className="rounded-xl border border-slate-200 bg-white p-4"><p className="text-xs text-slate-500">需要素材</p><p className="mt-1 text-xl font-bold">{detail.data.requiredImageCount} 张</p></div><div className="rounded-xl border border-slate-200 bg-white p-4"><p className="text-xs text-slate-500">单张上限</p><p className="mt-1 text-xl font-bold">{(Math.min(detail.data.maxFileSizeBytes, 5 * 1024 * 1024) / 1024 / 1024).toFixed(1)} MiB</p></div></div></div></div>
        <Studio template={detail.data} />
      </div> : null}
    </main>
  );
}
