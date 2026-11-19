package com.yhk.aistudyplanner.adaptive;

import com.fasterxml.jackson.databind.JsonNode;
import com.yhk.aistudyplanner.common.response.ApiResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/adaptive/plans")
class AdaptivePlanningController {
    private final AdaptivePlanningService planning;
    AdaptivePlanningController(AdaptivePlanningService planning) { this.planning=planning; }

    @PostMapping("/preview")
    ApiResponse<JsonNode> preview(@RequestBody AdaptivePlanningService.Request request) {
        return ApiResponse.success(planning.preview(request));
    }
    @PostMapping("/{id}/decision")
    ApiResponse<JsonNode> decide(@PathVariable UUID id,@RequestBody AdaptivePlanningService.Decision decision) {
        return ApiResponse.success(planning.decide(id,decision));
    }
}
