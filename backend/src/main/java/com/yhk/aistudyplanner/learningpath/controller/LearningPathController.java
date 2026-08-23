package com.yhk.aistudyplanner.learningpath.controller;

import com.yhk.aistudyplanner.common.response.*;
import com.yhk.aistudyplanner.learningpath.dto.*;
import com.yhk.aistudyplanner.learningpath.service.LearningPathService;
import com.yhk.aistudyplanner.learningpath.vo.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@Validated @RestController @RequestMapping("/api/v1/learning-paths")
public class LearningPathController {
    private final LearningPathService service; public LearningPathController(LearningPathService service){this.service=service;}
    @GetMapping public ApiResponse<PageResponse<LearningPathListView>> list(@RequestParam(defaultValue="1")@Min(1)long page,@RequestParam(defaultValue="10")@Min(1)@Max(100)long pageSize){return ApiResponse.success(service.list(page,pageSize));}
    @GetMapping("/{id}") public ApiResponse<LearningPathDetailView> get(@PathVariable long id){return ApiResponse.success(service.get(id));}
    @PostMapping("/confirm") public ApiResponse<LearningPathDetailView> confirm(@Valid @RequestBody LearningPathConfirmRequest request){return ApiResponse.success(service.confirm(request));}
    @PostMapping("/{pathId}/items/{itemId}/create-task") public ApiResponse<LearningPathTaskView> createTask(@PathVariable long pathId,@PathVariable long itemId){return ApiResponse.success(service.createTask(pathId,itemId));}
    @PatchMapping("/{pathId}/status") public ApiResponse<LearningPathDetailView> status(@PathVariable long pathId,@Valid @RequestBody LearningPathStatusRequest request){return ApiResponse.success(service.changeStatus(pathId,request));}
    @PatchMapping("/{pathId}/items/{itemId}/status") public ApiResponse<LearningPathDetailView> itemStatus(@PathVariable long pathId,@PathVariable long itemId,@Valid @RequestBody LearningPathItemStatusRequest request){return ApiResponse.success(service.changeItemStatus(pathId,itemId,request));}
}
