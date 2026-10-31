# 公开方案、许可与版本核查（P0）

核查日期：**2026-10-30（北京时间）**。本文件记录 P0 设计轮次的历史快照：当时本地只有 `docs/题目原文.md`、`docs/执行计划.md`，没有业务源码或可运行配置。此后本项目已经新增前后端源码、锁文件与容器配置；截至交付文档轮次仍无 `.git` 元数据，容器运行尚未验证。本轮 P0 审计通过联网读取 GitHub API/文件与技术官方资料，**没有 clone/fork 三个参考仓库，也没有运行其代码**。以下“可见”指在列明的固定 commit 上读取到了对应文件，不代表已在本机复现运行结果。

## 1. 许可结论与复用规则

三个仓库在所查固定提交的递归文件树中均**未发现 LICENSE/LICENCE/COPYING 文件**，仓库 API 的 `license` 字段均为 `null`；README 也没有找到对整个业务源码的明确复用许可。这是“**未见明确许可**”，不是声称作者绝对没有在别处授权。[GitHub 许可说明](https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/customizing-your-repository/licensing-a-repository)指出，公开可见并不自动授予复制、修改、再发布代码的许可。

**本项目决策：独立实现，不拉取这三个仓库作为基础，不复制其业务代码、图片、SVG、截图或文案。** 可参考通用架构想法和题目要求；后续如获得明确许可，先记录许可文本、来源 commit、文件范围和归属，再评估是否改变决策。框架本身和各开源依赖按其各自许可证使用，不能把它们与这三个无许可的笔试仓库混为一谈。

## 2. 三个仓库的固定提交与源码证据

### A. Jasonnal/xiaoying-2027-fullstack

- 固定提交：[6ebb3a7e2051b4fbb81fe7fb34378c4a866ecc5d](https://github.com/Jasonnal/xiaoying-2027-fullstack/commit/6ebb3a7e2051b4fbb81fe7fb34378c4a866ecc5d)，`main`，提交时间 2026-10-28 13:58 UTC；[该提交文件树](https://api.github.com/repos/Jasonnal/xiaoying-2027-fullstack/git/trees/6ebb3a7e2051b4fbb81fe7fb34378c4a866ecc5d?recursive=1)。[仓库 API](https://api.github.com/repos/Jasonnal/xiaoying-2027-fullstack) 的 `license=null`，文件树无许可证文件。
- [README](https://github.com/Jasonnal/xiaoying-2027-fullstack/blob/6ebb3a7e2051b4fbb81fe7fb34378c4a866ecc5d/README.md)说明 Next/Java/MySQL、模拟失败、Compose 和测试；其 [Actions 运行](https://github.com/Jasonnal/xiaoying-2027-fullstack/actions/runs/40701366023) 对该提交显示 `success`。这是 GitHub 记录，非本地复跑。
- 可借鉴的设计：[`JobService`](https://github.com/Jasonnal/xiaoying-2027-fullstack/blob/6ebb3a7e2051b4fbb81fe7fb34378c4a866ecc5d/backend/src/main/java/com/xiaoying/studio/job/JobService.java)在事务提交后投递任务，避免线程看不到未提交行；[`JobPipeline`](https://github.com/Jasonnal/xiaoying-2027-fullstack/blob/6ebb3a7e2051b4fbb81fe7fb34378c4a866ecc5d/backend/src/main/java/com/xiaoying/studio/job/JobPipeline.java)顺序记录阶段；[`hooks.ts`](https://github.com/Jasonnal/xiaoying-2027-fullstack/blob/6ebb3a7e2051b4fbb81fe7fb34378c4a866ecc5d/frontend/src/lib/hooks.ts)按未完成状态决定轮询。
- 不直接沿用的边界：它使用内存线程池，[`JobDispatcher`](https://github.com/Jasonnal/xiaoying-2027-fullstack/blob/6ebb3a7e2051b4fbb81fe7fb34378c4a866ecc5d/backend/src/main/java/com/xiaoying/studio/job/JobDispatcher.java)在 `executor.execute` 外没有看到持久投递失败处理；README 承认重启后 `PROCESSING` 可滞留。[`FileController`](https://github.com/Jasonnal/xiaoying-2027-fullstack/blob/6ebb3a7e2051b4fbb81fe7fb34378c4a866ecc5d/backend/src/main/java/com/xiaoying/studio/storage/FileController.java)对 `uploads/**` 与 `results/**` 根据路径读文件，所查方法没有用户归属校验。其历史列表有固定上限，不能无说明地声称全部历史可见。
- 结论：只参考“事务提交后启动”和“阶段落库”的原理；本项目按既定方案使用 MySQL 队列、私有文件读取与启动收敛。

### B. Ha1ifax/XiaoYingTest-FS

- 固定提交：[4b6a842811025533b97b8112ec2169d43270a5ac](https://github.com/Ha1ifax/XiaoYingTest-FS/commit/4b6a842811025533b97b8112ec2169d43270a5ac)，`main`，提交时间 2026-10-29 14:43 UTC；[该提交文件树](https://api.github.com/repos/Ha1ifax/XiaoYingTest-FS/git/trees/4b6a842811025533b97b8112ec2169d43270a5ac?recursive=1)。[仓库 API](https://api.github.com/repos/Ha1ifax/XiaoYingTest-FS) 的 `license=null`，文件树无许可证文件。
- [README](https://github.com/Ha1ifax/XiaoYingTest-FS/blob/4b6a842811025533b97b8112ec2169d43270a5ac/README.md)覆盖完整页面和账号，还加入模板管理、资料/密码修改等题外功能；其 [Actions 运行](https://github.com/Ha1ifax/XiaoYingTest-FS/actions/runs/40894414521) 对该提交显示 `success`。README 明说本机没有 Docker CLI，Compose 未在其本机实际构建；不能把 CI 的前后端成功理解为 Compose 全链路已在本机验证。
- 可借鉴的设计：[`TaskService`](https://github.com/Ha1ifax/XiaoYingTest-FS/blob/4b6a842811025533b97b8112ec2169d43270a5ac/backend/src/main/java/com/momento/studio/task/TaskService.java)在受理时创建三个任务步骤并保存模拟配置快照；[`TaskDispatcher`](https://github.com/Ha1ifax/XiaoYingTest-FS/blob/4b6a842811025533b97b8112ec2169d43270a5ac/backend/src/main/java/com/momento/studio/task/TaskDispatcher.java)使用 `AFTER_COMMIT` 事件；[`TaskWorkflowRunner`](https://github.com/Ha1ifax/XiaoYingTest-FS/blob/4b6a842811025533b97b8112ec2169d43270a5ac/backend/src/main/java/com/momento/studio/task/TaskWorkflowRunner.java)能把后续步骤记为跳过；[`task-state.ts`](https://github.com/Ha1ifax/XiaoYingTest-FS/blob/4b6a842811025533b97b8112ec2169d43270a5ac/frontend/lib/task-state.ts)将查询异常与任务失败区分。
- 不直接沿用的边界：邮箱账号、管理员模板后台和视觉扩展会扩大笔试实施/测试面；Java 仍是进程内事件/线程池，README 说明没有崩溃恢复保证。其 `failStage`、`skipRemaining`、`failTask` 在[`TaskStateService`](https://github.com/Ha1ifax/XiaoYingTest-FS/blob/4b6a842811025533b97b8112ec2169d43270a5ac/backend/src/main/java/com/momento/studio/task/TaskStateService.java)是多个独立事务，故本项目要用单个失败事务保证任务与步骤一致。
- 结论：参考状态表达与可验收 UI 的思路；不引入题外账户/模板管理，也不复制其实现和素材。

### C. xiaoxiaokai12138/xiaoyingkeji_task

- 固定提交：[ef6991b508b2a3a060f77e207418eb640de1c6e9](https://github.com/xiaoxiaokai12138/xiaoyingkeji_task/commit/ef6991b508b2a3a060f77e207418eb640de1c6e9)，`master`，提交时间 2026-10-30 06:25 UTC；[该提交文件树](https://api.github.com/repos/xiaoxiaokai12138/xiaoyingkeji_task/git/trees/ef6991b508b2a3a060f77e207418eb640de1c6e9?recursive=1)。[仓库 API](https://api.github.com/repos/xiaoxiaokai12138/xiaoyingkeji_task) 的 `license=null`，文件树无许可证文件。
- [README](https://github.com/xiaoxiaokai12138/xiaoyingkeji_task/blob/ef6991b508b2a3a060f77e207418eb640de1c6e9/README.md)记录容器验证、失败演示、重启收敛、来源与投入；[该提交的 Actions 运行](https://github.com/xiaoxiaokai12138/xiaoyingkeji_task/actions/runs/41014157202)显示 `success`。README 仍写“远程 CI 尚未确认”，与现查 Actions 状态不一致；AI 工作流章节仍标待用户填写。
- 可借鉴的设计：[`TaskService`](https://github.com/xiaoxiaokai12138/xiaoyingkeji_task/blob/ef6991b508b2a3a060f77e207418eb640de1c6e9/backend/src/main/java/studio/yingzuo/task/TaskService.java)做受理与配置快照；[`TaskWorker`](https://github.com/xiaoxiaokai12138/xiaoyingkeji_task/blob/ef6991b508b2a3a060f77e207418eb640de1c6e9/backend/src/main/java/studio/yingzuo/task/TaskWorker.java)从 MySQL 队列领取；[`TaskTransactions`](https://github.com/xiaoxiaokai12138/xiaoyingkeji_task/blob/ef6991b508b2a3a060f77e207418eb640de1c6e9/backend/src/main/java/studio/yingzuo/task/TaskTransactions.java)把阶段状态放短事务并在启动时收敛遗留任务；[`use-tasks.ts`](https://github.com/xiaoxiaokai12138/xiaoyingkeji_task/blob/ef6991b508b2a3a060f77e207418eb640de1c6e9/frontend/src/lib/use-tasks.ts)按用户/任务分查询键并在终态停轮询。
- 不直接沿用的边界：整个仓库是完整笔试答案且无明确复用许可；不能换名后提交。串行工作器及本地卷只适合单实例，其 README 也承认数据库长期不可达时失败落库可能延迟，以及进程中断时可能产生孤儿结果文件。AI 说明未完成不能复制为本项目声明。
- 结论：本项目独立设计同类数据库队列/短事务边界，并编写自己的状态机、测试、素材与 README。

## 3. 精确版本及兼容性核查

以下是 P0 当时**计划锁定的直接依赖和运行时补丁版本**，不是从 `latest` 解析的浮动版本。验证范围是官方发布记录、`peerDependencies`/最低版本和数据库镜像存在性。此后已写入 `frontend/package-lock.json`、`backend/pom.xml` 与 Dockerfile；**实际组合运行仍未验证**，以 [容器验收记录](container-acceptance.md) 为准。

| 组件 | 冻结版本 | 已核实的兼容依据 | 尚待运行验证 |
| --- | --- | --- | --- |
| Next.js | **16.4.3** | [官方 v16.4.3 包清单](https://github.com/vercel/next.js/blob/v16.4.3/packages/next/package.json)声明 Node `>=20.9.0`，React/React DOM peer 范围含 `^19.0.0`；[Next 16 升级文档](https://nextjs.org/docs/app/guides/upgrading/version-16)要求 TypeScript `>=5.1.0`。 | `npm ci`、Route Handler 代理、`next build`。 |
| React / React DOM | **19.3.1 / 19.3.1** | [React npm 包](https://www.npmjs.com/package/react)、[React DOM npm 包](https://www.npmjs.com/package/react-dom)显示 19.3.1 已发布；满足上述 Next peer 范围。 | Next 生产构建和组件测试。 |
| Node.js | **22.23.4** | [Node 官方版本档案](https://nodejs.org/en/download/archive/v22.23.4)列该版本；数值上满足 Next 的 `>=20.9.0`。[Docker Node 标签](https://hub.docker.com/_/node/tags?name=22)可见 `22.23.4-bookworm-slim`。 | 固定镜像 digest 后实际安装/构建。 |
| TypeScript | **5.9.3** | [npm 包版本](https://www.npmjs.com/package/typescript?activeTab=versions)存在；高于 Next 16 最低 5.1。 | `tsc --noEmit` 与 Next 构建。 |
| Java | **Temurin 21.0.12.1+1** | [Adoptium 发布记录](https://adoptium.net/temurin/releases?version=21)有该 Java 21 构建；在 Boot 3.5 支持范围内。 | 构建/运行镜像 digest、真实 JVM 启动。 |
| Spring Boot | **3.5.16** | [Spring 官方发布](https://spring.io/blog/2026/06/25/spring-boot-3-5-16-available-now/)与[系统要求](https://docs.spring.io/spring-boot/3.5/system-requirements.html)：Java 17～25，因此 Java 21 在范围内。 | Maven `verify`、实际服务启动。 |
| MyBatis Spring Boot Starter | **3.0.5** | [MyBatis 官方发布](https://github.com/mybatis/spring-boot-starter/releases)的 3.0.5 以 Boot 3.5 为基线；[兼容表](https://mybatis.org/spring-boot-starter/mybatis-spring-boot-autoconfigure/)列 3.0 系列支持 Boot 3.2～3.5、Java ≥17。 | 与 Boot 3.5.16 的 Mapper/事务集成测试。 |
| MySQL Server | **8.4.11** | [Docker Hub MySQL 标签](https://hub.docker.com/_/mysql/tags?name=8.4)明确列 `mysql:8.4.11`。 | JDBC 驱动、Flyway 迁移、索引/JSON/UTC 实际 MySQL 测试。 |
| MySQL Connector/J | **26.10.0** | [Oracle 26.10.0 发布说明](https://dev.mysql.com/doc/relnotes/connector-j/en/news-26-10-0.html)明确支持 MySQL Server 8.4+；[兼容表](https://dev.mysql.com/doc/connector-j/en/connector-j-versions.html)说明 JRE 8+，因此 Java 21 在范围内；[Maven Central](https://central.sonatype.com/artifact/com.mysql/mysql-connector-j)有该版本。 | 与 Boot 3.5.16/MyBatis 3.0.5 的真实连接、迁移及读写测试。 |

P0 时 Flyway、ESLint、TanStack Query 尚未写入项目配置；现在 ESLint/TanStack Query 已在 `package.json` 和 `package-lock.json` 固定，Flyway 由 Spring Boot 3.5.16 BOM 管理，实际 Maven 解析尚未执行。Docker 基础镜像已使用明确补丁标签，digest 尚未固定。Connector/J 坐标为官方文档中的 [`com.mysql:mysql-connector-j`](https://dev.mysql.com/doc/connector-j/en/connector-j-installing-maven.html)，POM 已显式固定 26.10.0。不得把 P0 的计划状态当成当前源码状态。

## 4. P0 门槛结论

| 门槛 | 结论 |
| --- | --- |
| 题目与现有文件核实 | **通过**：已读取原文和计划；本地无业务源码、Git 仓库。 |
| 三参考仓库 README、关键源码、具体 commit、许可 | **通过（只读审计）**：均可联网访问；无明确业务源码复用许可，决定独立实现。 |
| 指定技术组合的发布/兼容范围 | **通过（静态核查）**：上表所列补丁版本存在且直接约束相容。 |
| 接口、状态机、七表、启动收敛 | **通过（设计冻结）**：以 `api-contract.md`、`schema-design.md` 为准。 |
| 锁文件、镜像 digest、真实安装/构建/MySQL 联调 | **P0 当时未验证**：当时没有代码和容器配置。后来已新增 npm 锁文件与容器配置；digest 及真实安装/构建/MySQL 联调仍未验证，见 [容器验收记录](container-acceptance.md)。 |

**P0 设计门槛：通过，可进入独立编码。运行兼容性门槛：待后续验证。** 若后续实际依赖安装与官方静态兼容判断冲突，以可复现的错误为证据修订版本与文档，不悄悄漂移到 `latest`。
