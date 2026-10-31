"use client";

import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { useState } from "react";
import { SessionProvider } from "@/components/session-provider";
import { TaskTracker } from "@/components/task-tracker";

export function AppProviders({ children }: { children: React.ReactNode }) {
  const [client] = useState(() => new QueryClient({
    defaultOptions: { queries: { retry: false, staleTime: 60_000, refetchOnWindowFocus: false } },
  }));
  return <QueryClientProvider client={client}><SessionProvider><TaskTracker />{children}</SessionProvider></QueryClientProvider>;
}
