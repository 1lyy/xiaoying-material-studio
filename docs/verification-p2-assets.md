# 素材上传与私有文件验证记录

记录日期：2026-10-30（Asia/Shanghai）。本轮仅涉及 R-03、R-10 的后端素材链路及必要的 Next.js 代理缓存头修复；未实现任务状态机、Worker 或前端上传页面。

| 检查 | 命令/环境 | 实际结果 |
| --- | --- | --- |
| 后端测试 | `docker compose --profile test run --rm backend-test`，当前 PowerShell | **UNVERIFIED**：命令退出 1，`docker` 未被识别；没有运行 Maven、JUnit 或 MySQL。 |
| 后端构建 | `docker compose --profile test build backend-test`，当前 PowerShell | **UNVERIFIED**：同样因缺少 `docker` 命令退出 1；没有生成 JAR。 |
| Compose 规范化/启动 | 尝试 `docker compose config`；需容器运行时的 `up --build` | **UNVERIFIED**：`config` 因 `docker` 未被识别而退出 1；未运行容器，端到端上传、Cookie 与文件流效果未验证。 |
| XML 静态解析 | 宿主 Python `xml.etree.ElementTree.parse` 读取 POM 和三个 Mapper XML | **通过静态语法检查**：四个 XML 可解析；不证明 MyBatis 绑定或 SQL 执行正确。 |
| Compose YAML 静态解析 | 宿主 Python `yaml.safe_load` | **通过静态语法检查**：识别 `db/backend/frontend/backend-test`；不证明 Compose 插值可用。 |
| 演示图片静态检查 | Pillow `Image.verify()` 与重新打开 | **通过图片文件检查**：`coast.png` 为 PNG 640×400、4700 字节；`sunset.jpg` 为 JPEG 640×400、14151 字节。 |
| 前端代理测试 | 预定 Node 22 容器运行 `npm run test` | **UNVERIFIED**：没有容器运行时，未在宿主运行项目脚本。 |

新增测试源码：`PrivateImageStorageTest` 覆盖 JPEG/PNG、空文件、扩展名/MIME/签名伪装、超大小、坏图、像素上限、路径穿越和符号链接；`AssetServiceTest` 模拟数据库写入失败并断言新文件清理；`AssetMySqlTest` 需要 `TEST_DB_URL`，覆盖真实 MySQL 行、私有文件、Alice/Bob 隔离、重复文件字段、错误体、缺失文件和数据库路径逃逸；代理测试新增 201/413、multipart body 原样转发和私有缓存头。**这些是待执行断言，不是已通过测试。**

当前门槛：源码与示例素材已就位；**后端测试/构建、MySQL 8.4 迁移及 HTTP 行为均 UNVERIFIED**。下一轮在容器环境先运行测试并修正实测问题，再进入任务受理与 Worker。文件与数据库无法原子提交，进程在文件移动和事务提交之间中断可能留下孤儿文件；当前只对可捕获的数据库异常执行清理。
