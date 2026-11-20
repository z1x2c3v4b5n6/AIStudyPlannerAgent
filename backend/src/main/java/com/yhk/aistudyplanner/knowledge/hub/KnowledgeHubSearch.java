package com.yhk.aistudyplanner.knowledge.hub;

import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.knowledge.dto.KnowledgeSearchRequest;
import com.yhk.aistudyplanner.knowledge.dto.KnowledgeSearchResult;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeDomain;
import com.yhk.aistudyplanner.knowledge.service.KnowledgeRetriever;
import jakarta.validation.constraints.*;
import java.time.*;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class KnowledgeHubSearch {
    enum Scope { PUBLIC, PRIVATE, BOTH }
    public enum OnlineStatus { LOCAL_ONLY, DISABLED, RATE_LIMITED, NO_SOURCE, NO_RELEVANT_EVIDENCE, AVAILABLE, UNAVAILABLE }
    record Query(@NotBlank @Size(max=300) String query, KnowledgeDomain domain, @Size(max=80) String softwareVersion,
                 @Pattern(regexp="[0-9a-f-]{36}") String sourceId, LocalDate collectedAfter, Scope scope, boolean online) {}
    public record Evidence(String scope, String documentId, String versionId, int ordinal, String title, String heading, int line,
                           String excerpt, String source, String url, String softwareVersion, String unitId, String ingestKind,
                           LocalDateTime collectedAt, List<String> matchedTerms, int score) {}
    public record Result(List<KnowledgeSearchResult> structured, List<Evidence> published, List<Evidence> online,
                  OnlineStatus onlineStatus, String trigger) {}
    private record Stored(String scope, String documentId, String versionId, int ordinal, String title, String heading, int line,
                          String excerpt,String source,String url,String softwareVersion,String unitId,String ingestKind,LocalDateTime collectedAt) {}

    private final JdbcTemplate db;
    private final AuthSessionService auth;
    private final KnowledgeRetriever structured;
    private final KnowledgeSearchProvider provider;
    private final OfficialDocuments collector;
    private final StringRedisTemplate redis;
    private final Clock clock;
    private final DocumentRanking.Strategy ranking;

    KnowledgeHubSearch(JdbcTemplate db, AuthSessionService auth, KnowledgeRetriever structured, KnowledgeSearchProvider provider,
                       OfficialDocuments collector, StringRedisTemplate redis, Clock clock,
                       @org.springframework.beans.factory.annotation.Value("${app.knowledge.document-ranking:LEGACY}") DocumentRanking.Strategy ranking) {
        this.db=db;
        this.auth=auth;
        this.structured=structured;
        this.provider=provider;
        this.collector=collector;
        this.redis=redis;
        this.clock=clock;
        this.ranking=ranking;
    }

    public List<Evidence> publicEvidence(String query, KnowledgeDomain domain) {
        return local(new Query(query,domain,null,null,null,Scope.PUBLIC,false)).stream().limit(2).toList();
    }

    Result search(Query query) { return search(query, evidence->true); }

    Result search(Query query, java.util.function.Predicate<Evidence> relevant) {
        var published=local(query).stream().filter(relevant).toList();
        var pack=query.scope()==Scope.PRIVATE || query.softwareVersion()!=null || query.sourceId()!=null || query.collectedAfter()!=null
                ? List.<KnowledgeSearchResult>of()
                : structured.search(new KnowledgeSearchRequest(query.query(),query.domain(),5));
        String text=EvidenceText.normalize(query.query());
        String trigger=text.matches("(?s).*(最新|现在|latest|current version).*")?"LATEST":
                query.softwareVersion()!=null && published.isEmpty() ? "VERSION_MISS":
                published.isEmpty() && pack.isEmpty() ? "LOCAL_MISS":null;
        if(query.scope()==Scope.PRIVATE || !query.online() || trigger==null)
            return new Result(pack,published,List.of(),OnlineStatus.LOCAL_ONLY,trigger);
        if(!collector.enabled()) return new Result(pack,published,List.of(),OnlineStatus.DISABLED,trigger);
        try {
            if(!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent("knowledge:online:"+auth.currentUserId(),"1",Duration.ofSeconds(30))))
                return new Result(pack,published,List.of(),OnlineStatus.RATE_LIMITED,trigger);
        } catch (DataAccessException unavailable) {
            return new Result(pack,published,List.of(),OnlineStatus.UNAVAILABLE,trigger);
        }
        var sources=provider.find(query);
        if(sources.isEmpty()) return new Result(pack,published,List.of(),OnlineStatus.NO_SOURCE,trigger);
        var evidence=new ArrayList<Evidence>();
        int successful=0;
        for(var source:sources) {
            try {
                var page=collector.fetch(source);
                successful++;
                for(var chunk:EvidenceText.split(page.title(),page.text())) {
                    var row=new Stored("ONLINE",null,null,chunk.ordinal(),page.title(),chunk.heading(),chunk.line(),
                            chunk.excerpt(),source.name(),source.url(),source.softwareVersion(),null,"OFFICIAL_FETCH",LocalDateTime.now(clock));
                    score(row,EvidenceText.terms(query.query())).filter(relevant).ifPresent(evidence::add);
                }
            } catch (BusinessException unavailable) {
                // Preserve local evidence if any configured endpoint fails.
            }
        }
        return new Result(pack,published,sorted(evidence,5),!evidence.isEmpty()?OnlineStatus.AVAILABLE:
                successful>0?OnlineStatus.NO_RELEVANT_EVIDENCE:OnlineStatus.UNAVAILABLE,trigger);
    }

    List<Evidence> local(Query query) { return local(query,null); }

    List<Evidence> historical(Query query,String versionId) { return local(query,versionId); }

    private List<Evidence> local(Query query,String versionId) {
        var terms=EvidenceText.terms(query.query());
        if(terms.isEmpty()) return List.of();
        var sql=new StringBuilder("""
                SELECT CASE WHEN d.owner_id IS NULL THEN 'PUBLIC' ELSE 'PRIVATE' END visibility,
                       v.id,c.ordinal,v.title,c.heading,c.start_line,c.excerpt,s.name,d.origin,v.software_version,d.unit_id,v.ingest_kind,v.created_at,d.id
                FROM knowledge_document d JOIN knowledge_document_version v ON v.document_id=d.id
                JOIN knowledge_chunk c ON c.version_id=v.id LEFT JOIN knowledge_source s ON s.id=d.source_id
                WHERE
                """);
        var args=new ArrayList<Object>();
        if(versionId==null) sql.append(" v.id=d.active_version AND v.status='PUBLISHED' AND ");
        else {
            sql.append(" v.id=? AND d.owner_id IS NULL AND ");
            args.add(versionId);
        }
        switch(query.scope()==null?Scope.PUBLIC:query.scope()){
            case PUBLIC -> sql.append(" d.owner_id IS NULL");
            case PRIVATE -> { sql.append(" d.owner_id=?"); args.add(auth.currentUserId()); }
            case BOTH -> { sql.append(" (d.owner_id IS NULL OR d.owner_id=?)"); args.add(auth.currentUserId()); }
        }
        if(query.domain()!=null) { sql.append(" AND d.domain=?"); args.add(query.domain().name()); }
        if(query.softwareVersion()!=null) { sql.append(" AND LOWER(v.software_version)=?"); args.add(EvidenceText.normalize(query.softwareVersion())); }
        if(query.sourceId()!=null) { sql.append(" AND s.id=?"); args.add(query.sourceId()); }
        if(query.collectedAfter()!=null) { sql.append(" AND v.created_at>=?"); args.add(query.collectedAfter().atStartOfDay()); }
        sql.append(" AND (");
        for(int index=0;index<terms.size();index++) {
            if(index>0) sql.append(" OR ");
            sql.append("(LOWER(v.title) LIKE ? OR LOWER(c.heading) LIKE ? OR LOWER(c.excerpt) LIKE ?)");
            String match="%"+terms.get(index)+"%";
            args.addAll(List.of(match,match,match));
        }
        sql.append(") ORDER BY v.created_at DESC, v.id, c.ordinal LIMIT 240");
        var rows=db.query(sql.toString(),(r,n)->new Stored(r.getString(1),r.getString(14),r.getString(2),r.getInt(3),r.getString(4),
                r.getString(5),r.getInt(6),r.getString(7),r.getString(8),r.getString(9),r.getString(10),r.getString(11),r.getString(12),
                r.getObject(13,LocalDateTime.class)),args.toArray());
        return sorted(rows.stream().flatMap(row->score(row,terms).stream()).toList(),8);
    }

    boolean current(Evidence evidence) {
        if ("ONLINE".equals(evidence.scope())) {
            OfficialDocuments.validate(evidence.url());
            return true;
        }
        boolean personal = "PRIVATE".equals(evidence.scope());
        var sql = """
                SELECT COUNT(*) FROM knowledge_document d
                JOIN knowledge_document_version v ON v.document_id=d.id AND v.id=d.active_version
                JOIN knowledge_chunk c ON c.version_id=v.id
                WHERE d.id=? AND v.id=? AND v.status='PUBLISHED' AND c.ordinal=? AND c.start_line=?
                """ + (personal ? " AND d.owner_id=?" : " AND d.owner_id IS NULL");
        var args = new ArrayList<Object>(List.of(evidence.documentId(),evidence.versionId(),evidence.ordinal(),evidence.line()));
        if (personal) args.add(auth.currentUserId());
        return db.queryForObject(sql,Integer.class,args.toArray())==1;
    }

    private List<Evidence> sorted(List<Evidence> evidence,int limit) {
        return DocumentRanking.rank(ranking,evidence,limit);
    }

    private Optional<Evidence> score(Stored row,List<String> terms) {
        var match=DocumentRanking.score(ranking,row.title(),row.heading(),row.excerpt(),row.ingestKind(),terms);
        return match.points()==0?Optional.empty():Optional.of(new Evidence(row.scope(),row.documentId(),row.versionId(),row.ordinal(),row.title(),
                row.heading(),row.line(),row.excerpt(),row.source(),row.url(),row.softwareVersion(),row.unitId(),row.ingestKind(),
                row.collectedAt(),match.terms(),match.points()));
    }
}
