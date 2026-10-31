"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { useEffect } from "react";
import { useSession } from "@/components/session-provider";
import { api, ApiRequestError, type Task } from "@/lib/api";
import { queryKeys } from "@/lib/query-keys";
import { mergeTask, mergeTaskList, taskPollInterval } from "@/lib/task-cache";

export function useTasks(userId: string | null, enabled: boolean, track: boolean) {
  return useQuery<Task[]>({
    queryKey: queryKeys.userTasks(userId ?? "anonymous"),
    queryFn: ({ signal }) => api.tasks(signal),
    enabled: Boolean(userId) && enabled,
    retry: false,
    staleTime: 0,
    refetchOnMount: track ? true : "always",
    refetchInterval: track ? (query) => taskPollInterval(query.state.data, query.state.status === "error") : false,
    structuralSharing: (oldData, newData) => mergeTaskList(oldData as Task[] | undefined, newData as Task[]),
  });
}

export function TaskTracker() {
  const { user, busy } = useSession();
  const client = useQueryClient();
  const tasks = useTasks(user?.id ?? null, !busy, true);
  useEffect(() => {
    if (!user || busy || !tasks.data) return;
    for (const task of tasks.data) {
      client.setQueryData<Task>(queryKeys.userTask(user.id, task.id), (previous) => mergeTask(previous, task));
    }
  }, [tasks.data, user, busy, client]);
  useEffect(() => {
    if (tasks.error instanceof ApiRequestError && tasks.error.isUnauthorized && user && !busy) {
      void client.invalidateQueries({ queryKey: queryKeys.session });
    }
  }, [tasks.error, user, busy, client]);
  return null;
}
