# C-13 质量评估

## 固定评估与实际运行

`mvn -Dtest=C13TeachingEvaluationTest,C13RubricEvaluationTest test` 在 Java 17 运行冻结夹具。输入是人工编写的参考事实、负例和固定模型回复。输出 JSON 位于 `backend/target`。毫秒数据只计离线测试中的解释处理。模型 Token 与真实延迟状态为 **NOT_RUN**，离线费用为零。

### 解释模式对照

| 模式 | 7 个冻结案例的观察 |
| --- | --- |
| A 原文证据模式 | 7 个引用所选完整原文，0 个展开教学解释。固定片段包含一个版本不适用的测试条件，完整生产检索还会执行版本过滤。 |
| B 受约束教学 | 4 个产出带来源、版本和条件的已审核解释。部分引文、版本不匹配、附加越界结论三条均回退原文。 |
| C 无 RAG 固定参考回复 | 1/7 被人工参考标签记为支持。这些是用于对照的手工回复，实际无 RAG 模型准确率为 NOT_RUN。 |
| D 人工参考 | 为 7 个输入提供参考解释或拒答判断，用于记录依据和预期。 |

| 案例 | B 的实际结果 | 核查内容 |
| --- | --- | --- |
| tx-proxy | 解释 | 代理模式的自调用与外部事务条件 |
| executor-memory | 解释 | 提交、运行、Future.get 的可见性关系 |
| tool-application | 解释 | 应用承担工具执行职责 |
| advisor-retrieval | 解释 | 检索与生成分别评估 |
| partial-reference | 回退 | 部分引文未覆盖自调用前提 |
| version-mismatch | 回退 | 文档软件版本不适用于说明 |
| injected-claim | 回退 | 回复包含格式以外的技术结论 |

### 简答与学习效果

| 固定案例 | 人工覆盖 | 固定模型标记 | 后端接受辅助反馈 | 正式成绩 |
| --- | ---: | ---: | --- | --- |
| 完整代理解释 | 2 | 2 | 是 | 待复核 |
| 遗漏自调用 | 1 | 1 | 是 | 待复核 |
| 引用作答里没有的话 | 0 | 2 | 否 | 待复核 |
| 否定句包含引用短语 | 0 | 1 | 是 | 待复核 |

最后一条保留为误判案例。它说明真实引用还可能曲解句意。简答辅助标记参与展示，正式正确率只采用后端确定性评分。测评回放无法推断真实用户的采纳率、学习收益或生成题难度。知识表现页分别统计复习任务采纳和完成、首次答题与复练以及前后样本量。

### 出题质量

数据库端到端夹具以 Spring 事务文档为源，核对五道草稿、选择题唯一答案、版本、引用、无证据拒绝、复核和发布。Java 单元测试拒绝证据中没有的答案，并覆盖题目指纹的格式归一化。此检查覆盖来源对应关系和结构约束。真实模型的难度、语义重复率和答案准确率仍需逐题人工核验，当前为 **NOT_RUN**。

官方参考 [Spring Framework](https://docs.spring.io/spring/reference/6.2/data-access/transaction/declarative/annotations.html)、[Java SE 17 ExecutorService](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html)、[Spring AI Tool Calling](https://docs.spring.io/spring-ai/reference/api/tools.html)、[Spring AI Advisors](https://docs.spring.io/spring-ai/reference/api/retrieval-augmented-generation.html)。
