package com.yhk.aistudyplanner.knowledge.hub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.ai.config.AiPlanningProperties;
import com.yhk.aistudyplanner.ai.gateway.*;
import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.ErrorCode;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeDomain;
import java.net.URI;
import java.time.Clock;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EvidenceRagTest {
    private final ObjectMapper mapper=new ObjectMapper();
    private final String text="Proxy interception requires a method call through the proxy.";

    private KnowledgeHubSearch.Evidence row(String id,String version,String content) {
        return new KnowledgeHubSearch.Evidence("PUBLIC",id,id,1,"Transaction proxy","Transaction proxy",1,
                content,"Spring","https://docs.spring.io/spring-boot/3.5/reference/test.html",version,null,
                "OFFICIAL_FETCH",null,List.of("transaction","proxy"),8);
    }
    private RagRetrieval.RetrievalResult result(List<KnowledgeHubSearch.Evidence> docs) {
        return new RagRetrieval.RetrievalResult(List.of(),docs,Set.of("3.5"),"3.5",null,KnowledgeHubSearch.OnlineStatus.LOCAL_ONLY);
    }

    @Test void boundedStableBlocksRetainSourceAndDeduplicate() {
        var builder=new EvidenceContextBuilder(mapper,800);
        var actual=builder.build(result(List.of(row("a","3.5",text.repeat(25)),row("b","3.5",text.repeat(25)))),true,true);
        assertEquals(1,actual.blocks().size());
        assertEquals("E1",actual.blocks().get(0).id());
        assertEquals("a:1",actual.blocks().get(0).chunkId());
        assertEquals("3.5",actual.blocks().get(0).softwareVersion());
        assertTrue(actual.actualCharacters()<=800);
        assertEquals(actual,builder.build(result(List.of(row("a","3.5",text.repeat(25)))),true,true));
    }

    @Test void enforcesQuoteAndCitationAndRejectsInstructionsAsResponse() throws Exception {
        var block=new EvidenceContextBuilder(mapper,1000).build(result(List.of(row("a","3.5",text))),true,true).blocks();
        String valid=claim("E1","Proxy interception requires",mapper);
        assertEquals(1,RagAnswerService.decode(valid,block,mapper).size());
        assertThrows(IllegalArgumentException.class,()->RagAnswerService.decode(claim("E3",text,mapper),block,mapper));
        assertThrows(IllegalArgumentException.class,()->RagAnswerService.decode(claim("E1","A made-up fact",mapper),block,mapper));
        assertThrows(IllegalArgumentException.class,()->RagAnswerService.decode("{\"command\":\"publish\"}",block,mapper));
        assertTrue(RagAnswerService.SYSTEM.contains("不可信"));
    }

    @Test void detectsSameVersionConflictAndKeepsDifferentVersionsDistinct() {
        var enabled=row("a","3.5","app.transaction.enabled defaults to true");
        var disabled=row("b","3.5","app.transaction.enabled defaults to false");
        assertTrue(RagRetrieval.conflictWithStructure(List.of(enabled,disabled),List.of()));
        assertFalse(RagRetrieval.conflictWithStructure(List.of(enabled,row("c","4.0",disabled.excerpt())),List.of()));
        assertEquals("3.5",RagRetrieval.implicitVersion("Spring Boot 3.5 事务"));
        assertTrue(RagRetrieval.searchable("Spring Boot 的事务为什么没有生效").startsWith("transaction "));
    }

    @Test void officialCatalogFollowsRelatedLinksOnlyWithinVersionAndOrigin() {
        String root="https://docs.spring.io/spring-boot/3.5/reference/index.html";
        String html="""
                <a href="actuator/endpoints.html">Actuator endpoints</a>
                <a href="/spring-boot/4.0/reference/actuator/endpoints.html">Actuator endpoints 4</a>
                <a href="https://wrong.example/spring-boot/3.5/reference/endpoints.html">Endpoints elsewhere</a>
                <a href="logging.html">Logging</a>
                """;
        var query=officialQuery();
        var links=OfficialCatalogSearch.links(URI.create(root),html,query);
        assertEquals(1,links.size());
        assertTrue(links.get(0).url().endsWith("/actuator/endpoints.html"));
        assertEquals("3.5",links.get(0).softwareVersion());
    }

    @Test
    @EnabledIfEnvironmentVariable(named="C11_LIVE_OFFICIAL",matches="true")
    void readsRelevantOfficialCatalogDocument() {
        var collector=new OfficialDocuments(true);
        var fallback=mock(RegisteredSourceSearch.class);
        when(fallback.find(any())).thenReturn(List.of());
        var provider=new OfficialCatalogSearch(fallback,collector,
                "https://docs.spring.io/spring-boot/3.5/reference/index.html");
        var entries=provider.find(officialQuery());
        assertFalse(entries.isEmpty());
        var page=collector.fetch(entries.get(0));
        assertTrue(page.text().toLowerCase(Locale.ROOT).contains("endpoint"));
        assertTrue(entries.get(0).url().contains("/spring-boot/3.5/reference/"));
    }

    @Test void modelDisabledTimeoutAndRevocationAreDistinct() throws Exception {
        var rag=mock(RagRetrieval.class);
        var builder=new EvidenceContextBuilder(mapper,1200);
        @SuppressWarnings("unchecked")
        ObjectProvider<PlanningGateway> gateways=mock(ObjectProvider.class);
        var gateway=mock(PlanningGateway.class);
        when(gateways.getIfAvailable()).thenReturn(gateway);
        var properties=new AiPlanningProperties();
        var service=new RagAnswerService(rag,builder,gateways,properties,mapper,mock(JdbcTemplate.class),
                mock(AuthSessionService.class),Clock.systemUTC());
        var ask=new RagAnswerService.Ask("transaction proxy",null,"3.5",RagRetrieval.Scope.PUBLIC,false,false,null);
        var evidence=row("a","3.5",text);
        when(rag.retrieve(ask.query(),null,"3.5",RagRetrieval.Scope.PUBLIC,false)).thenReturn(result(List.of(evidence)));
        when(rag.current(evidence)).thenReturn(true);
        var disabled=service.ask(ask);
        assertEquals("AI_DISABLED",disabled.fallbackReason());
        assertEquals(1,disabled.citations().size());
        verifyNoInteractions(gateway);
        properties.setEnabled(true);
        when(gateway.generateWithUsage(anyString(),anyString())).thenThrow(new AiProviderException(ErrorCode.AI_PROVIDER_TIMEOUT))
                .thenReturn(new PlanningGateway.Completion(claim("E1","Proxy interception requires",mapper),10,15));
        assertEquals("AI_PROVIDER_TIMEOUT",service.ask(ask).fallbackReason());
        when(rag.current(evidence)).thenReturn(false);
        var changed=service.ask(ask);
        assertEquals("EVIDENCE_CHANGED",changed.fallbackReason());
        assertTrue(changed.citations().isEmpty());
        assertNull(changed.answer());
    }

    private static KnowledgeHubSearch.Query officialQuery(){
        return new KnowledgeHubSearch.Query("Spring Boot actuator endpoints",KnowledgeDomain.JAVA_BACKEND,
                "3.5",null,null,KnowledgeHubSearch.Scope.PUBLIC,true);
    }

    private static String claim(String id,String quote,ObjectMapper mapper) throws Exception {
        return mapper.writeValueAsString(Map.of("claims",List.of(Map.of("statement","Check the call through the proxy.",
                "evidenceId",id,"quote",quote))));
    }
}
