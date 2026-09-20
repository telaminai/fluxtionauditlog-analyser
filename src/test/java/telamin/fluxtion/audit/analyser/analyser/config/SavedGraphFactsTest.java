package telamin.fluxtion.audit.analyser.analyser.config;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.llm.SessionFacts;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class SavedGraphFactsTest {
    @Test void savedWithoutInputIsNotAnOpenOrBoundGraph() {
        var facts=SessionFacts.savedGraphs(List.of(new GraphSpec("PnL",List.of("book::pnl"))),Set.of(),false);
        assertEquals("PnL",facts.getFirst().get("name"));
        assertEquals(false,facts.getFirst().get("open"));
        assertEquals("waiting for input",facts.getFirst().get("input"));
    }
    @Test void openIsNotProofOfValidBindings() {
        var facts=SessionFacts.savedGraphs(List.of(new GraphSpec("PnL",List.of("book::pnl"))),Set.of("PnL"),true);
        assertEquals(true,facts.getFirst().get("open"));
        assertEquals("loaded; bindings require validation",facts.getFirst().get("input"));
    }
}
