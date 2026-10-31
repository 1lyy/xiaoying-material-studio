import { proxyApi } from "@/lib/api-proxy";

export const dynamic = "force-dynamic";
export const runtime = "nodejs";

type Context = { params: Promise<{ path: string[] }> };

async function handle(request: Request, context: Context): Promise<Response> {
  const { path } = await context.params;
  return proxyApi(request, path);
}

export { handle as GET, handle as HEAD, handle as POST, handle as PUT, handle as PATCH, handle as DELETE, handle as OPTIONS };
