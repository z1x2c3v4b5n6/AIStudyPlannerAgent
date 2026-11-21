package com.yhk.aistudyplanner.knowledge.hub;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class TeachingExplanationsTest {
    private static final String PATH="/spring/reference/6.2/data-access/transaction/declarative/annotations.html";
    private static final String PREFIX="In proxy mode, only external method calls coming in through the proxy are intercepted.";
    private static final String FULL=PREFIX+" This means that self-invocation does not lead to a new interception.";

    @Test void aValidIdAndPartialQuoteCannotAuthorizeTheExplanation() throws Exception {
        var block=block("6.2",FULL);
        var matches=TeachingExplanations.matches(List.of(block));
        assertEquals(1,matches.size());
        var json=new ObjectMapper();
        var selection=json.createObjectNode();
        var item=selection.putArray("selection").addObject().put("noteId","transaction-proxy").put("evidenceId","E1");
        item.put("quote",PREFIX);
        assertTrue(TeachingExplanations.select(selection,matches).isEmpty());
        item.put("quote",FULL);
        assertEquals(1,TeachingExplanations.select(selection,matches).size());
        item.put("modelClaim","A self-call always starts a transaction.");
        assertTrue(TeachingExplanations.select(selection,matches).isEmpty());
    }

    @Test void disallowsUnsupportedVersionAndOffTopicEvidence() {
        assertTrue(TeachingExplanations.matches(List.of(block("7.0",FULL),block("6.2","Transaction timeout."))).isEmpty());
    }

    private EvidenceContextBuilder.Block block(String version,String text) {
        return new EvidenceContextBuilder.Block("E1","PUBLIC","Spring",version,"spring-transaction","doc","version","chunk",
                "https://docs.spring.io"+PATH,9,"Spring Docs",text);
    }
}
