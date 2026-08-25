package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Review P1 — the routes hop bound must be reachable from the ACTION SOCKET, not only from a Swing
 * checkbox.
 *
 * <p>H4 shipped the bound as a toolbar checkbox that defaults on, and the verb path runs through it,
 * so {@code topology {scope: "routes"}} began returning a three-hop answer while the echo told the
 * caller the unbounded one was "one untick away in the Topology toolbar". A process cannot untick a
 * checkbox. That is the shape M35.7 and review N2 both name: a remedy only a human at a keyboard can
 * perform, offered to something that is not one.
 *
 * <p>This is a SCHEMA test rather than a UI one, deliberately. What broke was the published surface —
 * the parameter an agent can see and send — and that surface is pure data, so it is testable headless
 * (CLAUDE.md rule 4: Swing is not unit-tested; a JSON schema is not Swing). The behaviour behind it is
 * pinned by TopologyFocusRoutesBoundTest, which already proves bounded and unbounded differ.
 */
class TopologyRouteBoundVerbTest {

    @SuppressWarnings("unchecked")
    private static Map<String, Object> topologyProps() {
        Map<String, Object> topology = (Map<String, Object>) VerbSchemas.all().get("topology");
        return (Map<String, Object>) topology.get("properties");
    }

    @Test
    void theRoutesBoundIsOnThePublishedVerbSurface() {
        assertTrue(topologyProps().containsKey("routeBound"),
                "an agent told 'the unbounded answer is one untick away' must have something to call: "
                        + "topology's parameters are " + topologyProps().keySet());
    }

    @Test
    @SuppressWarnings("unchecked")
    void itIsABooleanSoFalseCanMeanEveryRoute() {
        Map<String, Object> p = (Map<String, Object>) topologyProps().get("routeBound");
        assertTrue("boolean".equals(p.get("type")), "routeBound must be a boolean, was " + p.get("type"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void itsDescriptionSaysWhichWayLiftsTheBound() {
        // the parameter is useless if the caller cannot tell which value means "all of them" — and the
        // echo's scopeNote points here, so this text is the end of that trail
        String doc = String.valueOf(((Map<String, Object>) topologyProps().get("routeBound")).get("description"));
        assertTrue(doc.contains("false"), "say what false does: " + doc);
        assertTrue(doc.toLowerCase().contains("default"), "say what the default is: " + doc);
    }

    @Test
    void theScopeEnumIsUNCHANGED() {
        // the brief froze scope: node|neighbours|routes|all, and the fix must not have smuggled a fifth
        // value in through the back door — routeBound is a SIBLING parameter, which is the whole point
        String scope = String.valueOf(topologyProps().get("scope"));
        assertTrue(scope.contains("routes"), scope);
        assertFalse(scope.contains("routesBounded") || scope.contains("routes3"),
                "the scope enum must stay as published: " + scope);
    }
}
