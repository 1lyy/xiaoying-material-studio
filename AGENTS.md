# 项目协作约束

本文件适用于本目录下后续开发。开始工作前先阅读 `docs/题目原文.md`、`docs/执行计划.md`、`docs/api-contract.md`、`docs/schema-design.md`、`docs/acceptance-matrix.md`、`docs/source-audit.md`；以题目原文和用户最新指令为最高优先级。

## 范围与来源

- 按 P0→P4 推进，优先满足 R-01～R-14。不要为了展示复杂度引入真实 AI、支付、消息队列、Redis、第三方登录、模板管理后台等题外业务。
- 三个已审计的公开笔试仓库均未见明确业务源码复用许可。默认独立实现；可参考通用设计思路，不复制其源码、素材、截图或文案。任何后续复用须先核实许可，并在 README 记录来源、固定 commit 与复用范围。
- 当前已有七表迁移、演示身份、只读模板、私有素材及 Java 任务 Worker/查询源码；前端已有模板列表、详情、演示用户切换、上传、任务受理缓存和历史查询页面源码。Ubuntu 虚拟机第三次 `frontend-test` 已完成 lint、typecheck、Vitest 35/35 与 Next.js build。2026-11-01 用户在 Ubuntu 用本地已有测试镜像挂载当前源码，执行完整 Maven verify，`backend-test-output/` 实测 **31 tests、0 failures、0 errors、0 skipped，BUILD SUCCESS**，包括真实 MySQL 8.4 集成测试；这是源码挂载测试通过，重新构建镜像和 GitHub CI 仍未核实。Compose 应用已从 Windows 浏览器完成单任务成功、结果预览、刷新恢复与 Bob 页面隔离；Ubuntu 离线 HTTP 冒烟 `initial` 模式已实测 `PASS`，覆盖两个成功任务、三个指定失败和 Bob 404。卷保留 `persist` 模式尚未验证。不要把源码存在、静态检查、设计文档或参考仓库的 CI 结果描述为本项目已运行通过。

## 技术与契约

- 前端使用 Next.js 16.4.3 App Router、React/React DOM 19.3.1、TypeScript 5.9.3、Node 22.23.4；后端使用 Java 21、Spring Boot 3.5.16、MyBatis Starter 3.0.5、MySQL Connector/J 26.10.0、MySQL Server 8.4.11。锁定直接和传递依赖、镜像标签/摘要；不得使用 `latest` 或未记录的浮动升级。实际兼容性仍须通过安装、构建和 MySQL 联调验证。
- 浏览器只访问同源 `/api/**`；Next.js 只代理 Java 接口，不能承担模板、素材、任务或个人记录的业务执行和持久化。Java 负责鉴权、校验、状态与历史，MySQL 存任务状态和步骤。
- 按 `docs/api-contract.md` 固定 202 + `Location`、统一 `{code,message,traceId}`、UTC 响应、私有文件入口、模拟参数、状态枚举和失败语义。按 `docs/schema-design.md` 固定七张业务表、UUID、版本号、索引、归属与启动收敛。若必须改变契约，先同步更新设计文档、相关测试和 README，并说明原因。
- Java 后台按 `ASSET_CHECK → GENERATE → FINALIZE` 运行；阶段开始/结束/失败真实落库，GET 只读。任务 `QUEUED → RUNNING → SUCCEEDED/FAILED`，终态不可回退。启动时把遗留 `RUNNING` 收敛为 `FAILED/PROCESS_INTERRUPTED`，保留 `QUEUED`；这是单实例中断处理，不是自动续跑。
- 前端把上传、提交、后台业务状态和查询异常分开管理；按用户和任务隔离缓存/轮询，单任务终态停止追踪，切换用户或离页清理旧请求。查询异常不改变任务业务状态。

## 验证与交付

- 按题干验收方式，在容器隔离环境运行项目安装、测试、构建和应用；不要在评估者宿主机直接执行项目脚本。此约束不妨碍只读查看文件和编辑设计文档。
- 关键测试至少覆盖 Java 步骤顺序与失败中断、MySQL 事务/归属、前端多任务隔离与查询异常区分。CI 在 push/PR 执行前端 lint/类型检查/构建及 Java 测试/构建；不得跳过失败来取得绿色结果。
- 每阶段记录实际投入、命令、运行环境、通过/失败及未验证项。README 区分源码证据与实际运行效果，如实说明限制、AI 工作流、第三方来源、部署与回滚；不填写未经验证的完成声明。
- 不提交真实凭据、会话令牌、数据库/上传数据、依赖目录或构建缓存。最终对外提交按题目要求使用收到任务的沟通渠道。
