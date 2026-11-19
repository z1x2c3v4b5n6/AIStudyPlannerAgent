package com.yhk.aistudyplanner.knowledge.hub;

import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeDomain;
import com.yhk.aistudyplanner.knowledge.repository.KnowledgeRepository;
import jakarta.validation.constraints.*;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KnowledgeHub {
    enum State { DRAFT, PENDING_REVIEW, APPROVED, PUBLISHED, SUPERSEDED, REJECTED, ARCHIVED }
    enum Decision { SUBMIT, APPROVE, REJECT, PUBLISH, ROLLBACK, ARCHIVE }
    enum FeedbackType { HELPFUL, UNSOLVED, INCORRECT, STALE, INCOMPATIBLE, INCOMPLETE }
    enum QueryType { CONCEPT, CONFIGURATION, API, TROUBLESHOOTING, OTHER }

    public record Source(String id, String name, String url, KnowledgeDomain domain, String softwareVersion,
                         LocalDateTime checkedAt, LocalDateTime lastSuccessAt, String lastError) {}
    record NewSource(@NotBlank @Size(max=120) String name, @NotBlank @Size(max=500) String url, @NotNull KnowledgeDomain domain,
                     @Size(max=80) String softwareVersion) {}
    record Import(@NotBlank @Size(max=200) String title, @NotBlank @Size(max=100000) String text,
                  @NotNull KnowledgeDomain domain, @Size(max=100) String unitId, @Size(max=80) String softwareVersion) {}
    record Version(String id, String documentId, State status, String title, String softwareVersion,
                   String hash, LocalDateTime createdAt) {}
    record Review(@NotNull Decision decision, @NotBlank @Size(max=500) String reason) {}
    record Document(String id, Long owner, String sourceId, String origin, String unitId, KnowledgeDomain domain, String active) {}
    record Detail(Version version, String text, List<EvidenceText.Chunk> chunks) {}
    record Audit(String versionId, String previous, String operation, long actor, String reason, String hash, LocalDateTime time) {}
    record Feedback(@NotNull FeedbackType category, @Size(max=80) String environment,
                    @NotNull QueryType queryType, @Positive int ordinal) {}
    record Signal(String versionId, String environment, QueryType queryType, int users) {}

    private final JdbcTemplate db;
    private final Clock clock;
    private final KnowledgeRepository units;
    private final AuthSessionService auth;
    private final Set<String> reviewers;
    private final int feedbackThreshold;

    KnowledgeHub(JdbcTemplate db, Clock clock, KnowledgeRepository units, AuthSessionService auth,
                 @Value("${app.knowledge.reviewers:}") String configuredReviewers,
                 @Value("${app.knowledge.feedback-threshold:3}") int feedbackThreshold) {
        this.db = db;
        this.clock = clock;
        this.units = units;
        this.auth = auth;
        reviewers = Set.copyOf(Arrays.stream(configuredReviewers.split(",")).map(String::strip)
                .filter(s -> s.matches("[1-9][0-9]*")).toList());
        this.feedbackThreshold = Math.max(2, feedbackThreshold);
    }

    boolean reviewer() { return reviewers.contains(Long.toString(auth.currentUserId())); }
    long requireReviewer() {
        long owner = auth.currentUserId();
        if (!reviewers.contains(Long.toString(owner))) throw error(ErrorCode.KNOWLEDGE_REVIEW_FORBIDDEN);
        return owner;
    }
    private static BusinessException error(ErrorCode code) { return new BusinessException(code); }
    private LocalDateTime now() { return LocalDateTime.now(clock); }

    Source addSource(NewSource request) {
        requireReviewer();
        String url = OfficialDocuments.validate(request.url()).toString();
        String id = UUID.randomUUID().toString();
        db.update("INSERT INTO knowledge_source(id,name,entry_url,domain,software_version) VALUES(?,?,?,?,?)",
                id,request.name().strip(),url,request.domain().name(),optional(request.softwareVersion()));
        return source(id,false);
    }

    List<Source> sources() {
        requireReviewer();
        return registeredSources();
    }

    List<Source> registeredSources() {
        return db.query("SELECT * FROM knowledge_source ORDER BY name,id LIMIT 100", this::readSource);
    }

    Source source(String source, boolean lock) {
        return one(db.query("SELECT * FROM knowledge_source WHERE id=?" + (lock?" FOR UPDATE":""),
                this::readSource,source));
    }

    void recordCheck(String source, String failure) {
        if (failure == null) db.update("UPDATE knowledge_source SET checked_at=?,last_success_at=?,last_error=NULL WHERE id=?",
                now(),now(),source);
        else db.update("UPDATE knowledge_source SET checked_at=?,last_error=? WHERE id=?",now(),failure,source);
    }

    private Source readSource(java.sql.ResultSet rs, int index) throws java.sql.SQLException {
        return new Source(rs.getString("id"),rs.getString("name"),rs.getString("entry_url"),
                KnowledgeDomain.valueOf(rs.getString("domain")),rs.getString("software_version"),
                rs.getObject("checked_at",LocalDateTime.class),rs.getObject("last_success_at",LocalDateTime.class),rs.getString("last_error"));
    }

    @Transactional
    public Version stage(String sourceId, Import input) {
        requireReviewer();
        return stageCandidate(sourceId,input);
    }

    private Version stageCandidate(String sourceId, Import input) {
        var source = source(sourceId,true);
        if (source.domain()!=input.domain() || !Objects.equals(source.softwareVersion(),optional(input.softwareVersion())))
            throw error(ErrorCode.VALIDATION_ERROR);
        String document = db.queryForList("SELECT id FROM knowledge_document WHERE source_id=?",String.class,sourceId)
                .stream().findFirst().orElseGet(() -> createDocument(null,sourceId,source.url(),input));
        if (!Objects.equals(document(document,false).unitId(),optional(input.unitId())))
            throw error(ErrorCode.KNOWLEDGE_CONFLICT);
        return append(document,input,State.PENDING_REVIEW);
    }

    @Transactional
    public Version stageCapture(Source source, OfficialDocuments.Captured snapshot, String requestedUnit) {
        var references = db.query("SELECT unit_id FROM knowledge_document WHERE source_id=?",
                (r,n)->Optional.ofNullable(r.getString(1)),source.id());
        String link = references.isEmpty() ? requestedUnit : references.get(0).orElse(null);
        return stageCandidate(source.id(),new Import(snapshot.title(),snapshot.text(),source.domain(),link,source.softwareVersion()));
    }

    List<Source> dueSources(LocalDateTime cutoff) {
        return db.query("""
                SELECT s.* FROM knowledge_source s JOIN knowledge_document d ON d.source_id=s.id
                WHERE s.checked_at IS NULL OR s.checked_at<? ORDER BY s.checked_at,s.id LIMIT 2
                """,this::readSource,cutoff);
    }

    record Difference(String activeVersion, String candidate, List<EvidenceText.Chunk> added,
                      List<EvidenceText.Chunk> removed, int addedCount, int removedCount) {}

    Difference difference(String id) {
        var candidate = detail(id,true);
        var doc = document(candidate.version().documentId(),false);
        if (doc.active()==null) return new Difference(null,id,candidate.chunks().stream().limit(20).toList(),
                List.of(),candidate.chunks().size(),0);
        var active = detail(doc.active(),true);
        var prior = active.chunks().stream().map(EvidenceText.Chunk::excerpt).collect(java.util.stream.Collectors.toSet());
        var next = candidate.chunks().stream().map(EvidenceText.Chunk::excerpt).collect(java.util.stream.Collectors.toSet());
        var added = candidate.chunks().stream().filter(chunk->!prior.contains(chunk.excerpt())).toList();
        var removed = active.chunks().stream().filter(chunk->!next.contains(chunk.excerpt())).toList();
        return new Difference(doc.active(),id,added.stream().limit(20).toList(),removed.stream().limit(20).toList(),added.size(),removed.size());
    }

    @Transactional
    public Version importPrivate(Import input) {
        long owner = auth.currentUserId();
        db.queryForList("SELECT id FROM `user` WHERE id=? FOR UPDATE",Long.class,owner);
        if (db.queryForObject("SELECT COUNT(*) FROM knowledge_document WHERE owner_id=?",Integer.class,owner) >= 100)
            throw error(ErrorCode.AGENT_LIMIT);
        String id = createDocument(owner,null,null,input);
        Version version = append(id,input,State.PUBLISHED);
        db.update("UPDATE knowledge_document SET active_version=? WHERE id=?",version.id(),id);
        return version;
    }

    private String createDocument(Long owner, String source, String origin, Import input) {
        String unit = optional(input.unitId());
        if (unit != null && units.findById(unit).filter(item -> item.domain() == input.domain()).isEmpty())
            throw error(ErrorCode.VALIDATION_ERROR);
        String id = UUID.randomUUID().toString();
        db.update("""
                INSERT INTO knowledge_document(id,owner_id,source_id,origin,unit_id,domain,created_at)
                VALUES(?,?,?,?,?,?,?)
                """, id,owner,source,origin,unit,input.domain().name(),now());
        return id;
    }

    private Version append(String document, Import input, State state) {
        String body = input.text().replace("\r\n","\n").replace('\r','\n').strip();
        if (body.isEmpty() || body.length() > 100000 || body.codePoints().anyMatch(cp ->
                Character.isISOControl(cp) && cp!='\n' && cp!='\t')) throw error(ErrorCode.VALIDATION_ERROR);
        String title = input.title().strip();
        String hash = EvidenceText.digest(title + "\n" + body);
        var prior = db.query("SELECT * FROM knowledge_document_version WHERE document_id=? AND content_hash=?",
                this::readVersion,document,hash);
        if (!prior.isEmpty()) return prior.get(0);
        if (db.queryForObject("SELECT COUNT(*) FROM knowledge_document_version WHERE document_id=?",Integer.class,document) >= 100)
            throw error(ErrorCode.AGENT_LIMIT);
        var chunks = EvidenceText.split(title,body);
        if (chunks.isEmpty() || chunks.size() > 250) throw error(ErrorCode.VALIDATION_ERROR);
        String id = UUID.randomUUID().toString();
        db.update("""
                INSERT INTO knowledge_document_version(id,document_id,status,title,software_version,body,content_hash,created_at)
                VALUES(?,?,?,?,?,?,?,?)
                """,id,document,state.name(),title,optional(input.softwareVersion()),body,hash,now());
        db.batchUpdate("INSERT INTO knowledge_chunk(version_id,ordinal,heading,start_line,excerpt) VALUES(?,?,?,?,?)",
                chunks.stream().map(chunk -> new Object[]{id,chunk.ordinal(),chunk.heading(),chunk.line(),chunk.excerpt()}).toList());
        return version(id);
    }

    @Transactional
    public Version review(String id, Review review) {
        long actor = requireReviewer();
        var candidate = version(id);
        var document = document(candidate.documentId(),true);
        if (document.owner()!=null) throw error(ErrorCode.RESOURCE_NOT_FOUND);
        candidate = version(id);
        var action = review.decision();
        State next = switch (action) {
            case SUBMIT -> require(candidate,State.DRAFT,State.PENDING_REVIEW);
            case APPROVE -> require(candidate,State.PENDING_REVIEW,State.APPROVED);
            case REJECT -> require(candidate,State.PENDING_REVIEW,State.REJECTED);
            case PUBLISH -> require(candidate,State.APPROVED,State.PUBLISHED);
            case ROLLBACK -> require(candidate,State.SUPERSEDED,State.PUBLISHED);
            case ARCHIVE -> require(candidate,State.REJECTED,State.ARCHIVED);
        };
        if (next == State.PUBLISHED) {
            OfficialDocuments.validate(document.origin());
            if (db.queryForObject("SELECT COUNT(*) FROM knowledge_chunk WHERE version_id=?",Integer.class,id)==0)
                throw error(ErrorCode.KNOWLEDGE_CONFLICT);
            if (document.active()!=null) setStatus(document.active(),State.SUPERSEDED);
            db.update("UPDATE knowledge_document SET active_version=? WHERE id=?",id,document.id());
        }
        setStatus(id,next);
        db.update("""
                INSERT INTO knowledge_publication(id,document_id,version_id,previous_version,operation,actor_id,reason,content_hash,created_at)
                VALUES(?,?,?,?,?,?,?,?,?)
                """,UUID.randomUUID().toString(),document.id(),id,document.active(),action.name(),actor,review.reason().strip(),candidate.hash(),now());
        return version(id);
    }

    private static State require(Version version, State current, State next) {
        if (version.status()!=current) throw error(ErrorCode.KNOWLEDGE_CONFLICT);
        return next;
    }

    private void setStatus(String id, State status) {
        db.update("UPDATE knowledge_document_version SET status=? WHERE id=?",status.name(),id);
    }

    List<Version> list(boolean managed) {
        if (managed) requireReviewer();
        return managed ? db.query("""
                SELECT v.* FROM knowledge_document_version v JOIN knowledge_document d ON d.id=v.document_id
                WHERE d.owner_id IS NULL ORDER BY v.created_at DESC,v.id LIMIT 100
                """, this::readVersion) : db.query("""
                SELECT v.* FROM knowledge_document_version v JOIN knowledge_document d ON d.active_version=v.id
                WHERE d.owner_id=? ORDER BY v.created_at DESC,v.id LIMIT 100
                """, this::readVersion,auth.currentUserId());
    }

    Detail detail(String id, boolean managed) {
        if (managed) requireReviewer();
        var version = version(id);
        var document = document(version.documentId(),false);
        if (managed) {
            if (document.owner()!=null) throw error(ErrorCode.RESOURCE_NOT_FOUND);
        } else if (!(Objects.equals(document.owner(),auth.currentUserId())
                || document.owner()==null && id.equals(document.active()) && version.status()==State.PUBLISHED)) {
            throw error(ErrorCode.RESOURCE_NOT_FOUND);
        }
        return new Detail(version,db.queryForObject("SELECT body FROM knowledge_document_version WHERE id=?",String.class,id),
                db.query("SELECT ordinal,heading,start_line,excerpt FROM knowledge_chunk WHERE version_id=? ORDER BY ordinal",
                        (r,n)->new EvidenceText.Chunk(r.getInt(1),r.getString(2),r.getInt(3),r.getString(4)),id));
    }

    List<Audit> history(String document) {
        requireReviewer();
        if (document(document,false).owner()!=null) throw error(ErrorCode.RESOURCE_NOT_FOUND);
        return db.query("""
                SELECT version_id,previous_version,operation,actor_id,reason,content_hash,created_at
                FROM knowledge_publication WHERE document_id=? ORDER BY created_at DESC,id LIMIT 100
                """,(r,n)->new Audit(r.getString(1),r.getString(2),r.getString(3),r.getLong(4),r.getString(5),r.getString(6),
                r.getObject(7,LocalDateTime.class)),document);
    }

    @Transactional
    public void removePrivate(String id) {
        if (db.update("DELETE FROM knowledge_document WHERE id=? AND owner_id=?",id,auth.currentUserId())!=1)
            throw error(ErrorCode.RESOURCE_NOT_FOUND);
    }

    @Transactional
    public void feedback(String id, Feedback input) {
        var candidate = version(id);
        var document = document(candidate.documentId(),true);
        if (document.owner()!=null || !id.equals(document.active())
                || db.queryForObject("SELECT COUNT(*) FROM knowledge_chunk WHERE version_id=? AND ordinal=?",Integer.class,id,input.ordinal())!=1)
            throw error(ErrorCode.RESOURCE_NOT_FOUND);
        long actor = auth.currentUserId();
        String environment = input.environment()==null ? "" : EvidenceText.normalize(input.environment().strip()).replaceAll("\\s+"," ");
        if (environment.length()>80 || !environment.matches("[a-z0-9 ._+/-]*")) throw error(ErrorCode.VALIDATION_ERROR);
        if (db.queryForObject("SELECT COUNT(*) FROM knowledge_feedback WHERE version_id=? AND user_id=?",Integer.class,id,actor)>0)
            throw error(ErrorCode.KNOWLEDGE_CONFLICT);
        db.update("""
                INSERT INTO knowledge_feedback(version_id,user_id,category,environment,query_type,ordinal,created_at)
                VALUES(?,?,?,?,?,?,?)
                """,id,actor,input.category().name(),environment,input.queryType().name(),input.ordinal(),now());
    }

    List<Signal> signals() {
        requireReviewer();
        return db.query("""
                SELECT f.version_id,f.environment,f.query_type,COUNT(*) votes
                FROM knowledge_feedback f JOIN knowledge_document d ON d.active_version=f.version_id
                WHERE f.created_at>=? AND f.category<>'HELPFUL' AND f.environment<>''
                GROUP BY f.version_id,f.environment,f.query_type HAVING COUNT(*)>=? ORDER BY votes DESC,f.version_id LIMIT 100
                """,(r,n)->new Signal(r.getString(1),r.getString(2),QueryType.valueOf(r.getString(3)),r.getInt(4)),
                now().minusDays(30),feedbackThreshold);
    }

    private Document document(String id, boolean lock) {
        return one(db.query("SELECT * FROM knowledge_document WHERE id=?" + (lock?" FOR UPDATE":""),
                (r,n)->new Document(r.getString("id"),r.getObject("owner_id",Long.class),r.getString("source_id"),
                        r.getString("origin"),r.getString("unit_id"),KnowledgeDomain.valueOf(r.getString("domain")),r.getString("active_version")),id));
    }

    private Version version(String id) { return one(db.query("SELECT * FROM knowledge_document_version WHERE id=?",this::readVersion,id)); }
    private Version readVersion(java.sql.ResultSet rs, int index) throws java.sql.SQLException {
        return new Version(rs.getString("id"),rs.getString("document_id"),State.valueOf(rs.getString("status")),
                rs.getString("title"),rs.getString("software_version"),rs.getString("content_hash"),
                rs.getObject("created_at",LocalDateTime.class));
    }
    private static String optional(String value) { return value==null || value.isBlank() ? null : value.strip(); }
    private static <T> T one(List<T> rows) {
        return rows.stream().findFirst().orElseThrow(()->error(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
