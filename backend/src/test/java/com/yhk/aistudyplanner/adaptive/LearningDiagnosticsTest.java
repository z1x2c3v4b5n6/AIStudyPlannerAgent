package com.yhk.aistudyplanner.adaptive;

import static org.junit.jupiter.api.Assertions.*;
import com.yhk.aistudyplanner.statistics.vo.*;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class LearningDiagnosticsTest {
    private final LocalDate date=LocalDate.of(2026,11,19);

    @Test void usesPairedRecordsAndUserBudget() {
        var p=profile(2,80,140,new LearningProfileService.PlanSample(4,1,1,120,80));
        var result=new LearningDiagnostics().analyze(p,60);
        assertEquals("ATTENTION",result.get(0).severity());
        assertEquals("HIGH",result.get(1).severity());
        assertEquals(-40L,result.get(2).evidence().get("deviationMinutes"));
        assertEquals(240L,result.get(3).evidence().get("deficitMinutes"));
    }

    @Test void distinguishesMissingEvidenceAndRejectsInvalidBudget() {
        var p=profile(0,0,0,new LearningProfileService.PlanSample(0,0,0,0,0));
        var result=new LearningDiagnostics().analyze(p,null);
        assertEquals("INSUFFICIENT_SAMPLE",result.get(0).severity());
        assertEquals("INSUFFICIENT_SAMPLE",result.get(2).severity());
        assertFalse(result.get(3).evidence().containsKey("deficitMinutes"));
        assertThrows(com.yhk.aistudyplanner.common.exception.BusinessException.class,
                () -> new LearningDiagnostics().analyze(p,721));
    }

    private LearningProfileService.Profile profile(long days,long minutes,long prior,LearningProfileService.PlanSample plans) {
        return new LearningProfileService.Profile(new LearningProfileService.Window(date,date),Instant.EPOCH,
                new StatisticsSummaryView(date,date,minutes,days,days,minutes,minutes),
                List.of(new DailyTrendView(date,minutes,days)),List.of(),List.of(),null,null,null,
                new LearningProfileService.TaskCounts(3,1,1,2,1,1,420),plans,1.0/3,
                plans.deviationRatio(),prior,"TEST");
    }
}
