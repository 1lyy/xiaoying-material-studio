# 开发日志

## 2026-10-30 · 单仓库脚手架与最小环境

本轮范围：仅建立 Next.js、Spring Boot、MySQL 的基础结构、同源代理、健康接口、统一错误体和最小测试。没有实现模板、上传、会话、任务或历史业务，也没有更改已冻结 API 字段。连续计时未在会话开始时启用，故不填写无法核实的实际小时数；本轮结束时点为 2026-10-30 17:03（Asia/Shanghai）。

### 已完成的源码与配置

- `frontend/`：固定直接依赖及 npm lockfile；App Router、strict TypeScript、Tailwind、TanStack Query、Vitest/Testing Library、lint/typecheck/test/build 脚本；基础首页、健康按钮、页面错误边界。
- Next.js `/api/[...path]` 代理：服务端环境指定唯一 Java 上游，流式转发 body、Cookie 与响应；保留 202/Location、Set-Cookie、Content-Type，使用 `AbortSignal` 与 `no-store`。代理基础测试覆盖 multipart、Cookie、202、结果流、404 和 502。
- `backend/`：Java 21 / Spring Boot 3.5 / MyBatis / MySQL Driver / Flyway / Actuator / Spring Boot Test 的 Maven 工程；Maven Wrapper；`GET /api/health`、统一错误体基础与 MVC 最小测试。
- 根目录 Compose、Dockerfile、`.env.example`、`.gitignore`；数据库和素材命名卷，仅前端发布宿主端口。

### 取舍与下一轮入口

- 本轮不建七张业务表，避免提前实施 P1；Flyway 依赖与目录规划已就位。下一轮首先在容器内完成安装、测试、构建、`docker compose config` 与 `up --build`，根据失败修正脚手架，再进入数据库迁移和 Java 业务闭环。
- `package-lock.json` 用宿主 npm 的 `--package-lock-only --ignore-scripts` **仅解析依赖元数据**产生；没有安装 `node_modules` 或运行项目脚本。宿主 Node 为 24.16.0，目标 Node 22.23.4 的 `npm ci` 和构建仍待容器验证。
- 解析锁文件后已删除临时 `.npm-cache/`；交付目录不留本轮生成的 npm 缓存。
- Maven Wrapper 脚本来自 Apache-2.0 的固定提交，未复用三个无明确业务源码许可的笔试仓库。
- 当前工作目录尚无 Git 仓库元数据，本轮没有验收 commit 或远端提交。

详见 [实际验证记录](verification-p0.md)。

## 2026-10-30 · 七表、演示身份与模板接口

实际投入约 20 分钟（按本轮会话时段估算，未启用连续计时器）。本轮只实现数据库与身份、模板最小闭环的源码，未开发 Worker、上传或任务处理。

- `V1__schema.sql` 建七张业务表、外键、唯一约束、CHECK、索引与状态/版本默认值；V2、V3 以固定 UUID 写入三个分类、1/2/3 张要求的模板和 Alice/Bob。迁移尚未在 MySQL 执行，若首次应用后需变更，必须新增版本，不得回写。
- 三张封面为本项目原创 SVG，随 Java 资源打包，通过公开模板封面接口访问。
- Java 会话使用 32 字节安全随机数、SHA-256 摘要和数据库过期时间；演示模式开关、HttpOnly/Lax/Secure Cookie、Origin 白名单和未知 JSON 字段拒绝已编码。演示身份仅供笔试，不是正式认证。
- MyBatis XML Mapper 读取用户和模板；接口测试及 MySQL 集成测试源码已写，增加 Compose `test` profile 作为后续执行入口。
- 实际执行了 XML 与 Compose YAML 静态解析和七表文件计数；Docker 命令仍不存在，故 Maven 测试、Flyway/MySQL 迁移和 HTTP 联调均未执行。详情见 [P1 验证记录](verification-p1.md)。

取舍：目前不引入 H2 替代 MySQL，也不为通过测试在宿主机运行 Maven；没有任务 Worker、上传或任务接口，启动收敛留待任务流水线实现时落地。

## 2026-10-30 · 素材上传与私有文件

本轮按 R-03、R-10 实现 `POST /api/assets` 和 `GET /api/assets/{id}/content` 的源码，新增 `demo-inputs/` 原创 PNG/JPEG 样图；未实现 Worker、任务接口或前端业务页面。没有从本轮开始启用连续计时器，不填写无法核实的实际小时数。

- Java 从会话获取用户、从 MySQL 模板取得 MIME/大小/图片数量配置；单文件上传同时受模板限制和 5 MiB 全局上限，校验扩展名、声明类型、文件签名、完整解码及 2000 万像素上限。模板精确素材张数留给后续任务受理验证，允许用户重新上传替换图片。
- `STORAGE_ROOT/tmp` 临时写入，校验后优先原子移动至 `STORAGE_ROOT/uploads`；数据库只保存随机文件名的相对路径、归属、实际 MIME/字节/尺寸和 `UPLOAD` purpose。写库事务异常后尽力删除新文件。
- 私有读取先查 `assetId + userId`，无记录、非本人、路径穿越或文件消失一律 404；图片响应设 `private, no-store` 和 `nosniff`。模板封面保持独立公开接口。Next.js 代理保持 multipart/图片流原样转发，并给私有图片保留私有缓存策略。
- 新增 Java 单元与 MySQL 集成测试源码和代理测试断言。实际容器测试/构建入口均因没有 Docker CLI 而未执行；XML/YAML/样图的宿主静态检查通过，细节见 [素材验证记录](verification-p2-assets.md)。

取舍：不修改既有七表迁移；不增加删除已上传图的业务接口。数据库与文件系统不能原子提交，已说明孤儿文件边界。下一轮首要任务仍是取得容器环境、运行 Maven/MySQL/前端测试并修复实测问题，然后才编码任务状态机。

## 2026-10-30 · Java 任务与串行 Worker

本轮实现 `POST /api/tasks`、两个只读 GET、MyBatis 任务 Mapper、三阶段流水线、可注入延迟器/生成器、结果 PNG 和启动中断收敛；不开发前端任务状态逻辑。未启用连续计时器，实际小时数无法核实，因此不填写估算为事实。

- 受理前校验会话、模板、素材顺序/数量/去重、归属、MIME/大小和真实文件；写入事务复核数据库归属，并一次插入 `QUEUED` 任务、素材槽位和三个 `PENDING` 步骤，返回 202/Location。
- Worker 单线程约每 500 ms 尝试领取队首；`WHERE status='QUEUED'` 条件更新并检查行数。阶段状态通过独立 `TaskTransactions` Bean 短事务写入，延迟/图片处理在外；查询仅 SELECT。
- `GENERATE` 写临时 PNG，`FINALIZE` 移动到私有目录并在一个事务插入结果 asset、完成最终步骤及任务。指定失败写 `FAILED/SKIPPED`；启动时先处理遗留 `RUNNING`，保留 `QUEUED`。模拟配置在受理时保存完整快照，非演示模式拒绝请求覆盖。
- 新增纯单元与 MySQL 集成测试源码，测试数据库改为隔离的 `db-test`。实际 Docker 构建、测试、Compose config 均因本机缺少 Docker CLI 未执行；成功和三种失败均未真实演示。证据见 [任务验证记录](verification-p3-tasks.md)。

取舍：保持七表迁移不变；使用单实例串行 Worker，不实现自动续跑、跨进程锁或消息队列。数据库不可达时失败落库可能延迟，结果文件与数据库不具备分布式原子性。下一轮先在容器中完成构建/MySQL/HTTP 运行验证并修复实测问题，再进入前端业务页面。

## 2026-10-30 · 后端 P1 验证补强

本轮没有新增业务功能或改动已应用迁移。实际投入未连续计时，不填写未经测量的小时数。

- 对照任务、素材、Worker 与 Mapper 实际源码补充了可重复断言：受理 202 与受控延迟并行、可解码私有结果、三阶段失败、两线程条件领取、双任务隔离、逐边界版本和终态保护、重复 GET 只读、异常素材与模板、重启收敛、MySQL 8.4/Flyway/约束。
- `TaskMySqlTest` 使用真实 MySQL 配置才会运行；没有以 H2 代替。延迟用 latch 控制，其他任务模拟延迟为 0，状态等待仅作短间隔有界轮询。
- 实际尝试 `docker compose --profile test config` 和 `docker compose --profile test run --build --rm backend-test`，均因本机没有 `docker` 命令退出 1；未在宿主机运行 Maven，故没有 Maven `verify` 通过结论、数据库迁移结果或三种失败的真实演示。
- 源码复核未确认需要修改的生产缺陷。`resolveOwnedFile` 条件曾被误读，复查后确认原逻辑正确，未将其算作修复。P1 门槛仍是容器内 `verify` 与 MySQL 8.4 集成测试实际通过。逐项证据与阻塞原因见 [后端 P1 验证记录](backend-verification.md)。

## 2026-10-30 · Next.js 模板、演示身份与上传 UI

本轮范围限定为模板列表/详情、演示用户切换、上传位与 202 受理反馈；未开发任务轮询或历史页面。没有连续计时数据，不填写猜测的实际小时数。

- 首页模板、分类、封面和详情都由 Java `/api/templates` 提供；上传位按模板数量生成，XHR 报告上传进度，替换/移除取消旧请求并释放 object URL。受理请求只传服务端 `assetId`，202 后显示任务编号，不假装有最终结果。
- 接入 TanStack Query Provider、统一 API 错误类型和公开/私有 Query Key。顶栏演示用户切换通过 Java 会话接口；切换时取消并清除旧用户私有缓存，详情组件重新挂载以清理上传/提交请求。401 与临时服务故障分别提示。
- 新增首页、上传位、会话缓存、API 错误测试源码；Dockerfile `verify` 阶段与 Compose `frontend-test` 服务为容器内 lint/typecheck/test/build 提供重复入口。
- 实际尝试 `docker compose --profile test config` 及 `docker compose --profile test run --build --rm --no-deps frontend-test`，均因找不到 `docker` 退出 1。按仓库约束未在宿主机运行 npm；因此无测试、构建、Java 联调或页面截图通过证据。静态 YAML 解析成功。详情见 [前端验证记录](frontend-verification.md)。

## 2026-10-30 · 前端任务异步状态与历史

本轮只改 Next.js 前端与说明文件，没有重写 Java 业务。没有连续计时数据，不填写猜测的实际小时数。

- 加入完整 `Task/TaskStep` DTO、任务列表/详情请求与 10 秒请求超时；模板详情收到 202 后按用户和任务 ID 插入快照、失效历史并复位提交状态。超时属于受理结果未知，不自动重发。
- 新增 `/history`：读取 Java 真实历史并显示时间、模板、任务/阶段/步骤状态、失败原因和私有结果图片；图片加载失败可重试。
- 全局 TanStack Query 任务跟踪器仅在列表含 `QUEUED/RUNNING` 时以 2 秒间隔查询。缓存按 ID/版本合并，终态不回退，不对列表做固定截断；查询失败保留旧数据并暂停轮询，手动恢复后重新评估活跃状态。401 触发会话重查，500/503/超时只作为请求错误。
- 新增多任务交错、版本保护、查询异常与业务失败、图片重试、POST 超时不重发的测试源码。分别尝试容器内 lint、typecheck、test、build 和 compose config；全部因本机没有 `docker` 命令在启动前退出 1。按 `AGENTS.md` 未在宿主机运行 npm，故无运行通过证据或真实双任务/失败/网络异常演示。详见 [异步状态验证记录](frontend-tasks-verification.md)。

## 2026-10-30 · R-07～R-11 行为测试与可访问性修复

只增加前端行为测试并修正发现的上传入口问题；没有修改 Java、接口契约或业务流程。没有连续计时数据，不填写估计工时。

- 补 A/B/C 同屏隔离、B 迟到低版本、500/503 保留状态及成功恢复、假时钟轮询启停与新任务重启、卸载/切用户取消请求、Alice/Bob 页面隔离、上传替换/移除、重复提交、模板 404/空数据、结果图失败和焦点反馈测试。
- 可见上传入口从不可直接聚焦的 `label` 改成原生按钮；隐藏文件输入保留可读名称并退出 Tab 顺序。历史页在身份切换时隐藏旧用户卡片。相关 RTL 测试断言焦点、触发和切换过渡；各页面测试显式清理 DOM。
- 实际分别尝试容器内 `npm run lint`、`npm run typecheck`、`npm test`、`npm run build`。四次均因本机找不到 `docker` 在启动前退出 1；遵守 `AGENTS.md` 未在宿主机运行 npm。因此所有检查仍为 **UNVERIFIED**，真实浏览器行为也未验证。逐项证据和遗留问题见 [前端验证记录](frontend-verification.md)。

## 2026-10-30 · 工程交付与容器验收入口

- 新增 push/PR GitHub Actions：在容器内执行前端 `npm ci`、lint、类型检查、Vitest、build，后端 Maven verify + MySQL 8.4，随后构建完整运行镜像、启动 Compose、执行 HTTP 冒烟，并经正常 down/up 核验历史和图片保留。没有 `continue-on-error`。
- 新增 `smoke` 配置与 `scripts/container-smoke.mjs`：覆盖成功、三种阶段失败、多任务、结果图片和 Bob 404 隔离；用独立 `smoke-state` 命名卷保存重启核对 ID。前端镜像依赖安装改为标准 `npm ci`，测试服务关闭后台 Worker，素材卷挂载目标与 `STORAGE_ROOT` 一致。没有修改业务契约或既有迁移。
- 实际执行 `docker compose --profile test config --quiet` 和 `docker compose up --build -d --wait` 均在命令解析前失败：本机找不到 `docker`。未运行容器构建、Maven/npm 测试、Flyway、HTTP 冒烟或卷保留验证。静态 YAML 读取与两张样图的 Pillow 解码成功；具体命令与证据见 [容器验收记录](container-acceptance.md)。
- 本轮未连续计时，实际工时不可核实。下一步是在具备 Docker 的环境按容器验收记录执行，遇到真实失败后修复并更新证据；`RUNNING` 重启收敛为失败，不能称为自动续跑。

## 2026-10-30 · R-13 最终 README 整理

- 逐项对照题目 R-13、实际源码、`package-lock.json`、POM/Wrapper、Flyway、CI、测试记录与开发日志，补齐精确版本、目录与配置、API 字段、演示流程、状态机、部署回滚、来源许可和 AI 工作流。实际有效投入没有连续计时，README 标为“待本人确认”。
- 发现 `docs/api-contract.md` 仍保留 P0 的每任务独立轮询建议；源码实际使用当前用户完整历史列表轮询并按任务 ID/version 合并，已更新契约描述。`docs/source-audit.md` 的“当前无代码/锁文件”改成明确的 P0 历史快照与现在状态。
- 本轮仅编辑文档，未修改已冻结 Java API 或业务源码；没有运行 npm/Maven/Compose。此前 Docker 缺失的阻塞仍在，所有运行验收仍为 UNVERIFIED。

## 2026-10-30 · 独立最终验收与源码打包

- 重新对照 R-01～R-14 和 P0 风险检查源码、测试入口、CI/Compose/Flyway/README。发现阶段之间中断时，旧收敛逻辑把已成功步骤对应阶段误记为失败阶段；先修改 MySQL 回归测试，要求下一未完成步骤 FAILED、原步骤保持 SUCCEEDED，再修正 `TaskTransactions.convergeInterrupted`，同步更新契约与表设计说明。回归测试因 Docker 缺失未运行，修复状态为 **UNVERIFIED**。
- 实际执行 `git status --short`、`git rev-parse HEAD`、`git diff --check`，均因本目录无 `.git` 失败。`docker compose config`、前端容器检查、后端 Maven verify 和完整启动均因找不到 `docker` 在启动前失败；未在宿主机运行 npm/Maven。静态 JSON/XML/YAML 解析通过，不能替代运行验收。
- 新增 `docs/final-acceptance.md` 和源文件白名单打包脚本；压缩包清单/排除项由脚本检查。未创建 commit、推送或向招聘方发送材料；实际投入仍待本人确认。

## 2026-10-31 · Ubuntu 容器首次前端运行与类型错误整改

- 用户在 Ubuntu 22.04.5 虚拟机执行 `docker compose --profile test run --build --rm --no-deps frontend-test` 并提供终端截图。镜像构建和 `npm ci` 完成；lint 为 0 errors、1 warning；typecheck 报 `src/app/templates/[id]/page.tsx:106:237` 的 `TS2339`，命令在此停止，Vitest/build 未运行。这是旧交付包的真实失败，不是通过记录。
- 先在模板详情测试增加“空详情可重试并恢复模板”回归用例，再把 `useQuery` 的数据类型明确为 `Template | null`，使空响应分支可被类型系统识别；同时把 PostCSS 配置改为具名变量导出，消除截图中的 lint 警告。未关闭 TypeScript strict 或 ESLint 规则，也未改 Java API。
- Windows 宿主没有 Docker CLI，无法直接复测修订版；修订版的 lint、typecheck、Vitest、build 均为 **UNVERIFIED**。下一步将新压缩包复制到虚拟机的干净目录，重跑同一容器命令。实际有效投入未连续计时，仍待本人确认。

## 2026-10-31 · 第二次容器结果与简化验证流程

- 第二次用户截图确认 Ubuntu 容器 lint/typecheck 已完成，Vitest 35 个用例中 32 通过、3 失败；build 未执行。失败分别位于任务轮询测试的两个初次查询等待断言和历史页旧版本测试的页面重绘断言。
- 任务轮询测试在伪造 `setInterval` 后改用 `vi.waitFor` 等待缓存查询；历史页测试等待 React 重绘后检查卡片。修订后未复测，保留失败状态，不删除用例或放宽业务断言。
- 尝试准备 Windows 远程 Docker CLI，但用户希望更简单的方案，已清理未使用的本地工具下载。后续改用 VirtualBox 共享文件夹，避免每次重打包复制；共享挂载及本轮测试修订仍待验证。实际投入未连续计时。

## 2026-10-31 · 共享目录前端门槛通过

- Windows 项目目录通过 VirtualBox 临时共享挂载到 Ubuntu `/mnt/XiaoYing`，避免继续复制压缩包；`.env` 由无真实凭据的 `.env.example` 复制。共享在本次运行中可用，关机后是否保持尚未验证。
- 用户在该目录执行 `docker compose --profile test run --build --rm --no-deps frontend-test` 并提供终端截图：lint/typecheck 完成、Vitest 10 文件 35 用例全通过、Next.js 16.4.3 生产构建完成并返回 shell。前端容器门槛从先前失败更新为 **PASS**，不推断后端或真实浏览器通过。
- 测试 mock 仍有 `<img>` 的 `fill` / `unoptimized` 属性警告；该问题不影响测试退出结果。下一步是后端 Maven verify + MySQL 8.4 集成测试。实际有效投入未连续计时。

## 2026-10-31 · 后端首次运行失败与 Windows 浏览器访问准备

- 用户从 Ubuntu 共享目录执行 `docker compose --profile test run --build --rm backend-test`；末尾截图显示 Maven verify 31 项、12 failures、2 errors、0 skipped，最终 `BUILD FAILURE`。没有报告细节，尚不能判断各失败根因或声称 MySQL 集成通过。
- 为避免反复截取长输出，新增容器内 `verify-and-export.sh`，完整执行 Maven verify、保持其退出码，把日志及 Surefire 报告写入被忽略且不交付的 `backend-test-output/`；Compose 测试服务挂载该目录。该诊断入口尚未复跑。
- Windows 宿主已 ping 通 VM host-only IP `192.168.56.101`；仅在本地不交付的 `.env` 中把前端绑定地址设为该 IP，加入同地址 Origin 白名单。网站尚未启动，Windows 浏览器 `http://192.168.56.101:3000` 访问未验证。没有在宿主机安装或运行项目依赖。

## 2026-10-31 · 后端 Surefire 报告定位

- 用户重跑后端测试容器，`backend-test-output/` 成功导出 Maven 和 Surefire 报告。`verify` 仍为 31 项中 12 failures、2 errors；可确认是 MyBatis `AssetRow` 数值构造器映射、`TaskRow.version` 同类潜在映射、显式 `simulation:null` 解析，以及会话过期测试夹具违反 CHECK。
- 修正两个 Mapper XML 的基本类型声明、`SimulationSnapshot.parse` 对显式 null 的处理，并补充其单元断言；会话测试改用满足 CHECK 的过期时间。未改迁移、API 字段或前端逻辑。
- Windows 侧仅完成 Mapper XML 语法解析；修订后的 Maven verify、完整 Compose 启动和浏览器访问尚未实际执行，后端状态仍为 **FAIL（待复测）**。

## 2026-10-31 · 第二次后端复测与映射别名更正

- 用户第二次容器 `verify` 为 31 项中 12 failures、1 error；会话过期和显式模拟 null 的问题已消失，余下仍为 `AssetRow` 构造器类型不匹配。
- 对照 MyBatis 官方别名文档，确认 XML 中 `long`/`int` 仍解析为包装类；已将 `AssetMapper` 和 `TaskMapper` 的基本数值参数改用 `_long`/`_int`。修订后尚未运行容器测试，不能宣称后端通过。

## 2026-10-31 · 后端镜像构建下载失败

- 用户提供新的 Ubuntu Docker 构建截图：`backend/Dockerfile` 的 `RUN sh ./mvnw -B -ntp -DskipTests package` 在获取 Maven 3.9.11 ZIP 时失败，镜像构建退出 1。这次未进入 Maven 测试，不能把旧 Surefire 报告当作新修订的结果；网站也未启动。
- 把后端构建阶段改用固定标签 `maven:3.9.11-eclipse-temurin-21`，容器内直接调用 `mvn`，保留仓库 Maven Wrapper 供其他环境使用。这避免构建时再下载 Maven 发行版；依赖仍需从仓库下载。`backend-test` 的报告导出脚本同步调用镜像内 Maven。该修订尚未在 Ubuntu 重跑，状态 **UNVERIFIED**。

## 2026-10-31 · Compose 启动与 Windows 浏览器主流程

- 用户反馈 Ubuntu 虚拟机中的 Compose 启动成功；Windows 端实际打开 `http://192.168.56.101:3000/`，页面从 Java 显示三个模板和分类，桌面与小屏布局均可查看。首次运行的完整 Compose 命令输出未保留，因此健康等待的退出码以用户反馈为依据；浏览器可访问是独立观察。
- 浏览器以 Alice 登录，在单图“节日问候卡”上传 `demo-inputs/coast.png`，页面显示上传就绪；点击提交后立即出现任务 ID `000d342c-6e7d-47d8-8772-03d63944a388` 和“等待处理”。进入历史页时，该任务已 `SUCCEEDED`，三个步骤均显示已完成；图片元素实际加载，`naturalWidth=900`、`naturalHeight=600`。刷新历史页后同一任务和图片仍可见。
- 切换 Bob 后历史为空；在 Bob 会话中直接访问 Alice 的结果地址，服务返回 `ASSET_NOT_FOUND` 统一错误体。浏览器没有单独采集该响应的 HTTP 状态码，因此记录错误体，不把 404 当作本次浏览器实测。首页分类筛选后只剩对应模板。截图保存在 `docs/screenshots/`，其中首页、详情和成功历史为桌面 1280×800 视口下的整页截图；筛选与 Bob 空历史也有截图。
- 这轮没有执行最新 Maven verify、自动 smoke、三种失败、多任务排队或 down/up 卷保留。旧 Surefire 报告的失败仍是历史证据，不得因为应用成功启动就宣布后端测试通过。实际有效投入继续待本人确认。

## 2026-10-31 · 第二次后端复测与映射别名更正

- 用户第二次容器 `verify` 为 31 项中 12 failures、1 error；会话过期和显式模拟 null 的问题已消失，余下仍为 `AssetRow` 构造器类型不匹配。
- 对照 MyBatis 官方别名文档，确认 XML 中 `long`/`int` 仍解析为包装类；已将 `AssetMapper` 和 `TaskMapper` 的基本数值参数改用 `_long`/`_int`。修订后尚未运行容器测试，不能宣称后端通过。

## 2026-10-31 · 后端依赖下载中断处理

- 用户新截图显示 `backend-test` 的镜像构建在下载 `jna:5.17.0` 和 `httpcore5-h2:5.3.6` 时因响应体短于 `Content-Length` 失败；未进入 Maven verify，旧 Surefire 报告不代表当前源码。
- 后端 Dockerfile 为 Maven 依赖目录加 BuildKit 缓存，并设置 Maven Resolver 下载重试和 `-U`；打包成功后将依赖复制进测试镜像，供 `verify` 使用。新增可选 `MAVEN_MIRROR=aliyun`，仓库默认仍用 Central；本地未交付的 `.env` 将使用阿里云 Central 镜像。没有修改依赖版本、业务逻辑或测试通过标准。
- Windows 宿主无 Docker CLI；本轮仅能静态检查配置。Ubuntu 容器重跑、构建是否成功、最新 Maven verify 和 MySQL 集成测试结果均待验证。

## 2026-10-31 · MySQL CHECK 异常断言修正

- 用户在 Ubuntu 重跑 `backend-test`：构建成功并进入真实 MySQL 8.4 测试，导出报告为 31 tests / 1 failure / 0 errors / 0 skipped，Maven verify 退出 1。先前 13 项映射相关失败在这次报告中未复现。
- 唯一失败来自 `TaskMySqlTest`：MySQL CHECK 实际以错误码 3819 拒绝非法状态，Spring 返回 `UncategorizedSQLException`，而测试固定期望 `DataIntegrityViolationException`。调整测试核对底层 SQL 错误码和约束名；重复键断言核对 1062。未改生产代码、Flyway 或业务契约，未删除测试。
- 修订后尚无 Ubuntu 容器复测；后端门槛仍 FAIL。完整日志保留在被忽略的 `backend-test-output/`，不进入交付包。

## 2026-11-01 · 重启后 Mockito agent 初始化失败

- Docker Hub 基础镜像请求连接被拒绝；用户使用已缓存的后端测试镜像挂载当前源码运行 Maven verify。新导出报告为 31 tests、0 failures、24 errors、0 skipped。所有 errors 共享 Mockito inline Byte Buddy mock maker 无法在该容器自附加 agent 的根因；未执行到对应业务断言。
- 按 Mockito 官方 Java 21+ 指南，在 Maven Surefire 的测试 JVM 启动参数显式加载 `mockito-core` agent，并通过 `maven-dependency-plugin:properties` 获取 JAR 路径。POM 静态 XML 检查完成；容器复测尚未执行，不能称通过。

## 2026-11-01 · 后端容器测试通过

- 用户用已有测试镜像挂载 Windows 共享目录中的当前 `backend/`，连接健康的 MySQL 8.4 `db-test` 执行完整 Maven verify；命令退出码 0，日志和 11 份 Surefire XML 汇总为 **31 tests、0 failures、0 errors、0 skipped，BUILD SUCCESS**，JAR 与 Spring Boot repackage 完成。
- 这验证当前源码、Mockito agent 配置、Mapper、Flyway 与 MySQL 集成用例在该容器环境下通过。Docker Hub 不可达，尚未验证干净重建或 CI；自动 HTTP 冒烟、卷保留仍待做。

## 2026-11-01 · 离线 HTTP 冒烟初轮通过

- 专用 `smoke` 镜像因 Docker Hub 连接被拒而无法拉取。新增 `scripts/offline-smoke.sh` 复用本地前端镜像的 Node，在同一 Compose 网络运行原冒烟脚本。
- 用户在 Ubuntu 执行 `sh scripts/offline-smoke.sh initial`，实际输出 `PASS initial: seed, cover, upload, 202, two successes, three staged failures, history, Bob 404`。数据卷正常 down/up 后的 `persist` 模式仍待运行，不能据此宣称卷保留通过。

## 2026-11-01 · 正常重启后的卷保留检查通过

- 用户执行 `docker compose down`、`docker compose up -d --wait` 后，三应用服务均 Healthy；`sh scripts/offline-smoke.sh persist` 实际输出 `PASS persistence: task history, uploaded file, result file retained after compose down/up; Bob remains isolated`。正常停止重启后的数据库历史、上传和结果文件以及 Bob 隔离均已通过 HTTP 验证。
- `down` 提示 Compose 网络仍在使用，但不影响本轮结论；干净镜像重建、干净数据卷迁移及 GitHub Actions 仍待验证。

## 2026-11-01 · 公开仓库 CI 全流程通过

- 用户从 Ubuntu 推送本地五个提交；GitHub `main` 核查为 `fe5ef9b08070051ddc17ca5de42d46b2a2e12bc6`，与本地一致。
- [Actions run #5](https://github.com/1lyy/xiaoying-material-studio/actions/runs/41286040184) 的 `verify` job 为 success。步骤依次通过 Compose config、前端 35/35 与构建、后端 Maven verify exit 0、运行镜像构建、新环境三服务启动、`PASS initial`、正常 down/up、`PASS persistence`。这补齐了先前因 Ubuntu Docker Hub 网络中断而未验证的标准构建与新卷初始化证据。
- 本人确认项目开发、验证及整理累计投入约 10 小时，属于估算而非逐分钟计时。README 据此更新；实际对招聘方提交动作仍由本人完成。
