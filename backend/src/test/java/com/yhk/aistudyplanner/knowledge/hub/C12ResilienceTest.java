package com.yhk.aistudyplanner.knowledge.hub;

import com.yhk.aistudyplanner.auth.service.AuthSessionService;
import com.yhk.aistudyplanner.common.exception.*;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeDomain;
import com.yhk.aistudyplanner.knowledge.service.KnowledgeRetriever;
import java.time.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.jdbc.core.JdbcTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class C12ResilienceTest {
    private final StringRedisTemplate redis=mock(StringRedisTemplate.class);
    private final OfficialDocuments collector=mock(OfficialDocuments.class);
    private final KnowledgeSearchProvider discovery=mock(KnowledgeSearchProvider.class);
    private final KnowledgeHubSearch search=new KnowledgeHubSearch(mock(JdbcTemplate.class),mock(AuthSessionService.class),
            mock(KnowledgeRetriever.class),discovery,collector,redis,Clock.systemUTC(),DocumentRanking.Strategy.LEGACY);
    private final KnowledgeHubSearch.Query latest=new KnowledgeHubSearch.Query("latest spring boot actuator endpoints",
            null,"3.5",null,null,KnowledgeHubSearch.Scope.PUBLIC,true);

    @Test void temporaryRedisFailureDisablesOnlineWithoutCallingSources() {
        when(collector.enabled()).thenReturn(true);
        when(redis.opsForValue()).thenThrow(new RedisConnectionFailureException("fixture"));
        var result=search.search(latest);
        assertEquals(KnowledgeHubSearch.OnlineStatus.UNAVAILABLE,result.onlineStatus());
        assertEquals("LATEST",result.trigger());
        assertTrue(result.online().isEmpty());
        verifyNoInteractions(discovery);
    }

    @Test void officialRequestFailureRemainsEmptyEvidence() {
        when(collector.enabled()).thenReturn(true);
        @SuppressWarnings("unchecked")
        ValueOperations<String,String> values=mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(values);
        when(values.setIfAbsent(anyString(),anyString(),any(Duration.class))).thenReturn(true);
        var source=new KnowledgeHub.Source(null,"Spring Boot",
                "https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html",
                KnowledgeDomain.JAVA_BACKEND,"3.5",null,null,null);
        when(discovery.find(latest)).thenReturn(List.of(source));
        when(collector.fetch(source)).thenThrow(new BusinessException(ErrorCode.KNOWLEDGE_SOURCE_UNAVAILABLE));
        var result=search.search(latest);
        assertEquals(KnowledgeHubSearch.OnlineStatus.UNAVAILABLE,result.onlineStatus());
        assertTrue(result.online().isEmpty());
        verify(collector).fetch(source);
    }
}
