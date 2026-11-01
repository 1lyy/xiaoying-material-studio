# 独立最终验收记录

> **最新增量（2026-11-01）：** Ubuntu 容器使用已有 `backend-test` 镜像挂载当前 `backend/` 源码，连接健康的 MySQL 8.4 `db-test` 执行 `mvn verify`。导出日志和 11 份 Surefire XML 均为 **31 tests、0 failures、0 errors、0 skipped，BUILD SUCCESS**，并完成 JAR 打包。下文初审矩阵及旧失败记录保留历史时间点，不能作为当前后端测试结论。标准 `--build` 干净重建、GitHub Actions、自动 HTTP 冒烟和卷保留仍未验证。

> **HTTP 冒烟增量（2026-11-01）：** Ubuntu 使用 `sh scripts/offline-smoke.sh initial` 复用已有前端镜像运行同一冒烟脚本，终端实际显示 `PASS initial: seed, cover, upload, 202, two successes, three staged failures, history, Bob 404`。下文初审矩阵中的 HTTP 未验证结论已过期；数据卷重启后的 `persist` 模式、干净镜像重建与 GitHub Actions 仍未验证。

> **持久化增量（2026-11-01）：** 正常执行 `docker compose down`、`docker compose up -d --wait` 后，db/backend/frontend 均 Healthy；`sh scripts/offline-smoke.sh persist` 实际显示 `PASS persistence: task history, uploaded file, result file retained after compose down/up; Bob remains isolated`。下文初审矩阵中的卷保留未验证结论已过期。干净镜像重建、干净卷初始化和 GitHub Actions 仍未验证。

初审日期：2026-10-30；增量核查：2026-10-31。依据 `题目原文.md`、`执行计划.md` 及本目录当前源码重新核对。**PASS** 仅指所列证据实际检查通过；**UNVERIFIED** 指当前修订尚未取得必需的运行证据；**FAIL** 指已确认不满足要求。测试文件存在不等于测试通过。Windows 宿主没有 Docker，目录没有 `.git`；用户在 Ubuntu 虚拟机第三次运行前端容器检查，lint/typecheck、Vitest 35/35 和 build 已通过；后端、整站运行与 CI 仍不能标 PASS。

## R-01～R-14 验收矩阵

| 要求 | 源码证据（文件/方法） | 自动测试证据（源码；本轮执行状态） | 容器运行证据 | 结论 |
| --- | --- | --- | --- | --- |
| R-01 技术栈与职责 | `frontend/package.json`、`src/app/`、`src/app/api/[...path]/route.ts`；`backend/pom.xml`、`TaskService.create`、`TaskTransactions`、MyBatis XML、Flyway | Ubuntu 前端 lint/typecheck/Vitest/build 已完成；后端首次 verify 失败，细节待查 | 前端和后端测试容器已运行；完整应用未运行 | UNVERIFIED |
| R-02 列表与数据 | `V2__seed_templates.sql` 三类/1、2、3 图；`TemplateMapper.xml`、`TemplateService.list/detail`、`frontend/src/app/page.tsx` | `IdentityTemplateMySqlTest`、`page.test.tsx`；未运行 | 无 | UNVERIFIED |
| R-03 详情与上传 | `frontend/src/app/templates/[id]/page.tsx`、`UploadSlot`；`AssetService.upload`、`PrivateImageStorage.save/inspect` | `AssetServiceTest`、`AssetMySqlTest`、`PrivateImageStorageTest`、`upload-slot.test.tsx`；未运行 | 无 | UNVERIFIED |
| R-04 异步受理 | `TaskController.create`、`TaskService.create`、`TaskAcceptance.accept`、`TaskWorker.tick`；202/Location 与独立 Worker | `TaskMySqlTest.httpAcceptanceReturnsWhileWorkerStageIsBlockedByControlledDelay`；未运行 | 无 | UNVERIFIED |
| R-05 阶段与查询 | `TaskPipeline.run`、`TaskTransactions.startStage/completeStage/completeFinal/failStage`、`TaskMapper.xml` 条件更新；`TaskService.get/history` 只读 | `TaskPipelineTest`、`TaskMySqlTest` 阶段/版本/GET 断言；未运行 | 无 | UNVERIFIED |
| R-06 失败与耗时 | `SimulationSnapshot.parse`、`TaskPipeline.run`；三个指定阶段、每阶段延迟 0～30000 ms | `SimulationSnapshotTest`、`TaskPipelineTest.eachConfiguredFailureStopsLaterStages`、`TaskMySqlTest.specifiedStageFailureSkipsLaterSteps`；未运行 | `scripts/container-smoke.mjs` 有三失败断言，但未运行 | UNVERIFIED |
| R-07 状态与请求异常 | `UploadSlot`、模板详情提交状态、`TaskTracker`、`frontend/src/app/history/page.tsx`、`api.ts` 错误类型 | `history/page.test.tsx`、`task-tracker.test.tsx` 等前端测试在 Ubuntu 容器通过 | 浏览器/Java 联调未运行 | UNVERIFIED |
| R-08 多任务与轮询 | `task-cache.ts` 按 ID/version 合并、`TaskTracker.useTasks` 活跃列表轮询、`SessionProvider.clearPrivateQueries` | `task-cache.test.ts`、`task-tracker.test.tsx`、`history/page.test.tsx` 在 Ubuntu 容器通过 | 实际多任务 HTTP 演示未运行 | UNVERIFIED |
| R-09 刷新与历史 | `TaskMapper.xml` 的 `history` 按 `created_at DESC,id DESC` 无固定截断；`history/page.tsx` 后端查询和结果图 | `TaskMySqlTest` 历史/结果断言、`history/page.test.tsx`；未运行 | 冒烟脚本含 down/up 后核验，未运行；浏览器刷新未验证 | UNVERIFIED |
| R-10 身份隔离 | `SessionService` 32 字节令牌/摘要、`SessionController`、`AssetMapper.xml.findOwned`、`TaskMapper.xml.findOwned/history`、`OriginGuardInterceptor` | `IdentityTemplateMySqlTest`、`TaskMySqlTest` 的 Bob 404、`SessionControllerTest`、前端切用户测试；未运行 | 冒烟脚本含 Alice/Bob 检查，未运行 | UNVERIFIED |
| R-11 视觉与可用性 | 首页、详情、历史页面，`globals.css`、原生按钮/标签/焦点与响应式布局 | `page.test.tsx`、`upload-slot.test.tsx`、`history/page.test.tsx` 在 Ubuntu 容器通过 | 无浏览器截图或手工小屏验收 | UNVERIFIED |
| R-12 CI 与测试 | `.github/workflows/ci.yml` push/PR；容器前端 lint/typecheck/test/build、MySQL 8.4 后端 verify、Compose 冒烟 | 第三次 Ubuntu 前端容器 lint/typecheck、Vitest 35/35、Next.js build 全部完成；首次后端 verify 31 项中 12 failures、2 errors | 无本项目 Actions run；完整应用未启动 | FAIL |
| R-13 交付说明 | `README.md`、`frontend/package-lock.json`、`backend/.mvn/wrapper/`、Flyway、`.env.example`、本验收记录与源码压缩包 | 包清单与排除项检查见下文；未执行应用测试 | 不要求运行才能检查文档/包；演示仍未验证 | PASS（静态交付） |
| R-14 Compose | `compose.yaml`、前后端多阶段 Dockerfile、健康检查与命名卷；仅前端端口映射 | 静态 YAML 解析通过；`docker compose config` 未执行 | `docker compose up --build` 未执行 | UNVERIFIED |

## P0 追查

| 风险 | 源码核查与测试入口 | 本轮判断 |
| --- | --- | --- |
| 未真正异步执行 | 受理事务 `TaskAcceptance.accept` 与独立 `TaskWorker` 分离；受控延迟下 202 的 `TaskMySqlTest` | 结构具备；真实运行 UNVERIFIED |
| 阶段乱序/失败后继续 | `TaskStage.values()` 顺序循环；`startStage` 检查前序 SUCCEEDED，`failStage` 标后续 SKIPPED；三失败测试 | 源码有保护；测试 UNVERIFIED |
| 终态回退 | SQL 更新带 `status='RUNNING'` 或 `status='QUEUED'` 条件，检查影响行数；前端 `mergeTask` 保终态/高版本 | 前端版本回退测试已通过；MySQL 测试 UNVERIFIED |
| 重复领取 | `queueHead` 后 `claim` 条件更新，受影响行数决定赢家；`concurrentConditionalClaimIsSingleWinnerAndTasksRemainIndependent` | 源码有原子领取；MySQL 测试 UNVERIFIED |
| 越权读取 | 任务/历史和素材均从会话取得 userId，SQL 按 `id AND user_id`；他人和不存在均 404 | 源码有归属过滤；MySQL/HTTP UNVERIFIED |
| 文件路径不安全 | `PrivateImageStorage.resolveOwnedFile` 仅接受 `uploads/文件名`，规范化、拒绝符号链接并检查 real path；存储随机名 | 源码有边界检查；测试 UNVERIFIED |
| 用户切换串扰 | `SessionProvider` 先取消并移除私有查询，查询键包含 userId；历史在 busy 时隐藏 | 前端回归测试已通过；浏览器 UNVERIFIED |
| 查询失败误判 | `useTasks` 错误状态独立于 Task.status；历史保留旧数据和重试；Java GET 无写操作 | 前端回归测试已通过；Java/HTTP 未验证 |
| 轮询不停 | `taskPollInterval` 仅有 QUEUED/RUNNING 且请求未报错返回 2000，否则 false；新 202 更新列表缓存 | 前端假时钟测试已通过；实际浏览器轮询未验证 |
| 重启边界造假 | `TaskWorker.start` 先 `convergeInterrupted`；RUNNING 失败，QUEUED 保留；README 明说不续跑 | 发现并修复阶段间中断记录不一致；回归测试未运行，实际重启 UNVERIFIED |

## 问题整改与剩余边界

1. **P1 已改，未验证：阶段之间重启收敛。** 原实现若 ASSET_CHECK 已完成、GENERATE 尚未开始，会把任务 `failedStage` 写成 ASSET_CHECK，同时三步为 `SUCCEEDED/SKIPPED/SKIPPED`。先在 `TaskMySqlTest.startupConvergenceFailsRunningAndKeepsQueued` 添加断言：`failedStage/currentStage=GENERATE`，步骤为 `SUCCEEDED/FAILED/SKIPPED`；再修改 `TaskTransactions.convergeInterrupted`，将中断归到首个未完成步骤。未运行 Maven/MySQL，不标通过。
2. **剩余 P1 运行门槛：**前后端依赖安装、lint/typecheck/Vitest/build、Maven verify、Flyway/MySQL 8.4、Compose config/build/start、全链路冒烟、卷保留、浏览器截图和小屏交互均无本轮运行证据。真实失败出现后需保留测试并修复。
   2026-10-31 增量：Ubuntu 容器旧包已完成前端 `npm ci`，lint 为 0 errors/1 warning，typecheck **FAIL**（`TS2339`）；先补空详情重试回归测试，再修正查询类型和 lint 警告。修订版尚未复测，Vitest/build 仍未运行；其余运行门槛未变。
3. **已知范围边界：**单实例串行 Worker；运行中的数据库长时间不可用可使任务暂留 RUNNING，直到进程重启收敛；数据库与文件系统不能原子提交，崩溃可能留下孤儿文件；历史无静默截断但无分页；无真实 AI、正式认证、受理幂等、断点续跑和自动重试。未做题外增强。

## 实际命令与结果

| 命令/检查 | 结果 |
| --- | --- |
| `git status --short`、`git rev-parse HEAD` | 退出码 1：`not a git repository`；无验收 commit。 |
| `git diff --check` | 退出码 1：无 `.git`，该命令不能检查本目录差异；不能写成通过。 |
| `docker compose --profile test --profile smoke config --quiet` | 退出码 1：PowerShell 找不到 `docker`。 |
| `docker compose --profile test run --build --rm --no-deps frontend-test` | 退出码 1：PowerShell 找不到 `docker`；其中的 `npm ci`、lint、typecheck、Vitest、build 均未开始。 |
| `docker compose --profile test run --build --rm backend-test` | 退出码 1：PowerShell 找不到 `docker`；Maven verify/MySQL 集成测试未开始。 |
| `docker compose up --build -d --wait` | 退出码 1：PowerShell 找不到 `docker`；无应用运行、健康或 HTTP 结果。 |
| Python 读取 `frontend/package.json`、`package-lock.json`，解析 POM/四个 MyBatis XML | 静态解析通过；不验证 Java/TS 编译或 SQL 执行。 |
| Python PyYAML 读取 `compose.yaml`、`application.yml`、`ci.yml` | 静态解析通过；不能替代 Compose config 或 Actions 执行。 |
| Python 检查本轮改动的 Java、测试、脚本与文档行尾空白 | 0 处行尾空白；这是 `git diff --check` 不可用时的补充检查，不能替代 Git 差异检查。 |
| `actions/checkout@v7.0.1` | 已对照[官方发布记录](https://github.com/actions/checkout/releases/tag/v7.0.1)确认该标签存在；本项目 CI 未运行。 |

按 `AGENTS.md` 和题干的容器隔离要求，没有在宿主机执行 npm、Maven、Java 应用或项目测试。当前目录也没有 `.git`，无法给出 HEAD 或差异检查结论。外部参考仓库的运行记录不计为本项目证据。

### 2026-10-31 Ubuntu 虚拟机首次运行与整改

用户提供的终端截图来自 VirtualBox Ubuntu 22.04.5、Docker 服务端 29.7.1、Compose 5.3.1：`docker compose --profile test run --build --rm --no-deps frontend-test` 成功完成 `npm ci`；`npm run lint` 报 0 errors/1 warning；`npm run typecheck` 在 `src/app/templates/[id]/page.tsx:106:237` 报 `TS2339: Property 'refetch' does not exist on type 'never'`，所以 Vitest 和 build 没有开始。**旧包的前端检查是 FAIL**。本地先补空详情重试回归测试，再将查询数据类型显式设为 `Template | null`，并消除 PostCSS lint 警告；**修订版尚未在容器复测，状态 UNVERIFIED**。后端 verify、MySQL、Compose 启动、HTTP 冒烟和持久化仍无运行证据。详细记录见 `frontend-verification.md` 与 `container-acceptance.md`。

## 交付清单与独立复现

交付包：`dist/xiaoying-fullstack-submission.zip`。通过 `python scripts/package-submission.py` 以明确白名单生成，包含 `frontend/`、`backend/`（含 `.mvn`、`mvnw`、`mvnw.cmd`）、`package-lock.json`、三份 Flyway 迁移、`.github/workflows/ci.yml`、`compose.yaml`、`.env.example`、`demo-inputs/`、`scripts/`、`README.md`、`docs/`、`AGENTS.md` 和 `.gitignore`。排除 `.git`、`.env`、依赖和构建缓存、上传/数据库持久化数据。生成后对 151 个条目执行 `ZipFile.testzip()`、重复/缺失条目与排除目录检查，结果通过；这仅验证压缩包可读，不验证应用可运行。

另一位评审者可从解压后的根目录复制 `.env.example` 为 `.env`，在容器环境执行 `docker compose --profile test --profile smoke config --quiet`、`docker compose --profile test run --build --rm --no-deps frontend-test`、`docker compose --profile test run --build --rm backend-test`、`docker compose up --build -d --wait`、`docker compose --profile smoke run --rm smoke`。正常 `docker compose down` 后重新 `up -d --wait`，再以 `SMOKE_MODE=persist docker compose --profile smoke run --rm smoke` 核对历史及素材卷。浏览器手工检查刷新、用户切换、键盘和小屏布局。具体操作与预期在 `README.md`；这些命令在本机均未取得通过证据。

**当前门槛：静态材料齐备；Ubuntu 前端容器门槛 PASS；旧版后端 verify FAIL，最新修订尚未复测；完整应用主流程已在浏览器运行，自动 smoke、卷保留和 CI 仍 UNVERIFIED。** 本人还需确认实际有效投入，并决定通过原沟通渠道提交。

### 2026-10-31 第二次前端容器运行

用户提供的第二次 Ubuntu 截图显示：lint/typecheck 完成；Vitest 35 项中 **32 passed、3 failed**，build 未开始。两项 `task-tracker.test.tsx` 在假 `setInterval` 环境中用 Testing Library `waitFor` 等待初次异步数据；一项 `history/page.test.tsx` 在缓存更新后立即检查尚未重绘的页面。已修正测试等待方式，**修订版尚未复测**。因此当前前端门槛仍为 **FAIL（上一轮实测）/UNVERIFIED（最新源码）**；后端和端到端门槛未验证。为减少重复搬运压缩包，下一步优先用 VirtualBox 共享文件夹从 Ubuntu 读取 Windows 源码。

### 2026-10-31 第三次前端容器运行

用户通过 VirtualBox 临时共享将 Windows 项目目录挂载在 Ubuntu `/mnt/XiaoYing`，从该目录执行 `docker compose --profile test run --build --rm --no-deps frontend-test`。截图可见镜像构建完成、lint/typecheck 完成、Vitest **10 files passed / 35 tests passed**、Next.js 16.4.3 生产编译及页面生成完成，随后返回 shell。**前端容器门槛 PASS**；前述第一、二次失败为历史记录。测试 mock 的 React 属性警告仍在，不影响检查通过。Maven verify、MySQL 8.4、完整 Compose 启动、HTTP 冒烟、卷保留、浏览器手工验收和 Actions 运行仍 **UNVERIFIED**。

### 2026-10-31 首次后端容器运行

用户的末尾截图显示从共享目录执行的 `backend-test` 最终由 Maven Surefire 报告 `Tests run: 31, Failures: 12, Errors: 2, Skipped: 0` 和 `BUILD FAILURE`。**后端验证 FAIL**，不推测具体测试原因；旧命令使用 `--rm`，报告随测试容器移除，截图不足以定位。已加入保留退出码的 `verify-and-export.sh` 和 `backend-test-output/` 共享输出目录，重跑后可从 Windows 直接读取日志与 Surefire 报告。测试容器不是网站服务；本地 `.env` 已准备 VM host-only 地址 `192.168.56.101:3000` 供后续 Windows 浏览器访问，但完整应用尚未启动，地址仍 **UNVERIFIED**。

### 2026-10-31 后端报告与新构建阻塞

第二次导出的 Surefire 报告显示 31 项、12 failures、1 error；`IdentityTemplateMySqlTest` 3/3 通过，`AssetMySqlTest` 和 `TaskMySqlTest` 主要因 `AssetRow` 构造器映射产生 500。修订 MyBatis 基本类型别名后，用户新截图显示后端镜像在下载 Maven Wrapper 3.9.11 发行版时构建失败，未进入新一轮测试。现改用固定 Maven 3.9.11 构建镜像，保留 Wrapper 源文件。**该修订的构建、测试和网站访问均未验证**；旧报告只证明旧源码失败，不能用于判定最新源码通过。

### 2026-10-31 网站主流程手工验收

用户随后报告 Compose 启动成功。Windows 浏览器实际访问 Ubuntu 虚拟机的 `http://192.168.56.101:3000/`，验证三模板与分类筛选、Alice 登录、`coast.png` 上传、提交立即出现任务 ID、历史页三阶段成功、结果图加载为 900×600、刷新恢复、Bob 空历史和 Bob 直接读取 Alice 结果得到 `ASSET_NOT_FOUND` 错误体。截图在 `docs/screenshots/`；这证明主流程运行，不能代替最新 Maven verify、三种失败、多任务、HTTP 404 状态或卷重启检查。前述“未运行网站”文字是历史快照，以本节为最新状态。

### 2026-11-01 补记：最新后端 MySQL 测试

用户最新截图及 `backend-test-output/` 报告显示测试镜像已构建、MySQL 8.4 测试数据库健康，Maven verify 实际运行 31 项，结果 **1 failure、0 errors、0 skipped**。唯一失败是 `TaskMySqlTest.realMySqlConstraintsRejectInvalidStateAndDuplicatePosition` 对 Spring 异常子类的预期过窄；MySQL 已以错误码 3819 拒绝违反 `chk_tasks_status` 的写入。测试现核对底层 SQL 错误码/约束名，并对重复键核对 1062；**修订版未复跑，后端门槛仍 FAIL**。前文 12 failures/1 error、构建下载失败等是历史记录，不代表最新运行结果。自动 HTTP 冒烟、数据卷重启及 CI 仍待核实。

### 2026-11-01 补记：重启后的测试容器环境

Docker Hub 镜像元数据请求连接被拒绝，用户改用本地已有测试镜像挂载当前源码。新 Surefire 报告为 **31 tests、0 failures、24 errors、0 skipped**，共同根因是 Mockito inline Byte Buddy mock maker 无法在该 Java 21 容器自附加 agent。POM 已按 Mockito 官方指南为 Surefire 配置显式 `mockito-core` Java agent，**修订版尚未运行**；后端门槛仍 FAIL，不能把 0 failures 误写成通过。详见 `backend-verification.md`。
