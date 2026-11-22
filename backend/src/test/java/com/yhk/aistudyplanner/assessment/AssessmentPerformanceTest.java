package com.yhk.aistudyplanner.assessment;

import static com.yhk.aistudyplanner.assessment.AssessmentPerformance.*;
import static com.yhk.aistudyplanner.assessment.AssessmentQuestions.Difficulty.BASIC;
import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

class AssessmentPerformanceTest {
    private static final LocalDateTime NOW=LocalDateTime.parse("2026-11-21T15:00:00");

    @Test void threeFirstAttemptErrorsAreNotErasedByAMemorizedRetake() {
        var row=measure("spring-transaction","Spring 事务","6.2",List.of(
                sample(1,true,false),sample(3,false,true),sample(2,false,true),sample(1,false,true)),3);
        assertEquals(Level.NEEDS_PRACTICE,row.level());
        assertEquals(3,row.consecutiveErrors());
        assertEquals(new Counts(3,0),row.newQuestions());
        assertEquals(new Counts(1,1),row.repeats());
    }

    @Test void oneQuestionWithRetakesIsInsufficient() {
        var row=measure("spring-transaction","Spring 事务","6.2",List.of(
                sample(1,true,false),sample(1,true,false),sample(1,true,true)),3);
        assertEquals(Level.INSUFFICIENT_DATA,row.level());
        assertEquals(new Counts(1,1),row.byDifficulty().get(BASIC));
    }

    private Sample sample(long id,boolean correct,boolean first) { return new Sample(id,correct,first,BASIC,NOW.minusDays(id)); }
}
