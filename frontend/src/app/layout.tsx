import type { Metadata } from "next";
import { AppProviders } from "@/components/app-providers";
import { SiteShell } from "@/components/site-shell";
import "./globals.css";

export const metadata: Metadata = {
  title: "映刻工坊 · 图片素材制作",
  description: "选择模板，上传照片，制作自己的专属画面。",
};

export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="zh-CN"><body><AppProviders><SiteShell>{children}</SiteShell></AppProviders></body></html>;
}
