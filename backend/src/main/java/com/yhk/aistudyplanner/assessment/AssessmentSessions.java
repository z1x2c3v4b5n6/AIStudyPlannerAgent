package com.yhk.aistudyplanner.assessment;

import static com.yhk.aistudyplanner.assessment.AssessmentQuestions.*;
import static com.yhk.aistudyplanner.common.exception.ErrorCode.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.learningpath.service.LearningPathService;
import com.yhk.aistudyplanner.subject.service.SubjectService;
import jakarta.validation.constraints.*;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssessmentSessions {
    public enum Mode { TOPIC, LEARNING_PATH, WRONG_ANSWERS, COMPREHENSIVE }
    public enum Status { IN_PROGRESS, SUBMITTED, CANCELLED, EXPIRED }
    public record Start(@NotNull Mode mode,@Positive Long subjectId,@Positive Long pathId,
                        @Size(max=100) String unitId,@Size(max=80) String softwareVersion,Difficulty difficulty,
                        @Min(1) @Max(20) int count, @Size(max=12) List<@NotBlank @Size(max=100) String> scope) {}
    record Save(@NotBlank @Size(max=2000) String answer) {}
    public record Session(UUID id, Mode mode, Long subjectId, Long pathId, Status status,
                          LocalDateTime startedAt,LocalDateTime expiresAt,LocalDateTime submittedAt,int scoringVersion) {}
    record Item(int ordinal,long questionId,long versionId,String unitId,Kind kind,Difficulty difficulty,String softwareVersion,
                String stem,List<String> options,String answer,boolean available) {}
    record Mark(int ordinal,AssessmentScoring.Grade grade,ErrorType errorType,String correctAnswer,
                Integer correctIndex,String explanation,List<Criterion> rubric,Source source,JsonNode assistance) {}
    record View(Session session,List<Item> items,List<Mark> marks,int total,int scored,int correct,int needsReview) {}
    record AnswerRow(int ordinal,Version question,String answer,AssessmentScoring.Grade grade,ErrorType errorType,JsonNode assistance) {}
    public record Started(UUID id,int count,String url) {}
    private final JdbcTemplate db;
    private final AuthSessionService auth;
    private final AssessmentQuestions questions;
    private final LearningPathService paths;
    private final SubjectService subjects;
    private final ObjectMapper json;

    AssessmentSessions(JdbcTemplate db,AuthSessionService auth,AssessmentQuestions questions,LearningPathService paths,
                       SubjectService subjects,ObjectMapper json) {
        this.db=db;this.auth=auth;this.questions=questions;this.paths=paths;this.subjects=subjects;this.json=json;
    }

    @Transactional
    public Started start(Start input) {
        if(input.count()<1 || input.count()>20 || input.mode()==null) throw error(VALIDATION_ERROR);
        long owner=auth.currentUserId();
        Long subject=input.subjectId();
        if(subject!=null) subjects.get(subject);
        Set<String> units=new LinkedHashSet<>();
        if(input.unitId()!=null && !input.unitId().isBlank()) units.add(questions.topic(input.unitId()).id());
        if(input.scope()!=null) for(String id:input.scope()) units.add(questions.topic(id).id());
        if(input.mode()==Mode.TOPIC && (units.size()!=1 || input.pathId()!=null)) throw error(VALIDATION_ERROR);
        if(input.mode()==Mode.LEARNING_PATH) {
            if(input.pathId()==null) throw error(VALIDATION_ERROR);
            var path=paths.get(input.pathId());
            if(subject!=null && !subject.equals(path.subjectId())) throw error(VALIDATION_ERROR);
            subject=path.subjectId();
            var linked=questions.topics().stream().filter(t->path.items().stream()
                    .anyMatch(item->AssessmentTopics.related(item.topic(),t.title()))).map(Topic::id).toList();
            if(units.isEmpty()) units.addAll(linked);
            else units.retainAll(linked);
            if(units.isEmpty()) throw error(RESOURCE_NOT_FOUND);
        } else if(input.pathId()!=null) throw error(VALIDATION_ERROR);
        if(input.mode()==Mode.WRONG_ANSWERS) {
            Set<String> weak=new HashSet<>(db.queryForList("""
                    SELECT DISTINCT q.unit_id FROM assessment_session s
                    JOIN assessment_session_item i ON i.session_id=s.id
                    JOIN assessment_question_version v ON v.id=i.question_version_id
                    JOIN assessment_question q ON q.id=v.question_id
                    WHERE s.owner_id=? AND s.state='SUBMITTED' AND i.grade='INCORRECT'
                      AND (? IS NULL OR s.subject_id=?)
                    """,String.class,owner,subject,subject));
            if(units.isEmpty()) units.addAll(weak);
            else units.retainAll(weak);
            if(units.isEmpty()) throw error(RESOURCE_NOT_FOUND);
        }
        db.queryForList("SELECT id FROM `user` WHERE id=? FOR UPDATE",Long.class,owner);
        LocalDateTime now=questions.now();
        if(db.queryForObject("""
                SELECT COUNT(*) FROM assessment_session WHERE owner_id=? AND state='IN_PROGRESS' AND expires_at>?
                """,Integer.class,owner,now)>=10) throw error(AGENT_LIMIT);

        var selected=select(owner,input,units);
        if(selected.isEmpty()) throw error(RESOURCE_NOT_FOUND);
        UUID id=UUID.randomUUID();
        db.update("""
                INSERT INTO assessment_session(id,owner_id,subject_id,path_id,mode,state,started_at,expires_at,scoring_version)
                VALUES(?,?,?,?,?,'IN_PROGRESS',?,?,?)
                """,id.toString(),owner,subject,input.pathId(),input.mode().name(),now,now.plusHours(24),AssessmentScoring.VERSION);
        db.batchUpdate("INSERT INTO assessment_session_item(session_id,ordinal,question_version_id) VALUES(?,?,?)",
                java.util.stream.IntStream.range(0,selected.size()).mapToObj(i->new Object[]{id.toString(),i+1,
                        selected.get(i).entry().versionId()}).toList());
        return new Started(id,selected.size(),"/assessments?session="+id);
    }

    private List<Version> select(long owner,Start input,Set<String> units) {
        var seen=new HashSet<>(db.queryForList("""
                SELECT DISTINCT v.question_id FROM assessment_session s JOIN assessment_session_item i ON i.session_id=s.id
                JOIN assessment_question_version v ON v.id=i.question_version_id WHERE s.owner_id=? AND s.state='SUBMITTED'
                """,Long.class,owner));
        var previousErrors=new HashSet<Long>();
        if(input.mode()==Mode.WRONG_ANSWERS) previousErrors.addAll(db.queryForList("""
                SELECT DISTINCT v.question_id FROM assessment_session s JOIN assessment_session_item i ON i.session_id=s.id
                JOIN assessment_question_version v ON v.id=i.question_version_id
                WHERE s.owner_id=? AND s.state='SUBMITTED' AND i.grade='INCORRECT'
                """,Long.class,owner));
        var options=questions.practice(owner,units,input.softwareVersion(),input.difficulty());
        var pool=new ArrayList<>(options);
        pool.sort(Comparator.comparing((Version x)->seen.contains(x.entry().questionId()))
                .thenComparing(x->!previousErrors.contains(x.entry().questionId()))
                .thenComparingLong(x->x.entry().versionId()));
        return balanced(pool,input.count(),input.mode()==Mode.COMPREHENSIVE);
    }

    static List<Version> balanced(List<Version> pool,int size,boolean distribute) {
        if(!distribute) return pool.stream().limit(size).toList();
        var buckets=new LinkedHashMap<String,ArrayDeque<Version>>();
        for(var row:pool) buckets.computeIfAbsent(row.entry().unitId()+":"+row.entry().difficulty(),key->new ArrayDeque<>()).add(row);
        var selected=new ArrayList<Version>();
        while(selected.size()<Math.min(size,pool.size())) {
            for(var bucket:buckets.values()) {
                if(!bucket.isEmpty()) selected.add(bucket.removeFirst());
                if(selected.size()>=size) break;
            }
        }
        return List.copyOf(selected);
    }

    public List<Session> history(int page) {
        if(page<1 || page>100) throw error(VALIDATION_ERROR);
        return db.query("""
                SELECT * FROM assessment_session WHERE owner_id=? ORDER BY started_at DESC,id DESC LIMIT 20 OFFSET ?
                """,this::read,auth.currentUserId(),(page-1)*20);
    }

    @Transactional(readOnly=true)
    public View get(UUID id) { return view(owned(id,false)); }

    @Transactional
    public View save(UUID id,int ordinal,Save input) {
        var session=owned(id,true);
        requireOpen(session);
        var row=item(id,ordinal);
        if(questions.current(row.question())==null) throw error(KNOWLEDGE_CONFLICT);
        AssessmentScoring.validateAnswer(row.question().entry().kind(),row.question().definition(),input.answer());
        db.update("UPDATE assessment_session_item SET answer=? WHERE session_id=? AND ordinal=?",input.answer(),id.toString(),ordinal);
        return view(session);
    }

    @Transactional
    public View submit(UUID id) {
        var session=owned(id,true);
        if(session.status()==Status.SUBMITTED) return view(session);
        requireOpen(session);
        var rows=items(id);
        if(rows.stream().allMatch(row->row.answer()==null)) throw error(VALIDATION_ERROR);
        for(var row:rows) {
            var question=row.question();
            var grade=AssessmentScoring.score(question.entry().kind(),question.definition(),row.answer(),questions.current(question)!=null);
            db.update("UPDATE assessment_session_item SET grade=?,error_type=? WHERE session_id=? AND ordinal=?",
                    grade.grade().name(),grade.errorType()==null?null:grade.errorType().name(),id.toString(),row.ordinal());
        }
        db.update("UPDATE assessment_session SET state='SUBMITTED',submitted_at=? WHERE id=?",questions.now(),id.toString());
        return view(owned(id,false));
    }

    @Transactional
    public View cancel(UUID id) {
        var session=owned(id,true);
        requireOpen(session);
        db.update("UPDATE assessment_session SET state='CANCELLED' WHERE id=?",id.toString());
        return view(owned(id,false));
    }

    Session owned(UUID id,boolean lock) {
        var rows=db.query("SELECT * FROM assessment_session WHERE id=? AND owner_id=?"+(lock?" FOR UPDATE":""),
                this::read,id.toString(),auth.currentUserId());
        if(rows.isEmpty()) throw error(RESOURCE_NOT_FOUND);
        return rows.get(0);
    }

    private Session read(ResultSet row,int n) throws SQLException {
        var state=Status.valueOf(row.getString("state"));
        var expiry=row.getObject("expires_at",LocalDateTime.class);
        if(state==Status.IN_PROGRESS && !expiry.isAfter(questions.now())) state=Status.EXPIRED;
        return new Session(UUID.fromString(row.getString("id")),Mode.valueOf(row.getString("mode")),
                row.getObject("subject_id",Long.class),row.getObject("path_id",Long.class),state,
                row.getObject("started_at",LocalDateTime.class),expiry,row.getObject("submitted_at",LocalDateTime.class),
                row.getInt("scoring_version"));
    }

    private void requireOpen(Session session) {
        if(session.status()!=Status.IN_PROGRESS) throw error(DATA_CONFLICT);
    }

    AnswerRow item(UUID id,int ordinal) {
        return items(id).stream().filter(row->row.ordinal()==ordinal).findFirst().orElseThrow(()->error(RESOURCE_NOT_FOUND));
    }

    private List<AnswerRow> items(UUID id) {
        var rows=db.query("""
                SELECT ordinal,question_version_id,answer,grade,error_type,assistance
                FROM assessment_session_item WHERE session_id=? ORDER BY ordinal
                """,(r,n)->new Stored(r.getInt(1),r.getLong(2),r.getString(3),r.getString(4),r.getString(5),r.getString(6)),id.toString());
        return rows.stream().map(r->new AnswerRow(r.ordinal(),questions.version(r.versionId()),r.answer(),
                r.grade()==null?null:AssessmentScoring.Grade.valueOf(r.grade()),
                r.error()==null?null:ErrorType.valueOf(r.error()),r.assistance()==null?null:decode(r.assistance()))).toList();
    }

    private record Stored(int ordinal,long versionId,String answer,String grade,String error,String assistance) {}

    private JsonNode decode(String data) {
        try { return json.readTree(data); }
        catch(com.fasterxml.jackson.core.JsonProcessingException failure) { throw new IllegalStateException(failure); }
    }

    private View view(Session session) {
        var items=new ArrayList<Item>();
        var marks=new ArrayList<Mark>();
        for(var row:items(session.id())) {
            var q=row.question();
            var e=q.entry();
            var def=q.definition();
            Source live=questions.current(q);
            boolean visible=live!=null || session.status()==Status.SUBMITTED && q.owner()==null;
            items.add(new Item(row.ordinal(),e.questionId(),e.versionId(),e.unitId(),e.kind(),e.difficulty(),e.softwareVersion(),
                    visible?e.stem():null,visible && def.options()!=null?def.options().stream().map(Option::text).toList():List.of(),
                    row.answer(),live!=null));
            if(session.status()==Status.SUBMITTED) {
                var source=visible?questions.source(q.sourceVersion(),q.ordinal(),q.owner(),false):null;
                marks.add(new Mark(row.ordinal(),row.grade(),row.errorType(),visible?def.referenceAnswer():null,
                        visible?def.correctIndex():null,visible?def.explanation():null,visible?def.rubric():null,source,
                        visible?row.assistance():null));
            }
        }
        int correct=(int)marks.stream().filter(mark->mark.grade()==AssessmentScoring.Grade.CORRECT).count();
        int incorrect=(int)marks.stream().filter(mark->mark.grade()==AssessmentScoring.Grade.INCORRECT).count();
        int needsReview=(int)marks.stream().filter(mark->mark.grade()==AssessmentScoring.Grade.NEEDS_REVIEW).count();
        return new View(session,List.copyOf(items),List.copyOf(marks),items.size(),correct+incorrect,correct,needsReview);
    }
}
