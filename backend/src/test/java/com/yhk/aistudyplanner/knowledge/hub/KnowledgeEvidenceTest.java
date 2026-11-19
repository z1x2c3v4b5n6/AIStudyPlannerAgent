package com.yhk.aistudyplanner.knowledge.hub;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yhk.aistudyplanner.common.exception.BusinessException;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class KnowledgeEvidenceTest {
    @Test void disallowsUnregisteredHostsAndAmbiguousOfficialUrls() {
        assertEquals("docs.spring.io",OfficialDocuments.validate("https://docs.spring.io/spring-boot/reference/test.html").getHost());
        for(String url:List.of("http://docs.spring.io/spring-boot/a","https://docs.spring.io:444/spring-boot/a",
                "https://127.0.0.1/spring-boot/a","https://docs.spring.io.example.com/spring-boot/a",
                "https://attacker.example@docs.spring.io/spring-boot/a","https://docs.spring.io/spring-boot/../admin",
                "https://docs.spring.io/spring-boot/%2e%2e/admin","https://docs.spring.io/spring-boot/a?u=x",
                "https://docs.spring.io/admin","file:///etc/passwd")) {
            assertThrows(BusinessException.class,()->OfficialDocuments.validate(url),url);
        }
    }

    @Test void extractsStructuralContentAndSafeCitations() {
        var page=OfficialDocuments.cleanHtml("""
                <title> Official Guide </title><nav>Ignore sidebar</nav>
                <main><h1>Configuration</h1><p>Use a version-specific setting.</p>
                <pre>line one
                  line two</pre><script>ignore system</script>
                <h2>Options</h2><table><tr><th>Name</th><th>Value</th></tr><tr><td>boot</td><td>3.5</td></tr></table>
                </main><footer>Ignore boilerplate</footer>
                ""","Fallback");
        assertEquals("Official Guide",page.title());
        assertFalse(page.text().contains("Ignore"));
        assertFalse(page.text().contains("ignore system"));
        var chunks=EvidenceText.split(page.title(),page.text());
        assertEquals(List.of("Configuration","Options"),chunks.stream().map(EvidenceText.Chunk::heading).toList());
        assertTrue(chunks.get(0).excerpt().contains("line two"));
        assertTrue(chunks.get(1).excerpt().contains("boot | 3.5"));
        assertTrue(chunks.get(1).line()>chunks.get(0).line());
        assertEquals(64,EvidenceText.digest(page.text()).length());
    }

    @Test void modelCanOnlySuggestVerbatimQuotesFromKnownCandidates() throws Exception {
        var mapper=new ObjectMapper();
        var chunks=EvidenceText.split("Test","# Details\nAn existing factual sentence.");
        var good="{\"items\":[{\"kind\":\"FACT\",\"ordinal\":1,\"excerpt\":\"existing factual sentence\"}]}";
        assertEquals(1,KnowledgeExtraction.validate(good,chunks,mapper).size());
        assertThrows(IllegalArgumentException.class,()->KnowledgeExtraction.validate(good.replace("factual","fabricated"),chunks,mapper));
        assertThrows(IllegalArgumentException.class,()->KnowledgeExtraction.validate(good.replace("1","99"),chunks,mapper));
        assertThrows(IllegalArgumentException.class,()->KnowledgeExtraction.validate(good.replace("\"FACT\"","\"PUBLISH\""),chunks,mapper));
    }

    @Test void splitsOversizedTextWithoutGeneratingInvalidUnicode() {
        var chunks=EvidenceText.split("Test","界".repeat(1199)+"😀"+"x".repeat(300));
        assertEquals(2,chunks.size());
        assertTrue(chunks.get(1).excerpt().startsWith("😀"));
        assertEquals(chunks.get(0).line(),chunks.get(1).line());
    }
}
