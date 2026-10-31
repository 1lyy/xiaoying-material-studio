"use client";

import { useState } from "react";

type HealthState = "idle" | "checking" | "up" | "unavailable";

export function HealthCheck() {
  const [state, setState] = useState<HealthState>("idle");

  async function checkHealth() {
    setState("checking");
    try {
      const response = await fetch("/api/health", { cache: "no-store" });
      if (!response.ok) throw new Error("health request failed");
      const result: unknown = await response.json();
      setState(
        typeof result === "object" && result !== null && "status" in result && result.status === "UP"
          ? "up"
          : "unavailable",
      );
    } catch {
      setState("unavailable");
    }
  }

  const label = {
    idle: "尚未检查 Java API",
    checking: "正在检查 Java API…",
    up: "Java API 可用",
    unavailable: "Java API 暂不可用，请检查后重试",
  }[state];

  return (
    <section className="rounded-2xl border border-slate-200 bg-white p-6 shadow-sm" aria-label="API 健康检查">
      <h2 className="text-lg font-semibold text-slate-900">连接检查</h2>
      <p className="mt-2 text-sm text-slate-600" role="status">{label}</p>
      <button
        type="button"
        onClick={checkHealth}
        disabled={state === "checking"}
        className="mt-5 rounded-lg bg-sky-700 px-4 py-2 font-medium text-white hover:bg-sky-800 focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700 disabled:cursor-wait disabled:opacity-60"
      >
        重新检查
      </button>
    </section>
  );
}
