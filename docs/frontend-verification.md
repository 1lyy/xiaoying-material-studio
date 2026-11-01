# Next.js 模板与上传轮次验证记录

记录日期：2026-10-30。本轮只做模板列表、模板详情、演示用户切换、素材上传和最小任务受理反馈；没有实现任务轮询或历史页面。前端所有业务数据来自 Java 的同源 `/api/**`，未放置永久假模板或假用户。

## 页面与交互源码

| 路由/模块 | 本轮接入 | 仍未实现 |
| --- | --- | --- |
| `/` | Java 模板列表、从全量列表派生分类、按分类请求 Java 过滤、卡片进入详情；加载、空数组、404/服务错误、手动重试 | 无历史入口 |
| `/templates/[id]` | Java 模板详情与封面；按 `requiredImageCount` 渲染上传位；桌面左右分栏、小屏上下排列；模板 400/404/服务错误及重试 | 不显示任务后续阶段 |
| 顶栏 | Java `GET /api/demo-users`、`GET/POST/DELETE /api/session`；切换身份取消旧私有查询并清缓存；401 提示重新选择用户，502/503 提示暂不可用并重试 | 不提供正式认证 |
| 上传位 | `empty/validating/uploading/ready/error`；本地类型、扩展名、大小检查；XHR 进度；替换/移除时中止旧请求、忽略旧回调并释放 object URL；201 后使用 Java 的 `assetId` 和私有 `previewUrl` | 文件实际格式仍以 Java 校验为准 |
| 提交按钮 | 槽位全部 `ready`、会话可用时提交 `templateId + assetIds`；提交中禁止重复点击；202 后显示任务 ID 并重置上传位 | 不轮询、不展示最终成功/失败或完整历史 |

状态缓存：`["public", ...]` 与 `["private", ...]` 分开；模板可缓存，私有请求禁用 HTTP 缓存。切换用户时取消私有 Query 并删除旧用户任务缓存；详情上传组件按用户和切换代次重新挂载，清理 XHR、提交请求与本地预览。`JAVA_API_ORIGIN` 只由已有服务端代理读取，浏览器客户端没有上游主机配置。

## 测试与运行证据

新增测试源码：

- `src/lib/api.test.ts`：401 与 503/网络异常区分、公开/私有 Query Key、任务受理请求仅传素材 ID。
- `src/app/page.test.tsx`：列表分类与卡片来自 API、分类请求路径、服务错误重试后空结果。
- `src/components/upload-slot.test.tsx`：上传进度、替换时取消旧请求和忽略晚到响应、释放 object URL、超限前端拦截。
- `src/components/session-provider.test.tsx`：用户切换时移除旧私有缓存，保留公开模板缓存。

可重复容器入口：

```powershell
docker compose --profile test run --build --rm --no-deps frontend-test
```

该服务使用固定 `node:22.23.4-bookworm-slim` 的 Dockerfile `verify` 阶段，依次执行 `npm run lint`、`npm run typecheck`、`npm run test`、`npm run build`。本轮实际尝试了上述命令及 `docker compose --profile test config`，两者均退出码 1，PowerShell 报告 `The term 'docker' is not recognized ...`。仓库 `AGENTS.md` 要求项目脚本在容器隔离环境执行，因此没有在宿主机运行 npm。**四项脚本、页面运行、Java 联调、浏览器视觉检查均为 UNVERIFIED；没有取得本项目实际页面截图。**

只读静态检查：Python YAML 解析 `compose.yaml` 成功，列出 `db/backend/frontend/backend-test/frontend-test/db-test` 六个服务。这不能证明镜像可构建或页面可运行。

## 待验证与下一轮入口

在有 Docker 的环境先执行上述 `frontend-test` 命令，保留失败测试并修复实际 lint/类型/构建错误；再启动 `docker compose up --build`，检查 `/` 与 `/templates/{id}` 的桌面和小屏页面、Alice/Bob 切换、真实上传、202 受理，并取得实际截图。本段只记录当时的模板/上传轮次；后续任务状态和历史源码见下方增量记录。当前不宣称 UI 已经运行通过。

## 2026-10-30 · R-07～R-11 行为测试补强

本节是对上方早期记录的增量；历史页与任务跟踪器现已有源码。没有连续计时数据，不填写估计工时。Java 业务与冻结的 API 字段未改动。

| 验收行为 | Vitest/RTL 测试位置 | 证据状态 |
| --- | --- | --- |
| A 成功有结果、B 处理中、C 失败有阶段和原因，三个卡片互不串扰 | `src/app/history/page.test.tsx`：`keeps A success, B running, and C failure in their own cards` | 断言已写，未运行 |
| B 的旧 `version=2` 请求迟于缓存里的 `version=6` 完成，终态不回退 | 同文件：`does not let B's late version 2 response replace its newer version 6` | 受控 Promise 断言已写，未运行 |
| Java `FAILED` 显示业务失败；初次 503 是完整请求错误；已有任务遇 500/503 保留状态；重试后显示成功图 | 同文件：`shows a full request error initially...` 参数化 500/503、`rejects a late lower-version response...` | 断言已写，未运行 |
| `QUEUED/RUNNING` 每 2 秒触发查询；全部终态或空列表停止；新增 202 任务后恢复；长历史保留末尾活跃任务 | `src/components/task-tracker.test.tsx`：`polls QUEUED and RUNNING...`、`makes no periodic request...`；`src/lib/task-cache.test.ts` | 使用假 `setInterval` 推进，无真实 2 秒等待；未运行 |
| 页面卸载和会话切换中止请求；Bob 页面无 Alice 卡片/结果 | `src/components/task-tracker.test.tsx`、`src/components/session-provider.test.tsx`、`src/app/history/page.test.tsx` | 断言 AbortSignal、用户缓存和页面内容；未运行 |
| 替换上传忽略旧成功；上传中移除会中止并释放 blob 预览；上传中/提交中防重复提交 | `src/components/upload-slot.test.tsx`、`src/app/templates/[id]/page.test.tsx` | 受控 Promise 与按钮状态断言已写，未运行 |
| 模板 404、空详情、首页空列表/请求失败、结果图错误/重试、键盘焦点 | `src/app/templates/[id]/page.test.tsx`、`src/app/page.test.tsx`、`src/app/history/page.test.tsx`、`src/components/upload-slot.test.tsx` | 断言已写，未运行 |

源码复核发现上传位原先用非焦点式 `<label>` 作为可见选择入口；已改为原生 `<button type="button">` 触发文件输入，并给按钮显式焦点样式。输入仍有可读名称，移出 Tab 顺序，避免两个选择入口。另修复历史页在用户切换 `busy` 期间仍可能显示旧用户卡片的问题；切换时只显示确认身份提示。测试文件显式 `cleanup()`，避免页面状态在用例间残留。`tsconfig.json` 保持 `strict: true`，未关闭 ESLint 或 TypeScript 规则。

本轮分别尝试以下容器命令：

```powershell
docker compose --profile test run --build --rm --no-deps frontend-test npm run lint
docker compose --profile test run --build --rm --no-deps frontend-test npm run typecheck
docker compose --profile test run --build --rm --no-deps frontend-test npm test
docker compose --profile test run --build --rm --no-deps frontend-test npm run build
```

四条均在 PowerShell 启动阶段退出码 1：`The term 'docker' is not recognized as a name of a cmdlet, function, script file, or executable program.` 本机只有宿主 Node/npm，没有 Docker 或 Podman；按 `AGENTS.md` 的容器隔离约束未在宿主机执行项目脚本。因此 **lint、strict 类型检查、测试和构建全部 UNVERIFIED**，没有把测试源码当成通过证据。遗留：需在可用容器中执行四项检查并修复实测失败；真实浏览器的响应式布局、键盘操作、Alice/Bob 文件隔离、双任务和失败/网络异常演示仍需运行验收。

## 2026-10-31 · Ubuntu 虚拟机首次容器前端检查（用户提供终端截图）

环境：VirtualBox Ubuntu 22.04.5、Docker 服务端 29.7.1、Compose 5.3.1。用户在解压的源码目录执行 `docker compose --profile test run --build --rm --no-deps frontend-test`。截图显示镜像依赖阶段 `npm ci` 完成；容器进入 `npm run lint`，结果 **0 errors、1 warning**（`postcss.config.mjs` 的匿名默认导出）；随后 `npm run typecheck` 以 `TS2339` 失败：`src/app/templates/[id]/page.tsx:106:237` 的空模板分支里，TypeScript 将 `detail` 收窄成 `never`，无法访问 `refetch`。因命令以 `&&` 串联，Vitest 与 build **未开始**。这份截图未提供可复用文本日志或退出码，不能宣称整个 `frontend-test` 通过。

先在 `page.test.tsx` 增加“空详情响应后点击重新加载，恢复显示模板”的回归测试，再将详情查询显式标为 `Template | null`，保留空数据分支并修复类型收窄；同时改 `postcss.config.mjs` 为具名配置后默认导出，以消除该 lint 警告。**修订后的源码尚未在 Ubuntu 容器复测**。需把重新生成的源码包复制进虚拟机，重跑同一命令；只有拿到 lint、typecheck、Vitest、build 全部输出后才能更新结果。

## 2026-10-31 · 第二次 Ubuntu 容器检查与测试整改

用户提供的第二次终端截图显示 `frontend-test` 镜像构建成功，lint 与 `tsc --noEmit` 完成，Vitest 运行 35 个用例：**32 passed、3 failed**；因此 `npm run build` 仍未开始。失败为 `task-tracker.test.tsx` 的首轮 QUEUED/空列表两项，以及 `history/page.test.tsx` 的迟到旧版本页面断言。截图没有提供可复制的文本日志或完整退出码，不能宣称前端测试通过。

复核测试发现：前两项只伪造 `setInterval`，却使用依赖定时轮询的 Testing Library `waitFor` 等待初次异步查询，导致第一次断言后不再重查；改用 Vitest 的 `vi.waitFor` 等待缓存状态，继续以假时钟推进 2 秒轮询。第三项已断言缓存保持 version 6，但立即检查 React 卡片，改为等待重绘后再断言。**这些修订尚未在容器复测**，未更改生产任务状态逻辑。另有 Next Image 测试 mock 的非布尔属性警告，不计为测试通过或失败。

## 2026-10-31 · 第三次 Ubuntu 容器检查（共享目录）

用户在已挂载的 Windows 项目目录 `/mnt/XiaoYing` 执行 `docker compose --profile test run --build --rm --no-deps frontend-test` 并提供完整终端截图。镜像构建完成，容器中的 `npm run lint` 和 `npm run typecheck` 均完成；Vitest 报告 **10 test files passed、35 tests passed**；`npm run build` 显示 Next.js 16.4.3 编译成功、TypeScript 完成、静态页面生成和路由表输出，随后返回 shell 提示符。以该截图为证，**前端容器检查 PASS**。未取得真实浏览器页面截图或 Java 联调结果。

Vitest 仍输出 `next/image` 测试 mock 向 `<img>` 透传 `fill` / `unoptimized` 的 React 警告，属于测试输出整洁度问题，未使测试失败。构建期间 Next.js 在容器内建议/添加 `.next/dev/types/**/*.ts` 到 `tsconfig.json` 的 include；此次构建镜像内的修改不能当作 Windows 源码已修改。共享目录目前是当前 VM 会话的临时 VirtualBox 共享，尚未验证重启后自动挂载。

## 2026-11-01 · GitHub Actions 前端入口通过

[CI run #5](https://github.com/1lyy/xiaoying-material-studio/actions/runs/41286040184) 对提交 `fe5ef9b` 的前端 `npm ci`、lint、TypeScript、Vitest 与 Next.js build 步骤为 success，日志显示 **35 passed (35)**；同一运行在新 runner 上成功构建前端运行镜像并通过 HTTP 冒烟。测试 mock 的 React 属性警告仍可见，但不影响退出码。真实小屏操作和完整键盘手工检查未单独留证据。
