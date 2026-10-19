package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Owner hands-on test (2026-10-18): {@code context.processors[].from} said "discovered under a root" for the SELECTED
 * processor with no source root configured at all. A selected or configured class is a settings value; only a class a
 * root scan found is discovered.
 */
class ProcessorFromTest {

    /**
     * ★ Witness: drop {@code || fqn.equals(selected)} from {@code SessionFacts.processorFrom} — the selected-only class
     * is reported as discovered under a root, the wrong result the owner saw.
     */
    @Test
    @DisplayName("★ a selected class is the settings' own, never 'discovered under a root'")
    void selectedIsFromSettings() {
        String sel = "com.acme.demo.generated.DemoQuoteProcessor";
        assertEquals("own settings", SessionFacts.processorFrom(sel, List.of(), sel, false), "no project, no roots");
        assertEquals("project", SessionFacts.processorFrom(sel, List.of(), sel, true));
        assertEquals("project", SessionFacts.processorFrom("a.B", List.of("a.B"), sel, true), "configured");
        assertEquals("discovered under a root", SessionFacts.processorFrom("a.Found", List.of("a.B"), sel, true),
                "only a scanned class is discovered");
        assertEquals("discovered under a root", SessionFacts.processorFrom("a.Found", List.of(), "", false));
    }
}
