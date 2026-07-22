package com.yhk.aistudyplanner.plan.controller;
import com.yhk.aistudyplanner.common.response.*; import com.yhk.aistudyplanner.plan.dto.*; import com.yhk.aistudyplanner.plan.entity.PlanStatus; import com.yhk.aistudyplanner.plan.service.StudyPlanService; import com.yhk.aistudyplanner.plan.vo.*;
import jakarta.validation.Valid; import jakarta.validation.constraints.*; import org.springframework.format.annotation.DateTimeFormat; import org.springframework.validation.annotation.Validated; import org.springframework.web.bind.annotation.*; import java.time.LocalDate;

@Validated @RestController @RequestMapping("/api/v1/plans")
public class StudyPlanController {
    private final StudyPlanService service; public StudyPlanController(StudyPlanService service){this.service=service;}
    @PostMapping("/draft") public ApiResponse<PlanDraftView> draft(@Valid @RequestBody PlanDraftRequest r){return ApiResponse.success(service.draft(r));}
    @PostMapping("/confirm") public ApiResponse<PlanDetailView> confirm(@Valid @RequestBody PlanConfirmRequest r){return ApiResponse.success(service.confirm(r));}
    @GetMapping public ApiResponse<PageResponse<PlanListView>> list(@RequestParam(defaultValue="1")@Min(1)long page,@RequestParam(defaultValue="10")@Min(1)@Max(100)long pageSize,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate startDate,@RequestParam(required=false)@DateTimeFormat(iso=DateTimeFormat.ISO.DATE)LocalDate endDate,@RequestParam(required=false)PlanStatus status){return ApiResponse.success(service.list(page,pageSize,startDate,endDate,status));}
    @GetMapping("/{id}") public ApiResponse<PlanDetailView> get(@PathVariable long id){return ApiResponse.success(service.get(id));}
    @PatchMapping("/{id}/status") public ApiResponse<PlanDetailView> status(@PathVariable long id,@Valid @RequestBody PlanStatusRequest r){return ApiResponse.success(service.changeStatus(id,r));}
    @PatchMapping("/{planId}/items/{itemId}/status") public ApiResponse<PlanDetailView> itemStatus(@PathVariable long planId,@PathVariable long itemId,@Valid @RequestBody PlanItemStatusRequest r){return ApiResponse.success(service.changeItemStatus(planId,itemId,r));}
}
