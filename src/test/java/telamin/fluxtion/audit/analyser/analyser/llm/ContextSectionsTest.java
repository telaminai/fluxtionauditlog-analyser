package telamin.fluxtion.audit.analyser.analyser.llm;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Spring-authoring spec §H feedback 17 — {@code context {sections}} is a filter over the full payload that
 * keeps every qualification of what it selects. Headless: the projection is pure, and the frame's builder
 * is held to the same key table by reading its source.
 */
class ContextSectionsTest {

    /** A fixed context captured from one state — a log mid-load with producer faults, a graph and a project. */
    static Map<String, Object> fixture() {
        Map<String, Object> ctx = new LinkedHashMap<>();
        ctx.put("log", Map.of("path", "/work/demo/logs/demo-quote-audit.yaml", "records", 10, "openedBy", "you"));
        ctx.put("provenance", "DEMO-quote-service");
        ctx.put("project", Map.of("active", true, "name", "DemoQuote", "root", "/work/demo"));
        ctx.put("fluxtionKey", Map.of("canonicalFilePresent", false, "canonicalFile", "~/.fluxtion/fluxtion.apiKeyFile"));
        Map<String, Object> pair = new LinkedHashMap<>();
        pair.put("graph", "demo-quote-processor.graphml");
        pair.put("graphSource", "OPENED");
        pair.put("applies", true);
        pair.put("loggedNodes", 5);
        pair.put("declaredByGraph", 5);
        pair.put("verdict", "5/5 logged nodes are declared by the graph");
        pair.put("auditLogging", "enabled");
        pair.put("auditLoggingNote", "the graph declares addEventAudit()");
        ctx.put("graphPairing", pair);
        ctx.put("inFlight", "open log demo-quote-audit-2.yaml");
        ctx.put("processors", List.of(Map.of("class", "com.acme.demo.generated.DemoQuoteProcessor",
                "selected", true, "source", "found", "from", "project")));
        ctx.put("runbooks", List.of(Map.of("name", "stale-quote", "path", "docs/runbooks/stale-quote.md")));
        ctx.put("processorDeclarations", List.of());
        ctx.put("menus", Map.of("Project", List.of("Open project…", "Close project"),
                "Audit log", List.of("Open log…", "Close log")));
        ctx.put("menuChanges", List.of(Map.of("was", "Reset", "now", "Project ▸ Close log and topology")));
        ctx.put("savedGraphs", List.of());
        ctx.put("handoff", Map.of("posture", "explore", "derived", true));
        ctx.put("vocabulary", Map.of("path", "docs/glossary.md", "exists", true, "text", "live: quoting now"));
        ctx.put("exports", Map.of("enabled", false));
        ctx.put("dispatchOrder", "total — position in nodeLogs IS dispatch order (derived); safe to read as causality");
        ctx.put("source", Map.of("roots", List.of("/work/demo/src/main/java")));
        ctx.put("topology", Map.of("selected", "quoteHandler", "scope", "node"));
        ctx.put("filter", Map.of("text", "quote"));
        ctx.put("showing", Map.of("visible", 4, "total", 10));
        ctx.put("selection", List.of());
        ctx.put("flags", List.of(Map.of("recordIndex", 3, "kind", "fault")));
        ctx.put("timeOrder", "2 records earlier than their predecessor");
        ctx.put("producer", List.of("record 7: nodeLogs entry without instanceId"));
        ctx.put("graphs", List.of("latency"));
        ctx.put("graphScopes", List.of(Map.of("name", "latency", "filter", "own")));
        return ctx;
    }

    private static ContextSections.Selection select(String... names) {
        var parsed = ContextSections.parse(List.of(names));
        assertTrue(parsed.ok(), parsed.error());
        return parsed.selection();
    }

    @Test
    void menuOnly_isTheMenusAndTheScope_andNothingElse() {
        Map<String, Object> full = fixture();
        Map<String, Object> menus = select("menus").project(full);
        assertEquals(List.of("menus", "menuChanges", "scope"), List.copyOf(menus.keySet()),
                "no qualifier applies to the menu bar, so nothing else rides along");
        assertEquals(full.get("menus"), menus.get("menus"));
        assertEquals(full.get("menuChanges"), menus.get("menuChanges"));
        @SuppressWarnings("unchecked") Map<String, Object> scope = (Map<String, Object>) menus.get("scope");
        assertEquals(List.of("menus"), scope.get("sections"));
        assertEquals(ContextSections.NAMES, scope.get("available"));
        assertFalse(scope.containsKey("carried"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void aSelectedVerdictCarriesItsBasisAndEveryQualification() {
        Map<String, Object> full = fixture();
        Map<String, Object> pairing = select("pairing").project(full);
        // the verdict with its basis: the whole graphPairing object, not a picked "verdict" field
        assertEquals(full.get("graphPairing"), pairing.get("graphPairing"));
        Map<String, Object> pair = (Map<String, Object>) pairing.get("graphPairing");
        for (String basis : List.of("verdict", "applies", "loggedNodes", "declaredByGraph", "graph", "auditLoggingNote")) {
            assertTrue(pair.containsKey(basis), "basis " + basis);
        }
        // a load in flight and producer faults both qualify "5/5 logged"; neither may be silently dropped
        assertEquals(full.get("inFlight"), pairing.get("inFlight"), "an in-flight load qualifies the pairing");
        assertEquals(full.get("producer"), pairing.get("producer"), "producer faults qualify the logged count");
        Map<String, Object> carried = (Map<String, Object>) ((Map<String, Object>) pairing.get("scope")).get("carried");
        assertEquals(Set.of("inFlight", "producer"), carried.keySet(), "the scope says why each rode along");
        // and nothing that does not qualify it
        assertFalse(pairing.containsKey("dispatchOrder"));
        assertFalse(pairing.containsKey("log"));
    }

    @Test
    void everySectionEqualsTheSameSectionOfTheFullContext() {
        Map<String, Object> full = fixture();
        for (String name : ContextSections.NAMES) {
            Map<String, Object> part = select(name).project(full);
            for (Map.Entry<String, Object> e : part.entrySet()) {
                if (e.getKey().equals("scope")) continue;
                assertEquals(full.get(e.getKey()), e.getValue(), name + " → " + e.getKey());
            }
            for (String key : ContextSections.SECTIONS.get(name)) {
                assertEquals(full.containsKey(key), part.containsKey(key), name + " keeps " + key + " iff full has it");
            }
        }
        // a multi-section selection is the union, in the full payload's order
        Map<String, Object> two = select("view", "log").project(full);
        List<String> order = full.keySet().stream().filter(two::containsKey).toList();
        assertEquals(order, two.keySet().stream().filter(k -> !k.equals("scope")).toList());
    }

    @Test
    void emptyUnknownAndMisshapenSelectionsRefuse_theAbsentOneIsTheFullDefault() {
        var absent = ContextSections.parse(null);
        assertTrue(absent.ok());
        assertNull(absent.selection(), "no 'sections' is the full context, not an empty projection");

        var empty = ContextSections.parse(List.of());
        assertFalse(empty.ok());
        assertTrue(empty.error().contains("omit it for the full context"), empty.error());

        var unknown = ContextSections.parse(List.of("menus", "pairings"));
        assertFalse(unknown.ok(), "one bad name refuses the whole call, never a guessed subset");
        assertTrue(unknown.error().contains("[pairings]"), unknown.error());
        assertTrue(unknown.error().contains(ContextSections.NAMES.toString()), "the refusal publishes the names");

        assertFalse(ContextSections.parse("menus").ok(), "a bare string is not a list");
    }

    @Test
    void aProjectTransitionIsReadFreshFromEachState() {
        Map<String, Object> before = new LinkedHashMap<>();
        before.put("project", Map.of("active", false, "note", "your own settings — no project is open"));
        before.put("fluxtionKey", Map.of("canonicalFilePresent", false));
        before.put("exports", Map.of("enabled", false));
        before.put("filter", Map.of());
        Map<String, Object> after = fixture();
        after.put("projectOffer", Map.of("settings", "/work/demo/.analyser/project.fluxtion-settings"));

        var project = select("project");
        Map<String, Object> closed = project.project(before);
        Map<String, Object> opened = project.project(after);
        assertEquals(before.get("project"), closed.get("project"));
        assertFalse(closed.containsKey("runbooks"), "no project, no runbooks — nothing carried from another state");
        assertEquals(after.get("project"), opened.get("project"));
        for (String key : List.of("runbooks", "vocabulary", "projectOffer")) {
            assertEquals(after.get(key), opened.get(key), "portable context travels in 'project': " + key);
        }
        // and back: closing again reads the closed state, not the last projection
        assertEquals(closed, project.project(before));
    }

    @Test
    void theFullDefaultIsUnchanged_throughTheReferenceImplementation() {
        Map<String, Object> full = fixture();
        AppControl app = (AppControl) java.lang.reflect.Proxy.newProxyInstance(AppControl.class.getClassLoader(),
                new Class<?>[]{AppControl.class}, (proxy, m, args) -> {
                    if (m.getName().equals("context") && m.getParameterCount() == 0) {
                        return ActionResult.ok("context", "context", full);
                    }
                    if (m.isDefault()) return java.lang.reflect.InvocationHandler.invokeDefault(proxy, m, args);
                    throw new UnsupportedOperationException(m.getName());
                });
        assertSame(full, app.context().payload());
        assertFalse(full.containsKey("scope"), "the full response gains no scope key");
        assertEquals(select("menus").project(full), app.context(select("menus")).payload());
    }

    /**
     * Response bytes on {@link #fixture()} — recorded, not estimated. A change to the fixture or the scope text
     * moves them; update them from the failure message, never to a hoped-for figure.
     */
    @Test
    void recordsResponseBytesForTheFixedFixture() {
        Map<String, Object> full = fixture();
        int all = bytes(ActionResult.ok("context", "context", full));
        int menus = bytes(ActionResult.ok("context", "context", select("menus").project(full)));
        int pairing = bytes(ActionResult.ok("context", "context", select("pairing").project(full)));
        assertEquals(List.of(FULL_BYTES, MENUS_BYTES, PAIRING_BYTES), List.of(all, menus, pairing));
    }

    static final int FULL_BYTES = 1709;
    static final int MENUS_BYTES = 541;
    static final int PAIRING_BYTES = 817;

    private static int bytes(ActionResult r) {
        return r.toJson().getBytes(StandardCharsets.UTF_8).length;
    }

    /**
     * Every top-level key the frame's context() puts belongs to exactly one section, and every section key is
     * one it puts — a key added to context without a section would be unreachable by projection. Also holds
     * the builder's skip guards to real keys, so a renamed key cannot silently stop being built.
     */
    @Test
    void theSectionTableCoversEveryKeyTheBuilderPuts() throws Exception {
        String mainFrame = Files.readString(Path.of(
                "src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java"));
        int start = mainFrame.indexOf("ActionResult context(\n");
        assertTrue(start > 0, "context(Selection) not found");
        String body = mainFrame.substring(start, mainFrame.indexOf("public boolean showTab", start));
        Set<String> put = new LinkedHashSet<>();
        Matcher m = Pattern.compile("out\\.put\\(\"([A-Za-z]+)\"").matcher(body);
        while (m.find()) put.add(m.group(1));
        Set<String> sectioned = new LinkedHashSet<>();
        for (Map.Entry<String, List<String>> s : ContextSections.SECTIONS.entrySet()) {
            for (String key : s.getValue()) assertTrue(sectioned.add(key), key + " is in two sections");
        }
        assertEquals(Set.copyOf(put), Set.copyOf(sectioned), "unsectioned "
                + put.stream().filter(k -> !sectioned.contains(k)).toList() + ", never put "
                + sectioned.stream().filter(k -> !put.contains(k)).toList());
        for (String q : ContextSections.QUALIFIERS.keySet()) assertTrue(sectioned.contains(q), "qualifier " + q);
        Matcher guard = Pattern.compile("need\\.test\\(\"([A-Za-z]+)\"\\)").matcher(body);
        while (guard.find()) assertTrue(sectioned.contains(guard.group(1)), "guard names " + guard.group(1));
        // spec-portable-context: every tier-1 key is reachable, in the project section
        assertTrue(ContextSections.SECTIONS.get("project").containsAll(List.of(
                "runbooks", "vocabulary", "environments", "analyses", "reportDestinations")));
    }
}
