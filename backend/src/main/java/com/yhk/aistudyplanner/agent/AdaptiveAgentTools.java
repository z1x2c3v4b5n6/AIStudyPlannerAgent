package com.yhk.aistudyplanner.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.adaptive.*;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
class AdaptiveAgentTools {
    record Period(@Min(1) @Max(90) Integer days, @Min(15) @Max(720) Integer dailyMinutes) {}
    record Topic(@Size(max=100) String topic) {}
    record PlanArgs(@NotNull LocalDate start,@Min(1) @Max(7) int days,
                    @Min(15) @Max(720) int dailyMinutes,@Positive Long preferredSubjectId) {}
    record PlanChoice(@NotNull LocalDate start,@Min(1) @Max(7) int days,@Min(15) @Max(720) int dailyMinutes,
                      @Positive Long preferredSubjectId,@NotBlank String strategy) {}
    record ReviewArgs(@NotEmpty @Size(max=10) List<LearningReviews.Selection> selections) {}
    private final LearningProfileService profiles;
    private final LearningDiagnostics diagnostics;
    private final AdaptivePlanningService planning;
    private final LearningReviews reviews;
    private final ObjectMapper mapper;
    private final Clock clock;

    AdaptiveAgentTools(LearningProfileService profiles, LearningDiagnostics diagnostics,
                       AdaptivePlanningService planning, LearningReviews reviews, ObjectMapper mapper,Clock clock) {
        this.profiles=profiles;this.diagnostics=diagnostics;this.planning=planning;this.reviews=reviews;
        this.mapper=mapper;this.clock=clock;
    }
    void register(AgentTools tools) {
        tools.register("get_learning_profile","按记录读取学习画像",Period.class,a->new AgentTools.Result(profile(a.days())));
        tools.register("analyze_learning_progress","依据实际记录计算诊断",Period.class,a->new AgentTools.Result(diagnosis(a.days(),a.dailyMinutes())));
        tools.register("detect_learning_risks","读取任务积压、计划偏差和容量证据",Period.class,a->new AgentTools.Result(diagnosis(a.days(),a.dailyMinutes())));
        tools.register("get_review_candidates","读取有学习路径节点证据的复习候选",Topic.class,a->new AgentTools.Result(review(a.topic(),10)));
        tools.register("get_planning_constraints","读取空闲预算下的规划比较摘要",PlanArgs.class,a->new AgentTools.Result(
                compact(planning.compare(request(a.start(),a.days(),a.dailyMinutes(),a.preferredSubjectId())))));
        tools.register("compare_plan_strategies","比较最小变更、优先级和均衡策略",PlanArgs.class,a->new AgentTools.Result(
                compact(planning.compare(request(a.start(),a.days(),a.dailyMinutes(),a.preferredSubjectId())))));
    }
    Map<String,Object> profile(Integer days){
        var p=profiles.profile(null,null,days);
        return Map.of("window",p.window(),"activity",p.activity(),"tasks",p.tasks(),"plans",p.plans(),
                "sampleStatus",p.sampleStatus(),"subjectTime",p.subjectTime().stream().limit(10).toList(),
                "activePaths",p.paths().total());
    }
    List<LearningDiagnostics.Diagnostic> diagnosis(Integer days,Integer minutes){
        return diagnostics.analyze(profiles.profile(null,null,days),minutes);
    }
    List<LearningReviews.Candidate> review(String topic,int limit){return reviews.candidates(topic).stream().limit(limit).toList();}
    JsonNode workflowPreview(JsonNode input){
        return planning.workflowPreview(request(LocalDate.parse(input.path("start").asText()),input.path("horizon").asInt(),
                input.path("budget").asInt(),input.path("subjectId").isIntegralNumber()?input.path("subjectId").asLong():null));
    }
    JsonNode applyWorkflow(JsonNode preview,String strategy) {
        return planning.decideWorkflow(UUID.fromString(preview.path("id").asText()),
                new AdaptivePlanningService.Decision(preview.path("digest").asText(),strategy,true));
    }
    JsonNode createIntent(String text) {
        boolean review=text.matches("(?s).*(需要复习|复习建议|提醒.*复习|复习.*知识).*");
        boolean planning=text.matches("(?s).*(下周.*(计划|安排|重排)|逾期.*(调整|安排)|自适应|多日.*规划).*");
        boolean diagnosis=text.matches("(?s).*((最近|两周|学情).*(分析|诊断|问题)|分析.*(最近|问题)|学习诊断).*");
        if(!planning&&!review&&!diagnosis) return null;
        int horizon=planning?7:0;
        var today=LocalDate.now(clock);
        var start=text.contains("下周")?today.with(java.time.temporal.TemporalAdjusters.next(DayOfWeek.MONDAY)):today.plusDays(1);
        var input=mapper.createObjectNode().put("profileDays",text.contains("30天")||text.contains("一个月")?30:text.contains("一周")?7:14)
                .put("overview",diagnosis).put("replan",planning).put("review",review).put("horizon",horizon)
                .put("start",start.toString()).put("reviewTopic",text.toLowerCase(Locale.ROOT).contains("rag")?"RAG":"");
        var minutes=java.util.regex.Pattern.compile("(\\d{1,3})\\s*分钟").matcher(text);
        var hours=java.util.regex.Pattern.compile("(\\d{1,2}|一|二|两|三|四)\\s*(?:个)?小时").matcher(text);
        if(minutes.find()) input.put("budget",Integer.parseInt(minutes.group(1)));
        else if(hours.find()){
            var value=hours.group(1);
            int amount=switch(value){case "一"->1;case "二","两"->2;case "三"->3;case "四"->4;default->Integer.parseInt(value);};
            input.put("budget",amount*60);
        }
        if(input.has("budget") && (input.path("budget").asInt()<15||input.path("budget").asInt()>720))
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        return input;
    }

    AgentActions.Proposal proposePlan(PlanChoice args){
        if(!Set.of("MINIMAL","PRIORITY","BALANCED").contains(args.strategy())) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        var compared=planning.compare(request(args.start(),args.days(),args.dailyMinutes(),args.preferredSubjectId()));
        var chosen=java.util.stream.StreamSupport.stream(compared.path("schemes").spliterator(),false)
                .filter(s->s.path("strategy").asText().equals(args.strategy())).findFirst().orElseThrow();
        if(chosen.path("scheduled").isEmpty()) throw new BusinessException(ErrorCode.PLAN_DRAFT_EMPTY);
        return new AgentActions.Proposal("propose_adaptive_plan", "多日学习计划",chosen,
                mapper.createObjectNode().put("strategy",args.strategy()).set("comparison",compared));
    }
    AgentActions.Proposal proposeReview(ReviewArgs args){
        var preview=reviews.reviewDraft(args.selections());
        return new AgentActions.Proposal("propose_review_tasks","复习任务",preview,preview);
    }
    Object apply(AgentActions.Proposal proposal){
        return proposal.type().equals("propose_review_tasks")?reviews.applyReviewed(proposal.payload())
                :planning.applyReviewed(proposal.payload().path("comparison"),proposal.payload().path("strategy").asText());
    }
    private static AdaptivePlanningService.Request request(LocalDate start,int days,int minutes,Long subject){
        return new AdaptivePlanningService.Request(start,days,minutes,subject,List.of());
    }
    private JsonNode compact(JsonNode comparison){
        var result=mapper.createArrayNode();
        for(var scheme:comparison.path("schemes")){
            result.addObject().put("strategy",scheme.path("strategy").asText())
                    .put("scheduled",scheme.path("scheduled").size()).put("omitted",scheme.path("omitted").size())
                    .put("changedDates",scheme.path("changedDates").asInt()).set("dailyMinutes",scheme.path("dailyMinutes"));
        }
        return result;
    }
}
