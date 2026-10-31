"use client";

import { useEffect } from "react";

export default function ErrorPage({ error, reset }: { error: Error & { digest?: string }; reset: () => void }) {
  useEffect(() => {
    console.error("Page error", error);
  }, [error]);

  return (
    <main className="mx-auto flex min-h-screen max-w-xl flex-col justify-center gap-4 px-6">
      <h1 className="text-2xl font-semibold">页面暂时无法显示</h1>
      <p>请重试。如果问题持续，请稍后返回。</p>
      <button type="button" onClick={reset} className="w-fit rounded-lg bg-sky-700 px-4 py-2 text-white focus-visible:outline-2 focus-visible:outline-offset-2">重试</button>
    </main>
  );
}
