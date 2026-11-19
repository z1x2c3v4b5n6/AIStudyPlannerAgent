package com.yhk.aistudyplanner.adaptive;

import com.yhk.aistudyplanner.common.response.ApiResponse;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/adaptive")
class AdaptiveController {
    private final LearningProfileService profiles;
    private final LearningDiagnostics diagnostics;
    AdaptiveController(LearningProfileService profiles, LearningDiagnostics diagnostics) {
        this.profiles = profiles;
        this.diagnostics = diagnostics;
    }

    @GetMapping("/profile")
    ApiResponse<LearningProfileService.Profile> profile(@RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to, @RequestParam(required=false) Integer days) {
        return ApiResponse.success(profiles.profile(from, to, days));
    }
    @GetMapping("/diagnostics")
    ApiResponse<List<LearningDiagnostics.Diagnostic>> diagnostics(@RequestParam(required=false) Integer days,
            @RequestParam(required=false) Integer dailyMinutes, @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required=false) @DateTimeFormat(iso=DateTimeFormat.ISO.DATE) LocalDate to) {
        return ApiResponse.success(diagnostics.analyze(profiles.profile(from, to, days), dailyMinutes));
    }
}
