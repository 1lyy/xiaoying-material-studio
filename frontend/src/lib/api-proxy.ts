const REQUEST_HOP_HEADERS = [
  "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
  "te", "trailer", "transfer-encoding", "upgrade", "host", "content-length",
  "forwarded", "x-forwarded-for", "x-forwarded-host", "x-forwarded-proto",
  "accept-encoding",
];

const RESPONSE_HOP_HEADERS = [
  "connection", "keep-alive", "proxy-authenticate", "proxy-authorization",
  "te", "trailer", "transfer-encoding", "upgrade", "content-length",
  "content-encoding",
];

type StreamingRequestInit = RequestInit & { duplex?: "half" };

function apiError(code: string, message: string, status: number): Response {
  return Response.json(
    { code, message, traceId: crypto.randomUUID() },
    { status, headers: { "Cache-Control": "no-store" } },
  );
}

function configuredOrigin(): URL | null {
  const raw = process.env.JAVA_API_ORIGIN;
  if (!raw) return null;
  try {
    const origin = new URL(raw);
    if (
      !["http:", "https:"].includes(origin.protocol) ||
      origin.username || origin.password || origin.pathname !== "/" ||
      origin.search || origin.hash
    ) return null;
    return origin;
  } catch {
    return null;
  }
}

export async function proxyApi(request: Request, path: string[]): Promise<Response> {
  const origin = configuredOrigin();
  if (!origin) return apiError("UPSTREAM_NOT_CONFIGURED", "Java API 地址未正确配置", 502);
  if (path.length === 0 || path.some((segment) =>
    segment === "." || segment === ".." || segment.includes("/") || segment.includes("\\")
  )) return apiError("INVALID_PATH", "请求路径无效", 400);

  // Only the server environment chooses the host; route parameters are path segments.
  const target = new URL(`/api/${path.map(encodeURIComponent).join("/")}`, origin);
  target.search = new URL(request.url).search;

  const headers = new Headers(request.headers);
  for (const name of REQUEST_HOP_HEADERS) headers.delete(name);
  headers.set("Accept-Encoding", "identity");
  headers.set("Cache-Control", "no-store");

  const hasBody = !["GET", "HEAD"].includes(request.method);
  const init: StreamingRequestInit = {
    method: request.method,
    headers,
    body: hasBody ? request.body : null,
    signal: request.signal,
    cache: "no-store",
    redirect: "manual",
  };
  if (hasBody && request.body) init.duplex = "half";

  try {
    const upstream = await fetch(target, init);
    const responseHeaders = new Headers(upstream.headers);
    for (const name of RESPONSE_HOP_HEADERS) responseHeaders.delete(name);
    responseHeaders.delete("set-cookie");
    for (const cookie of upstream.headers.getSetCookie()) responseHeaders.append("Set-Cookie", cookie);
    responseHeaders.set("Cache-Control", path.length === 3 && path[0] === "assets" && path[2] === "content"
      ? "private, no-store" : "no-store");

    const emptyBody = request.method === "HEAD" || [204, 205, 304].includes(upstream.status);
    return new Response(emptyBody ? null : upstream.body, {
      status: upstream.status,
      statusText: upstream.statusText,
      headers: responseHeaders,
    });
  } catch {
    return apiError("UPSTREAM_UNAVAILABLE", "Java API 暂不可用，请稍后重试", 502);
  }
}
