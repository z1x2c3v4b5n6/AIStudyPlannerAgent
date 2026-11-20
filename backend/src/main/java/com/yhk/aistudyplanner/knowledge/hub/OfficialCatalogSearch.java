package com.yhk.aistudyplanner.knowledge.hub;

import com.yhk.aistudyplanner.common.exception.BusinessException;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeDomain;
import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

@Primary
@Component
class OfficialCatalogSearch implements KnowledgeSearchProvider {
    private static final Pattern SPRING_VERSION =
            Pattern.compile("^/spring-(boot|ai|framework)/(\\d+(?:\\.\\d+){0,3})/");
    private final RegisteredSourceSearch registered;
    private final OfficialDocuments documents;
    private final List<URI> catalogs;

    OfficialCatalogSearch(RegisteredSourceSearch registered,OfficialDocuments documents,
                          @Value("${app.knowledge.catalog-urls:}") String urls) {
        this.registered=registered;
        this.documents=documents;
        catalogs=Arrays.stream(urls.split(",")).map(String::strip).filter(url->!url.isEmpty()).limit(8)
                .map(OfficialDocuments::validate).toList();
    }

    @Override public List<KnowledgeHub.Source> find(KnowledgeHubSearch.Query query) {
        var fallback=registered.find(query);
        if(query.sourceId()!=null) return fallback;
        var catalog=catalogs.stream().filter(uri->query.softwareVersion()==null
                        || query.softwareVersion().equals(version(uri)))
                .filter(uri->query.domain()==null || query.domain()==domain(uri))
                .filter(uri->addressScore(uri,query.query())>0).findFirst();
        if(catalog.isEmpty()) return fallback;
        try {
            var root=catalog.get();
            var found=links(root,documents.catalog(root.toString()),query);
            if(!found.isEmpty()) return found;
        } catch(BusinessException unreachable) {
            // A configured official catalog is optional; still try registered pages.
        }
        return fallback;
    }

    static List<KnowledgeHub.Source> links(URI root,String html,KnowledgeHubSearch.Query query) {
        var ranked=new HashMap<String,Ranked>();
        var page=Jsoup.parse(html,root.toString());
        for(var anchor:page.select("a[href]")) {
            try {
                String link=anchor.absUrl("href").split("#",2)[0];
                var uri=OfficialDocuments.validate(link);
                if(uri.equals(root) || !uri.getHost().equals(root.getHost()) || !family(uri).equals(family(root))
                        || !Objects.equals(version(root),version(uri))
                        || query.softwareVersion()!=null && !query.softwareVersion().equals(version(uri))) continue;
                int score=addressScore(uri,query.query());
                var label=EvidenceText.normalize(anchor.text()+" "+uri.getPath());
                var specific=EvidenceText.terms(query.query()).stream()
                        .filter(term->!Set.of("spring","boot","framework","java","spring-boot","latest","ai").contains(term))
                        .filter(term->!term.matches("[0-9.]+")).toList();
                long matches=specific.stream().filter(label::contains).count();
                if(matches==0) continue;
                var source=new KnowledgeHub.Source(null,EvidenceText.shorten(anchor.text().strip(),120),link,
                        domain(uri),version(uri),null,null,null);
                ranked.merge(link,new Ranked(source, score+3*(int)matches),(old,newer)->old.score()>=newer.score()?old:newer);
            } catch(BusinessException invalid) { /* Ignore untrusted index links. */ }
        }
        return ranked.values().stream().sorted(Comparator.comparingInt(Ranked::score).reversed()
                .thenComparing(item->item.source().url())).limit(2).map(Ranked::source).toList();
    }

    private record Ranked(KnowledgeHub.Source source,int score) {}
    private static int addressScore(URI uri,String query) {
        String path=uri.getPath();
        int count=(int)EvidenceText.terms(query).stream().filter(path::contains).count();
        return Math.max(count,path.contains("spring-boot")&&EvidenceText.normalize(query).contains("spring boot")?1:0);
    }
    private static String family(URI uri) {return uri.getPath().split("/",3)[1];}
    private static String version(URI uri) {
        var match=SPRING_VERSION.matcher(uri.getPath());
        return match.find()?match.group(2):null;
    }
    private static KnowledgeDomain domain(URI uri) {
        return family(uri).equals("spring-ai")?KnowledgeDomain.AI_APPLICATION:KnowledgeDomain.JAVA_BACKEND;
    }
}
