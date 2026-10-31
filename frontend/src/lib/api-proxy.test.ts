// @vitest-environment node
import { afterEach, expect, it, vi } from "vitest";
import { proxyApi } from "./api-proxy";

afterEach(() => {
  vi.unstubAllGlobals();
  delete process.env.JAVA_API_ORIGIN;
});

it("forwards a multipart stream, cookies, 202 and Location without choosing an upstream from the request", async () => {
  process.env.JAVA_API_ORIGIN = "http://backend:8080";
  const body = new FormData();
  body.set("file", new Blob(["test"], { type: "image/png" }), "sample.png");
  const request = new Request("http://localhost:3000/api/assets?draft=1", {
    method: "POST",
    headers: { Cookie: "session=abc", "X-Forwarded-Host": "evil.example" },
    body,
  });
  const upstream = new Response('{"id":"example"}', {
    status: 202,
    headers: { Location: "/api/tasks/example", "Content-Type": "application/json", "Set-Cookie": "session=next; HttpOnly" },
  });
  const fetchMock = vi.fn().mockResolvedValue(upstream);
  vi.stubGlobal("fetch", fetchMock);

  const response = await proxyApi(request, ["assets"]);
  const [url, init] = fetchMock.mock.calls[0] as [URL, RequestInit];
  expect(url.href).toBe("http://backend:8080/api/assets?draft=1");
  expect(init.method).toBe("POST");
  expect(init.body).toBe(request.body);
  expect(init.signal).toBe(request.signal);
  expect(new Headers(init.headers).get("cookie")).toBe("session=abc");
  expect(new Headers(init.headers).get("content-type")).toContain("multipart/form-data");
  expect(new Headers(init.headers).has("x-forwarded-host")).toBe(false);
  expect(response.status).toBe(202);
  expect(response.headers.get("location")).toBe("/api/tasks/example");
  expect(response.headers.get("set-cookie")).toContain("session=next");
  expect(response.headers.get("content-type")).toContain("application/json");
  expect(response.headers.get("cache-control")).toBe("no-store");
});

it("returns the frozen error shape when Java is unavailable", async () => {
  process.env.JAVA_API_ORIGIN = "http://backend:8080";
  vi.stubGlobal("fetch", vi.fn().mockRejectedValue(new Error("offline")));
  const response = await proxyApi(new Request("http://localhost:3000/api/health"), ["health"]);
  expect(response.status).toBe(502);
  expect(await response.json()).toEqual({
    code: "UPSTREAM_UNAVAILABLE",
    message: "Java API 暂不可用，请稍后重试",
    traceId: expect.any(String),
  });
});

it("keeps private image bytes, content type and cache policy; forwards upstream 404", async () => {
  process.env.JAVA_API_ORIGIN = "http://backend:8080";
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(new Uint8Array([137, 80, 78, 71]), {
    status: 200,
    headers: { "Content-Type": "image/png", "Cache-Control": "private, max-age=3600" },
  })));
  const image = await proxyApi(new Request("http://localhost:3000/api/assets/a/content"), ["assets", "a", "content"]);
  expect(image.headers.get("content-type")).toBe("image/png");
  expect(image.headers.get("cache-control")).toBe("private, no-store");
  expect(new Uint8Array(await image.arrayBuffer())).toEqual(new Uint8Array([137, 80, 78, 71]));

  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json({ code: "NOT_FOUND", message: "不存在", traceId: "t" }, { status: 404 })));
  const missing = await proxyApi(new Request("http://localhost:3000/api/tasks/a"), ["tasks", "a"]);
  expect(missing.status).toBe(404);
  expect(await missing.json()).toEqual({ code: "NOT_FOUND", message: "不存在", traceId: "t" });
});

it("forwards 201 upload response and 413 error without parsing multipart or binary bodies", async () => {
  process.env.JAVA_API_ORIGIN = "http://backend:8080";
  const payload = new Uint8Array([0, 255, 12, 34]);
  const request = new Request("http://localhost:3000/api/assets", {
    method: "POST",
    headers: { "Content-Type": "multipart/form-data; boundary=example", Cookie: "material_session=secret" },
    body: payload,
  });
  const fetchMock = vi.fn().mockResolvedValueOnce(Response.json({ id: "asset" }, { status: 201 }))
    .mockResolvedValueOnce(Response.json({ code: "FILE_TOO_LARGE", message: "过大", traceId: "t" }, { status: 413 }));
  vi.stubGlobal("fetch", fetchMock);
  const created = await proxyApi(request, ["assets"]);
  expect(created.status).toBe(201);
  const init = fetchMock.mock.calls[0][1] as RequestInit;
  expect(init.body).toBe(request.body);
  expect(new Headers(init.headers).get("content-type")).toBe("multipart/form-data; boundary=example");
  expect(new Headers(init.headers).get("cookie")).toBe("material_session=secret");
  expect(await created.json()).toEqual({ id: "asset" });
  const tooLarge = await proxyApi(new Request("http://localhost:3000/api/assets"), ["assets"]);
  expect(tooLarge.status).toBe(413);
  expect(await tooLarge.json()).toEqual({ code: "FILE_TOO_LARGE", message: "过大", traceId: "t" });
});

it("preserves unauthenticated status and Java error fields", async () => {
  process.env.JAVA_API_ORIGIN = "http://backend:8080";
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json(
    { code: "UNAUTHENTICATED", message: "请先选择演示用户", traceId: "java-trace" }, { status: 401 },
  )));
  const response = await proxyApi(new Request("http://localhost:3000/api/assets/a/content"),
    ["assets", "a", "content"]);
  expect(response.status).toBe(401);
  expect(await response.json()).toEqual({
    code: "UNAUTHENTICATED", message: "请先选择演示用户", traceId: "java-trace",
  });
});
