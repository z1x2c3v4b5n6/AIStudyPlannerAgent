package com.yhk.aistudyplanner.knowledge.hub;

import java.util.*;
import java.util.regex.Pattern;

final class DocumentRanking {
    enum Strategy { LEGACY, WEIGHTED }
    record Match(List<String> terms,int points) {}
    private static final Set<String> GENERIC=Set.of("the","and","for","with","java","spring","boot","ai","version",
            "配置","版本","为什么","怎么","可以","如何","什么","需要","是否","一个","多个");

    private DocumentRanking() {}

    static Match score(Strategy strategy,String title,String heading,String text,String source,List<String> terms) {
        String normalizedTitle=EvidenceText.normalize(title);
        String normalizedHeading=EvidenceText.normalize(heading);
        String body=EvidenceText.normalize(text);
        String combined=normalizedTitle+" "+normalizedHeading;
        var matched=new ArrayList<String>();
        int score=0;
        for(var term:terms) {
            int points;
            if(strategy==Strategy.LEGACY) points=combined.contains(term)?4:body.contains(term)?1:0;
            else {
                if(GENERIC.contains(term) || term.matches("[0-9.]+")) continue;
                boolean titleMatch=contains(normalizedTitle,term);
                boolean headingMatch=contains(normalizedHeading,term);
                boolean bodyMatch=contains(body,term);
                points=titleMatch?7:headingMatch?5:bodyMatch?2:0;
                if(points>0 && bodyMatch && (titleMatch || headingMatch)) points+=2;
            }
            if(points>0) {matched.add(term);score+=points;}
        }
        if(strategy==Strategy.WEIGHTED && score>0) {
            // Weak single-token mentions cannot outvote a title or topical heading.
            if(matched.size()==1 && score==2 && matched.get(0).codePointCount(0,matched.get(0).length())<6)
                return new Match(List.of(),0);
            if("OFFICIAL_FETCH".equals(source)) score++;
        }
        return new Match(List.copyOf(matched),score);
    }

    static List<KnowledgeHubSearch.Evidence> rank(Strategy strategy,List<KnowledgeHubSearch.Evidence> input,int limit) {
        var sorted=input.stream().sorted(Comparator.comparingInt(KnowledgeHubSearch.Evidence::score).reversed()
                .thenComparing(KnowledgeHubSearch.Evidence::title)
                .thenComparing(KnowledgeHubSearch.Evidence::versionId,Comparator.nullsLast(String::compareTo))
                .thenComparingInt(KnowledgeHubSearch.Evidence::ordinal));
        if(strategy==Strategy.LEGACY) return sorted.limit(limit).toList();
        var count=new HashMap<String,Integer>();
        return sorted.filter(row->count.merge(Objects.toString(row.documentId(),row.url()),1,Integer::sum)<=2).limit(limit).toList();
    }

    private static boolean contains(String field,String term) {
        if(term.codePoints().anyMatch(cp->Character.UnicodeScript.of(cp)==Character.UnicodeScript.HAN))
            return field.contains(term);
        return Pattern.compile("(?<![a-z0-9])"+Pattern.quote(term)+"(?![a-z0-9])").matcher(field).find();
    }
}
