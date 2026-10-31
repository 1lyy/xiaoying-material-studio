# 容器交付与验收记录

记录日期：2026-10-30。范围：干净环境的 Compose 启动、CI、真实 MySQL、HTTP 冒烟及数据卷保留。本机当前没有 Docker/Podman CLI；下文明确区分已完成的静态检查与未执行的容器验证。

## 交付结构与静态核查

| 项目 | 文件证据 | 本轮结论 |
| --- | --- | --- |
| 三服务和依赖健康 | `compose.yaml`：`db` 为 `mysql:8.4.11` 且有 healthcheck；`backend` 等 `db:service_healthy`；`frontend` 等 `backend:service_healthy` | 静态符合；Compose 实际解析/启动 UNVERIFIED |
| 网络与持久化 | 主服务仅 `frontend` 映射 `${WEB_BIND_ADDRESS:-127.0.0.1}:${WEB_PORT:-3000}:3000`；`mysql-data`、`asset-data` 命名卷；测试另用 `smoke-state` 保存验收 ID | 静态符合；卷保留效果 UNVERIFIED |
| 配置与镜像 | `.env.example` 只有本地演示值；Node 22.23.4、Temurin 21.0.12.1_1 多阶段 Dockerfile；MySQL 8.4.11 固定标签 | 文件存在；镜像拉取和构建 UNVERIFIED |
| Next standalone | `next.config.ts` 为 `output: "standalone"`；运行镜像复制 `.next/standalone` 和 `.next/static`；当前没有 `frontend/public/`，模板封面由 Java 资源接口提供 | 静态符合；页面静态资源加载 UNVERIFIED |
| 服务端代理 | `JAVA_API_ORIGIN` 由 Next 运行时环境提供；`src/lib/api-proxy.ts` 转发二进制流、Cookie、响应状态和 `Content-Type`，并禁用缓存 | 源码可见；容器网络联调 UNVERIFIED |
| 初始化与素材 | Flyway V1 建七表、V2 三模板、V3 Alice/Bob；三个原创 SVG 位于 Java resources；`demo-inputs/coast.png` 与 `sunset.jpg` 可用 | 迁移文件存在；MySQL 执行 UNVERIFIED。Pillow 12.1.0 实际解码两图，均为 640×400 |
| CI | `.github/workflows/ci.yml` 在 push/PR 运行 Compose config、容器内 `npm ci`/lint/`tsc --noEmit`/Vitest/build、容器内 Maven verify + MySQL 8.4、运行镜像构建、冒烟、正常 down/up 后保留验证；无 `continue-on-error` | YAML 静态解析成功；GitHub Actions 实际运行 UNVERIFIED |

Docker Hub 官方镜像页面列有 [`mysql:8.4.11`](https://hub.docker.com/_/mysql)、[`node:22.23.4-bookworm-slim`](https://hub.docker.com/_/node/tags?name=-bookworm-slim)、[Temurin 21.0.12.1_1 JDK](https://hub.docker.com/layers/library/eclipse-temurin/21-jdk-jammy/images/sha256-074d6f95f1e69965dbd23750a37ec3b234025d0864e9821e0b1fba0763a3e853) 和 [JRE](https://hub.docker.com/layers/library/eclipse-temurin/21-jre-jammy/images/sha256-900b7ac325b25c3a7b67351d6595403eba379e7d35babed3cebbb0a9fdc54112) 对应标签，但本机未拉取镜像。CI 使用的 [`actions/checkout@v7.0.1`](https://github.com/actions/checkout/releases/tag/v7.0.1) 为已发布固定版本。镜像标签尚未固定到 digest；这是交付的可重复性限制。

## 本轮实际执行及输出

在 `D:\Code\JAVA\XiaoYing` 用 PowerShell 执行：

| 命令或检查 | 实际结果 |
| --- | --- |
| `docker compose --profile test config --quiet` | 退出码 1：`docker` 不是可识别的命令。Compose 自身未执行。 |
| `docker compose up --build -d --wait` | 退出码 1：同上。无容器启动。 |
| Python `yaml.safe_load`/`BaseLoader` 读取 Compose 与 CI | 退出码 0：识别 `db,backend,frontend,backend-test,frontend-test,smoke,db-test`；只有 `frontend` 声明端口；CI 触发器为 push、pull_request。此检查不等价于 `docker compose config`。 |
| Pillow 打开并完整解码 `demo-inputs/coast.png`、`sunset.jpg` | 退出码 0：分别为 PNG、JPEG，均为 640×400。此检查不等价于 Java 上传验收。 |

没有在宿主机运行 npm、Maven、Next.js、Spring Boot 或项目测试脚本。当前工作目录没有 `.git` 元数据，工作流尚未被 GitHub push/PR 触发。**本轮没有任何容器构建、测试、HTTP 冒烟或数据卷保留的通过证据。**

## 在具备 Docker Compose 的隔离环境复现

以下 PowerShell 命令从项目根目录执行。运行中的安装、构建、测试和应用均处在容器内。首次演示使用 `.env.example` 的非真实本地凭据；若已有 `.env`，先检查其内容。`down` 不加 `--volumes`，以保留数据库和图片。

```powershell
Copy-Item .env.example .env
docker compose --profile test --profile smoke config --quiet
docker compose --profile test run --build --rm --no-deps frontend-test
docker compose --profile test run --build --rm backend-test
docker compose up --build -d --wait
docker compose ps
docker compose --profile smoke run --rm smoke
docker compose down
docker compose up -d --wait
$env:SMOKE_MODE = 'persist'
docker compose --profile smoke run --rm smoke
Remove-Item Env:SMOKE_MODE
```

`frontend-test` 的 `CMD` 顺序执行 `npm run lint`、`npm run typecheck`（脚本为 `tsc --noEmit`）、`npm run test`、`npm run build`，依赖阶段执行 `npm ci`。`backend-test` 在隔离的 `db-test` MySQL 8.4.11 健康后经 `verify-and-export.sh` 执行完整 `mvn -B -ntp verify` 并导出报告；Maven 来自固定的构建镜像标签。测试使用 `TEST_DB_URL`，`TaskMySqlTest`、`AssetMySqlTest` 和 `IdentityTemplateMySqlTest` 会运行，Worker 在该测试服务中关闭。后端运行镜像构建阶段用 `-DskipTests package` 仅产出 JAR，测试门槛由前述独立 `verify` 承担。

`smoke` 服务运行 `scripts/container-smoke.mjs`，从 Compose 内网访问前端代理。初次模式依次检查健康、演示用户、模板及封面、Alice 登录和上传、202 + Location、两个成功任务及结果 URL 的 PNG 签名/尺寸、三个指定阶段失败、完整历史与 Bob 的 404 隔离；另存任务/素材 ID 于 `smoke-state`。Maven verify 中的 `TaskMySqlTest` 用 `ImageIO` 完整解码结果图片。重启模式重新登录 Alice/Bob，检查成功历史、原上传图片和结果图片仍可读、Bob 仍收到 404。脚本的终态查询有 90 秒上限。当前脚本本身尚未在 Node 容器执行，结果为 **UNVERIFIED**。

上述自动冒烟验证的是通过新 HTTP 请求恢复历史；浏览器“刷新后 UI 继续轮询”和用户操作视觉效果仍需浏览器手动核验。可在完成初次冒烟前另用浏览器打开 `/history`，在任务进行中刷新并观察状态；切换 Bob 后确认 Alice 的卡片不出现。浏览器检查与截图目前均 **UNVERIFIED**。

若任一命令失败，保留 `docker compose logs --no-color --tail=200 db backend frontend`、`docker compose ps`、失败命令及退出码；不能跳过失败步骤获得绿色结果。`docker compose down --volumes` 会删除演示数据，**不能用于保留性验证的中间步骤**。测试完成后仅在明确要清空演示数据时使用。

## 门槛结论与重启语义

交付文件、静态结构与可重复冒烟入口已经准备；**容器运行验收未通过门槛，状态为 UNVERIFIED**，原因是本机缺少 Docker/Podman CLI。GitHub Actions 尚未触发。下一步必须在 Docker 环境实际执行上述命令，记录真实输出，并根据构建/迁移/HTTP 结果修复缺陷。

正常重启时，已终态历史和图片应由 `mysql-data`、`asset-data` 保留。若重启前任务处于 `RUNNING`，Java 启动收敛将其标成 `FAILED/PROCESS_INTERRUPTED`；`QUEUED` 保留排队。**这不是 RUNNING 任务的自动续跑。**本轮未实际验证这两条重启行为。

## 2026-10-31 · Ubuntu 虚拟机增量验证

用户已在 VirtualBox Ubuntu 22.04.5 中确认 Docker 服务端 29.7.1、Compose 5.3.1、根分区约 38 GiB 空余。用户提供的终端截图显示，在旧版交付包执行 `docker compose --profile test run --build --rm --no-deps frontend-test` 时，`npm ci` 完成，lint 为 0 errors/1 warning，typecheck 报 `TS2339`（模板详情空数据分支的 `detail.refetch`），命令未进入 Vitest/build。这个**真实失败**取代上文“无任何容器执行”的早期状态；Windows 宿主仍无 Docker CLI。宿主源码已先补空详情重试回归测试，再修正查询类型和 lint 警告；新版包的结果仍为 **UNVERIFIED**。后端 verify、Compose 应用启动、MySQL 迁移、冒烟和卷保留尚未执行。

第二次截图显示修订版 lint、typecheck 完成；Vitest 为 **32 passed、3 failed**，构建未开始。失败集中在两个假时钟等待断言和一个页面重绘断言；已修正测试等待方式，但再次运行仍 **UNVERIFIED**。为免反复搬运压缩包，后续优先使用 VirtualBox 共享文件夹，让 Ubuntu 容器直接读取 Windows 的当前源码；该共享方式尚未实际配置和验证。

第三次用户截图确认 Windows 项目目录以 VirtualBox 临时共享挂载在 Ubuntu `/mnt/XiaoYing`，并从该目录执行同一 `frontend-test` 命令；lint、typecheck、Vitest **35/35** 和 Next.js 生产 build 全部完成，命令返回 shell。**前端容器门槛 PASS**。测试 mock 有 React 非布尔属性警告，不影响退出结果。后端 Maven verify、MySQL 迁移、完整 Compose 启动、HTTP 冒烟、数据卷保留、浏览器手工验收和共享目录重启后持久性仍 **UNVERIFIED**。

首次后端容器 `docker compose --profile test run --build --rm backend-test` 的末尾截图显示 Maven verify `Tests run: 31, Failures: 12, Errors: 2, Skipped: 0` 与 `BUILD FAILURE`；后端门槛 **FAIL**，具体失败报告尚未取得。新增容器内报告导出脚本，把完整日志与 Surefire 报告写入本机共享目录的 `backend-test-output/`，保留原测试退出码；诊断脚本未复跑。Windows 浏览器访问准备：本地未交付的 `.env` 已将 `WEB_BIND_ADDRESS` 设为已从 Windows ping 通的虚拟机 host-only IP `192.168.56.101`，并将 `http://192.168.56.101:3000` 加入 `APP_ALLOWED_ORIGINS`。网站尚未启动，因此该地址 **UNVERIFIED**；不是本次 `backend-test` 的访问入口。

随后用户已重跑带报告导出的 `backend-test`：报告仍显示 31 项中 12 failures、2 errors。具体根因与已作修改见 `docs/backend-verification.md`；这些修订尚未在容器复测，后端门槛仍 **FAIL**。网站启动、HTTP 冒烟、卷保留和 Windows 浏览器访问继续为 **UNVERIFIED**。

第二次带报告运行结果为 31 项中 12 failures、1 error；MyBatis 基本类型别名已修订，尚无复测结果。随后新截图显示后端镜像在下载 Maven Wrapper 发行版 ZIP 时构建失败，**没有运行新的测试**。现将构建阶段改为固定 `maven:3.9.11-eclipse-temurin-21` 镜像，容器内直接使用 Maven 3.9.11；该改动尚未运行验证。下一次仍执行 `docker compose --profile test run --build --rm backend-test`，若构建成功，新日志会写入 `backend-test-output/`。完整应用和 Windows 浏览器访问仍 **UNVERIFIED**。

### 后续实际启动与浏览器检查

用户报告 Ubuntu 端 `docker compose up --build -d --wait` 已成功；完整终端输出未留存。Windows 浏览器实际从 `http://192.168.56.101:3000/` 打开站点，证明前端端口可达并能通过同源代理读取 Java 模板、会话、上传、任务与结果。浏览器操作与观察：三模板/分类筛选；Alice 登录、单图 `coast.png` 上传就绪；提交后页面显示任务 ID 与等待处理；历史页显示该任务成功及三步骤均完成；结果图完整加载为 900×600；刷新后保留；切换 Bob 后历史为空，直接打开 Alice 结果地址得到 `ASSET_NOT_FOUND` 错误体。整页截图位于 `docs/screenshots/`。此检查不等同于自动 smoke 或 Maven verify。

最新后端 Maven verify、三种指定失败、多任务排队、HTTP 404 状态、干净卷迁移和正常 down/up 后数据保留仍 **UNVERIFIED**。旧报表 `31 / 12 failures / 1 error` 对应前一版 Mapper；不得当作本次运行结果。GitHub Actions 尚无本项目运行记录。

### 2026-10-31 · 后端测试镜像下载中断

用户再次执行 `docker compose --profile test run --build --rm backend-test` 的截图显示：后端镜像构建在 `RUN mvn -B -ntp -DskipTests package` 下载 Maven Central 的两个 JAR 时因响应体截断失败，耗时约 904 秒；命令未进入 `verify`。后端 Dockerfile 现加入可跨失败构建保留的 BuildKit Maven 缓存及 Resolver 重试，测试镜像预置构建成功时的仓库依赖；Compose 可选 `MAVEN_MIRROR=aliyun`，本地 `.env` 将使用该镜像，CI/仓库默认 Central。该改动尚未在 Ubuntu 运行，**构建与测试均 UNVERIFIED**；下一次应执行同一条命令，检查其退出码及 `backend-test-output/maven-verify.log` 的新时间戳和 Surefire 汇总。旧报告不能作为本轮结果。
