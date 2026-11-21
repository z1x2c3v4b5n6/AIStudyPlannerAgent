package com.yhk.aistudyplanner.assessment;

import static com.yhk.aistudyplanner.assessment.AssessmentQuestions.*;
import static com.yhk.aistudyplanner.common.exception.ErrorCode.VALIDATION_ERROR;

final class AssessmentScoring {
    static final int VERSION=1;
    enum Grade { CORRECT, INCORRECT, NEEDS_REVIEW, UNANSWERED }
    record Result(Grade grade,ErrorType errorType) {}

    private AssessmentScoring() {}

    static void validateAnswer(Kind kind,Definition definition,String answer) {
        if(answer==null || answer.isBlank() || answer.length()>2000) throw error(VALIDATION_ERROR);
        if(kind==Kind.SINGLE_CHOICE && (!answer.matches("[0-5]") || Integer.parseInt(answer)>=definition.options().size()))
            throw error(VALIDATION_ERROR);
    }

    static Result score(Kind kind,Definition definition,String answer,boolean sourceAvailable) {
        if(answer==null) return new Result(Grade.UNANSWERED,null);
        validateAnswer(kind,definition,answer);
        if(!sourceAvailable || kind==Kind.SHORT_ANSWER) return new Result(Grade.NEEDS_REVIEW,ErrorType.INSUFFICIENT_EVIDENCE);
        int choice=Integer.parseInt(answer);
        if(choice==definition.correctIndex()) return new Result(Grade.CORRECT,null);
        return new Result(Grade.INCORRECT,definition.options().get(choice).errorType());
    }
}
