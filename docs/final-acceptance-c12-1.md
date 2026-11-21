# C-12.1 最终验收

基线为 C-12 合并后的 `7f4301cdb3efb023a198e6a67b54eba29a9e37ea`。本版本分支为 `fix/c12-final-acceptance`。最终提交及各作业的检出 SHA 由 PR 的 Checks 和 E2E 输出记录。报告中的旧 SHA 仅标识 C-12 基线。[C-12.1 PR 检查](https://github.com/z1x2c3v4b5n6/AIStudyPlannerAgent/pulls?q=is%3Apr+head%3Afix%2Fc12-final-acceptance)

## 验收矩阵

| 验收项 | 状态 | 环境、入口和证据 |
| --- | --- | --- |
| PR #30 基线 | PASS | Linux CI，Java 测试及打包、Vue 测试及构建、数据库和 Chromium 共 27 组端到端场景。[完整日志](https://github.com/z1x2c3v4b5n6/AIStudyPlannerAgent/actions/runs/44700100779) |
| 最新 main 基线 | PASS | 合并提交上述基线 SHA。Java 282 通过、4 跳过、0 失败，Vue 测试 10 项通过，MySQL/Redis/Chromium 27 组通过。[完整日志](https://github.com/z1x2c3v4b5n6/AIStudyPlannerAgent/actions/runs/44888178419) |
| Windows Doctor、Start、Smoke、E2E 实际运行 | NOT_RUN | 本轮没有可用的 Windows 运行环境。CI 的 PowerShell 语法检查独立于实际运行。按下方命令分别验证。 |
| 真实 DeepSeek | NOT_RUN | `node scripts/run-live-c12.mjs` 在本环境输出 `DEEPSEEK_API_KEY is missing`，无付费调用。预算逻辑使用 `node --test tests/c12-provider.test.mjs` 验证。 |
| JVM 直连官方源 | BLOCKED | Java 17 直连，三个目标均在 DNS 解析失败。使用下方探针复现。 |
| Spring Boot 3.5 和 Spring AI 1.1 官方正文 | PASS | 同一 Java 应用客户端通过已授权 HTTPS 代理和受信 CA，分别得到 74 和 92 个分块。验证页面版本路径、相关标记及临时引用。 |
| Oracle Java 17 官方正文 | BLOCKED | 代理链路上的 JVM 已建立连接，目标请求返回 HTTP 403；未计为已通过。 |
| RAG 输出事实支持 | PASS | `C12QualityEvaluationTest` 使用原 31 条人工标注的固定夹具对照。同一份报告含基线和新策略结果。 |
| 规划容量及截止日期 | PASS | `AdaptivePlannerTest` 验证高优先级临期任务、30/60/90 分钟容量、完整时段、截止窗口与原日期保留。 |
| Agent 审批、重复执行、恢复及资料权限 | PASS | 由 C-12.1 PR 的 MySQL/Redis/Chromium E2E 核查，模型使用夹具。包含提交后只写一次、并发旧提案失败、跨账号访问、知识撤回和发布故障回滚。真实模型部分单列。 |

## Windows 复现

安装 JDK 17、Maven、受支持的 Node、MySQL 客户端和 Redis。设置 `DB_URL`、`DB_USERNAME`、`DB_PASSWORD`、`REDIS_HOST`、`REDIS_PORT`；有密码时通过当前进程环境传入 `REDIS_PASSWORD`。使用本机 MySQL 独立账号与 Redis 测试实例或逻辑库。根目录若有 `application-local.yml`，请在独立工作目录执行，避免配置来源不一致。

```powershell
.\scripts\dev-acceptance.ps1 -Mode Doctor
.\scripts\dev-acceptance.ps1 -Mode Start
# 另开一个 PowerShell 窗口
.\scripts\dev-acceptance.ps1 -Mode Smoke
# 停止上述应用后再进行隔离验收
.\scripts\dev-acceptance.ps1 -Mode E2E
```

Doctor 校验工具版本、MySQL、Redis、Flyway 记录和环境开关。Start 启动前检查端口 8080、5173；HTTP 就绪等待期间会检测本轮子进程是否退出。普通及错误日志写入 `backend/target/local-c12/`。按 Ctrl+C 结束后，检查本轮子进程和端口释放。Smoke 检查前后端 HTTP 和 Flyway 记录。E2E 另检查 18081，创建唯一随机 `_test` 库，用已有合成数据脚本运行。清理由本轮创建的库名限定，停止进程失败时仍尝试其他清理操作；如果数据库离线而无法清理，会明确报告该临时库名。强制杀死整个 PowerShell 进程仍可能跳过 finally。MySQL 密码由子进程环境传递，Redis 校验不回显认证字符串。

## 真实联调

Windows 隔离 E2E 支持 `C12_LIVE_DEEPSEEK=true`。在 Bash 可运行 `node scripts/run-live-c12.mjs`。设置 `DEEPSEEK_MODEL`、`DEEPSEEK_API_KEY` 与 [C-12 文档列出的请求和费率预算](release-readiness-c12.md#可选真实供应商)。测试包含 JSON 对话、真实数据库任务读取、工具调用序列、等待确认后写入、重复回执、业务多步骤工作流、RAG 与 Learning Path。模型异常和非法工具参数由本地供应商夹具注入，独立标为 `LOCAL_INJECTED_FAULT`，不消耗付费调用。实际延迟、Token、估算费用及错误分类仅在真实执行时记录。

```powershell
$env:C12_LIVE_OFFICIAL='true'
mvn.cmd -f backend/pom.xml "-Dtest=C12OfficialAcceptanceTest" test
Remove-Item Env:C12_LIVE_OFFICIAL
```

如果本机需要授权的 HTTPS 代理，为 JVM 配置 `https.proxyHost`、`https.proxyPort`，并在 JVM 的可信 CA 集合中导入该代理 CA，或用 `javax.net.ssl.trustStore` 指向含默认公共 CA 和批准代理 CA 的信任库。保留 TLS 校验。JDK HttpClient 的直连 DNS 路径与独立代理客户端不同。探针在 `backend/target/c12-live-official.json` 保存每个来源的独立结果。应用仍执行 HTTPS、域和路径限制、禁用重定向、媒体类型、UTF-8/ASCII 校验、字节和超时限制；单次采集保持零自动重试，失败时回到本地证据。

## 质量结论

原 31 条用例保留。基线 21 条引用校验通过，其中 15 条结论受支持，6 条结论曲解；3 条引用编号被拒绝，7 条无可用证据用例拒答。新策略在相同 21 条有效选择上输出完整的引用块，6 条曲解文本均未展示；3 条无效引用和 7 条拒答保持原结果。这个对照衡量夹具中的错误文本阻断。模型负责选择证据，服务端负责输出原文，因此结果可能保留英文，也可能比短摘要更长。未验证真实模型的选段质量，原文之间复杂语义冲突仍需核查。

三种规划策略保留预算、锁定时间、截止日和任务前置约束。科目偏好仅在优先级和截止日相同后生效。测试中唯一的 30 分钟窗口现在分给高优先级临期任务，不增加日期变动。无法安排时分别说明单日预算、截止窗口、累计剩余容量或锁定/占用条件。学习时长依然来源于实际记录，代表投入时间；复习掌握程度只能参考单独的用户自评等证据。
