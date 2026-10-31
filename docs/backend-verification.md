# 后端 P1 验证记录

记录日期：2026-10-30（Asia/Shanghai）。本轮只增加验证用例并复核后端源码；未增加业务功能、未改 Flyway 迁移。本文件区分**测试源码已写**与**测试实际通过**。

## 环境与执行结果

仓库 `AGENTS.md` 要求项目安装、测试、构建及应用在容器隔离环境执行。实际尝试：

```powershell
docker compose --profile test config
docker compose --profile test run --build --rm backend-test
```

两条命令退出码均为 1，PowerShell 均报告 `The term 'docker' is not recognized ...`。`Get-Command docker,podman,nerdctl` 没有结果；`wsl --list --quiet` 未能列出可用发行版并返回服务访问错误。宿主机虽有 `java.exe` 和 `mvn.cmd`，本轮按仓库容器约束**没有在宿主机运行 Maven**。因此 `mvn verify`、Spring 上下文启动、Flyway V1～V3 应用、MySQL 8.4、HTTP 行为与所有测试的真实通过情况均为 **UNVERIFIED**，没有 Surefire 报告或通过数。

只读静态检查：Python `xml.etree.ElementTree` 成功解析 `backend/src/main/resources/mappers/` 下 4 个 XML 文件；`TaskMapper.java` 的 21 个方法名与 `TaskMapper.xml` 的 21 个 statement ID 一一对应。这不能证明 MyBatis 映射、SQL 或 Java 编译正确。当前目录没有 `.git` 元数据，`git status --short` 返回 `fatal: not a git repository`，无法提供变更 commit。

有容器运行时后，从仓库根目录重跑上面的第二条命令。`backend-test` 的命令为 `sh ./mvnw -B -ntp verify`，并通过 Compose `db-test` 使用 `mysql:8.4.11`；`TEST_DB_URL/USER/PASSWORD` 会使 MySQL 集成测试参与执行。直接运行 Maven 而不设置 `TEST_DB_URL` 会跳过这些测试，不能据此宣布 P1 通过。测试容器的镜像构建还执行 `-DskipTests package`，它只用于制作测试镜像，最终以容器命令的 `verify` 结果为准。

## 可重复用例与当前结果

| 要求 | 测试方法或证据位置 | 验证要点 | 本轮真实结果 |
| --- | --- | --- | --- |
| 202 先于长阶段结束 | `TaskMySqlTest.httpAcceptanceReturnsWhileWorkerStageIsBlockedByControlledDelay` | 可注入 `TaskDelay` 用两个 latch 卡住 ASSET_CHECK；POST 202/Location 返回后仍看到 RUNNING、步骤 RUNNING，释放后完成 | UNVERIFIED：未运行 |
| 单任务成功、顺序、私有结果 | `TaskMySqlTest.acceptedTaskReturns202ThenThreeRealTransactionsProducePrivateResult`、`TaskPipelineTest.successRunsThreeStagesInOrderAndProducesIndependentImage` | QUEUED GET 不推进；三阶段顺序；终态 SUCCEEDED；通过授权 URL 读取并用 ImageIO 解码 900×600 PNG；Bob 读任务和结果均 404 | UNVERIFIED：未运行 |
| 三种指定失败 | `TaskMySqlTest.specifiedStageFailureSkipsLaterSteps`（`@EnumSource` 三例）、`TaskPipelineTest.eachConfiguredFailureStopsLaterStages` | 之前 SUCCEEDED、当前 FAILED、后续 SKIPPED；任务 FAILED、无结果、重复查询与再次执行不改变版本 | UNVERIFIED：未运行 |
| 队列与竞争领取 | `TaskMySqlTest.concurrentConditionalClaimIsSingleWinnerAndTasksRemainIndependent` | 两线程同时执行真实 Mapper `claim`，受影响行数合计 1；另一任务随后领取，成功与失败及步骤不串扰 | UNVERIFIED：未运行 |
| 终态、只读与版本 | `TaskMySqlTest.mapperVersionAdvancesAtEveryBoundaryAndTerminalUpdatesAreRejected`；成功/失败用例中的重复 GET | 版本在领取、阶段开始/结束、失败事务边界由 0 单调到 7；终态 SQL 条件更新返回 0；GET 不推进 | UNVERIFIED：未运行 |
| 非法模板、张数/重复、归属、伪装、尺寸/大小 | `TaskMySqlTest.twoImageTemplatePreservesSlotsAndRejectsDuplicateOrForeignAssets`、`taskAcceptanceRejectsMissingTemplateTamperedImageAndInvalidStoredSizeOrDimensions`、`PrivateImageStorageTest.rejectsEmptySpoofedOversizeAndCorruptImagesWithoutLeavingFiles`、`AssetMySqlTest.uploadErrorsKeepUniformBodyAndDoNotInsertAssets` | 404/400；Alice/Bob 隔离；同尺寸损坏图片、伪 MIME/扩展名、超 5 MiB/模板限额/像素上限及错误元数据均拒绝 | UNVERIFIED：未运行 |
| 重启收敛 | `TaskMySqlTest.startupConvergenceFailsRunningAndKeepsQueued`、`TaskWorkerTest` | RUNNING→FAILED/PROCESS_INTERRUPTED；已完成步骤保留，后续 SKIPPED；重复收敛与旧 pipeline 不续跑；QUEUED 保留并可重新领取 | UNVERIFIED：未运行 |
| 真实 MySQL 8.4、Flyway 与约束 | `TaskMySqlTest.realMySqlConstraintsRejectInvalidStateAndDuplicatePosition`，以及本类所有集成用例 | 检查 `SELECT VERSION()` 前缀 `8.4.`、V1～V3 迁移成功、CHECK/唯一约束、Mapper 条件更新和归属过滤 | UNVERIFIED：未运行 |

## 源码复核与修复结论

- 复核了 `TaskMapper.xml` 的 `claim`、`startTaskStage`、`succeed`、`failTask`、`interruptTask` 条件更新及 `TaskTransactions` 的事务边界；新增用例将通过真实 MySQL 验证受影响行数和版本。源码审查本身不能证明 SQL 在 MySQL 8.4 上通过。
- 复核了 `PrivateImageStorage.resolveOwnedFile` 的相对路径判断。检查中一度误读 `.`/`..` 条件；再次查看原文件确认逻辑正确，本轮没有修改该实现，也不记为缺陷修复。
- 本轮**没有经运行证实的生产缺陷修复**。新增测试尚可能暴露编译、映射、迁移或运行缺陷；下一步必须先跑容器 `verify`，保留失败用例并按失败输出修复。不能把这些用例的存在当作 P1 通过。

## P1 门槛

**未通过（验证阻塞）**。需要在可用容器环境执行 `docker compose --profile test run --build --rm backend-test`，确认 Maven `verify` 退出码 0、MySQL 集成用例实际运行且 8.4/Flyway 断言通过，并检查 Surefire 报告无失败或跳过的关键用例。随后才可把本记录改为真实通过并进入前端任务状态开发。

## 2026-10-31 Ubuntu 虚拟机首次 Maven verify

用户从 `/mnt/XiaoYing` 的 Windows 共享源码执行 `docker compose --profile test run --build --rm backend-test`。末尾截图明确显示 `Tests run: 31, Failures: 12, Errors: 2, Skipped: 0`、`BUILD FAILURE`，Maven Surefire 3.5.6 以测试失败退出。**后端门槛 FAIL**；截图只有汇总，不能推断各测试的具体根因或把 MySQL 集成测试记为通过。该命令只运行测试容器，不会启动面向浏览器的网站。

为减少重复截图，`backend-test` 现经 `verify-and-export.sh` 运行完整 `mvnw verify`，原样保留非零退出码，并将 Maven 日志与 Surefire XML/TXT 报告写入共享源码根目录的 `backend-test-output/`。该目录被 `.gitignore` 排除且不进入交付压缩包。**新的诊断入口尚未实际复跑**；取得报告后逐项补回归断言、修复并验证。

## 2026-10-31 · 导出报告复核与待重跑修复

用户在 Ubuntu 共享目录重跑 `docker compose --profile test run --build --rm backend-test` 后，Windows 侧读到 `backend-test-output/maven-verify.log` 与 Surefire 报告。真实结果仍为 **31 tests、12 failures、2 errors、0 skipped，BUILD FAILURE**。报告显示：

- `AssetMapper.findOwned` 的构造器映射把 `sizeBytes/width/height` 指定为 `Long/Integer/Integer`，但 `AssetRow` 构造器是 `long/int/int`。MyBatis 报 `NoSuchMethodException`，引发私有素材读取 500，以及多项任务提交 500。已把 XML 的 `javaType` 改为基本类型；既有 `AssetMySqlTest` 和 `TaskMySqlTest` 是真实 MySQL 回归用例。
- `TaskMapper` 的 `version` 同样映射为 `Long`，而 `TaskRow` 使用 `long`。已修正该映射，避免素材映射修好后在任务查询处继续出现同类异常。既有 `TaskMySqlTest` 覆盖任务查询和版本推进。
- 双图任务用例的初次提交返回 `400 INVALID_SIMULATION`，因为 Jackson 将显式 JSON `null` 解析成 `NullNode`；契约允许省略模拟配置。已让 `simulation: null` 按缺省配置处理，并在 `SimulationSnapshotTest` 增加显式 null 的断言。
- 会话过期用例把 `expires_at` 改成 2000 年，却保留了当前 `created_at`，违反七表迁移的 `expires_at > created_at` CHECK。已调整测试数据：将创建和过期时间分别置于数据库 UTC 当前时间前 2 小时与 1 小时，仍可测试过期且不破坏约束；未改已应用的 Flyway 迁移。

以上修改**尚未在容器里重新执行 Maven verify**，因此不能称为通过。XML 仅在 Windows 侧完成语法解析。下一步同一条 `backend-test` 命令重跑，检查退出码、全部 Surefire 报告和是否还有新暴露问题。

## 2026-10-31 · 第二次报告与 MyBatis 别名更正

用户再次执行同一容器命令，导出报告显示 **31 tests、12 failures、1 error、0 skipped，exit code 1**。会话过期夹具和显式 `simulation:null` 的错误已不再出现；`IdentityTemplateMySqlTest` 3/3 通过。剩余 13 项均在 `AssetMapper.findOwned` 读回 `AssetRow` 时触发同一个 `NoSuchMethodException`，或因其返回 HTTP 500 而失败。

第一次修订使用 `javaType="long"/"int"`，但 [MyBatis 官方内置别名表](https://mybatis.org/mybatis-3/configuration#typeAliases)明确规定 `long`/`int` 对应 `Long`/`Integer`，只有 `_long`/`_int` 对应 Java 基本类型。现已把 `AssetMapper` 的三个数值参数和 `TaskMapper` 的 `version` 改为带下划线的基本类型别名。这次更正**尚未重跑**；后端门槛仍为 **FAIL**。

## 2026-10-31 · 构建阶段阻塞

用户新截图显示后端镜像在构建步骤 `RUN sh ./mvnw -B -ntp -DskipTests package` 下载 Maven 3.9.11 ZIP 时失败，尚未进入本次测试。构建阶段已改为固定 Maven 3.9.11 镜像并调用其内置 `mvn`；`verify-and-export.sh` 同步修改。此改动尚未复测。共享目录中 `31 tests / 12 failures / 1 error` 是上一次运行的报告，不能证明当前 MyBatis 修订仍失败或已通过。

## 2026-10-31 · 第二次报告与 MyBatis 别名更正

用户再次执行同一容器命令，导出报告显示 **31 tests、12 failures、1 error、0 skipped，exit code 1**。会话过期夹具和显式 `simulation:null` 的错误已不再出现；`IdentityTemplateMySqlTest` 3/3 通过。剩余 13 项均在 `AssetMapper.findOwned` 读回 `AssetRow` 时触发同一个 `NoSuchMethodException`，或因其返回 HTTP 500 而失败。

第一次修订使用 `javaType="long"/"int"`，但 [MyBatis 官方内置别名表](https://mybatis.org/mybatis-3/configuration#typeAliases)明确规定 `long`/`int` 对应 `Long`/`Integer`，只有 `_long`/`_int` 对应 Java 基本类型。现已把 `AssetMapper` 的三个数值参数和 `TaskMapper` 的 `version` 改为带下划线的基本类型别名。这次更正**尚未重跑**；后端门槛仍为 **FAIL**。

## 2026-10-31 · Maven Central 下载截断与构建修订

用户截图显示新版 `backend-test` 尚在后端镜像的 `RUN mvn -B -ntp -DskipTests package`：Spring Boot Maven Plugin 3.5.16 的传递依赖 `jna:5.17.0` 与 `httpcore5-h2:5.3.6` 下载时出现 `Premature end of Content-Length delimited message body`，Dockerfile 第 6 行退出 1。**这次没有进入 `mvn verify`，不能视为最新源码的测试失败或通过。**

已在 `backend/Dockerfile` 的构建步骤加入 BuildKit Maven 仓库缓存、Maven Resolver HTTP 重试次数 8 和 `-U`。构建成功后把已解析的依赖复制到测试镜像的 `/opt/maven-repository`，测试容器的 `MAVEN_OPTS` 使用该目录，避免 `verify` 再从零下载；运行镜像仍只复制 JAR。Compose 通过 `MAVEN_MIRROR=central|aliyun` 提供可选下载源，默认 Central；当前 Ubuntu 共享目录的本地 `.env` 设为 `aliyun`。缓存和镜像只改善下载，不跳过 Maven 校验或测试。该修订尚未在 Ubuntu 容器复测；下一步仍运行 `docker compose --profile test run --build --rm backend-test`，以新日志和 Surefire 报告判定结果。
