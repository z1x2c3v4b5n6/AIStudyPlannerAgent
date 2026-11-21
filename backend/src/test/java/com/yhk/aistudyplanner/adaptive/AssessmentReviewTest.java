package com.yhk.aistudyplanner.adaptive;

import static org.junit.jupiter.api.Assertions.*;
import com.yhk.aistudyplanner.assessment.AssessmentPerformance.*;
import java.time.*;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AssessmentReviewTest {
    @Test void assessmentEvidenceBringsReviewForwardWithoutChangingSelfRating() {
        var today=LocalDate.of(2026,11,21);
        var node=new LearningReviews.Candidate(5,1L,"Spring事务",22L,today.minusDays(1),
                LearningReviews.Rating.FLUENT,today.plusDays(4),"按学习记录复习","2026-11-20",30L);
        var weak=new TopicResult("spring-transaction","Spring事务","6.2",Level.NEEDS_PRACTICE,
                new Counts(3,0),new Counts(0,0),3,today.atStartOfDay(),Map.of());
        var candidate=LearningReviews.withAssessment(node,weak,today);
        assertEquals(today,candidate.suggestedDate());
        assertEquals(LearningReviews.Rating.FLUENT,candidate.rating());
        assertEquals(30L,candidate.reviewTaskId());
        assertTrue(candidate.reason().contains("与当前自评存在差异"));
        assertTrue(candidate.cycleKey().startsWith("ASSESS-"));
    }
}
