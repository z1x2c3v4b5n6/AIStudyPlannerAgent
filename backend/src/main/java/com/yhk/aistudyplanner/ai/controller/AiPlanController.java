package com.yhk.aistudyplanner.ai.controller;

import com.yhk.aistudyplanner.ai.service.AiPlanService;
import com.yhk.aistudyplanner.ai.service.NaturalLanguagePlanService;
import com.yhk.aistudyplanner.ai.service.PlanningQuickCreateService;
import com.yhk.aistudyplanner.ai.dto.NaturalLanguagePlanParseRequest;
import com.yhk.aistudyplanner.ai.dto.CandidateTasksRequest;
import com.yhk.aistudyplanner.ai.dto.QuickCreatePlanTaskRequest;
import com.yhk.aistudyplanner.ai.vo.AiPlanDraftView;
import com.yhk.aistudyplanner.ai.vo.NaturalLanguagePlanParseView;
import com.yhk.aistudyplanner.ai.vo.QuickCreatePlanTaskView;
import com.yhk.aistudyplanner.ai.vo.SelectableTaskView;
import java.util.List;
import com.yhk.aistudyplanner.common.response.ApiResponse;
import com.yhk.aistudyplanner.plan.dto.PlanDraftRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/ai/plans")
public class AiPlanController {
  private final AiPlanService service;
  private final NaturalLanguagePlanService naturalLanguagePlanService;
  private final PlanningQuickCreateService quickCreateService;

  public AiPlanController(
      AiPlanService s,
      NaturalLanguagePlanService naturalLanguagePlanService,
      PlanningQuickCreateService quickCreateService) {
    service = s;
    this.naturalLanguagePlanService = naturalLanguagePlanService;
    this.quickCreateService = quickCreateService;
  }

  @PostMapping("/draft")
  public ApiResponse<AiPlanDraftView> draft(@Valid @RequestBody PlanDraftRequest request) {
    return ApiResponse.success(service.generate(request));
  }

  @PostMapping("/parse-requirement")
  public ApiResponse<NaturalLanguagePlanParseView> parseRequirement(
      @Valid @RequestBody NaturalLanguagePlanParseRequest request) {
    return ApiResponse.success(naturalLanguagePlanService.parse(request));
  }

  @PostMapping("/candidate-tasks")
  public ApiResponse<List<SelectableTaskView>> candidateTasks(
      @Valid @RequestBody CandidateTasksRequest request) {
    return ApiResponse.success(naturalLanguagePlanService.candidateTasks(request));
  }

  @PostMapping("/quick-create-task")
  public ApiResponse<QuickCreatePlanTaskView> quickCreateTask(
      @Valid @RequestBody QuickCreatePlanTaskRequest request) {
    return ApiResponse.success(quickCreateService.createOrReuse(request));
  }
}
