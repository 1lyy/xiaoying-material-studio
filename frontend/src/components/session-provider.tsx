"use client";

import { useQuery, useQueryClient } from "@tanstack/react-query";
import { createContext, useCallback, useContext, useEffect, useRef, useState } from "react";
import { api, ApiRequestError, type User } from "@/lib/api";
import { isPrivateKey, queryKeys } from "@/lib/query-keys";

type SessionContextValue = {
  user: User | null;
  identityEpoch: number;
  loading: boolean;
  unavailable: boolean;
  unauthorized: boolean;
  busy: boolean;
  actionError: unknown;
  retry: () => Promise<unknown>;
  switchUser: (key: string) => Promise<void>;
  logout: () => Promise<void>;
};

const SessionContext = createContext<SessionContextValue | null>(null);

export function SessionProvider({ children }: { children: React.ReactNode }) {
  const client = useQueryClient();
  const [identityEpoch, setIdentityEpoch] = useState(0);
  const [busy, setBusy] = useState(false);
  const [actionError, setActionError] = useState<unknown>(null);
  const operation = useRef(false);
  const session = useQuery({
    queryKey: queryKeys.session,
    queryFn: ({ signal }) => api.session(signal),
    staleTime: 0,
    retry: false,
  });

  const clearPrivateQueries = useCallback(async () => {
    await client.cancelQueries({ predicate: (query) => isPrivateKey(query.queryKey) });
    client.removeQueries({ predicate: (query) => isPrivateKey(query.queryKey) && query.queryKey[1] !== "session" });
  }, [client]);

  useEffect(() => {
    if (session.error instanceof ApiRequestError && session.error.isUnauthorized) {
      void clearPrivateQueries();
    }
  }, [session.error, clearPrivateQueries]);

  const switchUser = useCallback(async (key: string) => {
    if (operation.current) return;
    operation.current = true;
    setBusy(true);
    setActionError(null);
    setIdentityEpoch((value) => value + 1);
    try {
      await clearPrivateQueries();
      client.setQueryData(queryKeys.session, null);
      const user = await api.login(key);
      client.setQueryData(queryKeys.session, user);
    } catch (error) {
      setActionError(error);
      await client.invalidateQueries({ queryKey: queryKeys.session });
    } finally {
      operation.current = false;
      setBusy(false);
    }
  }, [clearPrivateQueries, client]);

  const logout = useCallback(async () => {
    if (operation.current) return;
    operation.current = true;
    setBusy(true);
    setActionError(null);
    try {
      await clearPrivateQueries();
      await api.logout();
      client.setQueryData(queryKeys.session, null);
      setIdentityEpoch((value) => value + 1);
    } catch (error) {
      setActionError(error);
      await client.invalidateQueries({ queryKey: queryKeys.session });
    } finally {
      operation.current = false;
      setBusy(false);
    }
  }, [clearPrivateQueries, client]);

  const sessionError = session.error;
  const value: SessionContextValue = {
    user: sessionError instanceof ApiRequestError && sessionError.isUnauthorized ? null : session.data ?? null,
    identityEpoch,
    loading: session.isPending && session.isFetching,
    unavailable: sessionError instanceof ApiRequestError && sessionError.isUnavailable,
    unauthorized: sessionError instanceof ApiRequestError && sessionError.isUnauthorized,
    busy,
    actionError,
    retry: session.refetch,
    switchUser,
    logout,
  };
  return <SessionContext.Provider value={value}>{children}</SessionContext.Provider>;
}

export function useSession() {
  const value = useContext(SessionContext);
  if (!value) throw new Error("SessionProvider is missing");
  return value;
}
