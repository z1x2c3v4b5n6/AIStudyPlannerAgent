package com.yhk.aistudyplanner.ai.vo;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

public record NaturalLanguagePlanParseView(
    LocalDate planDate,
    LocalTime startTime,
    Integer availableMinutes,
    String requirement,
    List<String> preferences,
    List<String> topicKeywords,
    List<CandidateSubjectView> candidateSubjects,
    List<String> ambiguousTopics,
    boolean needsSubjectSelection,
    List<Long> selectedSubjectIds,
    List<String> unmatchedKeywords,
    boolean needsClarification,
    String clarificationMessage,
    boolean aiParsed) {}
