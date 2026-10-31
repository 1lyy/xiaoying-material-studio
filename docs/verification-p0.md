# P0 脚手架实际验证记录

记录日期：2026-10-30（Asia/Shanghai）。本文件区分静态检查、已执行命令和未执行的运行验证；不能把静态检查视作构建通过。

| 检查 | 环境与命令 | 结果 |
| --- | --- | --- |
| 容器运行时 | PowerShell `Get-Command docker,podman,nerdctl,wsl`；`docker version` / `docker compose version` | Docker、Podman、nerdctl 不在 PATH；WSL 枚举返回 `E_ACCESSDENIED`，无可用隔离容器。 |
| Compose 规范化 | PowerShell `docker compose config` | **未通过执行**：`docker` 命令不存在。配置是否被 Compose 接受仍未验证。 |
| Compose YAML 静态解析 | 宿主 Python `yaml.safe_load(open('compose.yaml'))` | 通过语法解析；识别 `db/backend/frontend`，只有 `frontend.ports`，`db/backend` 无 `ports`。这不等于 `docker compose config`。 |
| npm 锁文件解析 | 宿主 `npm install --package-lock-only --ignore-scripts --no-audit --no-fund --registry https://registry.npmjs.org --cache ...` | 退出码 0；只产生锁文件，没有 `node_modules`。锁文件 v3、606 个 package 节点；锁定 Next 16.4.3、React 19.3.1、Tailwind 4.1.10、Vitest 3.2.4。宿主 Node 24.16.0 产生 EBADENGINE 提醒，故目标 Node 22 验证仍待做。 |
| POM 与缓存静态检查 | PowerShell `[xml]` 解析 `backend/pom.xml`，`ConvertFrom-Json -AsHashtable` 解析 lockfile，检查目录存在性 | POM 父版本 3.5.16、Java 21、七个预期依赖可解析；lockfile 含 Next/React/TypeScript/Query/Vitest/Testing Library 的指定版本；`node_modules` 与临时 `.npm-cache` 均不存在。 |
| 前端 lint/typecheck/test/build | 目标 Node 22.23.4 容器内 `npm ci && npm run lint && npm run typecheck && npm run test && npm run build` | **未执行**：无容器运行时；依照 `AGENTS.md` 不在宿主机运行项目安装/测试/构建。 |
| 后端测试/构建 | Java 21 容器内 `sh ./mvnw -B -ntp verify` | **未执行**：同上。 |
| Compose 启动与健康链路 | `docker compose up --build -d --wait`，随后同源 `GET /api/health` | **未执行**：同上。首页与健康接口的实际效果未验证。 |

静态可见的限制：Docker 基础镜像只锁定了具体版本标签，镜像 digest 尚未在实际拉取后记录；Maven 传递依赖由固定 Spring Boot BOM/显式版本协调，仍待容器解析确认。业务表与业务接口尚未实现，属于下一轮工作。
