package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Option 2 (owner, 2026-10-18): the whole-call shape of a starter proposal — pure, before anything reaches the session. */
class ProposeShapeStarterTest {

    static Map<String, Object> starter(String id) {
        return Map.of("kind", "starter", "starter", id, "reason", "Spring is the preferred route");
    }

    @Test
    @DisplayName("a starter alone is accepted; its id and optional names are carried, their form observed")
    void aStarterAloneIsAccepted() {
        var shaped = ProposeShape.of(Map.of("items", List.of(Map.of("kind", "starter", "starter", "fluxtion-spring",
                "artifact", "risk-app", "group", "com.acme", "basePackage", "com.acme.risk", "reason", "r"))));
        assertNull(shaped.refusal());
        var item = shaped.call().items().get(0);
        assertEquals("fluxtion-spring", item.starter());
        assertEquals("risk-app", item.artifact());
        assertFalse(item.namesMalformed());
        for (var bad : List.of(Map.of("kind", "starter", "starter", "fluxtion spring", "reason", "r"),
                Map.of("kind", "starter", "starter", "fluxtion-spring", "artifact", "-bad", "reason", "r"),
                Map.of("kind", "starter", "starter", "fluxtion-spring", "group", "com..acme", "reason", "r"),
                Map.of("kind", "starter", "starter", "fluxtion-spring", "basePackage", "1com.acme", "reason", "r"))) {
            assertTrue(ProposeShape.of(Map.of("items", List.of(bad))).call().items().get(0).namesMalformed(), bad.toString());
        }
    }

    /**
     * ★ Witness: drop the mixed-proposal refusal in {@code ProposeShape.of} — the starter beside a source root reaches the
     * session and this fails.
     */
    @Test
    @DisplayName("★ a mixed proposal is refused whole, and so are two starters")
    void aMixedProposalIsRefusedWhole() {
        var mixed = ProposeShape.of(Map.of("items", List.of(starter("fluxtion-spring"),
                Map.of("kind", "sourceRoot", "path", "src", "reason", "r"))));
        assertNull(mixed.call(), "refused whole");
        assertTrue(mixed.refusal().contains("stands alone"), mixed.refusal());
        var two = ProposeShape.of(Map.of("items", List.of(starter("fluxtion-spring"), starter("fluxtion-embedded"))));
        assertNull(two.call());
        assertTrue(two.refusal().contains("at most one starter"), two.refusal());
        assertTrue(two.refusal().endsWith("nothing was recorded"), two.refusal());
    }
}
