package com.yhk.aistudyplanner.assessment;

import static com.yhk.aistudyplanner.assessment.AssessmentQuestions.*;
import static com.yhk.aistudyplanner.assessment.AssessmentScoring.Grade.*;
import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import java.util.*;
import org.junit.jupiter.api.Test;

class AssessmentScoringTest {
    private final Definition choice=new Definition(List.of(new Option("A",null), new Option("B",ErrorType.CONDITION_ERROR),
            new Option("C",ErrorType.VERSION_ERROR)),0,"A","解释",List.of(),List.of(),"Supporting passage");

    @Test void scoresByIndexAndPreservesReviewedDistractorReason() {
        assertEquals(CORRECT,AssessmentScoring.score(Kind.SINGLE_CHOICE,choice,"0",true).grade());
        var wrong=AssessmentScoring.score(Kind.SINGLE_CHOICE,choice,"1",true);
        assertEquals(INCORRECT,wrong.grade());
        assertEquals(ErrorType.CONDITION_ERROR,wrong.errorType());
        assertEquals(UNANSWERED,AssessmentScoring.score(Kind.SINGLE_CHOICE,choice,null,true).grade());
        assertThrows(BusinessException.class,()->AssessmentScoring.score(Kind.SINGLE_CHOICE,choice,"A",true));
    }

    @Test void withdrawnSourcesAndShortAnswersDoNotCreateDefinitiveScores() {
        assertEquals(NEEDS_REVIEW,AssessmentScoring.score(Kind.SINGLE_CHOICE,choice,"0",false).grade());
        assertEquals(NEEDS_REVIEW,AssessmentScoring.score(Kind.SHORT_ANSWER,choice,"A",true).grade());
    }

    @Test void advisoryFeedbackRequiresVerbatimAnswerEvidenceForCoverage() throws Exception {
        var output=new ObjectMapper().readTree("""
                {"criteria":[{"index":0,"status":"COVERED","quote":"external calls only"},
                {"index":1,"status":"UNCERTAIN","quote":""}],"factError":"UNCERTAIN","version":"UNCERTAIN"}
                """);
        assertTrue(AssessmentAssistance.valid(output,"It intercepts external calls only.",2));
        assertFalse(AssessmentAssistance.valid(output,"It intercepts all calls.",2));
    }
}
