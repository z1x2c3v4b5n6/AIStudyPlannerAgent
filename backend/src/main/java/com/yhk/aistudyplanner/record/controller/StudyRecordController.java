package com.yhk.aistudyplanner.record.controller;

import com.yhk.aistudyplanner.common.response.ApiResponse;
import com.yhk.aistudyplanner.common.response.PageResponse;
import com.yhk.aistudyplanner.record.dto.RecordCreateRequest;
import com.yhk.aistudyplanner.record.dto.RecordUpdateRequest;
import com.yhk.aistudyplanner.record.service.StudyRecordService;
import com.yhk.aistudyplanner.record.vo.StudyRecordView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@Validated
@RestController
@RequestMapping("/api/v1/records")
public class StudyRecordController {
    private final StudyRecordService recordService;

    public StudyRecordController(StudyRecordService recordService) {
        this.recordService = recordService;
    }

    @GetMapping
    public ApiResponse<PageResponse<StudyRecordView>> list(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long taskId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return ApiResponse.success(recordService.list(page, pageSize, subjectId, taskId, startDate, endDate));
    }

    @GetMapping("/{id}")
    public ApiResponse<StudyRecordView> get(@PathVariable long id) {
        return ApiResponse.success(recordService.get(id));
    }

    @PostMapping
    public ApiResponse<StudyRecordView> create(@Valid @RequestBody RecordCreateRequest request) {
        return ApiResponse.success(recordService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<StudyRecordView> update(@PathVariable long id, @Valid @RequestBody RecordUpdateRequest request) {
        return ApiResponse.success(recordService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        recordService.delete(id);
        return ApiResponse.success(null);
    }
}
