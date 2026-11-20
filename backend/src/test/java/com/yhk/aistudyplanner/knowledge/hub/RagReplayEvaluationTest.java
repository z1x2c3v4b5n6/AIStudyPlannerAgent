package com.yhk.aistudyplanner.knowledge.hub;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RagReplayEvaluationTest {
    enum Mode { A_NO_EVIDENCE, B_C4, C_PUBLIC, D_ONLINE, E_ORACLE }
    record Row(String query,String expectedFact,String structured,String published,String online) {}
    record Metric(int queries,int positive,int hitAt3,double precisionAt3,double recallAt3,double mrr,
                  int returnedNegative,double lineTraceability,int claims,
                  int supportedClaims,int invalidClaims,int abstained,int complete,int contradictions,
                  long p50Micros,long p95Micros) {}
    private final ObjectMapper mapper=new ObjectMapper();

    @Test void freezeComparisonReportAndValidateClaimsAgainstHumanLabels() throws Exception {
        List<Row> rows;
        try(var input=getClass().getResourceAsStream("/knowledge/c11-replay.tsv")){
            rows=new String(Objects.requireNonNull(input).readAllBytes(),StandardCharsets.UTF_8).lines().filter(line->!line.startsWith("#"))
                    .map(line->line.split("\\|",-1)).map(p->new Row(p[0],p[1],p[2],p[3],p[4])).toList();
        }
        var reports=new EnumMap<Mode,Metric>(Mode.class);
        for(var mode:Mode.values()) reports.put(mode,compare(mode,rows));
        assertEquals(0,reports.get(Mode.A_NO_EVIDENCE).hitAt3());
        assertEquals(2,reports.get(Mode.B_C4).hitAt3());
        assertEquals(3,reports.get(Mode.C_PUBLIC).hitAt3());
        assertEquals(4,reports.get(Mode.D_ONLINE).hitAt3());
        assertEquals(4,reports.get(Mode.E_ORACLE).hitAt3());
        assertEquals(1,reports.get(Mode.D_ONLINE).contradictions());
        assertTrue(reports.values().stream().allMatch(metric->metric.returnedNegative()==0));
        var report=Path.of("target","c11-rag-replay.json");
        Files.createDirectories(report.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(report.toFile(),reports);
        System.out.println("C11_RAG_REPLAY "+mapper.writeValueAsString(reports));
    }

    private Metric compare(Mode mode,List<Row> rows) throws Exception {
        int positive=0,hits=0,negative=0,claims=0,abstained=0,supported=0,invalid=0,traces=0,docs=0,complete=0,contradictions=0;
        var times=new ArrayList<Long>();
        for(var row:rows) {
            long start=System.nanoTime();
            if(!row.expectedFact().isEmpty()) positive++;
            var blocks=blocks(mode,row);
            if(row.expectedFact().isEmpty() && !blocks.isEmpty()) negative++;
            if(!blocks.isEmpty()){
                var block=blocks.get(0);
                hits++;
                if(!block.scope().equals("SYSTEM")) docs++;
                if(block.url()!=null && block.line()!=null) traces++;
                claims++;
                String fixtureStatement=mode==Mode.D_ONLINE && !row.online().isEmpty()
                        ?"工具回调不需要事先注册":row.expectedFact();
                var raw=mapper.writeValueAsString(Map.of("claims",List.of(Map.of(
                        "statement",fixtureStatement,"evidenceId",block.id(),"quote",block.text()))));
                var parsed=RagAnswerService.decode(raw,blocks,mapper);
                if(parsed.get(0).statement().equals(row.expectedFact())) {supported++;complete++;}
                else contradictions++;
            } else {
                abstained++;
                // Negative control models can offer a plausible claim but lack an admissible citation.
                if(mode==Mode.A_NO_EVIDENCE && !row.expectedFact().isEmpty()){
                    claims++;
                    try { RagAnswerService.decode("{\"claims\":[{\"statement\":\"Guess\",\"evidenceId\":\"E1\",\"quote\":\"Invented evidence.\"}]}",blocks,mapper); }
                    catch(IllegalArgumentException rejected){ invalid++; }
                }
            }
            times.add((System.nanoTime()-start)/1000);
        }
        times.sort(Long::compareTo);
        double hitRate=(double)hits/positive;
        return new Metric(rows.size(),positive,hits,hits/(3.0*rows.size()),hitRate,hitRate,negative,
                docs==0?0:(double)traces/docs,claims,supported,invalid,abstained,complete,contradictions,
                times.get((times.size()-1)/2),times.get(times.size()-1));
    }

    private static List<EvidenceContextBuilder.Block> blocks(Mode mode,Row row) {
        String quote=switch(mode){
            case A_NO_EVIDENCE -> "";
            case B_C4 -> row.structured();
            case C_PUBLIC -> row.published();
            case D_ONLINE,E_ORACLE -> row.published().isEmpty()?row.online():row.published();
        };
        if(quote.isBlank()) return List.of();
        boolean system=mode==Mode.B_C4;
        return List.of(new EvidenceContextBuilder.Block("E1",system?"SYSTEM":"PUBLIC",row.query(),
                system?null:"fixture-version",null,system?null:"fixture-doc",
                system?null:"fixture-version-id",system?null:"fixture-version-id:1",
                system?null:"https://docs.spring.io/spring-ai/reference/index.html",system?null:1,
                system?"C-4":"Fixture",quote));
    }
}
