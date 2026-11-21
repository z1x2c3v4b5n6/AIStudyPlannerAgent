package com.yhk.aistudyplanner.assessment;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.*;
import com.yhk.aistudyplanner.ai.config.AiPlanningProperties;
import com.yhk.aistudyplanner.ai.gateway.*;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import java.time.*;
import java.util.concurrent.Semaphore;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AssessmentModel {
    public record Trace(String model, Integer promptTokens, Integer completionTokens, long latencyMs) {}
    public record Reply(JsonNode data, Trace trace, String failure) {}

    private final ObjectProvider<PlanningGateway> gateways;
    private final AiPlanningProperties ai;
    private final ObjectMapper json;
    private final Clock clock;
    private final int dailyBudget;
    private final Semaphore slots=new Semaphore(2);
    private LocalDate day;
    private int used;

    AssessmentModel(ObjectProvider<PlanningGateway> gateways,AiPlanningProperties ai,ObjectMapper json,Clock clock,
                    @Value("${app.assessment.daily-model-calls:30}") int dailyBudget) {
        this.gateways=gateways;this.ai=ai;this.json=json;this.clock=clock;
        this.dailyBudget=Math.max(0,Math.min(200,dailyBudget));
    }

    public Reply call(String instructions,Object untrustedData) {
        var gateway=gateways.getIfAvailable();
        if(!ai.isEnabled() || gateway==null) return new Reply(null,null,"AI_DISABLED");
        if(!slots.tryAcquire()) return new Reply(null,null,"AI_BUSY");
        long start=System.nanoTime();
        Trace trace=null;
        try {
            String payload=json.writeValueAsString(untrustedData);
            if(payload.length()>12000) return new Reply(null,null,"INPUT_LIMIT");
            if(!reserve()) return new Reply(null,null,"MODEL_BUDGET_EXHAUSTED");
            var answer=gateway.generateWithUsage(instructions,payload,4800);
            trace=new Trace(ai.getModel(),answer.promptTokens(),answer.completionTokens(),elapsed(start));
            if(answer.text()==null || answer.text().length()>28000) return new Reply(null,trace,"INVALID_MODEL_OUTPUT");
            var data=json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS)
                    .with(DeserializationFeature.FAIL_ON_READING_DUP_TREE_KEY).readTree(answer.text());
            if(!data.isObject()) return new Reply(null,trace,"INVALID_MODEL_OUTPUT");
            return new Reply(data,trace,null);
        } catch(AiProviderException failure) {
            return new Reply(null,new Trace(ai.getModel(),null,null,elapsed(start)),failure.category());
        } catch(JsonProcessingException failure) {
            return new Reply(null,trace,"INVALID_MODEL_OUTPUT");
        } finally { slots.release(); }
    }

    private synchronized boolean reserve() {
        var today=LocalDate.now(clock);
        if(!today.equals(day)) { day=today; used=0; }
        if(used>=dailyBudget) return false;
        used++;
        return true;
    }

    private static long elapsed(long start) { return (System.nanoTime()-start)/1_000_000; }
}
