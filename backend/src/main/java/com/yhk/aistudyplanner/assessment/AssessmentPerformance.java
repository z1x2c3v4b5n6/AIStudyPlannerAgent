package com.yhk.aistudyplanner.assessment;

import static com.yhk.aistudyplanner.assessment.AssessmentQuestions.*;
import static com.yhk.aistudyplanner.common.exception.ErrorCode.VALIDATION_ERROR;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class AssessmentPerformance {
    public enum Level { INSUFFICIENT_DATA, NEEDS_PRACTICE, DEVELOPING, CONSISTENT }
    public record Sample(long questionId,boolean correct,boolean first,Difficulty difficulty,LocalDateTime submittedAt) {}
    public record Counts(int total,int correct) {}
    public record TopicResult(String unitId,String topic,String softwareVersion,Level level,Counts newQuestions,Counts repeats,
                              int consecutiveErrors,LocalDateTime lastScoredAt,Map<Difficulty,Counts> byDifficulty) {}
    public record Summary(int days,int minimumDistinct,int responseLimit,boolean truncated,int completedSessions,
                          int incorrect,int needsReview,List<TopicResult> topics) {}
    public record Wrong(UUID sessionId,int ordinal,long questionId,long questionVersion,String unitId,String topic,
                        Long subjectId,String softwareVersion,ErrorType errorType,LocalDateTime submittedAt) {}
    private record Observation(String unit,String version,Sample sample) {}
    private record Key(String unit,String version) {}
    private final JdbcTemplate db;
    private final AssessmentQuestions questions;
    private final AuthSessionService auth;
    private final int minimum;
    private static final int LIMIT=600;

    AssessmentPerformance(JdbcTemplate db,AssessmentQuestions questions,AuthSessionService auth,
                          @Value("${app.assessment.minimum-distinct-questions:3}") int minimum) {
        this.db=db;this.questions=questions;this.auth=auth;this.minimum=Math.max(3,Math.min(12,minimum));
    }

    public Summary summary(int days,Long subject,String unit) {
        if(days<7 || days>90) throw error(VALIDATION_ERROR);
        if(unit!=null) questions.topic(unit);
        long owner=auth.currentUserId();
        var since=questions.now().minusDays(days);
        var observed=observations(owner,since,subject,unit);
        var counts=db.queryForMap("""
                SELECT COUNT(DISTINCT s.id) AS sessions, COALESCE(SUM(i.grade='INCORRECT'),0) AS incorrect,
                  COALESCE(SUM(i.grade='NEEDS_REVIEW'),0) AS pending
                FROM assessment_session s JOIN assessment_session_item i ON i.session_id=s.id
                JOIN assessment_question_version v ON v.id=i.question_version_id JOIN assessment_question q ON q.id=v.question_id
                WHERE s.owner_id=? AND s.state='SUBMITTED' AND s.submitted_at>=?
                  AND (? IS NULL OR s.subject_id=?) AND (? IS NULL OR q.unit_id=?)
                """,owner,since,subject,subject,unit,unit);
        var topics=new LinkedHashMap<Key,List<Sample>>();
        observed.stream().limit(LIMIT).forEach(row->topics.computeIfAbsent(new Key(row.unit(),row.version()),k->new ArrayList<>()).add(row.sample()));
        var names=new HashMap<String,String>();
        questions.topics().forEach(topic->names.put(topic.id(),topic.title()));
        var results=topics.entrySet().stream().map(entry->{
            var key=entry.getKey();
            return measure(key.unit(),names.getOrDefault(key.unit(),key.unit()),key.version(),entry.getValue(),minimum);
        }).sorted(Comparator.comparingInt((TopicResult row)->row.level()==Level.NEEDS_PRACTICE?0:1)
                .thenComparing(TopicResult::lastScoredAt,Comparator.reverseOrder()).thenComparing(TopicResult::unitId)
                .thenComparing(TopicResult::softwareVersion)).toList();
        return new Summary(days,minimum,LIMIT,observed.size()>LIMIT,number(counts,"sessions"),
                number(counts,"incorrect"),number(counts,"pending"),results);
    }

    private List<Observation> observations(long owner,LocalDateTime since,Long subject,String unit) {
        return db.query("""
                SELECT q.unit_id,v.software_version,v.question_id,i.grade,v.difficulty,s.submitted_at,
                  NOT EXISTS(
                    SELECT 1 FROM assessment_session earlier JOIN assessment_session_item attempt ON attempt.session_id=earlier.id
                    JOIN assessment_question_version eq ON eq.id=attempt.question_version_id
                    WHERE earlier.owner_id=s.owner_id AND earlier.state='SUBMITTED' AND eq.question_id=v.question_id
                      AND attempt.grade IN ('CORRECT','INCORRECT') AND
                      (earlier.submitted_at<s.submitted_at OR earlier.submitted_at=s.submitted_at AND earlier.id<s.id)
                  ) AS first_result
                FROM assessment_session s JOIN assessment_session_item i ON i.session_id=s.id
                JOIN assessment_question_version v ON v.id=i.question_version_id JOIN assessment_question q ON q.id=v.question_id
                WHERE s.owner_id=? AND s.state='SUBMITTED' AND s.submitted_at>=?
                  AND (? IS NULL OR s.subject_id=?) AND (? IS NULL OR q.unit_id=?)
                  AND q.owner_id IS NULL AND i.grade IN ('CORRECT','INCORRECT')
                ORDER BY s.submitted_at DESC,s.id DESC,i.ordinal DESC LIMIT 601
                """,(r,n)->new Observation(r.getString(1),r.getString(2),new Sample(r.getLong(3),
                "CORRECT".equals(r.getString(4)),r.getBoolean(7),Difficulty.valueOf(r.getString(5)),
                r.getObject(6,LocalDateTime.class))),owner,since,subject,subject,unit,unit);
    }

    public record ReviewEffect(long itemId,long taskId,String taskStatus,LocalDateTime completedAt,String unitId,
                               String version,Difficulty difficulty,Counts before,Counts after,boolean sufficient) {}
    public record ReviewProgress(int accepted,int completed,List<ReviewEffect> effects) {}

    public ReviewProgress reviewProgress() {
        long owner=auth.currentUserId();
        var cycles=db.query("""
                SELECT c.path_item_id,c.task_id,t.status,i.topic,
                    (SELECT MAX(r.ended_at) FROM study_record r WHERE r.user_id=c.owner_id AND r.task_id=c.task_id)
                FROM adaptive_review_cycle c JOIN study_task t ON t.id=c.task_id AND t.user_id=c.owner_id
                JOIN study_path_item i ON i.id=c.path_item_id AND i.user_id=c.owner_id
                WHERE c.owner_id=? AND c.cycle_key LIKE 'ASSESS-%' ORDER BY c.created_ms DESC LIMIT 20
                """,(r,n)->new ReviewCycle(r.getLong(1),r.getLong(2),r.getString(3),r.getString(4),r.getObject(5,LocalDateTime.class)),owner);
        var known=questions.topics();
        var effects=new ArrayList<ReviewEffect>();
        for(var cycle:cycles) {
            if(cycle.completedAt()==null || !"COMPLETED".equals(cycle.status())) continue;
            var candidates=known.stream().filter(t->AssessmentTopics.related(cycle.topic(),t.title())).limit(2).toList();
            if(candidates.size()!=1) continue;
            String unit=candidates.get(0).id();
            var completion=cycle.completedAt();
            var rows=observations(owner,completion.minusDays(14),null,unit).stream()
                    .filter(row->row.sample().first() && row.sample().submittedAt().isBefore(completion.plusDays(14))).toList();
            var versions=new TreeSet<String>();
            rows.forEach(r->versions.add(r.version()));
            for(String version:versions) for(var difficulty:Difficulty.values()) {
                var exam=rows.stream().filter(r->r.version().equals(version) && r.sample().difficulty()==difficulty)
                        .map(Observation::sample).toList();
                var before=count(exam.stream().filter(r->r.submittedAt().isBefore(completion)).toList());
                var after=count(exam.stream().filter(r->!r.submittedAt().isBefore(completion)).toList());
                if(before.total()+after.total()>0) effects.add(new ReviewEffect(cycle.itemId(),cycle.taskId(),cycle.status(),
                        completion,unit,version,difficulty,before,after,before.total()>=minimum && after.total()>=minimum));
            }
        }
        return new ReviewProgress(cycles.size(),(int)cycles.stream().filter(cycle->"COMPLETED".equals(cycle.status())).count(),List.copyOf(effects));
    }

    private record ReviewCycle(long itemId,long taskId,String status,String topic,LocalDateTime completedAt) {}

    static TopicResult measure(String unit,String title,String version,List<Sample> newest,int minimum) {
        var first=newest.stream().filter(Sample::first).toList();
        var repeated=newest.stream().filter(row->!row.first()).toList();
        var newCounts=count(first);
        int misses=(int)first.stream().takeWhile(row->!row.correct()).count();
        double accuracy=newCounts.total()==0?0:newCounts.correct()/(double)newCounts.total();
        Level level=newCounts.total()<minimum?Level.INSUFFICIENT_DATA:misses>=3 || accuracy<0.6?Level.NEEDS_PRACTICE
                :accuracy>=0.85?Level.CONSISTENT:Level.DEVELOPING;
        var byDifficulty=new EnumMap<Difficulty,Counts>(Difficulty.class);
        for(var difficulty:Difficulty.values()) byDifficulty.put(difficulty,count(first.stream().filter(row->row.difficulty()==difficulty).toList()));
        return new TopicResult(unit,title,version,level,newCounts,count(repeated),misses,(first.isEmpty()?newest:first).get(0).submittedAt(),Map.copyOf(byDifficulty));
    }

    public List<Wrong> wrong(Long subject,String unit,int page) {
        if(page<1 || page>100) throw error(VALIDATION_ERROR);
        if(unit!=null) questions.topic(unit);
        var names=new HashMap<String,String>();
        questions.topics().forEach(topic->names.put(topic.id(),topic.title()));
        return db.query("""
                SELECT s.id,i.ordinal,q.id,v.id,q.unit_id,s.subject_id,v.software_version,i.error_type,s.submitted_at
                FROM assessment_session s JOIN assessment_session_item i ON i.session_id=s.id
                JOIN assessment_question_version v ON v.id=i.question_version_id JOIN assessment_question q ON q.id=v.question_id
                WHERE s.owner_id=? AND s.state='SUBMITTED' AND i.grade='INCORRECT'
                  AND (? IS NULL OR s.subject_id=?) AND (? IS NULL OR q.unit_id=?)
                ORDER BY s.submitted_at DESC,s.id DESC,i.ordinal LIMIT 20 OFFSET ?
                """,(r,n)->new Wrong(UUID.fromString(r.getString(1)),r.getInt(2),r.getLong(3),r.getLong(4),r.getString(5),
                names.getOrDefault(r.getString(5),r.getString(5)),r.getObject(6,Long.class),r.getString(7),
                r.getString(8)==null?null:ErrorType.valueOf(r.getString(8)),r.getObject(9,LocalDateTime.class)),
                auth.currentUserId(),subject,subject,unit,unit,(page-1)*20);
    }

    private static int number(Map<String,Object> data,String name) { return ((Number)data.get(name)).intValue(); }

    private static Counts count(List<Sample> samples) {
        return new Counts(samples.size(),(int)samples.stream().filter(Sample::correct).count());
    }
}
