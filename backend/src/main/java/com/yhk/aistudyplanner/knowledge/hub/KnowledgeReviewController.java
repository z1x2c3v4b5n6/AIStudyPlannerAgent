package com.yhk.aistudyplanner.knowledge.hub;

import com.yhk.aistudyplanner.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/knowledge-hub/manage/cases")
class KnowledgeReviewController {
    private final KnowledgeReviewCases cases;
    KnowledgeReviewController(KnowledgeReviewCases cases) {this.cases=cases;}
    @GetMapping
    ApiResponse<?> list(){return ApiResponse.success(cases.list());}
    @PostMapping("/{id}/recheck")
    ApiResponse<?> recheck(@PathVariable UUID id){return ApiResponse.success(cases.recheck(id.toString()));}
    @PostMapping("/{id}/close")
    ApiResponse<?> close(@PathVariable UUID id,@Valid @RequestBody KnowledgeReviewCases.Close input) {
        return ApiResponse.success(cases.close(id.toString(),input));
    }
    @PostMapping("/{id}/evaluate")
    ApiResponse<?> evaluate(@PathVariable UUID id,@Valid @RequestBody KnowledgeReviewCases.Evaluation input) {
        return ApiResponse.success(cases.evaluate(id.toString(),input));
    }
    @GetMapping("/{id}/runs")
    ApiResponse<?> runs(@PathVariable UUID id){return ApiResponse.success(cases.runs(id.toString()));}
}
