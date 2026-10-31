# P1 数据库、身份与模板验证记录

记录日期：2026-10-30（Asia/Shanghai）。本轮只实现七表迁移、演示身份和只读模板接口；没有 Worker、上传或任务 API。以下“通过”仅指已执行的静态检查，不表示 MySQL 迁移或 HTTP 联调通过。

| 检查 | 命令/环境 | 实际结果 |
| --- | --- | --- |
| XML 语法 | 宿主 PowerShell `[xml](Get-Content -Raw ...)` 解析 POM、两个 MyBatis XML 和三个 SVG | 六个 XML 文件均可解析。未证明 Maven 或 MyBatis 运行。 |
| Compose YAML 语法及端口 | 宿主 Python `yaml.safe_load(open('compose.yaml'))` | 识别 `db/backend/frontend/backend-test`；只有 `frontend` 声明宿主 `ports`。`backend-test` 属于 `test` profile。 |
| 迁移文件静态计数 | `Select-String` 检查 `V1__schema.sql` 的 `CREATE TABLE`，列出迁移目录 | V1 含 `users/sessions/templates/assets/tasks/task_assets/task_steps` 七表、9 处外键声明和 10 个命名普通索引；存在 V2 模板种子、V3 用户种子。未执行 SQL。 |
| Compose 规范化 | `docker compose config` | **未通过执行**：本机 `docker` 命令不存在；不能推断 Compose 插值或镜像构建正确。 |
| Maven 单元/集成测试 | 预定 `docker compose --profile test build backend-test` 和 `docker compose --profile test run --rm backend-test` | **未执行**：无 Docker/Podman 容器运行时；依 `AGENTS.md` 不在宿主机运行项目安装、测试、构建。 |
| MySQL 8.4 Flyway | 预定容器启动后查 `flyway_schema_history`、七表约束/索引和固定种子 | **未执行**：不能宣称 V1～V3 在 MySQL 8.4 已应用。 |
| HTTP 链路 | 预定经前端 `/api/**` 验证 demo-users、session、templates、cover | **未执行**：源码测试已写，实际 Cookie、代理和 404/400 响应未联调。 |

已写入的测试：`HealthControllerTest`、`OriginGuardInterceptorTest`、`SessionControllerTest` 可在无 MySQL 时运行；`IdentityTemplateMySqlTest` 仅在 `TEST_DB_URL` 非空时启用，使用真实 MySQL、Flyway 和 MockMvc，覆盖模板列表/分类/封面/404、Alice/Bob 独立会话、只存 token hash、过期/退出以及 Origin 和请求字段检查。测试文件存在不等于测试通过。

**门槛结论：源码与静态结构就位，运行门槛未通过。** 下一轮首先在容器内执行 `docker compose config`、`backend-test` 和 `docker compose up --build -d --wait`，修正任何实际失败；之后才能报告迁移和接口可运行。
