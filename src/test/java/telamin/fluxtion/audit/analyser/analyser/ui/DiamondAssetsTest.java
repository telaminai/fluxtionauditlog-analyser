package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.firstminute.DiamondDefinition;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;
import telamin.fluxtion.audit.analyser.analyser.topology.AuditTrace;
import telamin.fluxtion.audit.analyser.analyser.topology.GraphMlParser;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkIdentity;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class DiamondAssetsTest {
    static byte[] bytes(String name) throws Exception {
        try (var in = DiamondAssets.class.getResourceAsStream("/demo/diamond/" + name)) {
            assertNotNull(in, "every manifest member must ship: " + name);
            return in.readAllBytes();
        }
    }
    static String text(String name) throws Exception { return new String(bytes(name), StandardCharsets.UTF_8); }

    @Test void everyMemberMatchesThePinnedManifestAndCarriesItsOriginalRepositoryPath() throws Exception {
        assertEquals(DiamondDefinition.PIN, DiamondAssets.manifest().getProperty("commit"));
        assertEquals(14, DiamondAssets.files().size());
        for (var file : DiamondAssets.files()) {
            assertFalse(file.source().isBlank());
            assertEquals(file.sha256(), DiamondAssets.sha256(bytes(file.file())), file.file());
            String source = text(file.file()).toLowerCase(java.util.Locale.ROOT);
            for (String term : DemoAssetsTest.rule1SweepTerms()) assertFalse(source.contains(term), file.file());
            assertFalse(source.contains("/users/"), "shipped evidence must not contain a private machine path");
        }
        assertEquals(DiamondDefinition.LOG_SHA256, DiamondAssets.sha256(bytes("diamond-spring-audit.yaml")));
        assertEquals(DiamondDefinition.GRAPH_SHA256, DiamondAssets.sha256(bytes("DiamondSpringProcessor.graphml")));
        assertEquals(DiamondDefinition.DESIGN_SHA256, DiamondAssets.sha256(bytes("diamond.xml")));
        assertTrue(DiamondAssets.manifest().getProperty("sourceNotice").contains("neutral headers"));
        // TODO(r8 iteration): the walk pin is re-checked when the gates come back; the manifest and constant still agree.
        assertEquals(DiamondDefinition.WALK_SHA256, DiamondAssets.manifest().getProperty("walk.sha256"));
    }

    @Test void thePinnedSpringRunHasTheExactRecordBasesAndTracedAbsenceTheWalkClaims() throws Exception {
        var store = new HeapLogStore(text("diamond-spring-audit.yaml"));
        assertEquals(6, store.size());
        assertEquals(DiamondDefinition.RECORD_2, WalkIdentity.recordDigest(store.rawText(2)));
        assertEquals(DiamondDefinition.RECORD_3, WalkIdentity.recordDigest(store.rawText(3)));
        assertEquals(DiamondDefinition.RECORD_4, WalkIdentity.recordDigest(store.rawText(4)));
        for (int i = 2; i <= 5; i++) assertTrue(AuditTrace.tracesEveryInvocation(store.record(i)), "record " + i);
        assertEquals(4, store.record(2).nodeLogs().size());
        assertEquals(1, store.record(4).nodeLogs().size());
        assertTrue(store.rawText(4).contains("changed: false"));
        assertEquals(15, GraphMlParser.parse(text("DiamondSpringProcessor.graphml")).nodes().size());
        var walk = DiamondDefinition.walk();
        assertEquals(9, walk.steps().size(), "r8: the teaching sequence, then the repeat and its proof");
        assertEquals(4, walk.steps().get(7).view().record(), "the repeat selects the record with no d entry");
        assertEquals(4, walk.steps().get(8).view().record());
        assertTrue(walk.steps().getFirst().targets().stream().anyMatch(t -> t.target().equals("source:design:bean:d")),
                "owner r8: the XML comes first");
    }

    /** r8: what each code caption claims, checked against the shipped file at the pinned range (and, for a guard, its body). */
    private static final java.util.Map<String, List<String>> CODE_CLAIMS = java.util.Map.ofEntries(
            java.util.Map.entry("source:java:com.acme.diamond.node.D:line:13-16", List.of("public D(B b, C c)", "this.b = b;", "this.c = c;")),
            java.util.Map.entry("source:java:com.acme.diamond.node.D:line:18-19", List.of("@OnTrigger", "public boolean recalculate()")),
            java.util.Map.entry("source:java:com.acme.diamond.node.A:line:11-12", List.of("@OnEventHandler", "public boolean onX(X x)")),
            java.util.Map.entry("source:java:com.acme.diamond.node.A:line:13-16", List.of("boolean changed = x.value() != value;", "return changed;")),
            java.util.Map.entry("source:java:com.acme.diamond.node.B:line:17-19", List.of("Math.floorDiv(a.value(), 2)", "next != value")),
            java.util.Map.entry("source:java:com.acme.diamond.node.B:line:21", List.of("return changed;")),
            java.util.Map.entry("source:java:com.acme.diamond.generated.spring.DiamondSpringProcessor:line:307",
                    List.of("isDirty_a = a.onX(typedEvent);")),
            java.util.Map.entry("source:java:com.acme.diamond.generated.spring.DiamondSpringProcessor:line:308-311",
                    List.of("if (guardCheck_b()) {", "isDirty_b = b.recalculate();")),
            java.util.Map.entry("source:java:com.acme.diamond.generated.spring.DiamondSpringProcessor:line:316-319",
                    List.of("if (guardCheck_d()) {", "d.recalculate();")));

    @Test void everyShippedCaptionIsTrueToItsPinnedSourceOrRecord() throws Exception {
        var walk = DiamondDefinition.walk();
        var shipped = new java.util.HashMap<String, String>();
        for (var f : DiamondAssets.files()) shipped.put(f.file(), f.sha256());
        java.util.Set<String> seen = new java.util.HashSet<>();
        // Roles with their time, never a claim about who wrote these pinned files (the README says by hand).
        var labels = List.of("Your LLM · design time", "Fluxtion · build time", "Audit log · run time");
        for (var step : walk.steps()) {
            assertTrue(labels.contains(step.caption()), "every step names whose work it shows: " + step.caption());
            boolean code = step.targets().stream().anyMatch(t -> t.target().startsWith("source:"));
            boolean generated = step.targets().stream().anyMatch(t -> t.target().contains(".generated."));
            boolean evidence = step.targets().stream().anyMatch(t -> t.target().startsWith("records:") || t.target().startsWith("detail:"));
            assertEquals(generated ? "Fluxtion · build time" : code ? "Your LLM · design time"
                            : evidence ? "Audit log · run time" : "Fluxtion · build time",
                    step.caption(), "the label matches what the step shows");
            for (var t : step.targets()) {
                if (!t.target().startsWith("source:java:")) continue;
                String[] parts = t.target().split(":");
                String file = "source/" + parts[2].replace('.', '/') + ".java";
                assertEquals("source", t.basis().kind(), t.target());
                assertEquals("sha256:" + shipped.get(file), t.basis().digest(), "a code caption is pinned to the shipped revision");
                String[] range = parts[4].split("-");
                int from = Integer.parseInt(range[0]), to = Integer.parseInt(range.length > 1 ? range[1] : range[0]);
                String[] lines = text(file).split("\n", -1);
                String band = String.join("\n", java.util.Arrays.asList(lines).subList(from - 1, to));
                List<String> claims = CODE_CLAIMS.get(t.target());
                assertNotNull(claims, "every code caption has its claim checked here: " + t.target());
                for (String claim : claims) assertTrue(band.contains(claim), t.target() + " shows " + claim + ":\n" + band);
                seen.add(t.target());
            }
        }
        assertEquals(CODE_CLAIMS.keySet(), seen, "no stale claim table entry");
        String processor = text("source/com/acme/diamond/generated/spring/DiamondSpringProcessor.java");
        for (String guard : List.of("private boolean guardCheck_b() {\n    return isDirty_a;",
                "private boolean guardCheck_c() {\n    return isDirty_a;", "private boolean guardCheck_d() {\n    return isDirty_b | isDirty_c;",
                "isDirty_a = false;\n    isDirty_b = false;\n    isDirty_c = false;",
                "    afterEvent();\n  }\n\n  public void handleEvent(EventLogControlEvent", "if (guardCheck_c()) {\n      auditInvocation(c, \"c\", \"recalculate\", \"OnTrigger\", typedEvent);\n      isDirty_c = c.recalculate();"))
            assertTrue(processor.contains(guard), "the guard a caption names: " + guard);
        // record captions: step 4 on record [2], steps 7 and 8 on record [4]
        var store = new HeapLogStore(text("diamond-spring-audit.yaml"));
        String r2 = store.rawText(2), r4 = store.rawText(4);
        assertTrue(r2.contains("X[value=2]"), "An X event arrives: X[value=2]");
        assertTrue(r2.contains("- a: { thread: main, method: onX, annotation: OnEventHandler, x: 2, value: 2, changed: true}"), "a changed");
        assertTrue(r2.contains("- b: {") && r2.contains("value: 1, changed: true") && r2.contains("- c: {") && r2.contains("value: 6, changed: true"),
                "b and c ran, both changed");
        assertTrue(r2.contains("- d: { thread: main, method: recalculate, annotation: OnTrigger, b: 1, c: 6"), "d ran with b: 1 and c: 6");
        assertEquals(List.of("a"), store.record(4).nodeLogs().stream().map(n -> n.instanceId()).toList(), "only a logged here");
        assertTrue(r4.contains("changed: false"), "a reported no change");
        for (int i : new int[]{3, 5}) for (var step : walk.steps())
            assertNotEquals(Integer.valueOf(i), step.view().record(), "SC-C1's held-out cycles are never walked through");
    }

    @Test void aSameSizeEditedExtractionIsReplacedNotReusedByPath(@TempDir Path tmp) throws Exception {
        DiamondAssets.install(tmp);
        byte[] original = bytes("diamond-spring-audit.yaml");
        byte[] edited = original.clone(); edited[10] ^= 1;
        Files.write(tmp.resolve("diamond-spring-audit.yaml"), edited);
        assertEquals(original.length, Files.size(tmp.resolve("diamond-spring-audit.yaml")), "control: size cannot detect this edit");
        DiamondAssets.install(tmp);
        assertArrayEquals(original, Files.readAllBytes(tmp.resolve("diamond-spring-audit.yaml")),
                "an edited extracted log must heal from the pinned resource, not be reused because it is nonempty");
    }

    @Test void extractionCannotFollowAnExistingParentSymlinkOutsideItsOwnRoot(@TempDir Path tmp) throws Exception {
        Path root = Files.createDirectories(tmp.resolve("diamond"));
        Path outside = Files.createDirectories(tmp.resolve("outside"));
        Files.createSymbolicLink(root.resolve("source"), outside);
        assertThrows(java.io.IOException.class, () -> DiamondAssets.install(root));
        try (var files = Files.list(outside)) { assertEquals(List.of(), files.toList(), "no source bytes are written outside the extraction"); }
    }
}
