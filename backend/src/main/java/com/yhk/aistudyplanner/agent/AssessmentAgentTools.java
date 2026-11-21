package com.yhk.aistudyplanner.agent;

import static com.yhk.aistudyplanner.common.exception.ErrorCode.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.adaptive.LearningReviews;
import com.yhk.aistudyplanner.assessment.*;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.knowledge.dto.KnowledgeSearchRequest;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeDomain;
import com.yhk.aistudyplanner.knowledge.service.KnowledgeRetriever;
import com.yhk.aistudyplanner.plan.entity.PlanStatus;
import com.yhk.aistudyplanner.plan.service.StudyPlanService;
import jakarta.validation.constraints.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
class AssessmentAgentTools {
    static final String PRACTICE="propose_assessment_practice";
    static final String REVIEW="propose_assessment_review";
    record Query(@Min(7) @Max(90) Integer days,@Positive Long subjectId,@Size(max=100) String unitId) {}
    record Practice(@NotBlank @Size(max=100) String unitId,@Size(max=80) String softwareVersion,
                    @Positive Long subjectId,@Min(1) @Max(10) int count) {}
    record Review(@Size(max=100) String unitId,@Positive Long subjectId,@NotNull LocalDate start,
                  @Min(15) @Max(720) int dailyMinutes,KnowledgeDomain domain) {}
    private final AssessmentQuestions bank;
    private final AssessmentSessions sessions;
    private final AssessmentPerformance performance;
    private final KnowledgeRetriever retriever;
    private final LearningReviews reviews;
    private final StudyPlanService plans;
    private final ObjectMapper json;
    private final Clock clock;

    AssessmentAgentTools(AssessmentQuestions bank,AssessmentSessions sessions,AssessmentPerformance performance,
                         KnowledgeRetriever retriever,LearningReviews reviews,StudyPlanService plans,ObjectMapper json,Clock clock) {
        this.bank=bank;this.sessions=sessions;this.performance=performance;this.retriever=retriever;this.reviews=reviews;
        this.plans=plans;this.json=json;this.clock=clock;
    }

    void register(AgentTools tools) {
        tools.register("get_assessment_history","读取当前用户最近的测评会话",AgentTools.Page.class,
                a->new AgentTools.Result(sessions.history(a.number()).stream().limit(8).toList()));
        tools.register("get_knowledge_performance","读取当前用户知识点的首次作答与复练表现",Query.class,
                a->new AgentTools.Result(summary(a).topics().stream().limit(6).toList()));
        tools.register("list_weak_topics","读取有足够首次作答样本的薄弱知识点",Query.class,
                a->new AgentTools.Result(weak(a).stream().limit(6).toList()));
        tools.register("get_wrong_answers","读取当前用户错题索引和错误分类。不给出未提交题目的答案",Query.class,
                a->new AgentTools.Result(performance.wrong(a.subjectId(),a.unitId(),1).stream().limit(8).toList()));
        tools.register("get_assessment_summary","读取当前用户测评数量、错题及待复核数",Query.class,a->{
            var s=summary(a);
            return new AgentTools.Result(Map.of("days",s.days(),"completedSessions",s.completedSessions(),"incorrect",s.incorrect(),
                    "needsReview",s.needsReview(),"sampleLimitReached",s.truncated(),"topics",s.topics().size()));
        });
        tools.register("list_assessment_topics","从知识库查找测评知识点ID。只选择搜索返回的ID",AgentTools.Search.class,
                a->new AgentTools.Result(retriever.search(new KnowledgeSearchRequest(a.query(),null,5)).stream()
                        .map(hit->bank.topic(hit.unit().id())).toList()));
    }

    private AssessmentPerformance.Summary summary(Query q) {
        return performance.summary(q.days()==null?30:q.days(),q.subjectId(),q.unitId());
    }

    private List<AssessmentPerformance.TopicResult> weak(Query q) {
        return summary(q).topics().stream().filter(x->x.level()==AssessmentPerformance.Level.NEEDS_PRACTICE).toList();
    }

    AgentActions.Proposal practice(Practice input) {
        var topic=bank.topic(input.unitId());
        var eligible=bank.bank(false,topic.id()).stream().filter(q->input.softwareVersion()==null
                || input.softwareVersion().equals(q.softwareVersion())).toList();
        if(eligible.isEmpty()) throw new BusinessException(RESOURCE_NOT_FOUND);
        var versions=eligible.stream().map(AssessmentQuestions.BankEntry::softwareVersion).distinct().toList();
        if(versions.size()>1) throw new BusinessException(VALIDATION_ERROR);
        var request=new AssessmentSessions.Start(AssessmentSessions.Mode.TOPIC,input.subjectId(),null,
                topic.id(),versions.get(0),null,Math.min(input.count(),eligible.size()),null);
        return new AgentActions.Proposal(PRACTICE,topic.title(),json.valueToTree(Map.of("topic",topic.title(),
                "count",request.count(),"softwareVersion",versions.get(0))),json.valueToTree(request));
    }

    AgentActions.Proposal review(Review input) {
        var today=LocalDate.now(clock);
        if(input.start().isBefore(today) || input.start().plusDays(6).isAfter(today.plusDays(30))
                || input.dailyMinutes()<15 || input.dailyMinutes()>720) throw new BusinessException(VALIDATION_ERROR);
        var weak=weak(new Query(30,input.subjectId(),input.unitId())).stream()
                .filter(t->input.domain()==null || input.domain().name().equals(bank.topic(t.unitId()).domain())).toList();
        var candidates=reviews.candidates(null).stream().filter(c->c.reviewTaskId()==null && c.subjectId()!=null
                && c.cycleKey().startsWith("ASSESS-") && (input.subjectId()==null || input.subjectId().equals(c.subjectId()))
                && weak.stream().anyMatch(t->AssessmentTopics.related(c.topic(),t.topic()))).toList();
        var remaining=new LinkedHashMap<LocalDate,Integer>();
        for(int offset=0;offset<7;offset++) {
            var date=input.start().plusDays(offset);
            var page=plans.list(1,20,date,date,null);
            if(page.total()>page.list().size()) throw new BusinessException(AGENT_LIMIT);
            int scheduled=page.list().stream().filter(p->p.status()!=PlanStatus.CANCELLED && p.status()!=PlanStatus.ABANDONED)
                    .mapToInt(p->p.plannedMinutes()).sum();
            remaining.put(date,Math.max(0,input.dailyMinutes()-scheduled-reviews.unscheduledAssessmentMinutes(date)));
        }
        int minutes=Math.min(30,input.dailyMinutes());
        var selections=new ArrayList<LearningReviews.Selection>();
        var topics=new HashSet<String>();
        for(var candidate:candidates) {
            if(selections.size()==7) break;
            if(!topics.add(candidate.topic())) continue;
            var date=remaining.entrySet().stream().filter(day->day.getValue()>=minutes
                    && !day.getKey().isBefore(candidate.suggestedDate())).map(Map.Entry::getKey).findFirst();
            if(date.isEmpty()) continue;
            selections.add(new LearningReviews.Selection(candidate.itemId(),date.get(),minutes));
            remaining.computeIfPresent(date.get(),(day,free)->free-minutes);
        }
        if(selections.isEmpty()) throw new BusinessException(PLAN_DRAFT_EMPTY);
        JsonNode draft=reviews.reviewDraft(selections);
        return new AgentActions.Proposal(REVIEW,"测评薄弱知识点复习",draft,
                json.createObjectNode().set("request",json.valueToTree(input)));
    }

    Object apply(AgentActions.Proposal proposal) {
        if(PRACTICE.equals(proposal.type()))
            return sessions.start(json.convertValue(proposal.payload(),AssessmentSessions.Start.class));
        var recomputed=review(json.convertValue(proposal.payload().path("request"),Review.class));
        if(!recomputed.preview().equals(proposal.preview())) throw new BusinessException(AGENT_ACTION_CONFLICT);
        return reviews.applyReviewed(proposal.preview());
    }

    JsonNode intent(String input) {
        boolean review=input.matches("(?s).*(薄弱|错题|答错).*(复习|安排|计划).*");
        boolean practice=input.matches("(?s).*(测评|测验|测一测|检测.*掌握).*");
        boolean analyze=input.matches("(?s).*(薄弱|错题|答错).*");
        if(!review && !practice && !analyze) return null;
        String language=input.toLowerCase(Locale.ROOT);
        KnowledgeDomain domain=language.matches("(?s).*\\b(java|spring)\\b.*") && !language.matches("(?s).*\\b(rag|spring ai)\\b.*")
                ? KnowledgeDomain.JAVA_BACKEND:null;
        var hits=retriever.search(new KnowledgeSearchRequest(input,domain,2));
        String unit=hits.isEmpty()?null:hits.get(0).unit().id();
        var output=json.createObjectNode().put("intent",review?"REVIEW":practice?"PRACTICE":"ANALYZE").put("unitId",unit);
        var start=LocalDate.now(clock).plusDays(1);
        if(input.contains("下周")) start=start.minusDays(1).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        output.put("start",start.toString());
        if(domain!=null) output.put("domain",domain.name());
        var matcher=java.util.regex.Pattern.compile("(\\d{1,3})\\s*分钟").matcher(input);
        if(matcher.find()) output.put("budget",Integer.parseInt(matcher.group(1)));
        if(output.has("budget") && (output.path("budget").asInt()<15 || output.path("budget").asInt()>720))
            throw new BusinessException(VALIDATION_ERROR);
        return output;
    }

    Object readIntent(JsonNode input) {
        Long subject=input.hasNonNull("subjectId")?input.path("subjectId").asLong():null;
        String unit=input.path("unitId").asText(null);
        var topics=summary(new Query(30,subject,unit)).topics().stream().filter(t->inDomain(t.unitId(),input)).limit(8).toList();
        return Map.of("performance",topics,"wrongAnswers",performance.wrong(subject,unit,1).stream()
                .filter(t->inDomain(t.unitId(),input)).limit(6).toList());
    }

    private boolean inDomain(String unit,JsonNode input) {
        return !input.hasNonNull("domain") || bank.topic(unit).domain().equals(input.path("domain").asText());
    }

    JsonNode previewIntent(JsonNode input) {
        String unit=input.path("unitId").asText(null);
        Long subject=input.hasNonNull("subjectId")?input.path("subjectId").asLong():null;
        try {
            if("PRACTICE".equals(input.path("intent").asText())) {
                if(unit==null) return json.createObjectNode().put("reason","请指定要练习的知识点");
                return json.valueToTree(practice(new Practice(unit,null,subject,5)));
            }
            return json.valueToTree(review(new Review(unit,subject,LocalDate.parse(input.path("start").asText()),input.path("budget").asInt(),
                    input.hasNonNull("domain")?KnowledgeDomain.valueOf(input.path("domain").asText()):null)));
        } catch(BusinessException ex) {
            if(Set.of(PLAN_DRAFT_EMPTY,RESOURCE_NOT_FOUND,VALIDATION_ERROR).contains(ex.getErrorCode()))
                return json.createObjectNode().put("reason",ex.getErrorCode().message());
            throw ex;
        }
    }
}
