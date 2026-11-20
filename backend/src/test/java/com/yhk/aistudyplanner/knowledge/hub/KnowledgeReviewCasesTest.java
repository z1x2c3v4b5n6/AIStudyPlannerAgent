package com.yhk.aistudyplanner.knowledge.hub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeDomain;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class KnowledgeReviewCasesTest {
    private static final LocalDateTime NOW=LocalDateTime.of(2026,10,1,12,0);
    private final JdbcTemplate db=mock(JdbcTemplate.class);
    private final KnowledgeHub hub=mock(KnowledgeHub.class);
    private final OfficialDocuments collector=mock(OfficialDocuments.class);
    private final KnowledgeReviewCases cases=new KnowledgeReviewCases(db,hub,collector,mock(KnowledgeHubSearch.class),
            new ObjectMapper(),Clock.fixed(NOW.toInstant(ZoneOffset.UTC),ZoneOffset.UTC));
    private final KnowledgeHub.Source source=new KnowledgeHub.Source("source","Spring",
            "https://docs.spring.io/spring-boot/3.5/reference/index.html",KnowledgeDomain.JAVA_BACKEND,
            "3.5",null,null,null);
    private final OfficialDocuments.Captured snapshot=new OfficialDocuments.Captured("Spring","Stable reference body.");

    @Test void unchangedSourceRecordsCheckWithoutCandidatePublication() {
        assertReview(new KnowledgeHub.Version("old","document",KnowledgeHub.State.PUBLISHED,"Spring","3.5","same",
                KnowledgeHub.Ingest.OFFICIAL_FETCH,NOW),"CHECKED","NO_CONFIRMED_CHANGE",null);
    }

    @Test void changedSourceRetainsCandidateScope() {
        assertReview(new KnowledgeHub.Version("new","document",KnowledgeHub.State.PENDING_REVIEW,"Spring","3.5","changed",
                KnowledgeHub.Ingest.OFFICIAL_FETCH,NOW),"CANDIDATE_READY","SOURCE_CHANGED","new");
    }

    @Test void duplicateWorkerCannotFetchOrWriteEvidence() {
        prepare();
        when(db.update(anyString(),any(Object[].class))).thenReturn(0);
        assertThrows(BusinessException.class,()->cases.recheck("case"));
        verifyNoInteractions(collector);
        verify(hub,never()).stageCapture(any(),any(),any());
    }

    private void assertReview(KnowledgeHub.Version observed,String state,String outcome,String candidate) {
        prepare();
        when(db.update(anyString(),any(Object[].class))).thenReturn(1);
        when(collector.fetch(source)).thenReturn(snapshot);
        when(hub.stageCapture(source,snapshot,null)).thenReturn(observed);
        cases.recheck("case");
        var update=ArgumentCaptor.forClass(Object[].class);
        verify(db).update(contains("SET state=?,outcome=?,candidate_id=?"),update.capture());
        Object[] fields=update.getValue();
        assertEquals(state,fields[0]);
        assertEquals(outcome,fields[1]);
        assertEquals(candidate,fields[2]);
        verify(hub,never()).review(any(),any());
    }

    private void prepare() {
        when(hub.detail("old",true)).thenReturn(new KnowledgeHub.Detail(new KnowledgeHub.Version("old","document",
                KnowledgeHub.State.PUBLISHED,"Spring","3.5","same",KnowledgeHub.Ingest.OFFICIAL_FETCH,NOW),"",List.of()));
        when(hub.document("document",false)).thenReturn(new KnowledgeHub.Document("document",null,source.id(),
                source.url(),null,KnowledgeDomain.JAVA_BACKEND,"old"));
        when(hub.source(source.id(),false)).thenReturn(source);
        when(db.query(eq("SELECT * FROM knowledge_review_case WHERE id=?"),any(RowMapper.class),eq("case")))
                .thenReturn(List.of(new KnowledgeReviewCases.CaseRow("case","old","spring boot 3.5","API",3,
                        "OPEN",null,null,NOW)));
    }
}
