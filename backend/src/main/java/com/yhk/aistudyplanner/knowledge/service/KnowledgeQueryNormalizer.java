package com.yhk.aistudyplanner.knowledge.service;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

final class KnowledgeQueryNormalizer {
    private static final Pattern SEPARATORS = Pattern.compile("[\\p{P}\\p{S}\\s\\p{Z}]+");
    private static final List<Synonym> SYNONYMS = List.of(
            synonym("rag", "retrieval augmented generation", "检索增强生成"),
            synonym("llm", "large language model", "大语言模型", "大模型"),
            synonym("system prompt", "system message", "系统提示词"),
            synonym("embedding", "embeddings", "向量嵌入", "文本向量", "向量表示"),
            synonym("vector retrieval", "similarity search", "向量检索", "语义检索", "相似度搜索"),
            synonym("chunk", "chunking", "文档切块", "文本分块", "分段切块", "切块", "分块"),
            synonym("rerank", "reranker", "重排序", "检索重排", "重排"),
            synonym("context injection", "上下文注入", "上下文组装", "context组装"),
            synonym("context window", "上下文窗口"),
            synonym("structured output", "structured response", "结构化输出", "结构化响应", "结构化结果"),
            synonym("tool calling", "function calling", "工具调用", "函数调用"),
            synonym("tool use", "工具使用", "使用工具"),
            synonym("agent", "智能体"),
            synonym("human in the loop", "hitl", "人工确认", "人在回路"),
            synonym("spring boot", "springboot"),
            synonym("concurrenthashmap", "concurrent hash map", "并发map", "线程安全map"),
            synonym("hashmap", "hash map"),
            synonym("gc", "garbage collection", "垃圾回收"),
            synonym("jvm", "java virtual machine", "java虚拟机"),
            synonym("dependency injection", "依赖注入"),
            synonym("synchronized", "java内置锁", "内置锁", "同步锁"),
            synonym("visibility", "内存可见性", "可见性"),
            synonym("work queue", "工作队列", "任务队列"));

    NormalizedQuery normalize(String original) {
        String surface = surface(original);
        return new NormalizedQuery(original, surface, canonicalize(surface));
    }

    static String surface(String text) {
        if (text == null) {
            return "";
        }
        String lower = Normalizer.normalize(text, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        return SEPARATORS.matcher(lower).replaceAll(" ").strip();
    }

    static boolean containsPhrase(String text, String phrase) {
        if (phrase.isEmpty()) {
            return false;
        }
        for (int index = text.indexOf(phrase); index >= 0; index = text.indexOf(phrase, index + 1)) {
            int end = index + phrase.length();
            boolean startsInsideWord = index > 0 && asciiWord(phrase.charAt(0)) && asciiWord(text.charAt(index - 1));
            boolean endsInsideWord = end < text.length() && asciiWord(phrase.charAt(phrase.length() - 1))
                    && asciiWord(text.charAt(end));
            if (!startsInsideWord && !endsInsideWord) {
                return true;
            }
        }
        return false;
    }

    private static boolean asciiWord(char c) {
        return c >= 'a' && c <= 'z' || c >= '0' && c <= '9';
    }

    private String canonicalize(String text) {
        for (Synonym synonym : SYNONYMS) {
            text = synonym.variants().matcher(text).replaceAll(" " + synonym.canonical() + " ");
        }
        return SEPARATORS.matcher(text).replaceAll(" ").strip();
    }

    private static Synonym synonym(String canonical, String... variants) {
        String alternatives = Arrays.stream(variants)
                .map(variant -> (asciiWord(variant.charAt(0)) ? "(?<![a-z0-9])" : "")
                        + Pattern.quote(variant)
                        + (asciiWord(variant.charAt(variant.length() - 1)) ? "(?![a-z0-9])" : ""))
                .collect(Collectors.joining("|"));
        return new Synonym(canonical, Pattern.compile(alternatives));
    }

    record NormalizedQuery(String original, String surface, String canonical) {}

    private record Synonym(String canonical, Pattern variants) {}
}
