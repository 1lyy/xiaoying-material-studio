"use client";

import { useQuery } from "@tanstack/react-query";
import Image from "next/image";
import Link from "next/link";
import { useState } from "react";
import { api, ApiRequestError, errorMessage, type Template } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";

function TemplateCard({ template, index }: { template: Template; index: number }) {
  return (
    <Link href={`/templates/${template.id}`} className="group overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-[0_10px_30px_rgba(27,45,55,0.04)] transition hover:-translate-y-1 hover:shadow-[0_18px_45px_rgba(27,45,55,0.12)] focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-sky-700">
      <div className="relative aspect-[4/3] overflow-hidden bg-[#e8edf0]">
        <Image src={template.coverUrl} alt={`${template.title}模板示例`} fill unoptimized sizes="(max-width: 640px) 100vw, (max-width: 1024px) 50vw, 33vw" className="object-cover transition duration-300 group-hover:scale-[1.035]" />
        <span className="absolute left-4 top-4 rounded-full bg-white/95 px-3 py-1 text-xs font-semibold text-[#235571] shadow-sm">{template.category}</span>
        <span className="absolute bottom-4 right-4 rounded-full bg-slate-950/75 px-3 py-1 text-xs font-semibold text-white">{template.requiredImageCount} 张素材</span>
      </div>
      <div className="p-5 sm:p-6">
        <div className="mb-3 flex items-start justify-between gap-3">
          <h3 className="text-xl font-semibold tracking-tight text-slate-950">{template.title}</h3>
          <span className="text-xs font-medium text-slate-400">0{index + 1}</span>
        </div>
        <p className="line-clamp-2 min-h-12 text-sm leading-6 text-slate-600">{template.description}</p>
        <span className="mt-5 inline-flex items-center gap-2 text-sm font-semibold text-[#1a6687]">开始制作 <span aria-hidden="true" className="transition group-hover:translate-x-1">→</span></span>
      </div>
    </Link>
  );
}

export default function Home() {
  const [category, setCategory] = useState<string | null>(null);
  const all = useQuery({ queryKey: queryKeys.templates, queryFn: ({ signal }) => api.templates(undefined, signal) });
  const filtered = useQuery({
    queryKey: queryKeys.templatesByCategory(category ?? ""),
    queryFn: ({ signal }) => api.templates(category ?? undefined, signal),
    enabled: category !== null,
  });
  const active = category === null ? all : filtered;
  const categories = [...new Set((all.data ?? []).map((template) => template.category))];

  return (
    <main>
      <section className="relative overflow-hidden bg-[#11384e] text-white">
        <div className="absolute -right-24 -top-44 size-[530px] rounded-full border border-white/10" aria-hidden="true" />
        <div className="absolute -right-5 top-5 size-[360px] rounded-full border border-white/10" aria-hidden="true" />
        <div className="mx-auto grid max-w-[1320px] gap-12 px-5 py-16 sm:px-8 sm:py-20 lg:grid-cols-[1.2fr_.8fr] lg:items-center lg:px-12 lg:py-24">
          <div className="relative z-10">
            <p className="mb-5 text-xs font-bold tracking-[0.3em] text-[#abd2de]">CREATE WITH YOUR MOMENTS</p>
            <h1 className="max-w-3xl text-4xl font-bold leading-[1.2] tracking-tight sm:text-5xl lg:text-6xl">把你的照片，<br /><span className="text-[#f7ce8b]">做成值得收藏的作品。</span></h1>
            <p className="mt-6 max-w-xl text-base leading-8 text-[#d5e3e8]">选择喜欢的模板，上传自己的图片。制作在后台继续，你可以随时探索下一份灵感。</p>
            <a href="#templates" className="mt-8 inline-flex rounded-xl bg-[#f7ce8b] px-6 py-3 text-sm font-bold text-[#14364b] transition hover:bg-[#ffdc9f] focus-visible:outline-2 focus-visible:outline-offset-4 focus-visible:outline-white">浏览模板 <span aria-hidden="true" className="ml-3">↘</span></a>
          </div>
          <div className="relative hidden min-h-[310px] lg:block" aria-hidden="true">
            <div className="absolute left-[14%] top-[12%] h-[230px] w-[260px] rotate-[-11deg] rounded-2xl bg-[#e0aa7d] p-4 shadow-2xl"><div className="h-full rounded-lg border-2 border-white/70 bg-[#f1d7b9]" /></div>
            <div className="absolute right-[4%] top-[0%] h-[250px] w-[245px] rotate-[10deg] rounded-2xl bg-[#9cc9d4] p-4 shadow-2xl"><div className="h-full rounded-lg border-2 border-white/70 bg-[#d0e4e8]" /></div>
            <div className="absolute bottom-[-5%] left-[30%] flex h-[190px] w-[230px] rotate-[4deg] items-end rounded-2xl bg-[#efede4] p-6 shadow-2xl"><div className="h-1/2 w-full rounded-lg bg-[#608797]" /></div>
          </div>
        </div>
      </section>

      <section id="templates" className="mx-auto max-w-[1320px] scroll-mt-8 px-5 pt-14 sm:px-8 lg:px-12">
        <div className="flex flex-wrap items-end justify-between gap-4">
          <div><p className="mb-2 text-xs font-bold tracking-[0.22em] text-[#47748a]">TEMPLATE GALLERY</p><h2 className="text-3xl font-bold tracking-tight sm:text-4xl">探索模板</h2><p className="mt-3 text-sm text-slate-600">从一个想法开始，做出你的专属画面。</p></div>
          {all.data ? <p className="text-sm text-slate-500">共 {all.data.length} 款模板</p> : null}
        </div>
        {all.isError ? (
          <div className="mt-8 rounded-2xl border border-amber-200 bg-amber-50 p-5" role="alert">
            <p className="font-semibold text-slate-900">{all.error instanceof ApiRequestError && all.error.status === 404 ? "模板列表暂不存在" : "模板加载失败"}</p>
            <p className="mt-1 text-sm text-slate-700">{errorMessage(all.error)}</p>
            <button type="button" onClick={() => void all.refetch()} className="mt-4 rounded-lg bg-slate-900 px-4 py-2 text-sm font-semibold text-white focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700">重试加载</button>
          </div>
        ) : null}
        {!all.isError && all.isPending ? <p className="mt-8 rounded-2xl border border-slate-200 bg-white p-8 text-slate-600" role="status">正在加载模板与分类…</p> : null}
        {all.data ? (
          <nav aria-label="模板分类" className="mt-8 flex flex-wrap gap-2">
            {[null, ...categories].map((item) => <button type="button" key={item ?? "all"} onClick={() => setCategory(item)} aria-pressed={category === item}
              className={`rounded-full px-5 py-2.5 text-sm font-semibold transition focus-visible:outline-2 focus-visible:outline-offset-2 focus-visible:outline-sky-700 ${category === item ? "bg-[#173f56] text-white" : "border border-slate-200 bg-white text-slate-600 hover:border-[#6595a9] hover:text-slate-900"}`}>{item ?? "全部模板"}</button>)}
          </nav>
        ) : null}
        {active.isError && category !== null ? <div className="mt-7 rounded-xl border border-amber-200 bg-amber-50 p-5" role="alert"><p className="font-semibold">{active.error instanceof ApiRequestError && active.error.status === 404 ? "没有找到这个分类" : "分类加载失败"}</p><p className="mt-1 text-sm">{errorMessage(active.error)}</p><button type="button" onClick={() => void active.refetch()} className="mt-3 text-sm font-semibold text-sky-800 underline focus-visible:outline-2">重试</button></div> : null}
        {active.isPending && category !== null ? <p role="status" className="mt-8 text-slate-600">正在筛选模板…</p> : null}
        {active.data?.length === 0 ? <div className="mt-8 rounded-2xl border border-dashed border-slate-300 bg-white p-10 text-center"><p className="font-semibold text-slate-900">这里暂时没有模板</p><p className="mt-2 text-sm text-slate-600">试试其他分类，或稍后再来。</p></div> : null}
        {active.data && active.data.length > 0 ? <div className="mt-8 grid gap-6 sm:grid-cols-2 lg:grid-cols-3">{active.data.map((template, index) => <TemplateCard key={template.id} template={template} index={index} />)}</div> : null}
      </section>
    </main>
  );
}
