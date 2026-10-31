import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { cleanup, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, expect, it, vi } from "vitest";
import { SessionProvider, useSession } from "./session-provider";
import { queryKeys } from "@/lib/query-keys";
import { TaskTracker } from "./task-tracker";

function Probe() {
  const { user, switchUser } = useSession();
  return <><span>{user?.displayName ?? "未登录"}</span><button type="button" onClick={() => void switchUser("bob")}>切换 Bob</button></>;
}

afterEach(() => { cleanup(); vi.unstubAllGlobals(); });

it("cancels and removes old private queries while retaining public templates on user switch", async () => {
  const alice = { id: "alice-id", demoUserKey: "alice", displayName: "Alice" };
  const bob = { id: "bob-id", demoUserKey: "bob", displayName: "Bob" };
  const fetchMock = vi.fn((path: string, init: RequestInit) => Promise.resolve({
    ok: true, status: 200, json: async () => path === "/api/session" && init.method === "POST" ? bob : alice,
  }));
  vi.stubGlobal("fetch", fetchMock);
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  client.setQueryData(queryKeys.session, alice);
  client.setQueryData(queryKeys.userTasks(alice.id), [{ id: "old-task" }]);
  client.setQueryData(queryKeys.templates, [{ id: "public-template" }]);
  render(<QueryClientProvider client={client}><SessionProvider><Probe /></SessionProvider></QueryClientProvider>);
  await waitFor(() => expect(client.getQueryState(queryKeys.session)?.fetchStatus).toBe("idle"));
  fireEvent.click(screen.getByRole("button", { name: "切换 Bob" }));
  await waitFor(() => expect(screen.getByText("Bob")).toBeInTheDocument());
  expect(client.getQueryData(queryKeys.userTasks(alice.id))).toBeUndefined();
  expect(client.getQueryData(queryKeys.templates)).toEqual([{ id: "public-template" }]);
  expect(fetchMock).toHaveBeenCalledWith("/api/session", expect.objectContaining({ method: "POST", credentials: "same-origin" }));
});

it("aborts Alice's active task request before Bob's session becomes visible", async () => {
  const alice = { id: "alice-id", demoUserKey: "alice", displayName: "Alice" };
  const bob = { id: "bob-id", demoUserKey: "bob", displayName: "Bob" };
  let cookieUser = "alice";
  const observed = { oldSignal: null as AbortSignal | null };
  const fetchMock = vi.fn((path: string, init: RequestInit) => {
    if (path === "/api/session" && init.method === "POST") {
      cookieUser = "bob";
      return Promise.resolve({ ok: true, status: 200, json: async () => bob });
    }
    if (path === "/api/session") return Promise.resolve({ ok: true, status: 200, json: async () => cookieUser === "alice" ? alice : bob });
    if (path === "/api/tasks" && cookieUser === "alice") return new Promise((resolve, reject) => {
      void resolve;
      const requestSignal = init.signal as AbortSignal;
      observed.oldSignal = requestSignal;
      requestSignal.addEventListener("abort", () => reject(new DOMException("cancelled", "AbortError")));
    });
    return Promise.resolve({ ok: true, status: 200, json: async () => [] });
  });
  vi.stubGlobal("fetch", fetchMock);
  const client = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  client.setQueryData(queryKeys.session, alice);
  render(<QueryClientProvider client={client}><SessionProvider><TaskTracker /><Probe /></SessionProvider></QueryClientProvider>);
  await waitFor(() => expect(observed.oldSignal).not.toBeNull());
  fireEvent.click(screen.getByRole("button", { name: "切换 Bob" }));
  await waitFor(() => expect(screen.getByText("Bob")).toBeInTheDocument());
  expect(observed.oldSignal?.aborted).toBe(true);
  expect(client.getQueryData(queryKeys.userTasks("alice-id"))).toBeUndefined();
  await waitFor(() => expect(client.getQueryData(queryKeys.userTasks("bob-id"))).toEqual([]));
});
