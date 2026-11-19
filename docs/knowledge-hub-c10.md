# C-10 动态知识中心

C-10 增加文档证据层。原有 Java Backend 和 AI Application 知识包、C-4 检索器及 Learning Path 的上下文格式继续运行。登录后可从侧栏打开「知识中心」。后端 API 前缀为 `/api/v1/knowledge-hub`。

## 数据与决策流

| Flyway V9 表 | 职责 |
| --- | --- |
| `knowledge_source` | 审核人员登记受支持的官方 HTTPS 入口，保存领域、软件版本和最近采集状态 |
| `knowledge_document` | 以公共或账号所有权隔离资料，保存原始链接、稳定的可选 KnowledgeUnit ID 和当前版本 |
| `knowledge_document_version` | 保存正文、软件版本、采集时间、导入方式、内容 SHA-256 和生命周期 |
| `knowledge_chunk` | 保存按标题切分的文本、顺序和清洗后正文起始行 |
| `knowledge_publication` | 记录审批、拒绝、发布、归档和回滚的操作者、版本、哈希及理由 |
| `knowledge_feedback` | 记录对公开已发布证据的分类反馈，同一账号对同一版本最多一次 |

<table>
<thead><tr><th>状态</th><th>可见范围</th></tr></thead>
<tbody>
<tr><td>待审核、批准、拒绝、归档、历史版本</td><td>公共候选仅审核人员可见</td></tr>
<tr><td>当前已发布公共证据</td><td>已登录用户可见</td></tr>
<tr><td>个人资料</td><td>仅所有人可见，公共检索、审核界面和 Agent 工具均不纳入</td></tr>
</tbody>
</table>

文档分块和版本保存处于同一事务。发布端点先验证角色、版本状态、来源和分块，再锁定文档记录并切换当前版本，同时写入审计。检索直接读取事务提交后的当前版本和分块，因此没有独立索引服务的双写窗口。回滚将此前已发布且已被替换的版本重新设为当前版本。所有高影响切换需要明确操作和理由。软件版本不同的公开证据采用独立官方入口并存，不按照标题自动合并。

## 本地与在线检索

页面默认查公共本地知识。结构化结果使用原有 C-4 检索器；证据分块使用限定领域、软件版本、来源、采集日期及所有权的查询，返回匹配术语与引用信息。正文以纯文本展示。行号指向保存的清洗后文本。Agent 新增 `search_published_evidence`，只返回公共文档证据。原工具 `search_knowledge` 与现有 Learning Path RAG 继续使用内置知识。

用户明确勾选在线检查且出现最新版本请求、本地缺失或指定版本未匹配时，`RegisteredSourceSearch` 从管理员登记的官方入口中选择最多两个。它不会发起通用互联网搜索，也不会把用户的查询文本发送给来源。暂时获取的内容只返回到本次查询。Redis 对同一用户的在线查询间隔限流 30 秒。默认在线关闭，并返回明确的关闭、限流、无已知来源或无法取得证据的状态。

采集仅支持预置域名及其技术文档路径，要求 HTTPS、拒绝重定向和查询参数，限制响应时间、体积、类型、并发和字符编码。使用 Jsoup 做服务端 HTML 结构清洗，保留标题、列表、代码和表格，并丢弃脚本和导航等区域。Jsoup 是此次新增的 Java 依赖。上线时可通过独立出站网络规则限制官方站点。管理员须确认文档允许采集和使用，再登记入口。无授权内容可用手工授权文本替换试验数据。

## 审核和反馈

在环境中设置以下开关，配置变更后重启应用。

| 设置 | 默认 | 作用 |
| --- | --- | --- |
| `APP_KNOWLEDGE_REVIEWERS` | 空 | 逗号分隔的允许审核的应用账户数字 ID |
| `APP_KNOWLEDGE_ONLINE_ENABLED` | false | 允许实时检查登记的官方入口 |
| `APP_KNOWLEDGE_REFRESH_ENABLED` | false | 定时重查已有文档的来源，每轮最多两个 |
| `APP_KNOWLEDGE_RECHECK_HOURS` | 168 | 复查间隔，范围 1–720 小时 |
| `APP_KNOWLEDGE_EXTRACTION_ENABLED` | false | 可选启用模型逐字引用提取预览 |
| `APP_KNOWLEDGE_FEEDBACK_THRESHOLD` | 3 | 触发复核信号的最少独立账号数 |

新正文按哈希去重；正文变化会创建待审核版本，管理员可预览增删分块。模型开启后只能提出已给定分块中的逐字证据，校验失败无结果，也没有发布权限。反馈按当前版本、归一化的技术环境和问题类型分组，与原始问题文本无关。在最近 30 天超过门槛才产生复核信号，缺少环境的反馈不计入该信号。反馈不会覆盖公开证据。

## 复现

运行 `mvn -f backend/pom.xml clean test` 和 `mvn -f backend/pom.xml package -DskipTests`，再运行 `npm --prefix frontend run test:progress` 与 `npm --prefix frontend run build`。使用 C-8 文档中的隔离 MySQL、Redis、Node 和 Playwright 环境，执行 `node tests/c8-e2e.mjs`。C-10 用例代码位于 `tests/c10-e2e.mjs`，覆盖原有知识、本地回退、发布前不可见、分类反馈聚合、官方样例差异、历史版本回滚、双账号私有隔离和浏览器检索。用例使用固定技术文本和隔离数据库生成的测试数据。CI 的模型 HTTP 仍使用 fixture。

采集器探针可通过 `KNOWLEDGE_LIVE_CHECK=true mvn -f backend/pom.xml -Dtest=KnowledgeLiveSourceTest test` 显式运行。受控出网环境可通过 JVM HTTPS 代理和其受信证书配置运行探针。对真实联网做独立联调时，可授权测试账号并开启在线开关，在「来源与审核」登记允许采集的官方页面，先检查候选来源，再从检索页选择在线检查。请验证实际时间、URL、软件版本与引用片段，并确认在线证据没有成为正式版本。真实模型需已有的 DeepSeek 服务配置，以及单独开启提取开关。

## 已知范围

- 个人资料支持 UTF-8 Markdown 和 TXT，暂无 PDF、独立重建索引按钮或个人版本编辑。
- 官方站点搜索通过登记入口完成，暂无付费搜索提供商、复用在线结果缓存或 HTTP 条件请求。来源按统一配置周期复查，反馈聚合由审核人处理。
- 已发布证据和在线候选会带引用返回，暂无基于两路证据的通用生成答案，也未将文档正文加入现有学习路径 Prompt。
- 当前排序扫描匹配候选并限定返回数量，暂未做大规模语料压测或真实在线检索与真实 DeepSeek 的效果评估。E2E 输出 `c10-evidence-smoke-v1`，逐条记录样例引用命中、三条负例的误返回和同机 HTTP 延迟，样本量仅用于回归。仍缺独立的生成质量评估集。
