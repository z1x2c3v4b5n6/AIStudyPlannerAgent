package com.yhk.aistudyplanner.subject.controller;

import com.yhk.aistudyplanner.common.response.ApiResponse;
import com.yhk.aistudyplanner.subject.dto.SubjectCreateRequest;
import com.yhk.aistudyplanner.subject.dto.SubjectUpdateRequest;
import com.yhk.aistudyplanner.subject.service.SubjectService;
import com.yhk.aistudyplanner.subject.vo.SubjectView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/subjects")
public class SubjectController {
    private final SubjectService subjectService;

    public SubjectController(SubjectService subjectService) { this.subjectService = subjectService; }

    @GetMapping
    public ApiResponse<List<SubjectView>> list() { return ApiResponse.success(subjectService.list()); }

    @GetMapping("/{id}")
    public ApiResponse<SubjectView> get(@PathVariable long id) { return ApiResponse.success(subjectService.get(id)); }

    @PostMapping
    public ApiResponse<SubjectView> create(@Valid @RequestBody SubjectCreateRequest request) {
        return ApiResponse.success(subjectService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<SubjectView> update(@PathVariable long id, @Valid @RequestBody SubjectUpdateRequest request) {
        return ApiResponse.success(subjectService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        subjectService.delete(id);
        return ApiResponse.success(null);
    }
}
