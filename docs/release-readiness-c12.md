# C-12 发布验收

**结论**。CI、数据库和浏览器使用隔离运行环境。真实 DeepSeek 目前为 **NOT_RUN**，真实 JVM 官方请求为 **BLOCKED**。夹具通过表示可重复的服务集成通过。

## 运行与证据索引

| 证据 | 基准与入口 |
| --- | --- |
| C-11 合并后的 main | `0fa460725cd4cda4809982ffe2094ee7bd01aca8`。Backend、Frontend、Workflow E2E 均 PASS。[Actions 44696500503](https://github.com/z1x2c3v4b5n6/AIStudyPlannerAgent/actions/runs/44696500503) |
| C-10 main 的已分类失败 | `b976eac14f2cda003eb9bafcef342e8407734094`。Backend 与 Frontend PASS。E2E 在 Maven 读取 Spring Boot 父 POM 时收到 Central HTTP 429，尚未进入业务测试。[失败日志](https://github.com/z1x2c3v4b5n6/AIStudyPlannerAgent/actions/runs/44691300847/job/136137474699) |
| C-12 实测记录 | CI 检出 `629fa59bdee20a84b243886879e465e2018b3dc7`，对应 PR 头 `c41d20c65332d7fa87ddc1fe8f079b0924d4d543`。Java 282 通过、4 跳过、0 失败，前端构建通过。隔离 MySQL/Redis/Chromium 25 组检查 PASS。[该次 E2E 日志](https://github.com/z1x2c3v4b5n6/AIStudyPlannerAgent/actions/runs/44699203134/job/136163460770) |
| C-12 可复现运行 | PR #30 的 Checks 中查看运行 SHA、Maven/Node 日志与 E2E JSON。Backend 上传同一构建 JAR 与 `c12-offline-evaluation` 报告。[PR 检查](https://github.com/z1x2c3v4b5n6/AIStudyPlannerAgent/pull/30/checks) |
| 真实 DeepSeek | `node scripts/run-live-c12.mjs`。开发环境无密钥，实测输出 `NOT_RUN`，未发出付费请求。 |
| 真实官方来源 | `C12_LIVE_OFFICIAL=true mvn -f backend/pom.xml -Dtest=C12OfficialAcceptanceTest test`。开发环境 JVM 对官方域名的 DNS 解析失败，探针按 `DNS` 分类并以失败退出。发布门槛为 `BLOCKED`。同环境的 HTTP 客户端分别取得 Spring Boot 和 Spring AI 页面，使用应用的解析器得到 74 和 92 个分块，Java 目标 HTTP 请求返回 403。这组分步诊断不代表 JVM 联调成功。报告输出到 `backend/target/c12-live-official.json`。 |

CI 后端按现有 `backend/pom.xml` 配置 Maven 用户缓存。端到端作业直接使用同一运行产生的 JAR，避免第二次 Maven 依赖解析。只有明确的依赖网络临时故障重试，最多 3 次，等待 8 秒和 16 秒。编译、测试断言、容器健康检查、Chromium 启动、前端构建分别有步骤或故障类别。重试分类可用 `bash scripts/ci/test-dependency-retry.sh` 重放。

## Windows 启动、数据隔离和排错

安装 JDK 17、Maven 3.8+、Node 20.19+ 或 22.12+ LTS、MySQL 8 和 Redis 7。把 `java`、`mvn.cmd`、`node.exe`、`npm.cmd` 及 MySQL 客户端放进 PATH。已存在普通开发库时，将 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD` 配成该库。可配置 `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD` 和隔离用 `REDIS_DATABASE`。通过环境变量注入密码与 API Key。根目录如有 `application-local.yml`，启动脚本要求改用环境变量，以便健康检查与实际使用同一配置。

```powershell
.\scripts\dev-acceptance.ps1 -Mode Doctor
.\scripts\dev-acceptance.ps1 -Mode Start
.\scripts\dev-acceptance.ps1 -Mode Smoke
.\scripts\dev-acceptance.ps1 -Mode E2E
```

- **Doctor** 检查工具版本、MySQL 登录、Redis 认证和 PING、当前 Flyway 记录及功能开关。当前独立客户端可指定 `-MySql`。
- **Start** 打包后端、构建前端并启动两个进程。后端启动时迁移数据库，服务日志位于 `backend/target/local-c12`。登录地址为 `http://localhost:5173`。服务准备后按 Ctrl+C 退出。
- **Smoke** 检查已运行的 API、Vue 和最近 Flyway 记录。未登录的后端认证端点应返回 HTTP 401。若端口已占用，先确认运行的是目标实例。
- **E2E** 只接受本机 MySQL。它生成唯一的 `c12_<uuid>_test` 数据库、迁移并初始化合成测试账号和数据，在退出时清理本次创建的库。需要 CREATE DATABASE、DROP DATABASE 和 TRIGGER 权限。部署账户可保持较低权限。使用独立 Redis 测试实例或逻辑库。异常中止进程时，可由数据库管理员核对并清理脚本生成的测试库。

验证登录、科目、任务、路径、计划、学习记录和洞察后，进入 Agent 验证查询和人工审批，进入知识中心验证个人资料、引用及审核。审核用例启动时只为该次合成账号开启审核角色。真实环境按用户 ID 配置 `APP_KNOWLEDGE_REVIEWERS`。账号、密码、密钥与私人资料均应留在本机，勿复制到公开报告。C-12 默认保持 `APP_KNOWLEDGE_ONLINE_ENABLED=false`，联网时显式配置官方目录和网站访问策略。

### 可选真实供应商

在隔离数据库、独立 Redis 和非 CI 环境中设置 `C12_LIVE_DEEPSEEK=true`、`DEEPSEEK_API_KEY`、`DEEPSEEK_MODEL`。显式配置 `C12_MAX_CALLS`、`C12_MAX_TOKENS`、`C12_MAX_COST`、`C12_INPUT_PRICE_PER_MILLION` 与 `C12_OUTPUT_PRICE_PER_MILLION`，价格和费用上限使用同一币种。然后使用以上 E2E 启动流程准备依赖，并运行 `node scripts/run-live-c12.mjs`，或在同样的独立环境下直接运行启动脚本的 E2E 模式并另设 `C8_LIVE_DEEPSEEK=true C11_LIVE_DEEPSEEK=true`。预算在实际 HTTP 请求前预留，累计计算重试。实际费用仍以供应商账单为准。客户端记录模型名、成功失败、工具名称、参数 JSON 形态、延迟、Token 和费用估算。日志省略参数和值。业务测试另外核对参数是否被接受、是否在审批前保持不变，以及重复审批后数据库结果。

官方探针使用 Java 17 Javadoc、Spring Boot 3.5 Actuator、Spring AI 1.1 Tool Calling。按明确版本路径验收来源和主题。若需要可信 HTTPS 代理，在 JVM 配置受信的 `https.proxyHost` 和 `https.proxyPort` 并验证信任链，勿关闭 TLS 校验。真实联网状态需以 JVM 探针为准。

## 项目验收矩阵

默认入口 `node tests/c8-e2e.mjs`，GitHub 使用 MySQL 8.4、Redis 7、Chromium、真实 HTTP 和模拟模型网关。SQL 故障测试另外使用 `C12_VERIFY_DB_FAULT=true`。运行 JSON 包含检出 SHA、检查清单和故障类别。各项当前证据见 [C-12 PR checks](https://github.com/z1x2c3v4b5n6/AIStudyPlannerAgent/pull/30/checks)。

| 验收项 | 结果 | 可核查的入口及独立边界 |
| --- | --- | --- |
| 任务管理 | PASS | task 工具创建、查询和更新，真实任务 API 回读；`tests/c8-e2e.mjs` |
| 学习计划 | PASS | 自适应未来多日计划，真实确认回执；`tests/c8-e2e.mjs` |
| 学习记录 | PASS | 已持久化的合成记录用于真实统计；`tests/c8-e2e.mjs` |
| 学习路径 | PASS | Draft 业务校验，以及发布前和回滚后的网关输入；`tests/c11-e2e.mjs` |
| Agent Tool Calling | PASS（夹具） / NOT_RUN（供应商） | 夹具驱动，真实业务服务；真实模型 **NOT_RUN** |
| Agent Workflow | PASS | 暂停审批、服务重启、恢复后继续；`tests/c8-e2e.mjs` |
| 人工审批 | PASS | 跨账号拒绝、重复确认、数据库回读；`tests/c8-e2e.mjs` |
| 画像和规划 | PASS | 最近两周画像、实际时长与多日容量；`tests/c8-e2e.mjs` |
| RAG 技术问答 | PASS | 四类问答、版本隔离、实际引用视图；`tests/c11-e2e.mjs` |
| 可信在线检索 | BLOCKED | 固定官方目录和失败降级回归；真实 JVM **BLOCKED** |
| 知识审核和发布 | PASS | 并发审批与发布权限，真实 MySQL 故障后保持旧版本；`tests/c12-e2e.mjs` |
| 知识复核与回滚 | PASS | 三个独立账号唯一建单、文档替换和回滚；`tests/c11-e2e.mjs` |
| 私有数据隔离 | PASS | 所有权检索、直接读取拒绝、历史会话隔离；`tests/c10-e2e.mjs`、`tests/c11-e2e.mjs` |
| 系统故障恢复 | PASS（已注入场景） | 真实重启、MySQL 事务故障、模型请求中撤回。Redis 和在线来源失败另有模拟注入 |
| 前端端到端体验 | PASS（下列场景） / NOT_RUN（Windows 启动） | Chromium 登录、Agent 进度与确认、学习洞察、知识检索和引用。Windows 启动脚本的实际执行 **NOT_RUN** |

## 冻结评估与性能

`mvn -f backend/pom.xml -Dtest=C12QualityEvaluationTest test` 导出 `backend/target/c12-quality-v1.json`。56 条检索标注包含 16 条开发题、33 条留出排序题和 7 条额外验收边界标签。后者在专门的版本、隐私、撤回、冲突、最新与安全断言中验证，未混入排序分母。31 条生成夹具用人工参考事实核对固定响应，包含合法引用下的事实曲解。用例下的短句是合成事实断言，真实环境需继续以官方资料和人工复核独立验证。

| 留出策略 | Hit@3（31 正例） | Recall@3 | MRR | 负例返回（2 条） |
| --- | ---: | ---: | ---: | ---: |
| C-4 实际结构化检索 | 30/31 | 95.16% | 0.914 | 0 |
| LEGACY 文档计分回放 | 29/31 | 91.94% | 0.871 | 0 |
| WEIGHTED 文档计分回放 | 30/31 | 95.16% | 0.892 | 0 |

文档回放将内置知识包视为合成文档，验证相同语料上的计分变化。它不反映真实 MySQL 语料规模。留出集多命中一条，开发集却少一条，且内存回放变慢。默认保留 `LEGACY`，可用 `APP_KNOWLEDGE_DOCUMENT_RANKING=WEIGHTED` 显式试验。当前没有语义索引对照结果。生产规模 SQL 计划、索引与连接池占用仍需测量。

生成回放共 31 条，7/7 无证据夹具拒答。24 条有证据的输出中，21 条格式及引用有效，15 条受支持，6 条曲解引文，3 条引用无效。因此可核验结论的事实支持率为 15/21。真实模型 Token、费用和生成质量 **NOT_RUN**。

CI 另执行 24 个请求、并发 4 的小样本数据库回归。日志的 `c12-small-concurrency-v1` 分别记录检索、模拟模型和端到端时延、错误数、Linux 进程 RSS/CPU ticks 及 MySQL 服务器连接快照。该次 CI 记录 0 个错误，端到端 P50/P95 约 16.1/19.6 ms，检索 1/3 ms，固定供应商 5/6 ms。MySQL 服务器连接数前后均为 11。在线耗时和进程内连接池使用未实测。MySQL 吞吐和 Redis 长时间故障恢复也需要独立压测。
