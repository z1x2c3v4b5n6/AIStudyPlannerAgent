package com.yhk.aistudyplanner.assessment;

import com.yhk.aistudyplanner.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/assessments")
class AssessmentController {
    private final AssessmentQuestions questions;

    private final AssessmentGeneration generation;

    private final AssessmentSessions sessions;

    private final AssessmentAssistance assistance;

    AssessmentController(AssessmentQuestions questions,AssessmentGeneration generation,AssessmentSessions sessions,
                         AssessmentAssistance assistance) {
        this.questions=questions; this.generation=generation; this.sessions=sessions;
        this.assistance=assistance;
    }

    @PostMapping("/generate")
    ApiResponse<?> generate(@Valid @RequestBody AssessmentGeneration.Request request) {
        return ApiResponse.success(generation.generate(request));
    }

    @PostMapping("/sessions")
    ApiResponse<?> start(@Valid @RequestBody AssessmentSessions.Start request) {
        return ApiResponse.success(sessions.start(request));
    }

    @GetMapping("/sessions")
    ApiResponse<?> history(@RequestParam(defaultValue="1") int page) {
        return ApiResponse.success(sessions.history(page));
    }

    @GetMapping("/sessions/{id}")
    ApiResponse<?> session(@PathVariable UUID id) { return ApiResponse.success(sessions.get(id)); }

    @PutMapping("/sessions/{id}/items/{ordinal}")
    ApiResponse<?> save(@PathVariable UUID id,@PathVariable int ordinal,@Valid @RequestBody AssessmentSessions.Save answer) {
        return ApiResponse.success(sessions.save(id,ordinal,answer));
    }

    @PostMapping("/sessions/{id}/submit")
    ApiResponse<?> submit(@PathVariable UUID id) { return ApiResponse.success(sessions.submit(id)); }

    @PostMapping("/sessions/{id}/items/{ordinal}/assistance")
    ApiResponse<?> assist(@PathVariable UUID id,@PathVariable int ordinal) {
        return ApiResponse.success(assistance.evaluate(id,ordinal));
    }

    @PostMapping("/sessions/{id}/cancel")
    ApiResponse<?> cancel(@PathVariable UUID id) { return ApiResponse.success(sessions.cancel(id)); }

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
