# 数据模型与执行一致性（P0 冻结）

依据：[题目原文](题目原文.md)、[API 契约](api-contract.md)。`V1__schema.sql`、V2/V3 种子迁移已创建；运行中的 MySQL 已支持三模板、Alice/Bob、上传及任务成功读写，但尚未独立检查空卷迁移记录和全部数据库约束。数据库固定为 MySQL 8.4；只建立以下**七张业务表**，Flyway 自有元数据表不计入业务表。

## 1. 通用约定

- 公开的 `users.id`、`templates.id`、`assets.id`、`tasks.id` 是服务端生成的 UUID v4，存为 `CHAR(36)`。`demo_key`、Cookie 令牌及任务阶段不属于公开 ID。关联表用复合主键。
- 时间列使用 `DATETIME(6)`，约定存 UTC；Java 内部使用 `Instant`，MyBatis/JDBC 读写时明确 UTC 时区，HTTP 输出 ISO 8601 `Z`。不得直接将宿主机本地时间写入这些列。真实 MySQL 集成测试覆盖跨时区读写。
- 状态、阶段用受控字符串 `VARCHAR`，Java 枚举校验；表上添加能表达的 `CHECK` 约束。MySQL 的 `JSON` 列保存 MIME 列表和模拟配置，应用层再做结构校验，不能把 JSON 当作免校验的任意字段。
- 数据库字符集为 `utf8mb4`。用户资料、标题和错误消息设置明确长度上限，截断长异常文本；日志保留完整堆栈，接口不返回堆栈。
- 迁移按 `V1__schema.sql`、`V2__seed_templates.sql`、`V3__seed_demo_users.sql` 等递增版本编写；已执行迁移不修改。模板和用户种子 ID 固定，以便空库演示和测试可重复；示例封面与上传图必须随仓库提供。

## 2. 七张表

以下列类型为实施基准；长度可在首次迁移提交前微调，提交后通过新迁移变更。

### `users`

| 列 | 类型/约束 | 用途 |
| --- | --- | --- |
| `id` | `CHAR(36) PRIMARY KEY` | UUID 公开 ID。 |
| `demo_key` | `VARCHAR(32) NOT NULL UNIQUE` | 仅演示登录用，例如 `alice`、`bob`。 |
| `display_name` | `VARCHAR(80) NOT NULL` | 页面展示。 |
| `created_at` | `DATETIME(6) NOT NULL` | UTC 创建时间。 |

只提供演示身份，不保存密码或真实个人信息。演示模式关闭时不允许创建新演示会话；不宣称这是生产身份体系。

### `sessions`

| 列 | 类型/约束 | 用途 |
| --- | --- | --- |
| `token_hash` | `CHAR(64) PRIMARY KEY` | 随机令牌的 SHA-256 十六进制摘要，不存明文。 |
| `user_id` | `CHAR(36) NOT NULL`，外键到 `users.id` | 当前用户。 |
| `created_at` | `DATETIME(6) NOT NULL` | 签发时间。 |
| `expires_at` | `DATETIME(6) NOT NULL` | 过期时间。 |

索引 `idx_sessions_user(user_id)` 和 `idx_sessions_expires(expires_at)`。Cookie 中的令牌由安全随机数生成，退出删除数据库行；每次受保护请求验证摘要及过期时间。清理过期会话可在后续维护中实现，不影响判定过期。

### `templates`

| 列 | 类型/约束 | 用途 |
| --- | --- | --- |
| `id` | `CHAR(36) PRIMARY KEY` | 稳定 UUID。 |
| `title` | `VARCHAR(120) NOT NULL` | 卡片与详情标题。 |
| `category` | `VARCHAR(80) NOT NULL` | 列表分类；无额外分类表。 |
| `description` | `VARCHAR(1000) NOT NULL` | 详情说明。 |
| `cover_path` | `VARCHAR(255) NOT NULL` | 随应用打包的公开本地封面相对路径。 |
| `required_image_count` | `TINYINT UNSIGNED NOT NULL` | 建议 1～4，数据库检查 >0。 |
| `accepted_mime_types` | `JSON NOT NULL` | 非空 MIME 数组，Java 校验仅允许 JPEG/PNG 等明确格式。 |
| `max_file_size_bytes` | `BIGINT UNSIGNED NOT NULL` | 单张文件上限，不超过服务端全局上限。 |
| `created_at` | `DATETIME(6) NOT NULL` | 种子时间。 |

索引 `idx_templates_category(category,id)`。公开分类来自 `SELECT DISTINCT category` 或列表结果去重。此题不实现模板 CRUD；删除/更改已有模板的行为不作为演示入口。任务另存标题快照，历史不会因为文案改动丢失标题。

### `assets`

| 列 | 类型/约束 | 用途 |
| --- | --- | --- |
| `id` | `CHAR(36) PRIMARY KEY` | UUID 公开 ID。 |
| `user_id` | `CHAR(36) NOT NULL`，外键到 `users.id` | 素材/结果归属。 |
| `template_id` | `CHAR(36) NULL`，外键到 `templates.id` | `UPLOAD` 时为目标模板；`RESULT` 时可为空。 |
| `purpose` | `VARCHAR(10) NOT NULL` | `UPLOAD` 或 `RESULT`。 |
| `storage_path` | `VARCHAR(255) NOT NULL UNIQUE` | 仅服务端生成的相对路径，不能由浏览器提交。 |
| `mime_type` | `VARCHAR(64) NOT NULL` | 解码后确认的内容类型。 |
| `size_bytes` | `BIGINT UNSIGNED NOT NULL` | 实际文件字节数。 |
| `width`,`height` | `INT UNSIGNED NOT NULL` | 解码得到的像素尺寸。 |
| `created_at` | `DATETIME(6) NOT NULL` | UTC 时间。 |

索引 `idx_assets_user_created(user_id,created_at DESC,id DESC)` 和 `idx_assets_template(template_id)`。读取内容时先按 `id AND user_id` 查行，再解析路径，确认规范化路径仍在存储根目录内；不能直接把数据库路径、原始文件名或请求路径交给 `Files.read`。

### `tasks`

| 列 | 类型/约束 | 用途 |
| --- | --- | --- |
| `id` | `CHAR(36) PRIMARY KEY` | UUID 公开 ID。 |
| `user_id` | `CHAR(36) NOT NULL`，外键到 `users.id` | 个人历史与鉴权。 |
| `template_id` | `CHAR(36) NOT NULL`，外键到 `templates.id` | 受理时模板。 |
| `template_title_snapshot` | `VARCHAR(120) NOT NULL` | 历史文案快照。 |
| `status` | `VARCHAR(12) NOT NULL` | `QUEUED/RUNNING/SUCCEEDED/FAILED`。 |
| `current_stage` | `VARCHAR(16) NULL` | 队列中为 null；运行/终态保留最后阶段。 |
| `simulation_json` | `JSON NOT NULL` | 受理时的完整失败阶段与各阶段延迟快照。 |
| `result_asset_id` | `CHAR(36) NULL`，外键到 `assets.id` | 仅成功后关联结果图。 |
| `failed_stage` | `VARCHAR(16) NULL` | 仅失败时有值。 |
| `error_code` | `VARCHAR(64) NULL` | 稳定错误码，包括 `PROCESS_INTERRUPTED`。 |
| `error_message` | `VARCHAR(500) NULL` | 对用户可读，不含堆栈和磁盘路径。 |
| `created_at`,`started_at`,`finished_at` | `DATETIME(6)`；后两者可空 | UTC 生命周期。 |
| `version` | `BIGINT UNSIGNED NOT NULL DEFAULT 0` | 每次状态/阶段变化递增。 |

索引 `idx_tasks_queue(status,created_at,id)` 支持领取最早任务，`idx_tasks_user_history(user_id,created_at DESC,id DESC)` 支持个人历史。`status` 与结果/错误字段一致性由短事务和测试保证：`SUCCEEDED` 必须有 `result_asset_id,finished_at` 且没有失败字段；`FAILED` 必须有 `failed_stage,error_code,error_message,finished_at`；`QUEUED` 必须没有开始/结束时间。结果资产的 `user_id` 必须等于任务 `user_id`，服务层在关联前检查。

### `task_assets`

| 列 | 类型/约束 | 用途 |
| --- | --- | --- |
| `task_id` | `CHAR(36) NOT NULL`，外键到 `tasks.id` | 任务。 |
| `asset_id` | `CHAR(36) NOT NULL`，外键到 `assets.id` | 用户上传图，不能是结果图。 |
| `position` | `TINYINT UNSIGNED NOT NULL` | 槽位顺序，从 0 开始。 |

主键 `(task_id,position)`，另设 `UNIQUE(task_id,asset_id)` 和 `idx_task_assets_asset(asset_id)`。受理时校验所有素材的 `user_id`、`template_id`、`purpose=UPLOAD`；跨表同用户约束由服务层及集成测试保证。

### `task_steps`

| 列 | 类型/约束 | 用途 |
| --- | --- | --- |
| `task_id` | `CHAR(36) NOT NULL`，外键到 `tasks.id` | 任务。 |
| `stage` | `VARCHAR(16) NOT NULL` | `ASSET_CHECK/GENERATE/FINALIZE`。 |
| `stage_order` | `TINYINT UNSIGNED NOT NULL` | 固定为 0/1/2。 |
| `status` | `VARCHAR(12) NOT NULL` | `PENDING/RUNNING/SUCCEEDED/FAILED/SKIPPED`。 |
| `started_at`,`finished_at` | `DATETIME(6) NULL` | 阶段边界。 |
| `error_message` | `VARCHAR(500) NULL` | 该步失败原因。 |

主键 `(task_id,stage)`，`UNIQUE(task_id,stage_order)`。任务插入事务同时建立三行 `PENDING` 步骤。阶段开始、完成、失败分别独立落库；步骤顺序通过 `stage_order` 和前一阶段状态检查保证。`SKIPPED` 不伪造开始时间。

## 3. 事务与文件边界

| 操作 | 单次事务内容 | 事务外行为 |
| --- | --- | --- |
| 上传 | 插入验证过的 `assets` 行 | 临时写文件、签名与解码校验、移动到持久目录；数据库失败时尽力删新文件。 |
| 创建任务 | 校验数据库归属，插入 `tasks`、`task_assets`、3 个 `task_steps`；任一失败整体回滚 | 不执行耗时生成；返回 202。 |
| 领取任务 | `WHERE status='QUEUED'` 原子更新为 `RUNNING`，写 `started_at`，`version+1`；受影响行数必须为 1 | 工作器领取成功后才执行阶段。 |
| 开始阶段 | 任务与该步骤改为 `RUNNING`，写 `current_stage/started_at`，`version+1`；检查前序步骤已成功 | 延迟、模拟失败和实际文件检查在事务外。 |
| 完成非最终阶段 | 对应步骤 `SUCCEEDED`、`finished_at`，任务 `version+1` | 进入下一步。 |
| 最终成功 | 插入结果 `assets`，最终步骤 `SUCCEEDED`，任务 `SUCCEEDED/result_asset_id/finished_at/version+1` 同事务 | 结果文件在事务前写好；事务失败尽力删除孤儿文件。 |
| 阶段失败 | 当前步骤 `FAILED`，后续 `PENDING` → `SKIPPED`，任务 `FAILED/failed_stage/error_code/error_message/finished_at/version+1` 同事务 | 记录日志，停止后续执行。 |
| 启动收敛 | 遗留 `RUNNING` 任务转失败：当前未完成步骤 `FAILED`，其后 `PENDING` 步骤 `SKIPPED`，`version+1`；不更新 `QUEUED` 或终态 | 完成收敛后才开启工作器轮询。 |

Mapper 更新使用预期旧状态作为 SQL 条件，受影响行数不符要拒绝继续，不靠先读再无条件写。GET Mapper 只执行 `SELECT`。`@Transactional` 放在由工作器调用的独立服务 Bean 上，避免 Spring 代理内部调用失效。数据库与本地文件不能做真正分布式原子提交，孤儿文件、长期数据库中断及单实例假设在 README 如实说明。

## 4. 启动中断收敛

仅在单 Java 实例成立时执行：应用启动、调度器可领取新任务之前，扫描 `status=RUNNING`。若 `current_stage` 对应步骤仍在 `RUNNING`，该步骤转 `FAILED`；若它已 `SUCCEEDED`、下一个步骤尚未开始，改将下一个步骤记为中断失败，保留已成功步骤。后续 `PENDING` 标 `SKIPPED`，任务标 `FAILED`、`error_code=PROCESS_INTERRUPTED`、`failed_stage=实际未完成阶段`、写结束时间和 `version+1`。若 `RUNNING` 但 `current_stage=null`，按领取后尚未开始第一阶段处理：以 `ASSET_CHECK` 为失败阶段并写合成的开始/结束时间。`QUEUED` 任务不改，正常等待下一次领取。终态不改；不宣称自动续跑。

此操作由真实 MySQL 集成测试覆盖：预置 `RUNNING`、`QUEUED`、`SUCCEEDED`、`FAILED` 四类行，启动收敛后逐行断言；重复执行收敛应无额外变化。进程在文件创建与最终事务之间被杀时，结果文件可能孤立，但任务不能凭文件存在就变成 `SUCCEEDED`。
