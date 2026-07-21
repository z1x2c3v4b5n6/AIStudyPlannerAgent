package com.yhk.aistudyplanner.subject.vo;

import com.yhk.aistudyplanner.subject.entity.Subject;

import java.time.LocalDateTime;

public record SubjectView(Long id, String name, String description, String color, Integer sortOrder,
                          LocalDateTime createdAt, LocalDateTime updatedAt) {
    public static SubjectView from(Subject subject) {
        return new SubjectView(subject.getId(), subject.getName(), subject.getDescription(), subject.getColor(),
                subject.getSortOrder(), subject.getCreatedAt(), subject.getUpdatedAt());
    }
}

