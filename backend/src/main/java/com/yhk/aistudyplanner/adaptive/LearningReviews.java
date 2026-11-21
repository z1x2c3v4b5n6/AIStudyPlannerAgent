package com.yhk.aistudyplanner.adaptive;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.assessment.AssessmentPerformance;
import com.yhk.aistudyplanner.assessment.AssessmentTopics;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import com.yhk.aistudyplanner.task.dto.TaskCreateRequest;
import com.yhk.aistudyplanner.task.service.TaskService;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LearningReviews {
    public enum Rating { UNFAMILIAR, FAIR, FAMILIAR, FLUENT }
    public record Feedback(Rating rating, LocalDate deferredUntil, boolean dismissed) {}
    public record Candidate(long itemId, Long subjectId, String topic, Long originalTaskId, LocalDate lastStudy,
                            Rating rating, LocalDate suggestedDate, String reason, String cycleKey, Long reviewTaskId) {}
    public record Selection(long itemId, LocalDate date, int minutes) {}
    public record Decision(String digest, Boolean confirm) {}
    record Selected(Selection selection, Candidate candidate) {}
    private final JdbcTemplate jdbc;
    private final AuthSessionService auth;
    private final Clock clock;
    private final TaskService tasks;
    private final AdaptiveProposals proposals;
    private final ObjectMapper mapper;
    private final AssessmentPerformance performance;

    LearningReviews(JdbcTemplate jdbc,AuthSessionService auth,Clock clock,TaskService tasks,
                    AdaptiveProposals proposals,ObjectMapper mapper,AssessmentPerformance performance) {
        this.jdbc=jdbc;this.auth=auth;this.clock=clock;this.tasks=tasks;this.proposals=proposals;this.mapper=mapper;this.performance=performance;
    }

    @Transactional(readOnly=true)
    public List<Candidate> candidates(String topic) {
        if(topic!=null && topic.length()>100) throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        return candidatesOwned(auth.currentUserId()).stream().filter(item->topic==null
                || item.topic().toLowerCase(Locale.ROOT).contains(topic.toLowerCase(Locale.ROOT))).toList();
    }

    @Transactional
    public void feedback(long itemId,Feedback input) {
        long owner=auth.currentUserId();
        lockOwner(owner);
        if(jdbc.queryForList("SELECT id FROM study_path_item WHERE user_id=? AND id=?",Long.class,owner,itemId).isEmpty())
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        if (input==null || input.deferredUntil()!=null && input.deferredUntil().isBefore(LocalDate.now(clock)))
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        jdbc.update("""
                INSERT INTO adaptive_review_feedback(owner_id,path_item_id,rating,deferred_until,dismissed,updated_ms)
                VALUES(?,?,?,?,?,?) AS incoming ON DUPLICATE KEY UPDATE rating=incoming.rating,
                deferred_until=incoming.deferred_until,dismissed=incoming.dismissed,updated_ms=incoming.updated_ms
                """,owner,itemId,input.rating()==null?null:input.rating().name(),input.deferredUntil(),input.dismissed(),clock.millis());
    }

    @Transactional
    public JsonNode preview(List<Selection> selections) {
        var selected=select(auth.currentUserId(),selections);
        return mapper.valueToTree(proposals.open("REVIEW",selected,selected));
    }

    @Transactional
    public JsonNode decide(UUID id,Decision input) {
        if(input==null || input.confirm()==null || input.digest()==null || !input.digest().matches("[a-f0-9]{64}"))
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        return proposals.decide(id,"REVIEW",input.digest(),input.confirm()?"CONFIRM":"REJECT",this::applyReviewed);
    }

    @Transactional(readOnly=true)
    public JsonNode reviewDraft(List<Selection> selections) {
        return mapper.valueToTree(select(auth.currentUserId(),selections));
    }

    @Transactional
    public Object applyReviewed(JsonNode payload) {
        List<Selected> before=mapper.convertValue(payload,new com.fasterxml.jackson.core.type.TypeReference<>(){});
        long owner=auth.currentUserId();
        lockOwner(owner);
        var selected=select(owner,before.stream().map(Selected::selection).toList());
        if(!before.equals(selected)) throw new BusinessException(ErrorCode.DATA_CONFLICT);
        var created=new ArrayList<Long>();
        for(var row:selected){
            var item=row.candidate();
            String title="复习："+item.topic();
            if(title.length()>200) title=title.substring(0,title.offsetByCodePoints(0,Math.min(100,title.codePointCount(0,title.length()))));
            var task=tasks.create(new TaskCreateRequest(item.subjectId(),null,title,
                    "学习路径复习建议。"+item.reason(),3,row.selection().minutes(),row.selection().date(),null));
            jdbc.update("INSERT INTO adaptive_review_cycle(owner_id,path_item_id,cycle_key,task_id,created_ms) VALUES(?,?,?,?,?)",
                    owner,item.itemId(),item.cycleKey(),task.id(),clock.millis());
            created.add(task.id());
        }
        return Map.of("status","EXECUTED","taskIds",created);
    }

    private List<Selected> select(long owner,List<Selection> selections) {
        if(selections==null || selections.isEmpty() || selections.size()>10)
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        var candidates=new HashMap<Long,Candidate>();
        candidatesOwned(owner).forEach(item->candidates.put(item.itemId(),item));
        var ids=new HashSet<Long>();
        var today=LocalDate.now(clock);
        return selections.stream().map(input->{
            if(input==null || !ids.add(input.itemId()) || input.date()==null || input.date().isBefore(today)
                    || input.date().isAfter(today.plusDays(30)) || input.minutes()<15 || input.minutes()>120) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
            var item=candidates.get(input.itemId());
            if(item==null || item.subjectId()==null || item.suggestedDate()==null || item.reviewTaskId()!=null)
                throw new BusinessException(ErrorCode.DATA_CONFLICT);
            return new Selected(input,item);
        }).toList();
    }

    private List<Candidate> candidatesOwned(long owner) {
        var today=LocalDate.now(clock);
        var raw=jdbc.query("""
                SELECT i.id,COALESCE(t.subject_id,p.subject_id),i.topic,i.task_id,f.rating,p.target_date,
                  (SELECT MAX(r.ended_at) FROM study_record r WHERE r.user_id=i.user_id AND r.task_id=i.task_id) AS original_last,
                  (SELECT MAX(r.ended_at) FROM adaptive_review_cycle c JOIN study_record r
                    ON r.task_id=c.task_id AND r.user_id=c.owner_id WHERE c.owner_id=i.user_id AND c.path_item_id=i.id) AS review_last,
                  (SELECT COUNT(DISTINCT c.task_id) FROM adaptive_review_cycle c JOIN study_record r
                    ON r.task_id=c.task_id AND r.user_id=c.owner_id WHERE c.owner_id=i.user_id AND c.path_item_id=i.id) AS reviews,
                  (SELECT c.task_id FROM adaptive_review_cycle c JOIN study_task rt ON rt.id=c.task_id AND rt.user_id=c.owner_id
                    WHERE c.owner_id=i.user_id AND c.path_item_id=i.id AND rt.status IN ('TODO','IN_PROGRESS')
                    ORDER BY c.created_ms DESC,c.task_id DESC LIMIT 1) AS open_review
                FROM study_path_item i JOIN study_path p ON p.id=i.path_id AND p.user_id=i.user_id
                LEFT JOIN study_task t ON t.id=i.task_id AND t.user_id=i.user_id
                LEFT JOIN adaptive_review_feedback f ON f.path_item_id=i.id AND f.owner_id=i.user_id
                WHERE i.user_id=? AND p.status<>'CANCELLED'
                  AND (f.dismissed IS NULL OR f.dismissed=FALSE)
                  AND (f.deferred_until IS NULL OR f.deferred_until<=?)
                ORDER BY i.id DESC LIMIT 200
                """,(r,n)->{
            LocalDateTime original=r.getObject(7,LocalDateTime.class);
            LocalDateTime review=r.getObject(8,LocalDateTime.class);
            var last=review!=null && (original==null || review.isAfter(original)) ? review : original;
            String ratingName=r.getString(5);
            Rating rating=ratingName==null?null:Rating.valueOf(ratingName);
            String reason=rating==Rating.UNFAMILIAR?"用户自评不熟悉":"按实际学习记录建议复习";
            LocalDate target=r.getObject(6,LocalDate.class);
            boolean low=rating==Rating.UNFAMILIAR;
            if(last==null && !low) reason="没有实际学习记录，可补充自评";
            int interval=low?1:Math.min(30,3 << Math.min(3,r.getInt(9)));
            LocalDate lastDay=last==null?null:last.toLocalDate();
            LocalDate suggested=low?today:lastDay==null?null:lastDay.plusDays(interval);
            if(target!=null && !target.isBefore(today) && suggested!=null && suggested.isAfter(target)) suggested=target;
            Long latest=r.getObject(10,Long.class);
            return new Candidate(r.getLong(1),r.getObject(2,Long.class),r.getString(3),r.getObject(4,Long.class),
                    lastDay,rating,suggested,reason,lastDay==null?"SELF_RATED":lastDay.toString(),latest);
        },owner,today).stream().filter(Objects::nonNull).toList();
        if(raw.isEmpty()) return List.of();
        var weak=performance.summary(30,null,null).topics().stream()
                .filter(row->row.level()==AssessmentPerformance.Level.NEEDS_PRACTICE).toList();
        raw=raw.stream().map(item->{
            var matching=weak.stream().filter(topic->AssessmentTopics.related(item.topic(),topic.topic())).toList();
            return matching.size()==1?withAssessment(item,matching.get(0),today):item;
        }).toList();
        var args=new ArrayList<Object>(); args.add(owner);raw.forEach(item->args.add(item.itemId()));
        var cycles=new HashMap<String,Long>();
        jdbc.query("SELECT path_item_id,cycle_key,task_id FROM adaptive_review_cycle WHERE owner_id=? AND path_item_id IN ("
                +String.join(",",Collections.nCopies(raw.size(),"?"))+")",r->{
            cycles.put(r.getLong(1)+"/"+r.getString(2),r.getLong(3));
        },args.toArray());
        return raw.stream().map(item->new Candidate(item.itemId(),item.subjectId(),item.topic(),item.originalTaskId(),
                item.lastStudy(),item.rating(),item.suggestedDate(),item.reason(),item.cycleKey(),
                item.reviewTaskId()!=null ? item.reviewTaskId() : cycles.get(item.itemId()+"/"+item.cycleKey())))
                .sorted(Comparator.comparing(Candidate::suggestedDate,Comparator.nullsLast(Comparator.naturalOrder())).thenComparingLong(Candidate::itemId)).toList();
    }

    static Candidate withAssessment(Candidate item,AssessmentPerformance.TopicResult topic,LocalDate today) {
        String reason="近期 "+topic.softwareVersion()+" 新题 "+topic.newQuestions().correct()+"/"+topic.newQuestions().total()
                +" 正确，连续错 "+topic.consecutiveErrors()+" 题。";
        if(item.rating()==Rating.FAMILIAR || item.rating()==Rating.FLUENT) reason+="与当前自评存在差异。";
        if(item.lastStudy()!=null) reason+="最近学习 "+item.lastStudy()+"。";
        String cycle="ASSESS-"+topic.lastScoredAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS"));
        return new Candidate(item.itemId(),item.subjectId(),item.topic(),item.originalTaskId(),item.lastStudy(),item.rating(),
                today,reason,cycle,item.reviewTaskId());
    }

    private void lockOwner(long owner) {
        jdbc.queryForList("SELECT id FROM `user` WHERE id=? FOR UPDATE",Long.class,owner);
    }
}
