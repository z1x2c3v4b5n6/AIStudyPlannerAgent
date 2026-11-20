package com.yhk.aistudyplanner.knowledge.hub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.common.exception.*;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
class KnowledgeReviewCases {
    enum Outcome { OUTDATED, VERSION_MISMATCH, INCOMPLETE, WRONG_EVIDENCE, DID_NOT_SOLVE, INSUFFICIENT_EVIDENCE }
    record Close(@NotNull Outcome outcome) {}
    record CaseRow(String id,String versionId,String environment,String queryType,int users,String state,
                   String outcome,String candidateId,LocalDateTime updatedAt) {}
    record Evaluation(@NotBlank @Size(max=300) String query,@NotBlank @Size(max=80) String softwareVersion,
                      @NotBlank @Size(max=100) String expectedTerm) {}
    record Metrics(int count,int wrongVersion,int unrelated,int traceable,boolean hit) {}
    record Report(Metrics previous,Metrics current,Metrics candidate,boolean regression) {}
    record Run(String id,String query,String softwareVersion,String expectedTerm,Report report,LocalDateTime time) {}

    private final JdbcTemplate db;
    private final KnowledgeHub hub;
    private final OfficialDocuments collector;
    private final KnowledgeHubSearch search;
    private final ObjectMapper mapper;
    private final Clock clock;

    KnowledgeReviewCases(JdbcTemplate db,KnowledgeHub hub,OfficialDocuments collector,KnowledgeHubSearch search,
                         ObjectMapper mapper,Clock clock) {
        this.db=db;
        this.hub=hub;
        this.collector=collector;
        this.search=search;
        this.mapper=mapper;
        this.clock=clock;
    }

    List<CaseRow> list() {
        hub.requireReviewer();
        return db.query("SELECT * FROM knowledge_review_case ORDER BY updated_at DESC,id LIMIT 100",this::read);
    }

    CaseRow recheck(String id) {
        hub.requireReviewer();
        var review=get(id);
        var old=hub.detail(review.versionId(),true).version();
        var doc=hub.document(old.documentId(),false);
        var source=hub.source(doc.sourceId(),false);
        var run=UUID.randomUUID().toString();
        var now=LocalDateTime.now(clock);
        int claimed=db.update("""
                UPDATE knowledge_review_case SET state='CHECKING',run_id=?,updated_at=?
                WHERE id=? AND (state IN ('OPEN','FAILED') OR (state='CHECKING' AND updated_at<?))
                """,run,now,id,now.minusMinutes(2));
        if(claimed!=1) throw new BusinessException(ErrorCode.KNOWLEDGE_CONFLICT);
        String state="CHECKED";
        String candidate=null;
        String outcome="NO_CONFIRMED_CHANGE";
        try {
            var observed=hub.stageCapture(source,collector.fetch(source),null);
            hub.recordCheck(source.id(),null);
            if(!observed.hash().equals(old.hash()) && observed.id().equals(doc.active())) {
                outcome="SOURCE_ALREADY_PUBLISHED";
            } else if(!observed.hash().equals(old.hash())) {
                state="CANDIDATE_READY";
                candidate=observed.id();
                outcome="SOURCE_CHANGED";
            }
        } catch(BusinessException failure) {
            state="FAILED";
            outcome=failure.getErrorCode().name();
            hub.recordCheck(source.id(),outcome);
        }
        db.update("""
                UPDATE knowledge_review_case SET state=?,outcome=?,candidate_id=?,run_id=NULL,updated_at=?
                WHERE id=? AND run_id=?
                """,state,outcome,candidate,LocalDateTime.now(clock),id,run);
        return get(id);
    }

    CaseRow close(String id,Close input) {
        hub.requireReviewer();
        if(db.update("""
                UPDATE knowledge_review_case SET state='CLOSED',outcome=?,updated_at=?
                WHERE id=? AND state NOT IN ('CHECKING','CLOSED')
                """,input.outcome().name(),LocalDateTime.now(clock),id)!=1)
            throw new BusinessException(ErrorCode.KNOWLEDGE_CONFLICT);
        return get(id);
    }

    Report evaluate(String id,Evaluation input) {
        hub.requireReviewer();
        var row=get(id);
        var old=hub.detail(row.versionId(),true).version();
        var doc=hub.document(old.documentId(),false);
        var query=new KnowledgeHubSearch.Query(input.query(),doc.domain(),input.softwareVersion(),doc.sourceId(),null,
                KnowledgeHubSearch.Scope.PUBLIC,false);
        var previous=metrics(search.historical(query,old.id()),input);
        var current=metrics(search.local(query),input);
        var candidate=row.candidateId()==null?null:metrics(search.historical(query,row.candidateId()),input);
        var report=new Report(previous,current,candidate,(previous.hit()&&!current.hit())||current.unrelated()>previous.unrelated()
                ||current.wrongVersion()>0);
        db.update("""
                INSERT INTO knowledge_review_run(id,case_id,query_text,target_version,expected_term,report,created_at)
                VALUES(?,?,?,?,?,?,?)
                """,UUID.randomUUID().toString(),id,input.query(),input.softwareVersion(),input.expectedTerm(),encode(report),LocalDateTime.now(clock));
        return report;
    }

    List<Run> runs(String id) {
        hub.requireReviewer();
        get(id);
        return db.query("""
                SELECT id,query_text,target_version,expected_term,report,created_at FROM knowledge_review_run
                WHERE case_id=? ORDER BY created_at DESC,id LIMIT 20
                """,(r,n)->new Run(r.getString(1),r.getString(2),r.getString(3),r.getString(4),decode(r.getString(5)),
                r.getObject(6,LocalDateTime.class)),id);
    }

    private static Metrics metrics(List<KnowledgeHubSearch.Evidence> rows,Evaluation input) {
        String expected=EvidenceText.normalize(input.expectedTerm());
        int hit=(int)rows.stream().filter(row->EvidenceText.normalize(row.excerpt()).contains(expected)).count();
        int wrong=(int)rows.stream().filter(row->!input.softwareVersion().equalsIgnoreCase(row.softwareVersion())).count();
        int traceable=(int)rows.stream().filter(row->row.url()!=null && row.documentId()!=null && row.ordinal()>0 && row.line()>0).count();
        return new Metrics(rows.size(),wrong,rows.size()-hit,traceable,hit>0);
    }

    private String encode(Report report) {
        try {return mapper.writeValueAsString(report);}
        catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
    }
    private Report decode(String json) {
        try {return mapper.readValue(json,Report.class);}
        catch(com.fasterxml.jackson.core.JsonProcessingException e){throw new IllegalStateException(e);}
    }
    private CaseRow get(String id) {
        return db.query("SELECT * FROM knowledge_review_case WHERE id=?",this::read,id).stream().findFirst()
                .orElseThrow(()->new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
    private CaseRow read(java.sql.ResultSet r,int index) throws java.sql.SQLException {
        return new CaseRow(r.getString("id"),r.getString("version_id"),r.getString("environment"),r.getString("query_type"),
                r.getInt("distinct_users"),r.getString("state"),r.getString("outcome"),r.getString("candidate_id"),
                r.getObject("updated_at",LocalDateTime.class));
    }
}
