package com.yhk.aistudyplanner.knowledge.hub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.knowledge.dto.KnowledgeSearchRequest;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeUnit;
import com.yhk.aistudyplanner.knowledge.repository.ResourceKnowledgeRepository;
import com.yhk.aistudyplanner.knowledge.service.KnowledgeRetriever;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import static org.junit.jupiter.api.Assertions.*;

class C12QualityEvaluationTest {
    record RetrievalCase(String split, Set<String> labels,String version,String scope,String condition,String query) {}
    record SearchRow(String query,Set<String> labels,List<String> returned,long micros) {}
    record SearchMetric(int cases,int positives,int negatives,int hits,double hitAt3,double recallAt3,
                        double mrr,int irrelevantReturned,long p50Micros,long p95Micros) {}
    record GenerationMetric(int cases,int passedReference,int invalidCitation,int supported,int contradicted,
                            int missingCases,int abstained,double supportRate,double citationValidity,double criticalErrorRate) {}
    private final ObjectMapper mapper=new ObjectMapper();

    @Test void scoreFrozenCasesAndExportMetrics() throws Exception {
        var cases=load("retrieval-v1.tsv").stream().map(p->new RetrievalCase(p[0],p[1].isBlank()?Set.of():
                Set.of(p[1].split(",")),p[2],p[3],p[4],p[5])).toList();
        assertEquals(56,cases.size());
        var pack=new ResourceKnowledgeRepository(mapper,new DefaultResourceLoader());
        var c4=new KnowledgeRetriever(pack);
        var reports=new LinkedHashMap<String,Object>();
        for(String split:List.of("dev","heldout")) {
            var methods=new LinkedHashMap<String,List<SearchRow>>();
            for(String name:List.of("C4","LEGACY","WEIGHTED")) methods.put(name,new ArrayList<>());
            for(var query:cases.stream().filter(row->row.split().equals(split)&&row.condition().equals("normal")).toList()){
                add(methods.get("C4"),query,()->c4.search(new KnowledgeSearchRequest(query.query(),null,3)).stream()
                        .map(hit->hit.unit().id()).toList());
                for(var strategy:DocumentRanking.Strategy.values()) {
                    add(methods.get(strategy.name()),query,()->simulateDocuments(pack.findAll(),query.query(),strategy));
                }
            }
            var summaries=new LinkedHashMap<String,SearchMetric>();
            methods.forEach((name,rows)->summaries.put(name,summarize(rows)));
            reports.put(split,Map.of("summary",summaries,"results",methods));
        }
        var labelledGates=cases.stream().filter(row->!row.condition().equals("normal")).toList();
        reports.put("gatesVerifiedInIntegrationOrUnitTests",labelledGates);
        var generationRows=new ArrayList<Map<String,String>>();
        var generation=judge(load("generation-v1.tsv"),generationRows);
        reports.put("generation",generation);
        reports.put("generationResults",generationRows);
        reports.put("scope","C4 runs real structured retrieval. Document ranking replays the same pack as a synthetic, in-memory corpus. No live provider.");
        export(reports);
        System.out.println("C12_GENERATION "+mapper.writeValueAsString(generation));
    }

    private List<String> simulateDocuments(List<KnowledgeUnit> units,String query,DocumentRanking.Strategy strategy) {
        var terms=EvidenceText.terms(RagRetrieval.searchable(query));
        var result=new ArrayList<KnowledgeHubSearch.Evidence>();
        for(var item:units) {
            var text=String.join(" ",item.keyPoints());
            var heading=String.join(" ",item.aliases());
            var match=DocumentRanking.score(strategy,item.topic(),heading,text,"MANUAL_REVIEW",terms);
            if(match.points()>0) result.add(new KnowledgeHubSearch.Evidence("PUBLIC",item.id(),item.id(),1,item.topic(),
                    heading,1,text,"Fixture",null,null,item.id(),"MANUAL_REVIEW",null,match.terms(),match.points()));
        }
        return DocumentRanking.rank(strategy,result,3).stream().map(KnowledgeHubSearch.Evidence::unitId).toList();
    }

    private static void add(List<SearchRow> rows,RetrievalCase query,java.util.function.Supplier<List<String>> task) {
        long begin=System.nanoTime();
        var returned=task.get();
        rows.add(new SearchRow(query.query(),query.labels(),returned,(System.nanoTime()-begin)/1000));
    }

    private static SearchMetric summarize(List<SearchRow> rows) {
        int positives=0, negatives=0, hits=0, falseReturns=0;
        double recall=0,rr=0;
        var times=rows.stream().map(SearchRow::micros).sorted().toList();
        for(var entry:rows){
            if(entry.labels().isEmpty()){
                negatives++;
                if(!entry.returned().isEmpty()) falseReturns++;
                continue;
            }
            positives++;
            long matched=entry.returned().stream().filter(entry.labels()::contains).count();
            recall+=(double)matched/entry.labels().size();
            for(int i=0;i<entry.returned().size();i++) if(entry.labels().contains(entry.returned().get(i))){
                hits++;
                rr+=1.0/(i+1);
                break;
            }
        }
        return new SearchMetric(rows.size(),positives,negatives,hits,(double)hits/positives,recall/positives,
                rr/positives,falseReturns,times.get((times.size()-1)/2),times.get((int)Math.ceil(times.size()*.95)-1));
    }

    private GenerationMetric judge(List<String[]> rows,List<Map<String,String>> details) throws Exception {
        int invalid=0,supported=0,contradicted=0,abstained=0,referenceMatches=0,missing=0;
        for(var row:rows){
            String verdict;
            if(row[2].isBlank()){
                missing++;
                verdict=RagAnswerService.decode("{\"claims\":[]}",List.of(),mapper).isEmpty()?"ABSTAIN":"UNSUPPORTED";
                if(verdict.equals("ABSTAIN")) abstained++;
            } else {
                var block=new EvidenceContextBuilder.Block("E1","PUBLIC",row[0],null,null,null,null,null,null,1,"Fixture",row[2]);
                String response=mapper.writeValueAsString(Map.of("claims",List.of(Map.of("statement",row[4],"evidenceId",row[5],"quote",row[2]))));
                try {
                    var decoded=RagAnswerService.decode(response,List.of(block),mapper);
                    if(decoded.get(0).statement().equals(row[3])) {supported++;verdict="SUPPORTED";}
                    else {contradicted++;verdict="CONTRADICTED";}
                } catch(IllegalArgumentException failure){invalid++;verdict="INVALID_CITATION";}
            }
            assertEquals(row[6],verdict,row[0]);
            if(verdict.equals(row[6])) referenceMatches++;
            details.add(Map.of("id",row[0],"category",row[1],"verdict",verdict));
        }
        return new GenerationMetric(rows.size(),referenceMatches,invalid,supported,contradicted,missing,abstained,
                supported/(double)(supported+contradicted), (supported+contradicted)/(double)(rows.size()-missing),
                contradicted/(double)(rows.size()-missing));
    }

    private List<String[]> load(String name) throws IOException {
        try(var input=Objects.requireNonNull(getClass().getResourceAsStream("/knowledge/c12/"+name))){
            return new String(input.readAllBytes(),StandardCharsets.UTF_8).lines().filter(line->!line.startsWith("#")&&!line.isBlank())
                    .map(line->line.split("\\|",-1)).toList();
        }
    }

    private void export(Map<String,Object> reports) throws IOException {
        var path=Path.of("target","c12-quality-v1.json");
        Files.createDirectories(path.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(),reports);
        System.out.println("C12_RETRIEVAL "+mapper.writeValueAsString(Map.of(
                "dev",((Map<?,?>)reports.get("dev")).get("summary"),"heldout",((Map<?,?>)reports.get("heldout")).get("summary"))));
    }
}
