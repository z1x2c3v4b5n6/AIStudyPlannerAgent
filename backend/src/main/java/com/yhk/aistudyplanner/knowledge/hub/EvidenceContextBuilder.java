package com.yhk.aistudyplanner.knowledge.hub;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.knowledge.dto.KnowledgeSearchResult;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EvidenceContextBuilder {
    public record Block(String id, String scope, String title, String softwareVersion, String unitId,
                        String documentId, String versionId, String chunkId, String url, Integer line,
                        String source, String text) {}
    public record Context(List<Block> blocks, Map<String,KnowledgeHubSearch.Evidence> documents, int actualCharacters) {}
    private final int budget;
    private final ObjectMapper mapper;

    EvidenceContextBuilder(ObjectMapper mapper,@Value("${app.knowledge.rag-context-characters:6200}") int budget) {
        this.mapper=mapper;
        this.budget=Math.max(512,Math.min(16000,budget));
    }

    public Context build(RagRetrieval.RetrievalResult retrieval, boolean includeStructured, boolean includePrivate) {
        var blocks=new ArrayList<Block>();
        var documents=new LinkedHashMap<String,KnowledgeHubSearch.Evidence>();
        var counts=new HashMap<String,Integer>();
        var selected=new ArrayList<String>();
        for(var row:retrieval.documents()) {
            if ("PRIVATE".equals(row.scope()) && !includePrivate) continue;
            String group=Objects.toString(row.documentId(),row.url());
            if(counts.getOrDefault(group,0)>=2) continue;
            String normalized=EvidenceText.normalize(row.excerpt()).replaceAll("\\s+"," ").strip();
            if(selected.stream().anyMatch(other->other.equals(normalized)
                    || other.length()>=80 && normalized.length()>=80 && (other.contains(normalized)||normalized.contains(other)))) continue;
            String id="E"+(blocks.size()+1);
            if(append(blocks,new Block(id,row.scope(),row.title(),row.softwareVersion(),row.unitId(),
                    row.documentId(),row.versionId(),Objects.toString(row.versionId(), "online")+":"+row.ordinal(),
                    row.url(),row.line(),row.source(),row.excerpt()))) {
                documents.put(id,row);
                counts.merge(group,1,Integer::sum);
                selected.add(normalized);
            }
            if(blocks.size()>=6) break;
        }
        if(includeStructured) for(KnowledgeSearchResult result:retrieval.structured()) {
            var unit=result.unit();
            if(blocks.size()>=8) break;
            append(blocks,new Block("E"+(blocks.size()+1),"SYSTEM",unit.topic(),null,unit.id(),
                    null,null,null,null,null,"C-4",
                    "阶段 "+unit.stage()+"\n前置 "+String.join("、",unit.prerequisites())+"\n"+String.join("\n",unit.keyPoints())));
        }
        return new Context(List.copyOf(blocks),Collections.unmodifiableMap(documents),length(blocks));
    }

    private boolean append(List<Block> blocks,Block original) {
        for (int limit=Math.min(1000,original.text().length());limit>=64;limit-=100) {
            var raw=original.text();
            String snippet=EvidenceText.shorten(raw,limit);
            var block=new Block(original.id(),original.scope(),original.title(),original.softwareVersion(),original.unitId(),
                    original.documentId(),original.versionId(),original.chunkId(),original.url(),original.line(),original.source(),snippet);
            blocks.add(block);
            if(length(blocks)<=budget) return true;
            blocks.remove(blocks.size()-1);
        }
        if(original.text().length()<64){
            blocks.add(original);
            if(length(blocks)<=budget) return true;
            blocks.remove(blocks.size()-1);
        }
        return false;
    }

    private int length(List<Block> blocks) {
        try {return mapper.writeValueAsString(blocks).length();}
        catch(JsonProcessingException error) {throw new IllegalStateException("Cannot encode evidence",error);}
    }
}
