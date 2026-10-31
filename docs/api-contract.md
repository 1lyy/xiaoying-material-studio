# API 与状态契约（P0 冻结）

依据：[题目原文](题目原文.md)、[执行计划](执行计划.md)。本文件是 Java DTO、MyBatis 查询、前端类型和契约测试的共同标准；健康、演示身份、模板、上传/私有读取及任务接口已有源码，单任务成功主流程已在浏览器运行。未列明的异常和竞争边界仍需自动测试验证。变更字段、状态或错误码时须同步修改本文件、测试与 README。

## 1. 总体规则

- 浏览器只请求同源 `/api/**`。Next.js App Router 的 `app/api/[...path]/route.ts` 只将请求代理到 Java 的相同路径；Java 独占模板、素材、任务、身份、历史、文件访问的业务判断与持久化。上游地址由服务端运行时变量 `JAVA_API_ORIGIN` 指定，禁止从请求头或用户输入拼接上游主机。
- 代理保留方法、查询串、请求体、Cookie、状态码、`Location`、`Set-Cookie`、`Content-Type` 与图片流；逐跳头不转发。上传文件不得先转成浏览器临时 URL 充当服务端素材。Java 不可达时，代理返回 `502` 和统一错误体，不把任务改成失败。
- JSON 使用 UTF-8、`camelCase`。公开的用户、模板、素材、任务 ID 是 UUID 字符串；`demoUserKey` 是固定演示键，不是资源 ID。所有时间为 UTC 的 ISO 8601（例如 `2026-10-30T08:00:00Z`），缺失时间用 `null`。
- 所有错误统一为 `{ "code": string, "message": string, "traceId": string }`；`traceId` 用于查询服务端日志。非法请求不暴露堆栈、文件路径或他人资源是否存在。校验失败用稳定错误码和可理解中文消息。
- 除公开模板、封面与演示用户列表，接口从 Java 会话取得身份。Cookie 为 `HttpOnly; SameSite=Lax; Path=/`；本地 HTTP 的 `Secure=false`，HTTPS 部署为 `true`。无会话返回 401；他人任务、素材、结果统一返回 404。
- 任务 GET 和历史 GET 严格只读，不触发领取、阶段变更或模拟计时。页面刷新从 MySQL 恢复，不能依赖浏览器内存恢复历史。

## 2. 端点

| 方法与路径 | 请求 | 成功响应 | 身份/错误 |
| --- | --- | --- | --- |
| `GET /api/health` | 无 | `200 {"status":"UP"}` | 公开；只作容器健康检查。 |
| `GET /api/templates?category={name}` | `category` 可省略；按完整分类名精确筛选 | `200 Template[]`，无数据为 `[]` | 公开；非法参数 400。 |
| `GET /api/templates/{templateId}` | UUID | `200 Template` | 公开；不存在/非法 ID 返回 404/400。 |
| `GET /api/templates/{templateId}/cover` | UUID | `200 image/svg+xml` 或 `image/jpeg/png` | 公开；模板不存在 404。封面必须为仓库内可用素材。 |
| `GET /api/demo-users` | 无 | `200 DemoUser[]` | 公开但只在 `DEMO_MODE=true` 启用；关闭时 404。 |
| `POST /api/session` | `{"demoUserKey":"alice"}` | `200 User`，并设置会话 Cookie | 未知键 400；不能由客户端提交 `userId` 定义资源归属。 |
| `GET /api/session` | Cookie | `200 User` | 无有效会话 401。 |
| `DELETE /api/session` | Cookie | `204`，撤销会话并清 Cookie | 已无有效会话也返回 204，允许幂等退出。 |
| `POST /api/assets` | `multipart/form-data`：`templateId`、`file` | `201 Asset` | 需会话；无效模板 404，过大 413，非法图片 415，其他输入 400。 |
| `GET /api/assets/{assetId}/content` | Cookie | `200` 图片流；`Cache-Control: private, no-store` | 需会话；不存在、非本人或非图片均 404。上传及结果共用此私有读取入口。 |
| `POST /api/tasks` | `CreateTaskRequest` | **`202 Accepted`**，`Task` 快照，`Location: /api/tasks/{taskId}` | 需会话；必须在流水线完成前返回。 |
| `GET /api/tasks/{taskId}` | Cookie | `200 Task` | 需会话；非本人或不存在 404；只读。 |
| `GET /api/tasks` | Cookie | `200 Task[]`，按 `createdAt DESC,id DESC` | 需会话；演示规模下返回本人全部记录，不设隐藏的条数截断。 |

暂不增加模板 CRUD、积分、下载、删除、重试任务或第三方账号接口。公开模板分类由 `GET /api/templates` 的 `category` 去重生成；前端不写死分类。

## 3. DTO 字段

| 类型 | 字段与约束 |
| --- | --- |
| `Template` | `id` UUID；`title`、`category`、`description` 非空；`coverUrl` 指向可用公开封面；`requiredImageCount` 正整数；`acceptedMimeTypes` 至少一项，默认只用 `image/jpeg`、`image/png`；`maxFileSizeBytes` 正整数且不超过服务端上传上限。 |
| `DemoUser` | `id` UUID、`demoUserKey`、`displayName`；不返回令牌摘要。 |
| `User` | `id` UUID、`displayName`、`demoUserKey`；会话令牌只在 HttpOnly Cookie 中。 |
| `Asset` | `id` UUID、`previewUrl`（本人私有入口）、`mimeType`、`sizeBytes`、`width`、`height`；不返回物理路径。 |
| `CreateTaskRequest` | `templateId` UUID；`assetIds` 按模板槽位顺序排列，数量精确匹配且不能重复；`simulation` 可省略。 |
| `Task` | `id,templateId,templateTitle,status,stage,createdAt,startedAt,finishedAt,resultUrl,failedStage,errorCode,errorMessage,version,steps`；未产生的可空字段返回 JSON `null`。`resultUrl` 仅成功时非空，指向本人可访问的结果 `Asset`。 |
| `TaskStep` | `stage,status,startedAt,finishedAt,errorMessage`；受理时按固定阶段顺序返回三项。 |

受理响应示例（字段形状固定，值仅为示例）：

```http
POST /api/tasks
Content-Type: application/json

{"templateId":"11111111-1111-4111-8111-111111111111","assetIds":["22222222-2222-4222-8222-222222222222"],"simulation":{"failAtStage":"GENERATE","stageDelayMs":{"ASSET_CHECK":1500,"GENERATE":5000,"FINALIZE":1500}}}

HTTP/1.1 202 Accepted
Location: /api/tasks/33333333-3333-4333-8333-333333333333
Content-Type: application/json

{"id":"33333333-3333-4333-8333-333333333333","templateId":"11111111-1111-4111-8111-111111111111","templateTitle":"旅行明信片","status":"QUEUED","stage":null,"createdAt":"2026-10-30T08:00:00Z","startedAt":null,"finishedAt":null,"resultUrl":null,"failedStage":null,"errorCode":null,"errorMessage":null,"version":0,"steps":[{"stage":"ASSET_CHECK","status":"PENDING","startedAt":null,"finishedAt":null,"errorMessage":null},{"stage":"GENERATE","status":"PENDING","startedAt":null,"finishedAt":null,"errorMessage":null},{"stage":"FINALIZE","status":"PENDING","startedAt":null,"finishedAt":null,"errorMessage":null}]}
```

`POST /api/tasks` 要同时校验素材属于当前用户及指定模板，文件存在且仍符合模板 MIME/大小限制。错误示例：`400 ASSET_COUNT_MISMATCH`、`400 DUPLICATE_ASSET`、`400 INVALID_SIMULATION`、`404 TEMPLATE_NOT_FOUND`、`404 ASSET_NOT_FOUND`、`401 UNAUTHENTICATED`。未实现受理幂等时，提交请求超时要先刷新历史确认是否已受理，不能无提示地自动重发 POST。

## 4. 状态、阶段和模拟控制

| 任务 `status` | 含义 | 允许的下一状态 |
| --- | --- | --- |
| `QUEUED` | 已受理、等待 Java 工作器领取；`stage=null` | `RUNNING` |
| `RUNNING` | Java 已领取且正在按阶段推进 | `SUCCEEDED` 或 `FAILED` |
| `SUCCEEDED` | 终态；`resultUrl` 非空 | 无 |
| `FAILED` | 终态；`failedStage,errorCode,errorMessage` 非空 | 无 |

阶段只用 `ASSET_CHECK → GENERATE → FINALIZE`。步骤状态为 `PENDING → RUNNING → SUCCEEDED/FAILED`；失败阶段之后未开始的步骤转 `SKIPPED`。任务成功后 `stage=FINALIZE`；失败后 `stage=failedStage`。`stage` 表示最后执行的阶段，不表示终态。每次任务整体或步骤状态变化，`version` 严格递增；查询、文件预览和空轮询不改变版本。

`simulation` 格式为：

```json
{"failAtStage":null,"stageDelayMs":{"ASSET_CHECK":2000,"GENERATE":2000,"FINALIZE":2000}}
```

`failAtStage` 允许 `null` 或三阶段枚举；`stageDelayMs` 可只提供部分键，缺省取 Java 配置。各值须为 0～30000 的整数毫秒；拒绝未知阶段、负数、小数、超上限。只在 `DEMO_MODE=true` 接受请求覆盖，受理时将完整配置快照保存到任务，避免运行中配置变化影响旧任务。某阶段模拟失败时保存 `FAILED` 和可读原因，后续步骤 `SKIPPED`；不随机失败，不由 GET 触发失败。

## 5. 前端与代理验收点

- 上传中、提交请求中、`QUEUED/RUNNING`、`SUCCEEDED`、`FAILED` 是不同 UI 状态。202 后立即显示新任务，提交按钮不继续显示“提交中”。
- 查询异常是请求层错误：保留最后已知 `Task`，显示恢复查询按钮；绝不写 `status=FAILED`。首次查询失败与“历史为空”分开显示。
- 当前实现使用包含 `userId` 的任务列表查询键，轮询 Java 返回的本人完整历史；仅列表中存在 `QUEUED/RUNNING` 且上次查询未报错时约每 2 秒查询一次，全部终态或空列表时停止。每条任务仍按 ID、`version` 独立合并，迟到的旧版本或运行态不得覆盖更新版本或终态。新任务 202 后写入对应用户/任务缓存并更新列表，使轮询重新启动。历史页刷新重新查询；切换用户时取消旧请求并清除旧用户私有缓存。查询报错只保留快照并提供手动重试，不把任务标为 `FAILED`。
- 代理契约测试至少覆盖上传 multipart、Cookie/Set-Cookie、202/Location、401/404/502 错误体和私有图片 `Content-Type`。图片 URL 必须保持同源、带会话可预览。

## 6. 启动收敛与运行边界

Java **单实例**工作器启动前，在单个数据库事务里将遗留 `RUNNING` 任务改为 `FAILED`，写 `errorCode=PROCESS_INTERRUPTED`、实际未完成的 `failedStage` 和 `finishedAt`。正在 `RUNNING` 的步骤改 `FAILED`；若上一步已成功而下步尚未开始，则下步改 `FAILED`，保留上一步成功记录；其后 `PENDING` 步骤改 `SKIPPED`。`QUEUED` 原样保留并由工作器后续领取。终态不改。此策略是中断收敛，**不是阶段续跑**；不支持多 Java 实例竞争与故障转移。状态收敛与阶段行为的表结构约束见 [schema-design.md](schema-design.md)。
