import Link from "next/link";
import { SessionBar } from "@/components/session-bar";

export function SiteShell({ children }: { children: React.ReactNode }) {
  return (
    <div className="min-h-screen bg-[#f8f7f3] text-slate-950">
      <header className="border-b border-slate-200/80 bg-white/95">
        <div className="mx-auto flex w-full max-w-[1320px] flex-wrap items-center justify-between gap-4 px-5 py-4 sm:px-8 lg:px-12">
          <Link href="/" className="flex items-center gap-3 rounded-md focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-sky-700" aria-label="映刻工坊，返回模板首页">
            <span className="flex size-9 items-center justify-center rounded-xl bg-[#153d56] text-lg font-black text-white" aria-hidden="true">映</span>
            <span className="text-lg font-bold tracking-tight">映刻工坊 <span className="ml-1 text-xs font-normal tracking-[0.22em] text-slate-400">STUDIO</span></span>
          </Link>
          <div className="flex flex-1 flex-wrap items-center justify-end gap-4 sm:gap-8">
            <nav aria-label="主导航" className="flex items-center gap-4"><Link href="/" className="rounded-md text-sm font-semibold text-slate-700 hover:text-[#196184] focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-sky-700">探索模板</Link><Link href="/history" className="rounded-md text-sm font-semibold text-slate-700 hover:text-[#196184] focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-sky-700">制作记录</Link></nav>
            <SessionBar />
          </div>
        </div>
      </header>
      {children}
      <footer className="mt-16 border-t border-slate-200 px-5 py-8 text-center text-xs text-slate-500">
        映刻工坊 · 用自己的图片，完成一份独特创作
      </footer>
    </div>
  );
}
