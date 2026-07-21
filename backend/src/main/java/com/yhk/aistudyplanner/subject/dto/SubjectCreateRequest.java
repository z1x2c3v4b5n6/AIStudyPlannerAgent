package com.yhk.aistudyplanner.subject.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SubjectCreateRequest(
        @NotBlank(message = "科目名称不能为空") @Size(max = 100, message = "科目名称不能超过100个字符") String name,
        @Size(max = 500, message = "科目描述不能超过500个字符") String description,
        @Size(max = 20, message = "颜色值不能超过20个字符") String color,
        Integer sortOrder
) {
}

