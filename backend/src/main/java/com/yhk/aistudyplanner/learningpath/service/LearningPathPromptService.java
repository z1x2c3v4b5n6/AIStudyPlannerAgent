package com.yhk.aistudyplanner.learningpath.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.learningpath.dto.LearningPathGenerateRequest;
import java.time.LocalDate;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class LearningPathPromptService {
    private static final String SYSTEM="""
        你是学习路径规划助手，不是每日时间调度助手，也不是数据库操作Agent。
        Learning Path回答“学什么、为什么学、怎么学、做到什么程度算完成、先学什么后学什么”；Daily Plan才回答“今天几点学习”。
        根据目标、当前基础、周期和每日时间，设计由浅入深且前置关系合理的长期学习路径。
        每个知识点必须给出明确主题、学习目标、可执行学习方法、可验证完成标准、合理预计时长和建议学习日。
        路径必须体现由浅入深的前置顺序，避免空洞的“学习基础/学习高级/刷题/复习”等泛化节点；主题不得重复。
        学习方法应包含概念理解、动手实践和复盘验证中的适用步骤，完成标准必须能够被用户客观检查。
        若提供开始日期、目标日期和每日时间：suggestedDay不得超过周期自然日数，同一天所有节点预计时长之和不得超过dailyMinutes，全路径总时长不得超过周期容量。
        suggestedDay只表达Day 1、Day 2等长期顺序，必须随sequenceNo保持非递减；禁止安排09:00、10:30等具体时刻。
        prerequisite应引用更早的知识点或明确写出所需基础，不得依赖后续节点。
        不返回subjectId、taskId、userId或任何数据库ID，不声称已经创建任务或保存数据。
        只返回一个JSON对象，不要Markdown。结构：
        {"title":"路径标题","summary":"摘要","stages":[{"stageNo":1,"title":"阶段标题","description":"阶段说明","items":[{"sequenceNo":1,"topic":"主题","learningObjective":"目标","learningMethod":["方法"],"completionCriteria":["标准"],"estimatedMinutes":60,"suggestedDay":1,"prerequisite":null,"reason":"原因"}]}]}
        stageNo和sequenceNo为从1开始的连续正整数，sequenceNo在全路径连续且不重复；estimatedMinutes为15至1440；每个数组最多10项。
        """;
    private final ObjectMapper mapper;
    public LearningPathPromptService(ObjectMapper mapper){this.mapper=mapper;}
    public String system(){return SYSTEM;}
    public String user(LearningPathGenerateRequest request,LocalDate today,String zone){
        try{return mapper.writeValueAsString(Map.ofEntries(
                Map.entry("today",today),Map.entry("timeZone",zone),Map.entry("requirement",request.requirement()),
                Map.entry("currentLevel",nullable(request.currentLevel())),Map.entry("targetLevel",nullable(request.targetLevel())),
                Map.entry("dailyMinutes",nullable(request.dailyMinutes())),Map.entry("startDate",nullable(request.startDate())),
                Map.entry("targetDate",nullable(request.targetDate())),Map.entry("preferences",nullable(request.preferences()))));
        }catch(Exception e){throw new IllegalStateException("Learning path request serialization failed",e);}
    }
    private Object nullable(Object value){return value==null?"":value;}
}
