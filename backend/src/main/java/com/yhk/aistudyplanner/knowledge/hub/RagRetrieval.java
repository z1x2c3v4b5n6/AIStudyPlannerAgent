package com.yhk.aistudyplanner.knowledge.hub;

import com.yhk.aistudyplanner.knowledge.dto.KnowledgeSearchResult;
import com.yhk.aistudyplanner.knowledge.model.KnowledgeDomain;
import java.util.*;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

@Service
public class RagRetrieval {
    public enum Scope { PUBLIC, PRIVATE, BOTH }
    public record RetrievalResult(List<KnowledgeSearchResult> structured, List<KnowledgeHubSearch.Evidence> documents,
                                  Set<String> usedVersions, String effectiveVersion, String caution,
                                  KnowledgeHubSearch.OnlineStatus onlineStatus) {}
    private static final Pattern VERSION =
            Pattern.compile("(?i)\\b(?:spring\\s+(?:boot|ai|framework)|java|jdk)\\s+v?(\\d+(?:\\.\\d+){0,3})(?![\\w.])");
    private static final Set<String> GENERIC = Set.of("spring","boot","java","jdk","framework","ai","version","configuration",
            "配置","版本","为什么","请问","怎么","如何","不能","没有","最新","现在");
    private final KnowledgeHubSearch hub;

    RagRetrieval(KnowledgeHubSearch hub) { this.hub=hub; }

    public RetrievalResult retrieve(String query, KnowledgeDomain domain, String version, Scope scope, boolean online) {
        String effective=version==null || version.isBlank()?implicitVersion(query):EvidenceText.normalize(version.strip());
        var result=hub.search(new KnowledgeHubSearch.Query(query,domain,effective,null,null,
                KnowledgeHubSearch.Scope.valueOf((scope==null?Scope.PUBLIC:scope).name()),online));
        var docs=new ArrayList<KnowledgeHubSearch.Evidence>();
        docs.addAll(result.published());
        docs.addAll(result.online());
        docs.removeIf(row->!relevant(query,row) || effective!=null && !effective.equalsIgnoreCase(row.softwareVersion()));
        docs.sort(Comparator.comparingInt(KnowledgeHubSearch.Evidence::score).reversed()
                .thenComparing(row->"OFFICIAL_FETCH".equals(row.ingestKind())?0:1)
                .thenComparing(row->Objects.toString(row.versionId(),row.url())).thenComparingInt(KnowledgeHubSearch.Evidence::ordinal));
        var versions=new TreeSet<String>();
        docs.stream().map(KnowledgeHubSearch.Evidence::softwareVersion).filter(Objects::nonNull).forEach(versions::add);
        String caution=null;
        if ("LATEST".equals(result.trigger()) && docs.stream().noneMatch(row->"ONLINE".equals(row.scope()) && row.softwareVersion()!=null)) {
            caution="LATEST_UNVERIFIED";
        } else if (effective==null && versions.size()>1) {
            caution="AMBIGUOUS_VERSION";
        } else if (effective==null && docs.stream().anyMatch(row->!"PRIVATE".equals(row.scope()) && row.softwareVersion()==null)) {
            docs.removeIf(row->!"PRIVATE".equals(row.scope()) && row.softwareVersion()==null);
            if (docs.isEmpty()) caution="VERSION_UNKNOWN";
        }
        if (disagrees(docs) && caution==null) caution="POSSIBLE_CONFLICT";
        return new RetrievalResult(result.structured(),List.copyOf(docs),Collections.unmodifiableSet(versions),
                effective,caution,result.onlineStatus());
    }

    public static String implicitVersion(String query) {
        var versions=VERSION.matcher(query).results().map(match->match.group(1)).distinct().limit(2).toList();
        return versions.size()==1?versions.get(0):null;
    }

    private static boolean relevant(String query,KnowledgeHubSearch.Evidence row) {
        var matched=row.matchedTerms().stream().filter(term->!GENERIC.contains(term) && !term.matches("[0-9.]+")).toList();
        if (matched.isEmpty()) return false;
        String subject=EvidenceText.normalize(row.title()+" "+row.heading());
        String passage=EvidenceText.normalize(row.excerpt());
        return matched.size()>1 || matched.stream().anyMatch(term->subject.contains(term) && passage.contains(term))
                || matched.stream().anyMatch(term->term.length()>=6 && passage.contains(term))
                || EvidenceText.terms(query).size()==1;
    }

    static boolean disagrees(List<KnowledgeHubSearch.Evidence> docs) {
        // A bounded detector for explicitly opposite on/off defaults for the same named setting.
        var values=new HashMap<String,String>();
        var setting=Pattern.compile("(?i)\\b([a-z][a-z0-9-]*(?:\\.[a-z0-9-]+)+)\\s*(?:defaults?\\s+to|默认|=|:)\\s*(true|false)\\b");
        for(var row:docs) {
            var matcher=setting.matcher(row.excerpt());
            while(matcher.find()) {
                String key=Objects.toString(row.softwareVersion(),"")+":"+matcher.group(1).toLowerCase(Locale.ROOT);
                String value=matcher.group(2).toLowerCase(Locale.ROOT);
                var prior=values.putIfAbsent(key,value);
                if(prior!=null && !prior.equals(value)) return true;
            }
        }
        return false;
    }

    boolean current(KnowledgeHubSearch.Evidence evidence) { return hub.current(evidence); }
}
