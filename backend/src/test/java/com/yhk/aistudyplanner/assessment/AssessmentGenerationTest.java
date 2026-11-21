package com.yhk.aistudyplanner.assessment;

import static com.yhk.aistudyplanner.assessment.AssessmentQuestions.*;
import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class AssessmentGenerationTest {
    @Test void aPublishedQuoteCannotSupportAnInventedAnswer() {
        String quote="Only external method calls coming in through the proxy are intercepted.";
        assertTrue(AssessmentGeneration.grounded(draft(quote,quote),quote));
        assertFalse(AssessmentGeneration.grounded(draft(quote,"All method calls are intercepted."),quote));
        assertFalse(AssessmentGeneration.grounded(draft(quote,quote),"An unrelated transaction passage."));
    }

    private Draft draft(String quote,String answer) {
        var definition=new Definition(List.of(new Option(answer,null),new Option("All calls",null),new Option("None",null)),
                0,answer,"代理只能拦截通过它进入的调用。",List.of(),List.of(),quote);
        return new Draft("java-transaction",Kind.SINGLE_CHOICE,Difficulty.BASIC,"6.2","关于代理的哪一项正确？",
                definition,UUID.randomUUID(),1,false);
    }
}
