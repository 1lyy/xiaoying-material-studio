import type { QueryClient } from "@tanstack/react-query";
import type { Task } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";

export function isActiveTask(task: Task): boolean {
  return task.status === "QUEUED" || task.status === "RUNNING";
}

export function mergeTask(previous: Task | undefined, incoming: Task): Task {
  if (!previous || previous.id !== incoming.id) return incoming;
  // Java task terminal states are immutable. A delayed response cannot reopen one.
  if (!isActiveTask(previous)) return previous;
  if (incoming.version < previous.version) return previous;
  if (incoming.version === previous.version) return previous;
  return incoming;
}

export function mergeTaskList(previous: Task[] | undefined, incoming: Task[]): Task[] {
  const byId = new Map((previous ?? []).map((task) => [task.id, task]));
  for (const task of incoming) byId.set(task.id, mergeTask(byId.get(task.id), task));
  // There is no task deletion API. Preserve a just-accepted task if an older GET
  // response arrives after the 202 mutation, and never truncate active records.
  const merged = [...byId.values()].sort((a, b) =>
    b.createdAt.localeCompare(a.createdAt) || b.id.localeCompare(a.id));
  return previous && previous.length === merged.length && previous.every((task, index) => task === merged[index])
    ? previous : merged;
}

export function taskPollInterval(tasks: Task[] | undefined, requestFailed: boolean): number | false {
  if (requestFailed) return false;
  return tasks?.some(isActiveTask) ? 2_000 : false;
}

export function rememberAcceptedTask(client: QueryClient, userId: string, task: Task): void {
  client.setQueryData<Task>(queryKeys.userTask(userId, task.id), (previous) => mergeTask(previous, task));
  client.setQueryData<Task[]>(queryKeys.userTasks(userId), (previous) => mergeTaskList(previous, [task]));
}
