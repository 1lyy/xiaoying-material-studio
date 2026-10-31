# 前端异步任务轮次验证记录

记录日期：2026-10-30。范围仅为 Next.js 任务受理状态、历史展示、查询生命周期与错误处理；Java 业务未修改。没有连续计时数据，不填写猜测的小时数。

## 源码与测试证据

| 行为 | 源码/测试 | 结论 |
| --- | --- | --- |
| 202 后立即更新 `userId/taskId` 与用户列表缓存，提交状态结束，可再次上传提交 | `templates/[id]/page.tsx`、`lib/task-cache.ts`、`lib/task-cache.test.ts` | 源码与断言已写，未运行 |
| 真实历史、阶段步骤、失败原因、授权结果图片和图片错误重试 | `app/history/page.tsx`、`app/history/page.test.tsx` | 源码与断言已写，未运行 |
| 仅活跃任务每 2 秒轮询；长列表不截断；按 ID/版本合并且终态不回退 | `components/task-tracker.tsx`、`lib/task-cache.ts`、`lib/task-cache.test.ts` | 源码与断言已写，未运行 |
| 初次 500/503 完整错误，已有快照 500/503 保留；Java `FAILED` 单独展示 | `app/history/page.test.tsx`、`lib/api.test.ts` | 源码与断言已写，未运行 |
| GET/POST 使用 AbortSignal，POST 超时不自动重发，401 才令会话失效 | `lib/api.ts`、`components/session-provider.tsx`、`lib/api.test.ts` | 源码与断言已写，未运行 |

历史列表由 Java `GET /api/tasks` 返回当前用户全部记录。全局任务跟踪组件在站内导航时继续追踪，历史页卸载时移除该页的查询观察；全局跟踪器仍按活跃任务保持必要请求。切换用户时取消并删除旧用户私有缓存。GET 出错后停止定时查询，历史页提供手动恢复。新 202 快照显式写入列表缓存使轮询重新计算。

## 实际命令与阻塞

本机 PowerShell 中分别执行：

```powershell
docker compose --profile test run --build --rm --no-deps frontend-test npm run lint
docker compose --profile test run --build --rm --no-deps frontend-test npm run typecheck
docker compose --profile test run --build --rm --no-deps frontend-test npm run test
docker compose --profile test run --build --rm --no-deps frontend-test npm run build
docker compose --profile test config
```

每条均退出码 1，PowerShell 报 `The term 'docker' is not recognized ...`；`Get-Command` 只找到 `wsl.exe`，`wsl --list --quiet` 返回 `WSL/E_ENUMERATE_DISTROS/SERVICE_E_ACCESSDENIED`。遵守 `AGENTS.md` 的容器隔离要求，未在宿主机执行 npm。因此 lint、typecheck、测试、构建、页面截图及真实 HTTP 联调均为 **UNVERIFIED**。没有取得两个任务或指定失败/网络异常的实际运行演示证据；测试中构造的响应不是实际演示。

## 后续验证入口

在具备 Docker Compose 的环境运行上面的验证命令，再 `docker compose up --build -d --wait`。用 Alice 连续提交两项任务，观察独立状态、结果/失败；暂时停用 Java 或拦截 `/api/tasks` 产生 503，确认保留旧快照且不显示业务失败；恢复后手动重试。刷新 `/history` 核对 ID 与版本，切 Bob 确认历史隔离，并检查所有终态后 Network 不再定时发 GET。当前不宣称这些运行效果已通过。
