package com.yhk.aistudyplanner.knowledge.hub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeDomain;
import java.net.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import static org.junit.jupiter.api.Assertions.*;

class C12OfficialAcceptanceTest {
    record Target(String name,String url,String version,KnowledgeDomain domain,String marker,String versionPath) {}
    record Result(String source,String url,String version,String status,String category,int chunks,long millis) {}
    private static final List<Target> TARGETS=List.of(
        new Target("Java 17","https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ConcurrentHashMap.html",
                "17",KnowledgeDomain.JAVA_BACKEND,"concurrenthashmap","/javase/17/"),
        new Target("Spring Boot","https://docs.spring.io/spring-boot/3.5/reference/actuator/endpoints.html",
                "3.5",KnowledgeDomain.JAVA_BACKEND,"endpoint","/spring-boot/3.5/"),
        new Target("Spring AI","https://docs.spring.io/spring-ai/reference/1.1/api/tools.html",
                "1.1",KnowledgeDomain.AI_APPLICATION,"tool","/spring-ai/reference/1.1/"));

    @Test
    void recognizesSpringAiReferenceVersionWithoutLettingOtherReleasesThrough() {
        var root=URI.create("https://docs.spring.io/spring-ai/reference/1.1/index.html");
        var query=new KnowledgeHubSearch.Query("Spring AI tool calling",KnowledgeDomain.AI_APPLICATION,
                "1.1",null,null,KnowledgeHubSearch.Scope.PUBLIC,true);
        var links=OfficialCatalogSearch.links(root,"""
                <a href="api/tools.html">Tool Calling</a>
                <a href="/spring-ai/reference/2.0/api/tools.html">Tool Calling</a>
                """,query);
        assertEquals(1,links.size());
        assertEquals("1.1",links.get(0).softwareVersion());
    }

    @Test
    @EnabledIfEnvironmentVariable(named="C12_LIVE_OFFICIAL",matches="true")
    void findsRelevantSpringBootFromLiveOfficialCatalog() {
        var collector=new OfficialDocuments(true);
        var url=URI.create("https://docs.spring.io/spring-boot/3.5/reference/index.html");
        var query=new KnowledgeHubSearch.Query("Spring Boot actuator endpoints",KnowledgeDomain.JAVA_BACKEND,
                "3.5",null,null,KnowledgeHubSearch.Scope.PUBLIC,true);
        var results=OfficialCatalogSearch.links(url,collector.catalog(url.toString()),query);
        var discovered=results.stream().filter(candidate->candidate.url().endsWith("/actuator/endpoints.html"))
                .findFirst().orElseThrow();
        assertEquals("3.5",discovered.softwareVersion());
        var content=collector.fetch(discovered);
        assertTrue(content.title().contains("Endpoints"));
        assertTrue(EvidenceText.split(content.title(),content.text()).stream().anyMatch(chunk->
                EvidenceText.normalize(chunk.excerpt()).contains("endpoint")));
        System.out.println("C121_OFFICIAL_CATALOG PASS "+discovered.url());
    }

    @Test void transportDiagnosticsAndEncodingDoNotExposeResponseContent() {
        assertEquals("TLS",OfficialDocuments.transportCategory(new javax.net.ssl.SSLHandshakeException("redacted")));
        assertEquals("DNS",OfficialDocuments.transportCategory(new java.net.UnknownHostException("redacted")));
        assertEquals("TIMEOUT",OfficialDocuments.transportCategory(new java.net.http.HttpTimeoutException("redacted")));
        assertEquals("HTTP_403",OfficialDocuments.transportCategory(new OfficialDocuments.FetchFailure("HTTP_403")));
        assertEquals("事务",OfficialDocuments.decode("事务".getBytes(java.nio.charset.StandardCharsets.UTF_8),"text/html; charset=UTF-8"));
        assertThrows(OfficialDocuments.FetchFailure.class,()->OfficialDocuments.decode(new byte[]{(byte)0xff},"text/html"));
    }

    @Test
    @EnabledIfEnvironmentVariable(named="C12_LIVE_OFFICIAL",matches="true")
    void verifiesActualOfficialContentWithTheApplicationCollector() throws Exception {
        var collector=new OfficialDocuments(true);
        var checks=new ArrayList<Result>();
        for(var target:TARGETS) {
            long started=System.nanoTime();
            String status="FAIL", category="CONTENT_OR_TRANSPORT";
            int count=0;
            try {
                var uri=OfficialDocuments.validate(target.url());
                var proxies=ProxySelector.getDefault();
                boolean direct=proxies==null || proxies.select(uri).stream().allMatch(proxy->proxy.type()==Proxy.Type.DIRECT);
                if(direct) InetAddress.getAllByName(uri.getHost());
                if(!uri.getPath().contains(target.versionPath())) throw new IllegalArgumentException("WRONG_VERSION");
                var page=collector.fetch(new KnowledgeHub.Source(null,target.name(),target.url(),target.domain(),target.version(),null,null,null));
                var chunks=EvidenceText.split(page.title(),page.text());
                var chosen=chunks.stream().filter(chunk->EvidenceText.normalize(chunk.excerpt()).contains(target.marker()))
                        .findFirst().orElseThrow();
                var citation=new KnowledgeHubSearch.Evidence("ONLINE",null,null,chosen.ordinal(),page.title(),chosen.heading(),chosen.line(),
                        chosen.excerpt(),target.name(),target.url(),target.version(),null,"OFFICIAL_FETCH",LocalDateTime.now(),
                        List.of(target.marker()),1);
                if(citation.line()<1 || citation.documentId()!=null || citation.versionId()!=null) throw new IllegalStateException("BAD_CITATION");
                count=chunks.size();
                status="PASS";
                category="ACTUAL_JVM_HTTP";
            } catch(UnknownHostException error) {
                category="DNS";
            } catch(RuntimeException error) {
                if(error instanceof OfficialDocuments.FetchFailure failure) category=failure.category();
                else category=error instanceof com.yhk.aistudyplanner.common.exception.BusinessException business
                        ? business.getErrorCode().name() : "EVIDENCE_VALIDATION";
            }
            checks.add(new Result(target.name(),target.url(),target.version(),status,category,count,(System.nanoTime()-started)/1_000_000));
        }
        var path=Path.of("target","c12-live-official.json");
        Files.createDirectories(path.getParent());
        var mapper=new ObjectMapper();
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(),checks);
        System.out.println("C12_OFFICIAL "+mapper.writeValueAsString(checks));
        assertTrue(checks.stream().allMatch(check->check.status().equals("PASS")),checks.toString());
    }
}
