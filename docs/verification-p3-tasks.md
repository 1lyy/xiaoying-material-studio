# Java 任务与 Worker 验证记录

记录日期：2026-10-30（Asia/Shanghai）。本轮实现任务受理、查询、串行 Worker、三阶段事务、结果文件与启动收敛的源码。前端任务状态逻辑未开发。

| 检查 | 命令/环境 | 实际结果 |
| --- | --- | --- |
| 后端构建 | `docker compose --profile test build backend-test`，当前 PowerShell | **UNVERIFIED**：退出 1，`docker` 未被识别；没有执行 Maven 编译或打包。 |
| 后端测试 | `docker compose --profile test run --rm backend-test`，当前 PowerShell | **UNVERIFIED**：退出 1，`docker` 未被识别；没有执行 JUnit/MySQL 集成测试。 |
| Compose 规范化 | `docker compose config` | **UNVERIFIED**：退出 1，`docker` 未被识别；仅作了 YAML 静态解析。 |
| MyBatis/配置静态检查 | Python XML/YAML 解析和 Mapper 方法 ID 对照 | **静态通过**：POM、四个 Mapper XML、`application.yml`、`compose.yaml` 可解析；`TaskMapper` 方法与 XML statement ID 一致。不证明 SQL 可在 MySQL 8.4 执行。 |
| 成功任务真实演示 | 需启动容器并上传、提交、等待、读结果 | **UNVERIFIED**：未运行应用、MySQL 或 HTTP。 |
| ASSET_CHECK 失败真实演示 | 需 `failAtStage=ASSET_CHECK` 的容器 HTTP 请求 | **UNVERIFIED**。 |
| GENERATE 失败真实演示 | 需 `failAtStage=GENERATE` 的容器 HTTP 请求 | **UNVERIFIED**。 |
| FINALIZE 失败真实演示 | 需 `failAtStage=FINALIZE` 的容器 HTTP 请求 | **UNVERIFIED**。 |

待执行测试源码：`SimulationSnapshotTest` 验证配置快照和参数边界；`TaskPipelineTest` 使用可注入的无等待延迟器/生成器验证步骤调用顺序、三种失败中断与最终写库失败清理结果文件；`TaskWorkerTest` 验证启动收敛先于领取与关闭生命周期；`TaskMySqlTest` 在独立 MySQL 测试服务中验证 202/Location、七次版本推进、独立提交的阶段事务、结果 asset、三个指定阶段失败、用户隔离、素材顺序、GET 只读、启动中断收敛。测试源码存在不等于测试通过。

门槛结论：**P1 任务核心运行门槛未通过**。首先在容器环境运行构建和测试，按实测编译/SQL/事务结果修正，然后真实演示成功和三种失败。`db-test` 与运行数据库分离，避免测试数据污染持久卷。
