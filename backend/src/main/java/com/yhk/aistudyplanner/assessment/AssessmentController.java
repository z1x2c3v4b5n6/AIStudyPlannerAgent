package com.yhk.aistudyplanner.assessment;

import com.yhk.aistudyplanner.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/assessments")
class AssessmentController {
    private final AssessmentQuestions questions;

    AssessmentController(AssessmentQuestions questions) { this.questions=questions; }

    @GetMapping("/topics")
    ApiResponse<?> topics() { return ApiResponse.success(questions.topics()); }

    @GetMapping("/questions")
    ApiResponse<?> bank(@RequestParam(required=false) String unitId) { return ApiResponse.success(questions.bank(false,unitId)); }

    @GetMapping("/manage/questions")
    ApiResponse<?> manage(@RequestParam(required=false) String unitId) { return ApiResponse.success(questions.bank(true,unitId)); }

    @GetMapping("/manage/questions/{id}")
    ApiResponse<?> inspect(@PathVariable long id) { return ApiResponse.success(questions.inspect(id)); }

    @PostMapping("/manage/questions")
    ApiResponse<?> create(@Valid @RequestBody AssessmentQuestions.Draft draft) {
        return ApiResponse.success(questions.create(draft));
    }

    @PostMapping("/manage/questions/{id}/revision")
    ApiResponse<?> revise(@PathVariable long id,@Valid @RequestBody AssessmentQuestions.Draft draft) {
        return ApiResponse.success(questions.revise(id,draft));
    }

    @PostMapping("/manage/questions/{id}/decision")
    ApiResponse<?> review(@PathVariable long id,@Valid @RequestBody AssessmentQuestions.Decision decision) {
        return ApiResponse.success(questions.decide(id,decision));
    }
}
