package com.yhk.aistudyplanner.assessment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import com.yhk.aistudyplanner.knowledge.repository.KnowledgeRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssessmentQuestions {
    public enum Kind { SINGLE_CHOICE, SHORT_ANSWER }
    public enum Difficulty { BASIC, INTERMEDIATE, ADVANCED }
    public enum State { DRAFT, PENDING_REVIEW, APPROVED, PUBLISHED, REJECTED, ARCHIVED }
    public enum ErrorType { CONCEPT_CONFUSION, MISSING_CONCEPT, VERSION_ERROR, CONDITION_ERROR,
        PREREQUISITE_GAP, PARTIALLY_CORRECT, INSUFFICIENT_EVIDENCE }
    public record Option(@NotBlank @Size(max=500) String text, ErrorType errorType) {}
    public record Criterion(@NotBlank @Size(max=200) String concept,
                            @NotEmpty @Size(max=8) List<@NotBlank @Size(max=160) String> accepted) {}
    public record Definition(@Size(max=6) List<@NotNull @Valid Option> options, Integer correctIndex,
                             @NotBlank @Size(max=1000) String referenceAnswer,
                             @NotBlank @Size(max=1000) String explanation,
                             @Size(max=8) List<@NotNull @Valid Criterion> rubric,
                             @Size(max=8) List<@NotBlank @Size(max=160) String> commonErrors,
                             @NotBlank @Size(min=12,max=800) String quote) {}
    public record Draft(@NotBlank @Size(max=100) String unitId, @NotNull Kind kind, @NotNull Difficulty difficulty,
                        @NotBlank @Size(max=80) String softwareVersion, @NotBlank @Size(min=10,max=1000) String stem,
                        @NotNull @Valid Definition definition, @NotNull UUID sourceVersion,
                        @Min(1) int sourceOrdinal, boolean publicBank) {}
    public record Decision(@NotNull State state, @NotBlank @Size(max=500) String rationale) {}
    public record Source(String documentId, UUID versionId, int ordinal, int line, String title, String url,
                         String softwareVersion, String excerpt, boolean current) {}
    public record BankEntry(long questionId, long versionId, int revision, String unitId, Kind kind,
                            Difficulty difficulty, State state, String softwareVersion, String stem,
                            boolean publicBank, boolean needsSourceReview) {}
    public record Managed(BankEntry question, Definition definition, Source source) {}
    record Version(BankEntry entry, Long owner, Definition definition, UUID sourceVersion, int ordinal) {}
    public record Topic(String id, String title, String domain, List<String> prerequisites) {}

    private final JdbcTemplate db;
    private final AuthSessionService auth;
    private final KnowledgeRepository knowledge;
    private final ObjectMapper json;
    private final Clock clock;
    private final Set<String> reviewers;

    AssessmentQuestions(JdbcTemplate db, AuthSessionService auth, KnowledgeRepository knowledge, ObjectMapper json,
                        Clock clock, @org.springframework.beans.factory.annotation.Value("${app.knowledge.reviewers:}") String reviewers) {
        this.db=db; this.auth=auth; this.knowledge=knowledge; this.json=json; this.clock=clock;
        this.reviewers=Arrays.stream(reviewers.split(",")).map(String::strip).filter(s->!s.isEmpty())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
    }

    public List<Topic> topics() {
        return knowledge.findAll().stream().map(unit->new Topic(unit.id(),unit.topic(),unit.domain().name(),unit.prerequisites())).toList();
    }

    public Topic topic(String id) {
        return knowledge.findById(id).map(unit->new Topic(unit.id(),unit.topic(),unit.domain().name(),unit.prerequisites()))
                .orElseThrow(()->error(ErrorCode.RESOURCE_NOT_FOUND));
    }

    public boolean reviewer() { return reviewers.contains(Long.toString(auth.currentUserId())); }

    @Transactional
    public Managed create(Draft draft) {
        return add(null,List.of(draft)).get(0);
    }

    @Transactional
    public Managed revise(long questionId,Draft draft) {
        return add(questionId,List.of(draft)).get(0);
    }

    @Transactional
    public List<Managed> createBatch(List<Draft> drafts) { return add(null,drafts); }

    private List<Managed> add(Long existing,List<Draft> drafts) {
        long actor=auth.currentUserId();
        if(drafts.stream().anyMatch(Draft::publicBank)) requireReviewer();
        db.queryForList("SELECT id FROM `user` WHERE id=? FOR UPDATE",Long.class,actor);
        if(db.queryForObject("SELECT COUNT(*) FROM assessment_question WHERE owner_id=?",Integer.class,actor)>=500)
            throw error(ErrorCode.AGENT_LIMIT);
        var output=new ArrayList<Managed>();
        for(var draft:drafts) {
            topic(draft.unitId());
            validate(draft);
            Long owner=draft.publicBank()?null:actor;
            var source=source(draft.sourceVersion(),draft.sourceOrdinal(),owner,true);
            if(source==null || !draft.softwareVersion().equals(source.softwareVersion())
                    || !source.excerpt().contains(draft.definition().quote())) throw error(ErrorCode.KNOWLEDGE_CONFLICT);
            long id;
            int revision=1;
            if(existing==null) {
                id=insert("INSERT INTO assessment_question(owner_id,unit_id,created_at) VALUES(?,?,?)",
                        owner,draft.unitId(),now());
            } else {
                var previous=latest(existing,true);
                requireManage(previous);
                if(!Objects.equals(owner,previous.owner()) || !draft.unitId().equals(previous.entry().unitId()))
                    throw error(ErrorCode.VALIDATION_ERROR);
                id=existing;
                revision=previous.entry().revision()+1;
            }
            String hash=fingerprint(draft.stem());
            if(db.queryForObject("""
                    SELECT COUNT(*) FROM assessment_question_version v JOIN assessment_question q ON q.id=v.question_id
                    WHERE v.fingerprint=? AND v.software_version=? AND q.unit_id=? AND q.id<>? AND (q.owner_id IS NULL OR q.owner_id=?)
                      AND v.state NOT IN ('ARCHIVED','REJECTED')
                    """,Integer.class,hash,draft.softwareVersion(),draft.unitId(),id,actor)>0) throw error(ErrorCode.DATA_CONFLICT);
            long version=insert("""
                    INSERT INTO assessment_question_version(question_id,revision,state,kind,difficulty,software_version,
                      stem,fingerprint,definition,source_version,source_ordinal,created_at)
                    VALUES(?,?,'DRAFT',?,?,?,?,?,?,?,?,?)
                    """,id,revision,draft.kind().name(),draft.difficulty().name(),draft.softwareVersion(),draft.stem(),
                    hash,encode(draft.definition()),draft.sourceVersion().toString(),draft.sourceOrdinal(),now());
            output.add(managed(version(version)));
        }
        return List.copyOf(output);
    }

    public List<BankEntry> bank(boolean manage,String unitId) {
        Long owner=auth.currentUserId();
        if(manage && reviewer()) return entries("WHERE (q.owner_id IS NULL OR q.owner_id=?)",owner,unitId);
        return entries(manage?"WHERE q.owner_id=?":"WHERE (q.owner_id IS NULL OR q.owner_id=?) AND v.state='PUBLISHED'",owner,unitId)
                .stream().filter(row->manage || !row.needsSourceReview()).toList();
    }

    private List<BankEntry> entries(String scope,long owner,String unitId) {
        List<Version> rows=db.query(SELECT+scope+" AND (? IS NULL OR q.unit_id=?) ORDER BY v.id DESC LIMIT 100",
                this::read,owner,unitId,unitId);
        return rows.stream().map(row->checked(row,current(row))).toList();
    }

    public Managed inspect(long versionId) {
        var row=version(versionId);
        requireManage(row);
        return managed(row);
    }

    @Transactional
    public Managed decide(long versionId,Decision decision) {
        var row=version(versionId);
        requireManage(row);
        latest(row.entry().questionId(),true);
        row=version(versionId);
        State from=row.entry().state();
        if(!allows(from,decision.state())) throw error(ErrorCode.DATA_CONFLICT);
        if(decision.state()==State.APPROVED || decision.state()==State.PUBLISHED) {
            var current=source(row.sourceVersion(),row.ordinal(),row.owner(),true);
            if(current==null || !current.excerpt().contains(row.definition().quote())
                    || !row.entry().softwareVersion().equals(current.softwareVersion())) throw error(ErrorCode.KNOWLEDGE_CONFLICT);
        }
        if(decision.state()==State.PUBLISHED) {
            var published=db.query(SELECT+"WHERE q.id=? AND v.state='PUBLISHED' ORDER BY v.revision DESC",this::read,row.entry().questionId());
            for(var prior:published) {
                if(prior.entry().revision()>row.entry().revision()) throw error(ErrorCode.DATA_CONFLICT);
                transition(prior,State.ARCHIVED,"已由更新的审核版本替换");
            }
        }
        transition(row,decision.state(),decision.rationale());
        return managed(version(versionId));
    }

    static boolean allows(State from,State to) {
        return switch(from) {
            case DRAFT -> to==State.PENDING_REVIEW || to==State.ARCHIVED;
            case PENDING_REVIEW -> to==State.APPROVED || to==State.REJECTED;
            case APPROVED -> to==State.PUBLISHED || to==State.REJECTED || to==State.ARCHIVED;
            case PUBLISHED, REJECTED -> to==State.ARCHIVED;
            case ARCHIVED -> false;
        };
    }

    private void transition(Version row,State state,String rationale) {
        db.update("UPDATE assessment_question_version SET state=? WHERE id=?",state.name(),row.entry().versionId());
        db.update("""
                INSERT INTO assessment_question_review(version_id,actor_id,previous_state,next_state,rationale,decided_at)
                VALUES(?,?,?,?,?,?)
                """,row.entry().versionId(),auth.currentUserId(),row.entry().state().name(),state.name(),rationale,now());
    }

    static void validate(Draft draft) {
        var def=draft.definition();
        if(draft.kind()==Kind.SINGLE_CHOICE) {
            if(def.options()==null || def.options().size()<3 || def.correctIndex()==null || def.correctIndex()<0
                    || def.correctIndex()>=def.options().size()
                    || def.options().stream().map(o->fingerprint(o.text())).distinct().count()!=def.options().size()
                    || def.options().get(def.correctIndex()).errorType()!=null || def.rubric()!=null && !def.rubric().isEmpty())
                throw error(ErrorCode.VALIDATION_ERROR);
        } else if(def.correctIndex()!=null || def.options()!=null && !def.options().isEmpty()
                || def.rubric()==null || def.rubric().size()<2 || def.commonErrors()==null || def.commonErrors().isEmpty())
            throw error(ErrorCode.VALIDATION_ERROR);
    }

    static String fingerprint(String text) {
        String key=java.text.Normalizer.normalize(text,java.text.Normalizer.Form.NFKC).toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]","");
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(key.getBytes(StandardCharsets.UTF_8))); }
        catch(NoSuchAlgorithmException failure) { throw new IllegalStateException(failure); }
    }

    private static final String SELECT="""
            SELECT q.id,q.owner_id,q.unit_id,v.id,v.revision,v.kind,v.difficulty,v.state,v.software_version,v.stem,
                   v.definition,v.source_version,v.source_ordinal
            FROM assessment_question_version v JOIN assessment_question q ON q.id=v.question_id
            """;

    Version version(long id) {
        return one(db.query(SELECT+"WHERE v.id=?",this::read,id));
    }

    private Version latest(long id,boolean lock) {
        return one(db.query(SELECT+"WHERE q.id=? ORDER BY v.revision DESC LIMIT 1"+(lock?" FOR UPDATE":""),this::read,id));
    }

    private Version one(List<Version> rows) {
        if(rows.isEmpty()) throw error(ErrorCode.RESOURCE_NOT_FOUND);
        return rows.get(0);
    }

    private Version read(ResultSet row,int index) throws SQLException {
        var owner=row.getObject(2,Long.class);
        try {
            var entry=new BankEntry(row.getLong(1),row.getLong(4),row.getInt(5),row.getString(3),Kind.valueOf(row.getString(6)),
                    Difficulty.valueOf(row.getString(7)),State.valueOf(row.getString(8)),row.getString(9),row.getString(10),owner==null,false);
            return new Version(entry,owner,json.readValue(row.getString(11),Definition.class),UUID.fromString(row.getString(12)),row.getInt(13));
        } catch(com.fasterxml.jackson.core.JsonProcessingException failure) { throw new SQLException(failure); }
    }

    Source current(Version row) { return source(row.sourceVersion(),row.ordinal(),row.owner(),true); }

    Source source(UUID version,int ordinal,Long owner,boolean currentOnly) {
        var rows=db.query("""
                SELECT d.id,v.id,c.ordinal,c.start_line,v.title,d.origin,v.software_version,c.excerpt,
                       (v.id=d.active_version AND v.status='PUBLISHED') AS current_source
                FROM knowledge_document_version v JOIN knowledge_document d ON d.id=v.document_id
                JOIN knowledge_chunk c ON c.version_id=v.id
                WHERE v.id=? AND c.ordinal=?
                  AND ((d.owner_id IS NULL AND d.source_id IS NOT NULL) OR d.owner_id=?)
                """+(currentOnly?" AND v.id=d.active_version AND v.status='PUBLISHED'":""),(r,n)->new Source(
                r.getString(1),UUID.fromString(r.getString(2)),r.getInt(3),r.getInt(4),r.getString(5),r.getString(6),
                r.getString(7),r.getString(8),r.getBoolean(9)),version.toString(),ordinal,owner);
        return rows.isEmpty()?null:rows.get(0);
    }

    private Managed managed(Version row) {
        var current=current(row);
        return new Managed(checked(row,current),row.definition(),current);
    }

    private static BankEntry checked(Version row,Source source) {
        var e=row.entry();
        return new BankEntry(e.questionId(),e.versionId(),e.revision(),e.unitId(),e.kind(),e.difficulty(),e.state(),
                e.softwareVersion(),e.stem(),e.publicBank(),source==null);
    }

    private void requireManage(Version row) {
        if(row.owner()!=null && row.owner()==auth.currentUserId()) return;
        if(row.owner()==null && reviewer()) return;
        throw error(ErrorCode.RESOURCE_NOT_FOUND);
    }

    private void requireReviewer() {
        if(!reviewer()) throw error(ErrorCode.KNOWLEDGE_REVIEW_FORBIDDEN);
    }

    String encode(Object value) {
        try { return json.writeValueAsString(value); }
        catch(com.fasterxml.jackson.core.JsonProcessingException failure) { throw new IllegalStateException(failure); }
    }

    private long insert(String sql,Object... args) {
        var key=new GeneratedKeyHolder();
        db.update(connection->{
            var stmt=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);
            for(int i=0;i<args.length;i++) stmt.setObject(i+1,args[i]);
            return stmt;
        },key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    LocalDateTime now() { return LocalDateTime.now(clock); }
    static BusinessException error(ErrorCode code) { return new BusinessException(code); }
}
