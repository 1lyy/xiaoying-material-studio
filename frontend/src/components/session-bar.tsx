"use client";

import { useQuery } from "@tanstack/react-query";
import { useState } from "react";
import { useSession } from "@/components/session-provider";
import { api, ApiRequestError, errorMessage } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";

export function SessionBar() {
  const { user, loading, unavailable, unauthorized, busy, actionError, retry, switchUser, logout } = useSession();
  const [selected, setSelected] = useState("");
  const demos = useQuery({ queryKey: queryKeys.demoUsers, queryFn: ({ signal }) => api.demoUsers(signal), retry: false });
  const activeKey = selected || user?.demoUserKey || "";

  return (
    <div className="flex flex-wrap items-center justify-end gap-2 text-sm">
      {loading ? <span role="status" className="text-slate-500">正在确认身份…</span> : null}
      {!loading && user ? <span className="hidden text-slate-600 sm:inline">当前：<strong className="text-slate-950">{user.displayName}</strong></span> : null}
      {!loading && unauthorized ? <span className="text-amber-700">请选演示用户</span> : null}
      {unavailable ? <button type="button" className="text-sky-800 underline underline-offset-2 focus-visible:outline-2" onClick={() => void retry()}>会话暂不可用，重试</button> : null}
      {demos.data && demos.data.length > 0 ? (
        <div className="flex items-center gap-2">
          <label htmlFor="demo-user" className="sr-only">演示用户</label>
          <select id="demo-user" value={activeKey} onChange={(event) => setSelected(event.target.value)}
            disabled={busy} className="rounded-lg border border-slate-300 bg-white px-3 py-2 text-slate-900 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700">
            {!activeKey ? <option value="">选择演示用户</option> : null}
            {demos.data.map((demo) => <option key={demo.id} value={demo.demoUserKey}>{demo.displayName}</option>)}
          </select>
          <button type="button" disabled={busy || !activeKey || activeKey === user?.demoUserKey}
            onClick={() => void switchUser(activeKey)}
            className="rounded-lg bg-slate-900 px-3 py-2 font-medium text-white hover:bg-slate-700 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700 disabled:cursor-not-allowed disabled:opacity-40">
            {busy ? "处理中…" : user ? "切换" : "进入"}
          </button>
        </div>
      ) : null}
      {demos.data?.length === 0 ? <span className="text-xs text-slate-500" role="status">暂无演示用户</span> : null}
      {user ? <button type="button" disabled={busy} onClick={() => void logout()}
        className="rounded-lg px-2 py-2 text-slate-600 hover:text-slate-950 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700 disabled:opacity-40">退出</button> : null}
      {demos.isError && !unavailable ? (
        <div className="w-full text-right text-xs text-slate-500" role="status">
          {demos.error instanceof ApiRequestError && demos.error.status === 404
            ? "当前环境未开放演示用户选择"
            : <><span>{errorMessage(demos.error, "用户列表加载失败")}</span>{" "}<button type="button" className="underline" onClick={() => void demos.refetch()}>重试</button></>}
        </div>
      ) : null}
      {actionError ? <p className="w-full text-right text-xs text-red-700" role="alert">{errorMessage(actionError, "切换失败，请重试")}</p> : null}
    </div>
  );
}
