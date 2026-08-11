package com.yhk.aistudyplanner.ai.vo;

public record QuickCreatePlanTaskView(
    Long subjectId,
    String subjectName,
    Long taskId,
    String taskTitle,
    boolean createdSubject,
    boolean createdTask) {}
