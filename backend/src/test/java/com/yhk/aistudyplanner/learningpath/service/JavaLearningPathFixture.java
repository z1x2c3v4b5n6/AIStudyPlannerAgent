package com.yhk.aistudyplanner.learningpath.service;

final class JavaLearningPathFixture {
    private JavaLearningPathFixture() {}

    static String modelJson() {
        return """
                {
                  "title":"Java后端面试学习路径",
                  "summary":"面向Java基础一般学习者的10天循序渐进复习路径",
                  "stages":[
                    {"stageNo":1,"title":"Java语言基础","description":"建立语言与面向对象基础","items":[
                      {"sequenceNo":1,"topic":"面向对象","learningObjective":"理解封装、继承、多态及Java对象模型的核心概念","learningMethod":["阅读封装继承多态的核心概念","编写Person和Student继承示例","脱稿对比重载与重写"],"completionCriteria":["能解释封装的作用并举例","能区分重载和重写","能独立完成一个继承示例"],"estimatedMinutes":120,"suggestedDay":1,"prerequisite":null,"reason":"后续集合和框架依赖面向对象基础"}
                    ]},
                    {"stageNo":2,"title":"Java集合","description":"掌握常用集合与底层机制","items":[
                      {"sequenceNo":2,"topic":"HashMap","learningObjective":"理解JDK8 HashMap数据结构、put/get流程与扩容机制","learningMethod":["学习数组链表红黑树结构","编写put和get示例","分析一次put执行流程","完成三道HashMap面试题"],"completionCriteria":["能解释JDK8 HashMap基本结构","能说明put流程和扩容条件","能说明HashMap非线程安全原因"],"estimatedMinutes":120,"suggestedDay":3,"prerequisite":"面向对象","reason":"HashMap是Java集合和面试的核心知识点"}
                    ]},
                    {"stageNo":3,"title":"Java并发","description":"建立线程安全与并发工具能力","items":[
                      {"sequenceNo":3,"topic":"线程池","learningObjective":"理解线程池核心参数、任务提交流程和拒绝策略","learningMethod":["梳理线程池七个核心参数","运行不同队列配置示例","模拟触发拒绝策略","复述任务提交流程"],"completionCriteria":["能解释线程池核心参数作用","能选择合适拒绝策略","能说明不建议直接使用Executors的原因"],"estimatedMinutes":120,"suggestedDay":6,"prerequisite":"面向对象、线程基础","reason":"线程池是Java并发实践和面试重点"}
                    ]},
                    {"stageNo":4,"title":"JVM与面试复习","description":"串联JVM知识并完成综合复盘","items":[
                      {"sequenceNo":4,"topic":"JVM垃圾回收","learningObjective":"理解JVM内存区域、对象回收判定和常见垃圾收集器","learningMethod":["绘制JVM内存区域图","对比引用计数与可达性分析","整理常见收集器特点","完成一次模拟面试复述"],"completionCriteria":["能画出JVM主要内存区域","能解释可达性分析","能比较常见垃圾收集器适用场景"],"estimatedMinutes":120,"suggestedDay":9,"prerequisite":"面向对象","reason":"形成Java后端面试所需的JVM知识闭环"}
                    ]}
                  ]
                }
                """;
    }
}
