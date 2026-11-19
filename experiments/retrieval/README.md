# C-5 本地语义与混合检索实验

这是独立的离线 Python 3.11 实验。Java 后端通过显式启用的测试导出真实 C-4 排名。应用运行时继续调用 C-4，不加载模型、不启动 Python 进程。

## 环境与复现

需要 JDK17、Maven、Python3.11、约1GB可用内存和约500MB可用磁盘。Windows 可以使用 conda，命令在仓库根目录运行。

```sh
conda create -n retrieval-c5 python=3.11 -y
conda activate retrieval-c5
pip install -r experiments/retrieval/requirements.txt
python experiments/retrieval/download_model.py experiments/retrieval/model
cd backend
mvn clean test -Dretrieval.export=true
mvn package -DskipTests
cd ..
python experiments/retrieval/run.py --model-dir experiments/retrieval/model --split dev --output experiments/retrieval/dev-results.json
python experiments/retrieval/run.py --model-dir experiments/retrieval/model
python experiments/retrieval/run.py --model-dir experiments/retrieval/model --mode keyword --output backend/target/keyword-only.json
python experiments/retrieval/run.py --model-dir experiments/retrieval/model --mode semantic --output backend/target/semantic-only.json
python experiments/retrieval/run.py --model-dir experiments/retrieval/model --mode hybrid --output backend/target/hybrid-only.json
cd experiments/retrieval
python -m unittest -v
```

首次安装和下载需要网络。之后将 conda 环境、模型目录与仓库复制到离线机器，即可运行；离线安装依赖时先在同平台准备 `pip download -r requirements.txt -d wheels`，再用 `pip install --no-index --find-links wheels -r requirements.txt`。模型下载只在显式执行下载脚本时发生。运行器仅打开本地 ONNX 和 tokenizer 文件。语义模式可独立于 Java 导出运行，关键词和混合模式需要先生成 `backend/target/retrieval-keyword.json`。

量化模型为 [paraphrase-multilingual-MiniLM-L12-v2](https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2)，固定提交 `e8f8c211226b894fcb81acc59f3b34ba3efd5f42` 的 `onnx/model_quint8_avx2.onnx`。输出384维，mask mean pooling 后归一化。模型与 tokenizer 的 SHA-256 见 `model-manifest.json`。量化AVX2文件面向x86 CPU，其他架构需要独立验证并重新锁定模型文件。

## 配置与缓存

`--config` 指定融合权重、RRF常数、相似度阈值、候选池和CPU线程。每个RRF结果都记录各来源名次与分数贡献。同分以知识ID排序。仅keyword与semantic排名各自前20条参与融合，最终返回前8条。

缓存元数据包含模型文件哈希、revision、维度、最大长度、文本格式版本与整个知识包指纹。任何一项变化会重新生成。缓存通过同目录临时文件原子替换，读取时检查shape、有限值及非零范数；损坏缓存会重算。模型文件不符、模型缺失、推理错误会让命令失败，模型文件和向量缓存均不提交到Git。

`protocol.md` 定义评估规则和采用门槛，`evaluation-lock.json` 保存首次最终评估前的数据与配置哈希。8条dev和20条final均为手工编写的新场景，final含16条正例及4条负例。只有原30条可直接与C-4对照，新增案例有主观标注局限。无参数搜索；dev暴露出误召回后保留初始配置，将其作为失败实验交付。

## 测试与结果

完整结果、逐案例排名及RRF解释见 `results.json`，dev见 `dev-results.json`。分析见 [C-5评估报告](../../docs/rag-retrieval-c5.md)。

本地 `mvn clean test -Dretrieval.export=true` 236项通过，含原235项测试和显式导出测试，`mvn package -DskipTests`通过。Python另有5项数值、RRF、过滤、缓存与指标测试通过。普通 `mvn clean test` 跳过导出测试；实验启用由系统属性控制。沙箱测试使用预加载Byte Buddy agent解决动态附加限制，无须修改pom或应用参数。
