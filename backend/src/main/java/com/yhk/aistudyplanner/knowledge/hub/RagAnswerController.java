package com.yhk.aistudyplanner.knowledge.hub;

import com.yhk.aistudyplanner.common.response.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/knowledge-hub/rag")
class RagAnswerController {
    private final RagAnswerService answers;
    RagAnswerController(RagAnswerService answers) { this.answers=answers; }
    @PostMapping("/answer")
    ApiResponse<RagAnswerService.Answer> answer(@Valid @RequestBody RagAnswerService.Ask ask) {
        return ApiResponse.success(answers.ask(ask));
    }
    @GetMapping("/history")
    ApiResponse<List<RagAnswerService.History>> history() { return ApiResponse.success(answers.history()); }
}
