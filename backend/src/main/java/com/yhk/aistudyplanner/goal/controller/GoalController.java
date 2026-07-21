package com.yhk.aistudyplanner.goal.controller;

import com.yhk.aistudyplanner.common.response.ApiResponse;
import com.yhk.aistudyplanner.common.response.PageResponse;
import com.yhk.aistudyplanner.goal.dto.*;
import com.yhk.aistudyplanner.goal.entity.GoalStatus;
import com.yhk.aistudyplanner.goal.service.GoalService;
import com.yhk.aistudyplanner.goal.vo.GoalDetailView;
import com.yhk.aistudyplanner.goal.vo.GoalView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated
@RestController
@RequestMapping("/api/v1/goals")
public class GoalController {
    private final GoalService goalService;

    public GoalController(GoalService goalService) { this.goalService = goalService; }

    @GetMapping
    public ApiResponse<PageResponse<GoalView>> list(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) GoalStatus status) {
        return ApiResponse.success(goalService.list(page, pageSize, subjectId, status));
    }

    @GetMapping("/{id}")
    public ApiResponse<GoalDetailView> get(@PathVariable long id) { return ApiResponse.success(goalService.get(id)); }

    @PostMapping
    public ApiResponse<GoalView> create(@Valid @RequestBody GoalCreateRequest request) {
        return ApiResponse.success(goalService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<GoalView> update(@PathVariable long id, @Valid @RequestBody GoalUpdateRequest request) {
        return ApiResponse.success(goalService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<GoalView> status(@PathVariable long id, @Valid @RequestBody GoalStatusRequest request) {
        return ApiResponse.success(goalService.changeStatus(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        goalService.delete(id);
        return ApiResponse.success(null);
    }
}
