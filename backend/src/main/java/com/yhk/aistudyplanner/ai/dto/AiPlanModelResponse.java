package com.yhk.aistudyplanner.ai.dto;import java.util.List;
public record AiPlanModelResponse(String summary,List<Item> items){public record Item(Long taskId,Integer plannedMinutes,String reason){}}
