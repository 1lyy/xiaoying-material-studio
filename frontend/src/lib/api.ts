export type Template = {
  id: string;
  title: string;
  category: string;
  coverUrl: string;
  description: string;
  requiredImageCount: number;
  acceptedMimeTypes: string[];
  maxFileSizeBytes: number;
};

export type DemoUser = { id: string; demoUserKey: string; displayName: string };
export type User = DemoUser;
export type Asset = {
  id: string;
  previewUrl: string;
  mimeType: string;
  sizeBytes: number;
  width: number;
  height: number;
};
export type TaskStatus = "QUEUED" | "RUNNING" | "SUCCEEDED" | "FAILED";
export type TaskStage = "ASSET_CHECK" | "GENERATE" | "FINALIZE";
export type StepStatus = "PENDING" | "RUNNING" | "SUCCEEDED" | "FAILED" | "SKIPPED";
export type TaskStep = {
  stage: TaskStage;
  status: StepStatus;
  startedAt: string | null;
  finishedAt: string | null;
  errorMessage: string | null;
};
export type Task = {
  id: string;
  templateId: string;
  templateTitle: string;
  status: TaskStatus;
  stage: TaskStage | null;
  createdAt: string;
  startedAt: string | null;
  finishedAt: string | null;
  resultUrl: string | null;
  failedStage: TaskStage | null;
  errorCode: string | null;
  errorMessage: string | null;
  version: number;
  steps: TaskStep[];
};

type ErrorBody = { code?: unknown; message?: unknown; traceId?: unknown };

export class ApiRequestError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
    public readonly traceId?: string,
  ) {
    super(message);
    this.name = "ApiRequestError";
  }

  get isUnauthorized() { return this.status === 401; }
  get isUnavailable() { return this.status === 0 || this.status === 502 || this.status === 503 || this.status === 504; }
}

function parseError(status: number, raw: string): ApiRequestError {
  let body: ErrorBody = {};
  try {
    const parsed: unknown = JSON.parse(raw);
    if (typeof parsed === "object" && parsed !== null) body = parsed as ErrorBody;
  } catch { /* Upstream may return an empty body. */ }
  return new ApiRequestError(
    status,
    typeof body.code === "string" ? body.code : "REQUEST_FAILED",
    typeof body.message === "string" ? body.message : `请求失败（HTTP ${status}）`,
    typeof body.traceId === "string" ? body.traceId : undefined,
  );
}

async function request<T>(path: string, init: RequestInit = {}, expectedStatus?: number, timeoutMs?: number): Promise<T> {
  const controller = new AbortController();
  let timedOut = false;
  const abortFromCaller = () => controller.abort();
  if (init.signal?.aborted) controller.abort();
  else init.signal?.addEventListener("abort", abortFromCaller, { once: true });
  const timer = timeoutMs === undefined ? undefined : setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, timeoutMs);
  try {
    const response = await fetch(path, { ...init, signal: controller.signal, credentials: "same-origin", cache: "no-store" });
    if (!response.ok) throw parseError(response.status, await response.text());
    if (expectedStatus !== undefined && response.status !== expectedStatus) {
      throw new ApiRequestError(response.status, "UNEXPECTED_RESPONSE", "服务端响应不符合预期");
    }
    if (response.status === 204) return undefined as T;
    try { return await response.json() as T; }
    catch (error) {
      if (controller.signal.aborted) throw error;
      throw new ApiRequestError(response.status, "INVALID_RESPONSE", "服务端响应无法解析");
    }
  } catch (error) {
    if (timedOut && !init.signal?.aborted) throw new ApiRequestError(0, "REQUEST_TIMEOUT", init.method === "POST" ? "提交请求超时，受理状态未知" : "查询超时，请重试");
    if (init.signal?.aborted || (error instanceof Error && error.name === "AbortError")) throw error;
    if (error instanceof ApiRequestError) throw error;
    throw new ApiRequestError(0, "NETWORK_ERROR", "网络暂不可用，请检查连接后重试");
  } finally {
    if (timer !== undefined) clearTimeout(timer);
    init.signal?.removeEventListener("abort", abortFromCaller);
  }
}

export const api = {
  templates: (category?: string, signal?: AbortSignal) => request<Template[]>(
    `/api/templates${category ? `?category=${encodeURIComponent(category)}` : ""}`, { signal }),
  template: (id: string, signal?: AbortSignal) => request<Template>(`/api/templates/${encodeURIComponent(id)}`, { signal }),
  demoUsers: (signal?: AbortSignal) => request<DemoUser[]>("/api/demo-users", { signal }),
  session: (signal?: AbortSignal) => request<User>("/api/session", { signal }),
  login: (demoUserKey: string, signal?: AbortSignal) => request<User>("/api/session", {
    method: "POST", headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ demoUserKey }), signal,
  }, 200),
  logout: (signal?: AbortSignal) => request<void>("/api/session", { method: "DELETE", signal }, 204),
  tasks: (signal?: AbortSignal) => request<Task[]>("/api/tasks", { signal }, 200, 10_000),
  task: (id: string, signal?: AbortSignal) => request<Task>(`/api/tasks/${encodeURIComponent(id)}`, { signal }, 200, 10_000),
  createTask: (templateId: string, assetIds: string[], signal?: AbortSignal) => request<Task>(
    "/api/tasks", {
      method: "POST", headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ templateId, assetIds }), signal,
    }, 202, 10_000),
};

export type UploadRequest = { promise: Promise<Asset>; abort: () => void };

export function uploadAsset(
  templateId: string,
  file: File,
  onProgress: (percent: number) => void,
): UploadRequest {
  const xhr = new XMLHttpRequest();
  const form = new FormData();
  form.append("templateId", templateId);
  form.append("file", file);
  const promise = new Promise<Asset>((resolve, reject) => {
    xhr.open("POST", "/api/assets");
    xhr.withCredentials = true;
    xhr.upload.onprogress = (event) => {
      if (event.lengthComputable && event.total > 0) {
        onProgress(Math.min(100, Math.round(event.loaded * 100 / event.total)));
      }
    };
    xhr.onload = () => {
      if (xhr.status === 201) {
        try { resolve(JSON.parse(xhr.responseText) as Asset); }
        catch { reject(new ApiRequestError(201, "INVALID_RESPONSE", "上传响应无法解析")); }
      } else {
        reject(parseError(xhr.status, xhr.responseText));
      }
    };
    xhr.onerror = () => reject(new ApiRequestError(0, "NETWORK_ERROR", "上传连接中断，请重试"));
    xhr.onabort = () => reject(new DOMException("上传已取消", "AbortError"));
    xhr.send(form);
  });
  return { promise, abort: () => xhr.abort() };
}

export function errorMessage(error: unknown, fallback = "请求失败，请重试"): string {
  if (error instanceof ApiRequestError) {
    if (error.isUnauthorized) return "会话已失效，请先选择演示用户";
    if (error.code === "REQUEST_TIMEOUT") return error.message;
    if (error.isUnavailable) return "服务暂时不可用，已保留当前页面，请稍后重试";
    return error.message;
  }
  return fallback;
}
