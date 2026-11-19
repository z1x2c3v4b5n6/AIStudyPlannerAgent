package com.yhk.aistudyplanner.adaptive;

import com.fasterxml.jackson.databind.JsonNode;
import com.yhk.aistudyplanner.common.response.ApiResponse;
import java.util.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/adaptive/reviews")
class LearningReviewsController {
    private final LearningReviews reviews;
    LearningReviewsController(LearningReviews reviews) { this.reviews=reviews; }
    @GetMapping
    ApiResponse<List<LearningReviews.Candidate>> candidates(@RequestParam(required=false) String topic) {
        return ApiResponse.success(reviews.candidates(topic));
    }
    @PutMapping("/{item}/feedback")
    ApiResponse<Void> feedback(@PathVariable long item,@RequestBody LearningReviews.Feedback feedback) {
        reviews.feedback(item,feedback);
        return ApiResponse.success(null);
    }
    @PostMapping("/preview")
    ApiResponse<JsonNode> preview(@RequestBody List<LearningReviews.Selection> items) {
        return ApiResponse.success(reviews.preview(items));
    }
    @PostMapping("/{id}/decision")
    ApiResponse<JsonNode> decide(@PathVariable UUID id,@RequestBody LearningReviews.Decision input) {
        return ApiResponse.success(reviews.decide(id,input));
    }
}
