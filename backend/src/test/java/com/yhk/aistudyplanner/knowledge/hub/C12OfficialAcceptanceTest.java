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
        new Target("Spring AI","https://docs.spring.io/spring-ai/1.1/reference/api/tools.html",
                "1.1",KnowledgeDomain.AI_APPLICATION,"tool","/spring-ai/1.1/"));

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
                category=error instanceof com.yhk.aistudyplanner.common.exception.BusinessException business
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
