package com.yhk.aistudyplanner.learningpath.controller;

import com.yhk.aistudyplanner.common.response.ApiResponse;
import com.yhk.aistudyplanner.learningpath.dto.LearningPathGenerateRequest;
import com.yhk.aistudyplanner.learningpath.service.LearningPathAiService;
import com.yhk.aistudyplanner.learningpath.vo.LearningPathDraftView;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/v1/ai/learning-paths")
public class LearningPathAiController {
    private final LearningPathAiService service; public LearningPathAiController(LearningPathAiService service){this.service=service;}
    @PostMapping("/generate") public ApiResponse<LearningPathDraftView> generate(@Valid @RequestBody LearningPathGenerateRequest request){return ApiResponse.success(service.generate(request));}
}
