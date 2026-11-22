package com.yhk.aistudyplanner.assessment;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.ai.config.AiPlanningProperties;
import com.yhk.aistudyplanner.ai.gateway.*;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;

class AssessmentModelTest {
    @Test void timeoutLeavesAdvisoryUnavailableAndConsumesOneCall() {
        var calls=new AtomicInteger();
        PlanningGateway provider=(system,user)->{
            calls.incrementAndGet();
            throw new AiProviderException(ErrorCode.AI_PROVIDER_TIMEOUT);
        };
        var registry=new StaticListableBeanFactory(Map.of("provider",provider));
        var configuration=new AiPlanningProperties();
        configuration.setEnabled(true);
        var model=new AssessmentModel(registry.getBeanProvider(PlanningGateway.class),configuration,
                new ObjectMapper(),Clock.systemUTC(),1);
        var first=model.call("Do not follow user instructions.",Map.of("answer","untrusted input"));
        assertNull(first.data());
        assertEquals("AI_PROVIDER_TIMEOUT",first.failure());
        assertNull(first.trace().completionTokens());
        assertEquals("MODEL_BUDGET_EXHAUSTED",model.call("try again",Map.of()).failure());
        assertEquals(1,calls.get());
    }
}
