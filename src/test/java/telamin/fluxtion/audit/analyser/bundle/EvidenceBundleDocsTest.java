package telamin.fluxtion.audit.analyser.bundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Evidence bundles: the docs site section is now the ONLY procedure (the capture and open skills were removed in the
 * convergence, 2026-09-28), so what it names must exist. The cheap static check (rule 8) that replaced
 * EvidenceBundleSkillsTest when the files it guarded were deleted:
 * <ul>
 *   <li>every {@code analyser --flag} the pages name is routed, except the two they state were removed;</li>
 *   <li>the {@code report {bundle}} fields they document are exactly the schema's;</li>
 *   <li>the {@code context.capture} fields they document are the ones the frame publishes;</li>
 *   <li>no page claims authentication or replay except to deny it.</li>
 * </ul>
 */
class EvidenceBundleDocsTest {

    static final Path SITE = Path.of("docs/site/evidence-bundles");
    static final Path FRAME = Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java");

    static String pages() throws Exception {
        StringBuilder b = new StringBuilder();
        try (var s = Files.list(SITE)) {
            for (Path p : s.sorted().toList()) b.append(Files.readString(p)).append('\n');
        }
        return b.toString();
    }

    @Test
    @DisplayName("the flags the pages name are the routed ones, and the retired two are named only as removed")
    void theFlagsExist() throws Exception {
        String text = pages();
        Set<String> named = new TreeSet<>();
        Matcher m = Pattern.compile("analyser (--[a-z-]+)").matcher(text);
        while (m.find()) named.add(m.group(1));
        assertEquals(new TreeSet<>(telamin.fluxtion.audit.analyser.MainAccess.bundleFlags()), named,
                "a page naming a flag the binary lacks fails at the recipient's terminal");
        for (String retired : telamin.fluxtion.audit.analyser.MainAccess.retiredBundleFlags()) {
            for (String line : text.split("\n")) {
                if (line.contains("`" + retired + "`")) {
                    assertTrue(line.contains("removed") || line.contains("existed"), "a retired flag is named only as removed: " + line);
                }
            }
        }
    }

    @Test
    @DisplayName("the report {bundle} fields the pages document are exactly the schema's")
    @SuppressWarnings("unchecked")
    void theBundleFieldsAreTheSchemas() throws Exception {
        var report = (Map<String, Object>) telamin.fluxtion.audit.analyser.analyser.llm.VerbSchemas.all().get("report");
        var props = (Map<String, Object>) ((Map<String, Object>) report.get("properties")).get("bundle");
        Set<String> schema = new TreeSet<>(((Map<String, Object>) props.get("properties")).keySet());
        assertEquals(Set.of("from", "notes", "path", "replay", "to"), schema, "control: the schema's bundle fields");
        String reference = Files.readString(SITE.resolve("reference.md"));
        assertTrue(reference.contains("report {bundle: {path, notes?, from?, to?, replay?}}"), "the reference states the form");
        String table = reference.substring(reference.indexOf("| field | meaning |"), reference.indexOf("## Reading"));
        for (String field : schema) assertTrue(table.contains("`" + field + "`"), "the reference's field table documents '" + field + "'");
    }

    @Test
    @DisplayName("the context.capture fields the pages document are the ones the frame publishes")
    void theCaptureFieldsArePublished() throws Exception {
        String frame = Files.readString(FRAME);
        String reference = Files.readString(SITE.resolve("reference.md"));
        for (String field : List.of("phase", "path", "identity", "reason", "lines")) {
            assertTrue(frame.contains("c.put(\"" + field + "\""), "the frame publishes context.capture." + field);
            assertTrue(reference.contains("`" + field + "`"), "and the reference names it: " + field);
        }
        assertTrue(frame.contains("out.put(\"capture\", c)"), "under context.capture");
    }

    @Test
    @DisplayName("no page claims more than a bundle can: authentication, or reproduction beyond the limit")
    void noOverclaim() throws Exception {
        // First delivery: a page could mention replay only to deny it. M70.R2–R4 made replay real (a bundle carries the
        // records, the runner replays them, --replay-compare judges), so mentioning it is no longer an overclaim. What
        // still is: saying the sender is authenticated, or that a bundle reproduces anything without the limit's
        // qualification. So a line about either must deny it, or qualify it ("only …").
        for (String line : pages().split("\n")) {
            String l = line.toLowerCase(java.util.Locale.ROOT);
            if (l.contains("authentic")) {
                assertTrue(l.contains("not") || l.contains("never") || l.contains("unsigned"),
                        "a page may mention authentication only to deny it: " + line);
            }
            if (l.contains("reproduc")) {
                assertTrue(l.contains("not") || l.contains("never") || l.contains("no replay") || l.contains("only"),
                        "a page may mention reproducing only to deny it or to qualify it: " + line);
            }
        }
    }

    static final Path CONVERSATIONS = Path.of("tools/capture-bundle-conversations.py");

    /** Claims the checks do not support (PR #70 review 5), in the pages and in the script that writes one of them. */
    static final java.util.List<String> UNSUPPORTED = java.util.List.of(
            "cannot be from another run",                  // pairing is consistency evidence, not run identity
            "so nothing the replay",                       // no service calls is not completeness
            "**is** the bundle's processor",               // a graph match is compatibility, not the same code
            "build the bundle's processor",
            "nodes and edges are the bundle's");

    @Test
    @DisplayName("PR #70 review 5: no page, nor the script that writes one, claims run identity, completeness or the same code")
    void theReplayIsDescribedAsTheChecksShowIt() throws Exception {
        String text = pages() + Files.readString(CONVERSATIONS).replaceAll("\"\\s*\n\\s*\"", "");
        for (String claim : UNSUPPORTED) assertFalse(text.contains(claim), "an unsupported claim: " + claim);
        // what IS said, in the page an assistant's user reads
        String page = Files.readString(SITE.resolve("with-an-assistant.md"));
        for (String bounded : java.util.List.of("That is consistency evidence", "That is not a completeness check",
                "not that it is the same code", "not proof of everything the build does")) {
            assertTrue(page.contains(bounded), "the page states the bounded meaning: " + bounded);
        }
    }
}
