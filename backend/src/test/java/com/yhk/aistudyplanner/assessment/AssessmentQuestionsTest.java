package com.yhk.aistudyplanner.assessment;

import static com.yhk.aistudyplanner.assessment.AssessmentQuestions.State.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class AssessmentQuestionsTest {
    @Test void lifecycleRequiresReviewBeforePublicationAndArchiveIsFinal() {
        assertTrue(AssessmentQuestions.allows(DRAFT,PENDING_REVIEW));
        assertTrue(AssessmentQuestions.allows(PENDING_REVIEW,APPROVED));
        assertTrue(AssessmentQuestions.allows(APPROVED,PUBLISHED));
        assertTrue(AssessmentQuestions.allows(PUBLISHED,ARCHIVED));
        assertFalse(AssessmentQuestions.allows(DRAFT,PUBLISHED));
        assertFalse(AssessmentQuestions.allows(ARCHIVED,PUBLISHED));
        assertFalse(AssessmentQuestions.allows(REJECTED,APPROVED));
    }

    @Test void fingerprintsIgnorePresentationDifferences() {
        assertEquals(AssessmentQuestions.fingerprint("Ｓpring 事务？"),AssessmentQuestions.fingerprint("spring事务"));
        assertNotEquals(AssessmentQuestions.fingerprint("Spring 事务"),AssessmentQuestions.fingerprint("Spring 代理"));
    }
}
