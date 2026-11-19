package com.yhk.aistudyplanner.adaptive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import com.yhk.aistudyplanner.plan.dto.*;
import com.yhk.aistudyplanner.plan.service.StudyPlanService;
import com.yhk.aistudyplanner.subject.service.SubjectService;
import com.yhk.aistudyplanner.task.mapper.TaskMapper;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdaptivePlanningService {
    public record Lock(LocalDateTime start, LocalDateTime end) {}
    public record Request(LocalDate start, int days, int dailyMinutes, Long preferredSubjectId, List<Lock> locked) {}
    public record Decision(String digest, String strategy, Boolean confirm) {}
    record Snapshot(List<AdaptivePlanner.Task> tasks, List<AdaptivePlanner.Slot> occupied, long total, int alreadyScheduled) {}
    record PreviewData(Request request, LocalDateTime generatedFor, String fingerprint,
                       List<AdaptivePlanner.Scheme> schemes, long total, int alreadyScheduled) {}
    private final JdbcTemplate jdbc;
    private final AuthSessionService auth;
    private final SubjectService subjects;
    private final Clock clock;
    private final ObjectMapper mapper;
    private final AdaptiveProposals proposals;
    private final StudyPlanService plans;
    private final TaskMapper tasks;

    AdaptivePlanningService(JdbcTemplate jdbc, AuthSessionService auth, SubjectService subjects, Clock clock,
                            ObjectMapper mapper, AdaptiveProposals proposals, StudyPlanService plans, TaskMapper tasks) {
        this.jdbc=jdbc; this.auth=auth; this.subjects=subjects; this.clock=clock; this.mapper=mapper;
        this.proposals=proposals; this.plans=plans; this.tasks=tasks;
    }

    @Transactional(readOnly=true)
    public JsonNode compare(Request request) { return mapper.valueToTree(calculate(request)); }

    @Transactional
    public JsonNode preview(Request request) {
        var data=calculate(request);
        return mapper.valueToTree(proposals.open("PLAN", data, data));
    }

    @Transactional
    public JsonNode decide(UUID id, Decision input) {
        if (input == null || input.confirm()==null || input.digest()==null || !input.digest().matches("[a-f0-9]{64}"))
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        var strategy=input.confirm() ? strategy(input.strategy()) : null;
        return proposals.decide(id,"PLAN",input.digest(), strategy==null ? "REJECT" : strategy.name(),
                payload -> apply(id, mapper.convertValue(payload,PreviewData.class), strategy));
    }

    private Object apply(UUID id, PreviewData data, AdaptivePlanner.Strategy strategy) {
        long owner=auth.currentUserId();
        validate(data.request(),owner);
        var scheme=data.schemes().stream().filter(entry->entry.strategy()==strategy).findFirst().orElseThrow();
        if (scheme.scheduled().isEmpty()) throw new BusinessException(ErrorCode.PLAN_DRAFT_EMPTY);
        scheme.scheduled().stream().map(AdaptivePlanner.Placement::task).sorted(Comparator.comparingLong(AdaptivePlanner.Task::id))
                .forEach(task -> {
                    var locked=tasks.selectOwnedForUpdate(task.id(),owner);
                    if (locked == null || !Objects.equals(locked.getUpdatedAt(),task.updatedAt())
                            || !Set.of("TODO","IN_PROGRESS").contains(locked.getStatus().name())) {
                        throw new BusinessException(ErrorCode.DATA_CONFLICT);
                    }
                });
        var snapshot=load(owner,data.request());
        if (!data.fingerprint().equals(proposals.fingerprint(snapshot))
                || scheme.scheduled().stream().anyMatch(item->item.start().isBefore(LocalDateTime.now(clock)))) {
            throw new BusinessException(ErrorCode.DATA_CONFLICT);
        }
        var grouped=scheme.scheduled().stream().collect(Collectors.groupingBy(item->item.start().toLocalDate(),
                TreeMap::new,Collectors.toList()));
        var planIds=new ArrayList<Long>();
        for(var group:grouped.entrySet()){
            var items=new ArrayList<PlanConfirmItemRequest>();
            int total=0;
            for(var item:group.getValue()){
                int minutes=(int)java.time.temporal.ChronoUnit.MINUTES.between(item.start(),item.end());
                items.add(new PlanConfirmItemRequest(items.size()+1,item.task().id(),item.start(),item.end(),minutes,"自适应规划"));
                total+=minutes;
            }
            planIds.add(plans.confirm(new PlanConfirmRequest(id+"-"+group.getKey(),group.getKey(),
                    data.request().dailyMinutes(),total,"自适应规划",scheme.objective(),items)).id());
        }
        return Map.of("status","EXECUTED","planIds",planIds,"scheduled",scheme.scheduled().size());
    }

    private PreviewData calculate(Request request) {
        long owner=auth.currentUserId();
        validate(request,owner);
        var now=LocalDateTime.now(clock).truncatedTo(java.time.temporal.ChronoUnit.MINUTES).plusMinutes(1);
        var snapshot=load(owner,request);
        var locks=request.locked()==null ? List.<AdaptivePlanner.Slot>of() : request.locked().stream()
                .map(lock->new AdaptivePlanner.Slot(lock.start(),lock.end())).toList();
        var constraints=new AdaptivePlanner.Constraints(request.start(),request.days(),request.dailyMinutes(),
                request.preferredSubjectId(),locks,now);
        var schemes=Arrays.stream(AdaptivePlanner.Strategy.values()).map(strategy->
                AdaptivePlanner.plan(strategy,constraints,snapshot.tasks(),snapshot.occupied())).toList();
        return new PreviewData(request,now,proposals.fingerprint(snapshot),schemes,snapshot.total(),snapshot.alreadyScheduled());
    }

    private Snapshot load(long owner,Request request) {
        var last=request.start().plusDays(request.days()-1);
        var today=LocalDate.now(clock);
        var occupied=jdbc.query("""
                SELECT i.start_at,i.end_at FROM study_plan_item i JOIN study_plan p ON p.id=i.plan_id AND p.user_id=i.user_id
                WHERE i.user_id=? AND p.plan_date BETWEEN ? AND ? AND p.status NOT IN ('CANCELLED','ABANDONED')
                AND i.status<>'SKIPPED' ORDER BY i.start_at,i.end_at,i.id LIMIT 1001
                """,(r,n)->new AdaptivePlanner.Slot(r.getObject(1,LocalDateTime.class),r.getObject(2,LocalDateTime.class)),owner,request.start(),last);
        if (occupied.size()>1000) throw new BusinessException(ErrorCode.AGENT_LIMIT);
        var scheduled=new HashSet<>(jdbc.queryForList("""
                SELECT DISTINCT i.task_id FROM study_plan_item i JOIN study_plan p ON p.id=i.plan_id AND p.user_id=i.user_id
                WHERE i.user_id=? AND p.plan_date>=? AND p.status NOT IN ('CANCELLED','ABANDONED')
                AND i.status='PENDING' LIMIT 1001
                """,Long.class,owner,today));
        if (scheduled.size()>1000) throw new BusinessException(ErrorCode.AGENT_LIMIT);
        String filter="""
                 FROM study_task t WHERE t.user_id=? AND t.status IN ('TODO','IN_PROGRESS')
                    AND (t.planned_date IS NULL OR t.planned_date<=? OR t.due_at<?)
                """;
        Object[] args={owner,last,last.plusDays(1).atStartOfDay()};
        long total=jdbc.queryForObject("SELECT COUNT(*)"+filter,Long.class,args);
        var eligible=jdbc.query("""
                SELECT t.id,t.subject_id,t.title,t.priority,t.estimated_minutes,t.planned_date,t.due_at,
                  (SELECT g.target_date FROM study_goal g WHERE g.id=t.goal_id AND g.user_id=t.user_id AND g.status='ACTIVE'),
                  t.updated_at
                """+filter+" ORDER BY t.priority DESC,t.due_at IS NULL,t.due_at,t.id LIMIT 200",
                (r,n)->new AdaptivePlanner.Task(r.getLong(1),r.getLong(2),r.getString(3),r.getInt(4),r.getInt(5),
                        r.getObject(6,LocalDate.class),r.getObject(7,LocalDateTime.class),r.getObject(8,LocalDate.class),
                        r.getObject(9,LocalDateTime.class),null,false),args);
        var ids=eligible.stream().map(AdaptivePlanner.Task::id).filter(id->!scheduled.contains(id)).toList();
        var dependencies=new HashMap<Long,Dependency>();
        if(!ids.isEmpty()){
            String parameters=String.join(",",Collections.nCopies(ids.size(),"?"));
            var inputs=new ArrayList<Object>();inputs.add(owner);inputs.addAll(ids);
            jdbc.query("""
                    SELECT child.task_id, prev.task_id, prev.status, t.status
                    FROM study_path_item child
                    JOIN study_path_item prev ON prev.user_id=child.user_id AND prev.path_id=child.path_id
                      AND prev.sequence_no=child.sequence_no-1
                    LEFT JOIN study_task t ON t.id=prev.task_id AND t.user_id=prev.user_id
                    WHERE child.user_id=? AND child.task_id IN (
                    """+parameters+")", r->{
                Long id=r.getObject(2,Long.class);
                boolean complete=Set.of("COMPLETED","SKIPPED").contains(r.getString(3)) || "COMPLETED".equals(r.getString(4));
                if(!complete) dependencies.put(r.getLong(1),new Dependency(id,id==null || !ids.contains(id)));
            },inputs.toArray());
        }
        var candidates=eligible.stream().filter(t->!scheduled.contains(t.id())).map(t->{
            var dependency=dependencies.get(t.id());
            return dependency==null ? t : new AdaptivePlanner.Task(t.id(),t.subjectId(),t.title(),t.priority(),t.minutes(),
                    t.plannedDate(),t.dueAt(),t.goalDate(),t.updatedAt(),dependency.missing()?null:dependency.id(),dependency.missing());
        }).toList();
        return new Snapshot(candidates,occupied,total,eligible.size()-candidates.size());
    }
    private record Dependency(Long id,boolean missing) {}

    private void validate(Request request,long owner) {
        if(request==null || request.start()==null || request.days()<1 || request.days()>7 || request.dailyMinutes()<15
                || request.dailyMinutes()>720 || request.start().isBefore(LocalDate.now(clock))
                || request.start().isAfter(LocalDate.now(clock).plusDays(60))) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        if(request.preferredSubjectId()!=null) subjects.requireOwned(request.preferredSubjectId(),owner);
        if(request.locked()!=null && (request.locked().size()>30 || request.locked().stream().anyMatch(lock ->
                lock==null || lock.start()==null || lock.end()==null || !lock.end().isAfter(lock.start())
                || !lock.start().toLocalDate().equals(lock.end().toLocalDate())
                || lock.start().toLocalDate().isBefore(request.start())
                || !lock.start().toLocalDate().isBefore(request.start().plusDays(request.days()))))) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
    private AdaptivePlanner.Strategy strategy(String text) {
        try { return AdaptivePlanner.Strategy.valueOf(text); }
        catch (RuntimeException ex) { throw new BusinessException(ErrorCode.VALIDATION_ERROR); }
    }
}
