"use client";

import Image from "next/image";
import Link from "next/link";
import { useState } from "react";
import { useSession } from "@/components/session-provider";
import { useTasks } from "@/components/task-tracker";
import { ApiRequestError, errorMessage, type StepStatus, type Task, type TaskStage, type TaskStatus } from "@/lib/api";

const stageNames: Record<TaskStage, string> = {
  ASSET_CHECK: "素材检查", GENERATE: "模拟生成", FINALIZE: "结果整理",
};
const statusNames: Record<TaskStatus, string> = {
  QUEUED: "等待处理", RUNNING: "处理中", SUCCEEDED: "制作成功", FAILED: "制作失败",
};
const stepNames: Record<StepStatus, string> = {
  PENDING: "未开始", RUNNING: "进行中", SUCCEEDED: "已完成", FAILED: "失败", SKIPPED: "已跳过",
};

function ResultPreview({ url, title }: { url: string; title: string }) {
  const [failed, setFailed] = useState(false);
  const [attempt, setAttempt] = useState(0);
  const src = attempt === 0 ? url : `${url}${url.includes("?") ? "&" : "?"}previewRetry=${attempt}`;
  return (
    <div className="mt-5 overflow-hidden rounded-xl border border-slate-200 bg-slate-50">
      {failed ? <div className="p-5 text-sm text-amber-900" role="alert">结果图片暂时无法加载。<button type="button" className="ml-2 rounded font-semibold underline focus-visible:outline-2" onClick={() => { setFailed(false); setAttempt((value) => value + 1); }}>重试图片</button></div> :
        <Image key={src} src={src} alt={`${title}的制作结果`} width={900} height={600} unoptimized onError={() => setFailed(true)} className="h-auto max-h-[420px] w-full object-contain" />}
    </div>
  );
}

function TaskCard({ task }: { task: Task }) {
  const badge = task.status === "SUCCEEDED" ? "bg-emerald-100 text-emerald-900" : task.status === "FAILED" ? "bg-red-100 text-red-900" : "bg-sky-100 text-sky-900";
  return (
    <article className="rounded-2xl border border-slate-200 bg-white p-5 shadow-sm sm:p-7" aria-label={`${task.templateTitle}，${statusNames[task.status]}`}>
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="min-w-0"><p className="text-xs font-semibold tracking-[0.12em] text-slate-500">{new Date(task.createdAt).toLocaleString("zh-CN", { timeZone: "Asia/Shanghai" })} · 任务 {task.id}</p><h2 className="mt-2 text-xl font-bold text-slate-950">{task.templateTitle}</h2></div>
        <span className={`rounded-full px-3 py-1.5 text-sm font-semibold ${badge}`}>{statusNames[task.status]}</span>
      </div>
      <p className="mt-3 text-sm text-slate-600">{task.status === "QUEUED" ? "已受理，等待后台领取" : task.status === "FAILED" && task.failedStage ? `失败阶段：${stageNames[task.failedStage]}` : task.stage ? `当前阶段：${stageNames[task.stage]}` : "等待服务端更新阶段"}</p>
      {task.status === "FAILED" ? <p className="mt-3 rounded-lg bg-red-50 p-3 text-sm text-red-900" role="status">{task.errorMessage ?? "制作失败，请查看任务记录"}{task.errorCode ? `（${task.errorCode}）` : ""}</p> : null}
      <ol className="mt-5 grid gap-2 sm:grid-cols-3" aria-label="制作步骤">
        {task.steps.map((step) => <li key={step.stage} className="rounded-xl border border-slate-200 bg-slate-50 p-3"><p className="text-sm font-semibold text-slate-900">{stageNames[step.stage]}</p><p className="mt-1 text-xs text-slate-600">{stepNames[step.status]}</p>{step.errorMessage ? <p className="mt-1 text-xs text-red-800">{step.errorMessage}</p> : null}</li>)}
      </ol>
      {task.status === "SUCCEEDED" && task.resultUrl ? <ResultPreview key={task.resultUrl} url={task.resultUrl} title={task.templateTitle} /> : null}
    </article>
  );
}

export default function HistoryPage() {
  const { user, loading, unavailable, busy } = useSession();
  const tasks = useTasks(user?.id ?? null, !busy, false);
  const items = tasks.data;

  return <main className="mx-auto max-w-[1100px] px-5 py-9 sm:px-8 lg:px-12 lg:py-12">
    <div className="flex flex-wrap items-end justify-between gap-4"><div><p className="text-xs font-bold tracking-[0.2em] text-[#41738a]">YOUR CREATIONS</p><h1 className="mt-2 text-3xl font-bold tracking-tight">制作记录</h1><p className="mt-3 text-sm text-slate-600">任务由 Java 后台处理；这里展示服务器保存的进度与结果。</p></div><Link href="/" className="rounded-xl bg-[#155678] px-4 py-2.5 text-sm font-semibold text-white focus-visible:outline-2 focus-visible:outline-offset-2">选择新模板</Link></div>
    {loading || busy ? <div className="mt-8 rounded-xl bg-white p-8 text-slate-600" role="status">正在确认当前用户…</div> : null}
    {!loading && !busy && !user ? <div className="mt-8 rounded-xl border border-amber-200 bg-amber-50 p-8" role={unavailable ? "alert" : "status"}><h2 className="text-lg font-bold">{unavailable ? "会话服务暂不可用" : "请先选择演示用户"}</h2><p className="mt-2 text-sm">{unavailable ? "请在顶栏重试会话查询。" : "在顶栏进入 Alice 或 Bob，查看各自的制作记录。"}</p></div> : null}
    {user && !busy && tasks.isPending ? <div className="mt-8 rounded-xl bg-white p-8 text-slate-600" role="status">正在加载历史记录…</div> : null}
    {user && !busy && tasks.isError ? <div className="mt-8 rounded-xl border border-amber-200 bg-amber-50 p-5" role="alert"><h2 className="font-bold">{items ? "历史更新暂时失败，保留上次记录" : "历史记录加载失败"}</h2><p className="mt-1 text-sm">{tasks.error instanceof ApiRequestError && tasks.error.isUnauthorized ? "会话已失效，请在顶栏重新选择演示用户。" : errorMessage(tasks.error)}</p><button type="button" onClick={() => void tasks.refetch()} disabled={tasks.isFetching} className="mt-3 rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white focus-visible:outline-2 disabled:opacity-50">{tasks.isFetching ? "正在重试…" : "重新查询"}</button></div> : null}
    {user && !busy && items && !tasks.isError && items.length === 0 ? <div className="mt-8 rounded-xl border border-slate-200 bg-white p-8 text-slate-600" role="status">还没有制作记录。选择模板并提交任务后，这里会显示历史和结果。</div> : null}
    {user && !busy && items && items.length > 0 ? <div className="mt-8 space-y-5"><p className="text-sm text-slate-500" aria-live="polite">共 {items.length} 项{items.some((task) => task.status === "QUEUED" || task.status === "RUNNING") ? " · 有任务处理中" : ""}</p>{items.map((task) => <TaskCard key={task.id} task={task} />)}</div> : null}
  </main>;
}
