# C-11 证据问答与知识演进

## 从检索到回答

`RagRetrieval` 复用 C-10 的文档查询、在线状态和 C-4 内置召回。软件版本按精确字符串匹配，支持显式传参及从明确的 Spring Boot、Spring AI、Spring Framework、Java/JDK 版本短语提取版本。常用中英术语可扩展检索词。通用词和版本号本身不能通过文档相关性过滤。相关性判断在在线回退决定之前执行。未明确版本的“最新”查询可展示临时线索，仍需确认当前发行版本。多版资料且缺少版本、无法验证最新、资料版本不明或检测到同一配置布尔默认值冲突时，问答返回证据及原因。冲突检测只覆盖明确的布尔设置，审核人员仍需要核对复杂语义冲突。

`EvidenceContextBuilder` 保留 C-4 的知识 ID 与阶段、前置关系，以及文档 ID、版本 ID、复合分块 ID、来源、适用版本、清洗后的行号。按相关度与来源排序，对同一文档最多取两段，并去掉相同或被包含的近重复文本。序列化证据数组默认限制 6200 个字符，可用 `APP_KNOWLEDGE_RAG_CONTEXT_CHARACTERS` 配置。优先缩短正文，元数据保留。所有文档以 `untrustedEvidence` 字段发送，问答使用没有挂载工具的现有 DeepSeek 网关。

在「知识中心 → 技术问答」设置版本和范围。API 为 `POST /api/v1/knowledge-hub/rag/answer` 和 `GET /api/v1/knowledge-hub/rag/history`。个人文档仅由所有者检索；模型只会接收明确授权的个人片段。可继续最近的历史问题，继承其已确认软件版本，再重新检索。历史保存问题、版本及范围，不保存全文证据或模型回答。答案指向当前版本和分块，引用撤回后由原文 API 拒绝访问。

服务端验证 JSON、引用编号和逐字证据，再查数据库确认发布状态、所有权和引用位置。AI 关闭、超时、引用不存在或版本变化时，返回仍可见的证据和降级原因。逐字引用校验能证明片段存在，不能证明模型推导正确。回放包含故意曲解真实引文的对照例。响应记录供方返回的 Token 用量和各阶段时长。

## Learning Path

Learning Path 保留原有 C-4 知识顺序、前置依赖和 JSON/业务校验。生成请求新增可选 `softwareVersion`，也可在需求中写明版本。公开且仍有效的文档才作为 `untrustedDocumentEvidence` 注入。出现版本歧义、检测到冲突或文档查询失败时沿用 C-4。日志记录实际注入的文档版本 ID。CI 在发布前、发布后、替换和回滚后分别捕获真实出站 Prompt 来核对版本。

## 官方目录在线发现

`RegisteredSourceSearch` 保留为后备。配置 `APP_KNOWLEDGE_ONLINE_ENABLED=true` 和 `APP_KNOWLEDGE_CATALOG_URLS` 后，可从配置的官方 HTML 目录发现相关链接。例如 Spring Boot 3.5 的 `https://docs.spring.io/spring-boot/3.5/reference/index.html`。每次只读取一个目录，最多继续读取两篇同站点、同产品和同版本文档，逐页复用 C-10 的 HTTPS、重定向禁用、响应大小和请求时限。服务端通过正文匹配再次筛选结果。Redis 维持每用户的 30 秒间隔。官方目录链接和页面正文都是临时证据。

CI 使用固定目录夹具。要主动测试真实官方客户端，可运行 `C11_LIVE_OFFICIAL=true mvn -f backend/pom.xml -Dtest=EvidenceRagTest#readsRelevantOfficialCatalogDocument test`。需要 Java 进程具备出网能力。要支付实际模型调用成本，请使用隔离测试数据库，在 CI 以外同时显式设置 `C8_LIVE_DEEPSEEK=true C11_LIVE_DEEPSEEK=true`，提供 `DEEPSEEK_API_KEY`，然后按 C-8 文档运行 `node tests/c8-e2e.mjs`。该探针用合成参考文本测试有来源的真实模型回答，报告返回的 Token 用量，费用按账号费率核算。

## 知识复核

首次反馈仍使用 C-10 POST。同一用户可用 PUT 更正，独立用户计数保持唯一。默认三个独立账号在最近 30 天内对同一版本、技术环境、问题类型提交负面反馈时，事务内建立一条复核任务。审核台可触发官方重查，已锁定的任务拒绝重复执行，超时租约可重试。内容哈希一致时记录未确认变化，内容不同保留为候选并沿用现有审核发布。也可记录版本不符、错引、内容不完整或证据不足等人工结论。反馈和复核均不会自动切换公开版本。

同一复核任务可在发布前后运行固定查询。报告保存旧版、当前版和候选的命中、错版本、无关项和可追踪项数量。期望词由审核人定义，因而该检查适合固定故障回归。退化提示不自动回滚。C-10 的事务发布与人工回滚仍负责当前版本切换。

| 迁移 | 新表 |
|---|---|
| V10 | `knowledge_rag_turn` |
| V11 | `knowledge_review_case`、`knowledge_review_run` |

## 评估范围

`mvn -f backend/pom.xml -Dtest=RagReplayEvaluationTest test` 输出 `backend/target/c11-rag-replay.json`。固定夹具包括四道正例和一道无关例。它比较无证据、C-4 证据、公开文档、公开文档加在线夹具以及人工 Oracle，验证引用结构、用标注核对事实支持并报告回放延时。精确率按每查询三个位置计，召回率和 MRR 以四条正例计。错版本、私有文档越权和回滚由 MySQL/Redis/Chromium E2E 单独验证，未用零样本推算比例。该回放使用固定的模型响应和人工证据输入，指标不能代表模型或生产检索整体质量。完整基准语料和真实模型成本仍待实际采样。
