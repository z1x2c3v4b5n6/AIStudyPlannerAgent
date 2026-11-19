# C-5 语义与混合检索评估

**结论为维持 C-4 默认关键词检索。** 等权RRF在独立final正例上有收益，但负例返回率从0升至0.5，未达到预先规定的采用门槛。量化多语言MiniLM提供了可用的语义证据，也带来了无关内容的高分匹配。

实验运行于Linux x86_64 CPU，ONNX单线程，Python3.11。原30条与新增final分别报告。表格所有指标为宏平均，Precision分母沿用实际返回数量。final有16条正例和4条负例，负例单独计分。

| 数据 | 算法 | K | Hit | Precision | Recall | MRR | 多主题覆盖 |
|---|---|---:|---:|---:|---:|---:|---:|
| legacy | keyword | 3 | 0.8333 | 0.4722 | 0.7806 | 0.7500 | 0.6019 |
| legacy | keyword | 8 | 0.8333 | 0.3872 | 0.8167 | 0.7500 | 0.7222 |
| legacy | semantic | 3 | 0.8000 | 0.3222 | 0.7278 | 0.7056 | 0.5370 |
| legacy | semantic | 8 | 0.9000 | 0.2172 | 0.8583 | 0.7289 | 0.7500 |
| legacy | hybrid | 3 | 0.8333 | 0.3556 | 0.7889 | 0.8000 | 0.6296 |
| legacy | hybrid | 8 | 0.9000 | 0.2106 | 0.8750 | 0.8125 | 0.8056 |
| final | keyword | 3 | 0.5625 | 0.4583 | 0.4167 | 0.5625 | 0.6111 |
| final | keyword | 8 | 0.5625 | 0.4182 | 0.4167 | 0.5625 | 0.6111 |
| final | semantic | 3 | 0.6250 | 0.2604 | 0.4792 | 0.4583 | 0.6111 |
| final | semantic | 8 | 0.8750 | 0.2063 | 0.7917 | 0.4991 | 0.7778 |
| final | hybrid | 3 | 0.6875 | 0.2917 | 0.5417 | 0.5833 | 0.6111 |
| final | hybrid | 8 | 0.9375 | 0.2375 | 0.8542 | 0.6226 | 0.7778 |
| JAVA_BACKEND | keyword | 3 | 0.7222 | 0.3889 | 0.6806 | 0.6852 | 0.6250 |
| JAVA_BACKEND | keyword | 8 | 0.7222 | 0.3088 | 0.6944 | 0.6852 | 0.7500 |
| JAVA_BACKEND | semantic | 3 | 0.6111 | 0.2222 | 0.5926 | 0.5463 | 0.3333 |
| JAVA_BACKEND | semantic | 8 | 0.8333 | 0.1500 | 0.7917 | 0.5825 | 0.6250 |
| JAVA_BACKEND | hybrid | 3 | 0.6667 | 0.2685 | 0.6667 | 0.6389 | 0.5000 |
| JAVA_BACKEND | hybrid | 8 | 0.8889 | 0.1690 | 0.8472 | 0.6696 | 0.6250 |
| AI_APPLICATION | keyword | 3 | 0.7083 | 0.4931 | 0.6528 | 0.6319 | 0.6296 |
| AI_APPLICATION | keyword | 8 | 0.7083 | 0.4358 | 0.6875 | 0.6319 | 0.7222 |
| AI_APPLICATION | semantic | 3 | 0.7917 | 0.3403 | 0.6944 | 0.6806 | 0.6296 |
| AI_APPLICATION | semantic | 8 | 0.9167 | 0.2538 | 0.8750 | 0.7097 | 0.7778 |
| AI_APPLICATION | hybrid | 3 | 0.8333 | 0.3681 | 0.7569 | 0.7708 | 0.6852 |
| AI_APPLICATION | hybrid | 8 | 0.9167 | 0.2521 | 0.8958 | 0.7896 | 0.8333 |

JAVA_BACKEND/AI_APPLICATION表格统计原案例与final中显式指定单领域的案例。跨领域及无领域案例在legacy/final总表中计分。

## 性能与开销

| 算法 | 查询P50 ms | 查询P95 ms |
|---|---:|---:|
| keyword | 2.941 | 6.045 |
| semantic | 4.983 | 8.105 |
| hybrid | 8.619 | 12.348 |

Hybrid采用Java关键词延迟加Python语义延迟和融合时间的串行估算，未测量真实跨进程服务开销。仅对这些短查询采样；并发和端到端Learning Path延迟需单独测量。

- 模型权重 118,453,870 字节，tokenizer约9.08MB。
- 模型初始化 2.441 秒。
- 55条知识的冷索引构建及写缓存 1.519 秒。
- 向量内存 84,480 字节，压缩缓存 79,213 字节。
- 再运行热缓存读取与校验 0.004241 秒，cacheHit=True。查询每次仍执行Embedding。

## C-4五条完全漏召回的结果

单元格列出期望ID的名次，—表示Top-8之外。

| 案例 | 相关ID | keyword | semantic | hybrid |
|---|---|---|---|---|
| java-007 | java-volatile | — | — | — |
| ai-010 | rag-vector | — | 5 | 5 |
| ai-010 | rag-vector-retrieval | — | 1 | 2 |
| ai-011 | rag-context-injection | — | — | — |
| ai-012 | rag-architecture | — | — | — |
| ai-012 | rag-context-injection | — | — | — |
| ai-012 | rag-vector-retrieval | — | — | — |
| ai-015 | ai-tool-function-calling | — | 4 | 4 |

模型对向量检索意图有帮助，但跨线程可见性、函数调用和“基于自己的资料回答”等意图仍不稳定。java-007的语义首位为CompletableFuture，ai-015首位为Retry/Fallback，显示相似度不足以可靠识别技术意图。

## 退化、误召回与限制

- keyword 原本Top-3命中转为未命中 []，Top-8退化 []。
- semantic 原本Top-3命中转为未命中 ['java-012', 'ai-014', 'final-01']，Top-8退化 ['final-01']。
- hybrid 原本Top-3命中转为未命中 ['java-012']，Top-8退化 []。
- final负例中semantic及hybrid均返回了final-19和final-20的结果，keyword均为空。通用阈值0.30不足以识别分布外输入。
- java-012的多主题查询在hybrid的Top-3发生退化，即使总体MRR提升，仍存在单例损失。融合可能把共同支持的局部主题推高，无法保证主题多样性。
- final多主题覆盖在Top-3上keyword与hybrid均为0.6111，Top-3的平均收益没有覆盖到所有多意图场景。
- 文本拼接采用128 token截断，后置字段可能丢失。未评估其他模型、未校准语义置信度、未搜索RRF参数。更大的模型或不同字段编码可能改变结论，当前结果只适用于这个固定模型和配置。
- final仅16条正例、4条负例，且由开发者编写及标注。尚无独立标注者或统计显著性证据。protocol和数据哈希在首次final运行前固定，首次最终结果用于报告；热缓存复跑只检查数值一致性与性能。

## 全部未完整召回案例

### keyword

- java-007，query=一个线程修改变量后怎样让其他线程立即看见，Top-8缺失=['java-volatile']，实际=['java-thread']。
- java-012，query=准备Java后端面试，想系统复习集合、并发、JVM和Spring，Top-8缺失=['java-collection-overview', 'java-thread-pool']，实际=['java-concurrent-hashmap', 'spring-aop', 'spring-boot', 'spring-ioc', 'spring-mvc', 'spring-transaction', 'jvm-gc', 'jvm-memory']。
- ai-010，query=怎样从大量向量中找到语义最相似的资料，Top-8缺失=['rag-vector', 'rag-vector-retrieval']，实际=['rag-embedding']。
- ai-011，query=怎样让大模型只根据检索资料回答，Top-8缺失=['rag-context-injection']，实际=['llm-structured-output', 'llm-temperature', 'llm-token-context']。
- ai-012，query=我想让大模型结合自己的资料回答问题，Top-8缺失=['rag-architecture', 'rag-context-injection', 'rag-vector-retrieval']，实际=['llm-structured-output', 'llm-temperature', 'llm-token-context']。
- ai-015，query=模型怎么调用后端函数，Top-8缺失=['ai-tool-function-calling']，实际=[]。
- final-02，query=往列表遍历的时候删元素为什么会ConcurrentModificationException，Top-8缺失=['java-iterator-collections']，实际=[]。
- final-03，query=子类的方法在运行时被调用，这个现象怎么解释，Top-8缺失=['java-oop']，实际=['jvm-memory']。
- final-04，query=程序不停创建短命对象，怎么判断哪些对象还能活着，Top-8缺失=['jvm-gc']，实际=[]。
- final-06，query=两个服务都要用一个对象，交给容器负责组装依赖，Top-8缺失=['spring-ioc']，实际=[]。
- final-08，query=回答总是变花样，想调低temperature并固定输出字段，Top-8缺失=['llm-structured-output']，实际=['llm-temperature']。
- final-09，query=文档被截成小段后如何保留前后衔接的内容，Top-8缺失=['rag-document-chunk']，实际=[]。
- final-10，query=模型给出了不存在的字段，后端要拒绝这份响应，Top-8缺失=['ai-output-validation']，实际=[]。
- final-11，query=机器人执行失败后怎么修正剩下的步骤，Top-8缺失=['agent-replanning']，实际=[]。
- final-13，query=给自动化程序删库权限之前需要审批和撤销机制，Top-8缺失=['ai-tool-function-calling']，实际=['agent-human-loop']。
- final-14，query=先理解线程池，再学习LLM请求的timeout重试策略，Top-8缺失=['ai-retry-fallback']，实际=['java-thread-pool', 'llm-structured-output', 'llm-temperature', 'llm-token-context', 'java-thread', 'java-completable-future']。
- final-15，query=JVM class loading和Spring自动配置怎么联系起来，Top-8缺失=['jvm-class-loading']，实际=['spring-boot', 'spring-aop', 'spring-ioc', 'spring-mvc', 'spring-transaction', 'java-annotations-reflection', 'jvm-gc', 'jvm-memory']。
- final-16，query=从PDF切块到向量编码再到检索资料，需要哪些步骤，Top-8缺失=['rag-vector-retrieval']，实际=['rag-document-chunk', 'rag-embedding']。
### semantic

- java-007，query=一个线程修改变量后怎样让其他线程立即看见，Top-8缺失=['java-volatile']，实际=['java-completable-future', 'java-juc-tools']。
- java-012，query=准备Java后端面试，想系统复习集合、并发、JVM和Spring，Top-8缺失=['java-collection-overview', 'jvm-gc', 'spring-boot']，实际=['spring-transaction', 'jvm-memory', 'jvm-class-loading', 'spring-aop', 'java-thread-pool', 'java-thread', 'java-synchronized', 'java-map']。
- ai-011，query=怎样让大模型只根据检索资料回答，Top-8缺失=['rag-context-injection']，实际=['rag-rerank', 'rag-vector-retrieval', 'prompt-few-shot', 'agent-human-loop', 'rag-architecture', 'agent-planning', 'rag-vector', 'agent-replanning']。
- ai-012，query=我想让大模型结合自己的资料回答问题，Top-8缺失=['rag-architecture', 'rag-context-injection', 'rag-vector-retrieval']，实际=['llm-structured-output', 'rag-rerank', 'agent-planning', 'agent-replanning', 'prompt-system', 'ai-tool-function-calling', 'ai-output-validation', 'agent-human-loop']。
- ai-017，query=智能体如何编排多个步骤并根据结果继续执行，Top-8缺失=['agent-workflow']，实际=['agent-planning', 'agent-replanning', 'agent-human-loop', 'ai-tool-function-calling', 'ai-retry-fallback', 'prompt-few-shot', 'rag-rerank', 'ai-output-validation']。
- final-01，query=数据库拿到的空Integer赋值给int为什么报错，Top-8缺失=['java-types-wrappers']，实际=['java-exceptions']。
- final-04，query=程序不停创建短命对象，怎么判断哪些对象还能活着，Top-8缺失=['jvm-gc']，实际=['java-thread', 'java-iterator-collections', 'java-annotations-reflection', 'java-arraylist', 'java-oop', 'jvm-memory', 'jvm-class-loading', 'java-exceptions']。
- final-08，query=回答总是变花样，想调低temperature并固定输出字段，Top-8缺失=['llm-structured-output']，实际=['llm-temperature', 'ai-retry-fallback']。
- final-13，query=给自动化程序删库权限之前需要审批和撤销机制，Top-8缺失=['ai-tool-function-calling']，实际=['prompt-constraints', 'agent-replanning', 'agent-human-loop', 'java-iterator-collections', 'ai-retry-fallback', 'java-thread-pool', 'java-exceptions', 'java-generics']。
- final-16，query=从PDF切块到向量编码再到检索资料，需要哪些步骤，Top-8缺失=['rag-vector-retrieval']，实际=['rag-document-chunk', 'rag-architecture', 'rag-embedding', 'rag-rerank', 'rag-vector']。
### hybrid

- java-007，query=一个线程修改变量后怎样让其他线程立即看见，Top-8缺失=['java-volatile']，实际=['java-completable-future', 'java-thread', 'java-juc-tools']。
- java-012，query=准备Java后端面试，想系统复习集合、并发、JVM和Spring，Top-8缺失=['java-collection-overview', 'java-thread-pool', 'jvm-gc']，实际=['spring-aop', 'spring-transaction', 'jvm-memory', 'jvm-class-loading', 'spring-mvc', 'spring-ioc', 'java-thread', 'spring-boot']。
- ai-011，query=怎样让大模型只根据检索资料回答，Top-8缺失=['rag-context-injection']，实际=['llm-temperature', 'llm-structured-output', 'rag-rerank', 'rag-vector-retrieval', 'llm-token-context', 'prompt-few-shot', 'agent-human-loop', 'rag-architecture']。
- ai-012，query=我想让大模型结合自己的资料回答问题，Top-8缺失=['rag-architecture', 'rag-context-injection', 'rag-vector-retrieval']，实际=['llm-structured-output', 'llm-temperature', 'rag-rerank', 'agent-planning', 'llm-token-context', 'agent-replanning', 'prompt-system', 'ai-tool-function-calling']。
- final-04，query=程序不停创建短命对象，怎么判断哪些对象还能活着，Top-8缺失=['jvm-gc']，实际=['java-thread', 'java-iterator-collections', 'java-annotations-reflection', 'java-arraylist', 'java-oop', 'jvm-memory', 'jvm-class-loading', 'java-exceptions']。
- final-08，query=回答总是变花样，想调低temperature并固定输出字段，Top-8缺失=['llm-structured-output']，实际=['llm-temperature', 'ai-retry-fallback']。
- final-13，query=给自动化程序删库权限之前需要审批和撤销机制，Top-8缺失=['ai-tool-function-calling']，实际=['agent-human-loop', 'prompt-constraints', 'agent-replanning', 'java-iterator-collections', 'ai-retry-fallback', 'java-thread-pool', 'java-exceptions', 'java-generics']。
- final-16，query=从PDF切块到向量编码再到检索资料，需要哪些步骤，Top-8缺失=['rag-vector-retrieval']，实际=['rag-document-chunk', 'rag-embedding', 'rag-architecture', 'rag-rerank', 'rag-vector']。
