package com.yhk.aistudyplanner.ai.vo;

public record CandidateSubjectView(
    Long subjectId,
    String subjectName,
    String subjectColor,
    long pendingTaskCount,
    String matchReason,
    String matchLevel,
    boolean recommended) {}
