import { QueryClient } from "@tanstack/react-query";
import { expect, it } from "vitest";
import type { Task, TaskStatus } from "./api";
import { queryKeys } from "./query-keys";
import { mergeTaskList, rememberAcceptedTask, taskPollInterval } from "./task-cache";

function task(id: string, version: number, status: TaskStatus): Task {
  return {
    id, templateId: "template", templateTitle: id, status,
    stage: status === "QUEUED" ? null : "ASSET_CHECK",
    createdAt: `2026-10-30T08:00:0${id === "a" ? "1" : "2"}Z`,
    startedAt: null, finishedAt: null, resultUrl: null,
    failedStage: null, errorCode: null, errorMessage: null, version,
    steps: ["ASSET_CHECK", "GENERATE", "FINALIZE"].map((stage) => ({
      stage: stage as Task["steps"][number]["stage"], status: "PENDING" as const,
      startedAt: null, finishedAt: null, errorMessage: null,
    })),
  };
}

it("keeps task IDs independent when responses arrive out of order", () => {
  const latest = mergeTaskList([task("a", 4, "RUNNING"), task("b", 2, "QUEUED")],
    [task("a", 2, "QUEUED"), task("b", 3, "SUCCEEDED")]);
  expect(latest.find((item) => item.id === "a")).toMatchObject({ version: 4, status: "RUNNING" });
  expect(latest.find((item) => item.id === "b")).toMatchObject({ version: 3, status: "SUCCEEDED" });
  expect(taskPollInterval(latest, false)).toBe(2_000);
});

it("never reopens a terminal task or drops a newly accepted task from an older list response", () => {
  const latest = mergeTaskList([task("a", 8, "FAILED"), task("b", 0, "QUEUED")],
    [task("a", 7, "RUNNING")]);
  expect(latest).toHaveLength(2);
  expect(latest.find((item) => item.id === "a")).toMatchObject({ version: 8, status: "FAILED" });
  expect(latest.find((item) => item.id === "b")?.status).toBe("QUEUED");
  expect(taskPollInterval([task("a", 8, "FAILED")], false)).toBe(false);
  expect(taskPollInterval([task("a", 8, "FAILED"), task("b", 5, "SUCCEEDED")], false)).toBe(false);
  expect(taskPollInterval(latest, true)).toBe(false);
});

it("stores 202 snapshots by task ID inside only the accepting user's cache", () => {
  const client = new QueryClient();
  rememberAcceptedTask(client, "alice", task("a", 0, "QUEUED"));
  rememberAcceptedTask(client, "alice", task("b", 0, "QUEUED"));
  expect(client.getQueryData<Task[]>(queryKeys.userTasks("alice"))).toHaveLength(2);
  expect(client.getQueryData<Task>(queryKeys.userTask("alice", "a"))?.id).toBe("a");
  expect(client.getQueryData(queryKeys.userTasks("bob"))).toBeUndefined();
});

it("keeps an active task at the end of a long complete history", () => {
  const history = Array.from({ length: 120 }, (_, index) => task(`done-${index}`, 8, "SUCCEEDED"));
  const merged = mergeTaskList(undefined, [...history, task("tail", 0, "QUEUED")]);
  expect(merged).toHaveLength(121);
  expect(merged.some((item) => item.id === "tail")).toBe(true);
  expect(taskPollInterval(merged, false)).toBe(2_000);
});
