package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The fixture generator builds with the SAME Fluxtion toolchain as the analyser's own {@code regen} profile.
 *
 * <p>Found 2026-09-28 (spike README ▸ R0): {@code examples/fixture-generator} imported {@code fluxtion-bom} 1.0.64,
 * which gave {@code fluxtion-builder} 1.0.64 and {@code fluxtion-builder-api} 1.0.13, against a hosted generator
 * at 1.0.75. The processor it generated compiled, and its {@code auditEvent} methods were EMPTY: no clock reading,
 * no audit record, no replay record. Nothing failed; the regenerated DEMO would simply have written no audit log.
 * The root pom's comment claimed the two builds shared a pairing, and they did not.
 *
 * <p>So the claim is a test: both poms pin one runtime and one builder, and the fixture generator imports no
 * Fluxtion BOM, which would pin the builder API underneath the builder.
 */
class FixtureGeneratorToolchainTest {

    private static final Path ROOT_POM = Path.of("pom.xml");
    private static final Path FIXTURE_POM = Path.of("examples/fixture-generator/pom.xml");
    private static final List<String> PAIRED = List.of("fluxtion.version", "fluxtion.builder.version",
            "fluxtion.maven-plugin.version");

    /**
     * RAPID-OFF (2026-11-07): the legacy key route to the hosted generator is closed, so a regeneration pin below the
     * first builder with managed auth (compiler sign-in) or below fluxtion-maven-plugin 1.3.1 ({@code mvn fluxtion:login})
     * cannot regenerate at all. The floor, per property.
     */
    static final Map<String, String> MANAGED_AUTH_FLOOR = Map.of(
            "fluxtion.builder.version", "1.0.84",
            "fluxtion.maven-plugin.version", "1.3.1");

    static String property(String pom, String name) {
        Matcher m = Pattern.compile("<" + Pattern.quote(name) + ">\\s*([^<\\s]+)\\s*</" + Pattern.quote(name) + ">")
                .matcher(pom);
        return m.find() ? m.group(1) : null;
    }

    /** Every way the two poms disagree about the toolchain, in words; empty when they agree. */
    static List<String> disagreements(String rootPom, String fixturePom) {
        List<String> out = new ArrayList<>();
        for (String name : PAIRED) {
            String root = property(rootPom, name);
            String fixture = property(fixturePom, name);
            if (root == null) out.add("the root pom declares no " + name);
            else if (!root.equals(fixture)) out.add(name + ": root " + root + ", fixture generator " + fixture);
        }
        if (fixturePom.contains("<artifactId>fluxtion-bom</artifactId>")) {
            out.add("the fixture generator imports fluxtion-bom, which pins fluxtion-builder-api underneath the builder");
        }
        return out;
    }

    /** Numeric dotted-version comparison; a qualifier ({@code -SNAPSHOT}) is ignored. */
    static int compareVersions(String a, String b) {
        String[] x = a.replaceAll("-.*$", "").split("\\.");
        String[] y = b.replaceAll("-.*$", "").split("\\.");
        for (int i = 0; i < Math.max(x.length, y.length); i++) {
            int l = i < x.length ? Integer.parseInt(x[i]) : 0;
            int r = i < y.length ? Integer.parseInt(y[i]) : 0;
            if (l != r) return Integer.compare(l, r);
        }
        return 0;
    }

    /** Every regeneration pin in {@code pom} below the managed-auth floor, in words; empty when none is. */
    static List<String> belowManagedAuth(String label, String pom) {
        List<String> out = new ArrayList<>();
        for (var floor : new java.util.TreeMap<>(MANAGED_AUTH_FLOOR).entrySet()) {
            String pinned = property(pom, floor.getKey());
            if (pinned == null) out.add(label + " declares no " + floor.getKey());
            else if (compareVersions(pinned, floor.getValue()) < 0)
                out.add(label + " pins " + floor.getKey() + " " + pinned + ", below " + floor.getValue()
                        + " — the legacy key route is closed; regeneration needs compiler sign-in");
        }
        return out;
    }

    @Test
    void theRegenerationPinsCanSignIn() throws IOException {
        List<String> found = new ArrayList<>(belowManagedAuth("the root pom", Files.readString(ROOT_POM)));
        found.addAll(belowManagedAuth("the fixture generator", Files.readString(FIXTURE_POM)));
        assertTrue(found.isEmpty(), String.join("\n", found));
    }

    @Test
    void witnessTheFloorNamesALegacyKeyOnlyPin() {
        String legacy = "<fluxtion.builder.version>1.0.76</fluxtion.builder.version>"
                + "<fluxtion.maven-plugin.version>1.3.0</fluxtion.maven-plugin.version>";
        List<String> found = belowManagedAuth("the root pom", legacy);
        assertEquals(2, found.size(), found.toString());
        assertTrue(found.get(0).contains("fluxtion.builder.version 1.0.76, below 1.0.84"), found.get(0));
        assertTrue(found.get(1).contains("fluxtion.maven-plugin.version 1.3.0, below 1.3.1"), found.get(1));
        String signedIn = "<fluxtion.builder.version>1.0.90-SNAPSHOT</fluxtion.builder.version>"
                + "<fluxtion.maven-plugin.version>1.3.10</fluxtion.maven-plugin.version>";
        assertEquals(List.of(), belowManagedAuth("the root pom", signedIn), "1.3.10 is above 1.3.1, numerically");
    }

    @Test
    void theFixtureGeneratorBuildsWithTheAnalysersToolchain() throws IOException {
        List<String> found = disagreements(Files.readString(ROOT_POM), Files.readString(FIXTURE_POM));
        assertTrue(found.isEmpty(), String.join("\n", found));
    }

    @Test
    void theDependenciesUseThePinnedProperties() throws IOException {
        String fixture = Files.readString(FIXTURE_POM);
        // a property nobody references pins nothing
        assertTrue(fixture.contains("<version>${fluxtion.version}</version>"), "fluxtion-runtime must use it");
        assertTrue(fixture.contains("<version>${fluxtion.builder.version}</version>"), "fluxtion-builder must use it");
    }

    @Test
    void witnessTheCheckNamesTheSkewThatBrokeR0() {
        String plugin = "<fluxtion.maven-plugin.version>1.3.3</fluxtion.maven-plugin.version>";
        String root = "<fluxtion.version>1.0.16</fluxtion.version><fluxtion.builder.version>1.0.71</fluxtion.builder.version>"
                + plugin;
        String skewed = "<fluxtion.version>1.0.13</fluxtion.version>" + plugin
                + "<dependency><artifactId>fluxtion-bom</artifactId></dependency>";
        List<String> found = disagreements(root, skewed);
        assertEquals(3, found.size(), found.toString());
        assertTrue(found.get(0).contains("root 1.0.16, fixture generator 1.0.13"), found.get(0));
        assertTrue(found.get(1).contains("fixture generator null"), found.get(1));
        assertFalse(disagreements(root, root).iterator().hasNext(), "identical poms agree");
        // RAPID-OFF: the plugin is paired too — a fixture generator on the key-only plugin is named
        List<String> plugins = disagreements(root, root.replace("1.3.3", "1.3.0"));
        assertEquals(List.of("fluxtion.maven-plugin.version: root 1.3.3, fixture generator 1.3.0"), plugins);
    }

    /**
     * UPS-2, the review of PR #104, N1: the regenerated DEMO processors implement fluxtion 1.1.0's
     * {@code DataFlow.runInEventCycle}; without it the interface's refusing default applies, and nothing else here or in
     * the replay runner's test noticed when the review renamed it. A static check of the declaration only — the method's
     * behaviour is the runtime's and the generator's, and is exercised upstream (mongoose GeneratedCycleAdminAuditTest).
     */
    @Test
    void theDemoProcessorsImplementTheReleasedCycle() throws IOException {
        for (String processor : List.of("DemoQuoteProcessor", "DemoQuoteTracedProcessor", "DemoQuoteRecordedProcessor")) {
            for (String root : List.of("src/main/java", "src/main/resources")) {
                Path file = Path.of("examples/fixture-generator", root, "com/acme/demo/generated", processor + ".java");
                assertTrue(Files.readString(file).contains("public void runInEventCycle(Object auditEvent, Runnable action) {"),
                        file + " does not implement DataFlow.runInEventCycle (fluxtion runtime 1.1.0)");
            }
        }
    }

    /**
     * UPS-2, the review of PR #104, N3: the documentation-capture tool compiles the DEMO replay processor against the
     * analyser's runtime, so it reads that version from the root pom rather than naming one. It named 1.0.16 after the
     * pom moved to 1.1.0, and no guard noticed.
     */
    @Test
    void theCaptureToolTakesTheRuntimeFromThePom() throws IOException {
        String tool = Files.readString(Path.of("tools/capture-bundle-conversations.py"));
        assertTrue(tool.contains("<fluxtion\\.version>"), "the tool reads fluxtion.version from the pom");
        Matcher pinned = Pattern.compile("fluxtion-runtime[/-]\\d+\\.\\d+\\.\\d+").matcher(tool);
        assertFalse(pinned.find(), "the tool pins a runtime version of its own");
    }
}
