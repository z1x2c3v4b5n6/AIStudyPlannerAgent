package com.yhk.aistudyplanner.task.controller;

import com.yhk.aistudyplanner.common.response.ApiResponse;
import com.yhk.aistudyplanner.common.response.PageResponse;
import com.yhk.aistudyplanner.task.dto.*;
import com.yhk.aistudyplanner.task.entity.TaskStatus;
import com.yhk.aistudyplanner.task.service.TaskService;
import com.yhk.aistudyplanner.task.vo.TaskView;
import com.yhk.aistudyplanner.task.vo.TodayTasksView;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Validated
@RestController
@RequestMapping("/api/v1/tasks")
public class TaskController {
    private final TaskService taskService;

    public TaskController(TaskService taskService) { this.taskService = taskService; }

    @GetMapping
    public ApiResponse<PageResponse<TaskView>> list(
            @RequestParam(defaultValue = "1") @Min(1) long page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) long pageSize,
            @RequestParam(required = false) Long subjectId,
            @RequestParam(required = false) Long goalId,
            @RequestParam(required = false) TaskStatus status,
            @RequestParam(required = false) @Min(1) @Max(4) Integer priority,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate plannedDate) {
        return ApiResponse.success(taskService.list(page, pageSize, subjectId, goalId, status, priority, plannedDate));
    }

    @GetMapping("/today")
    public ApiResponse<TodayTasksView> today(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ApiResponse.success(taskService.today(date));
    }

    @GetMapping("/upcoming")
    public ApiResponse<List<TaskView>> upcoming(@RequestParam(defaultValue = "7") @Min(1) @Max(30) int days) {
        return ApiResponse.success(taskService.upcoming(days));
    }

    @GetMapping("/{id}")
    public ApiResponse<TaskView> get(@PathVariable long id) { return ApiResponse.success(taskService.get(id)); }

    @PostMapping
    public ApiResponse<TaskView> create(@Valid @RequestBody TaskCreateRequest request) {
        return ApiResponse.success(taskService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<TaskView> update(@PathVariable long id, @Valid @RequestBody TaskUpdateRequest request) {
        return ApiResponse.success(taskService.update(id, request));
    }

    @PatchMapping("/{id}/status")
    public ApiResponse<TaskView> status(@PathVariable long id, @Valid @RequestBody TaskStatusRequest request) {
        return ApiResponse.success(taskService.changeStatus(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id) {
        taskService.delete(id);
        return ApiResponse.success(null);
    }
}
