package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Review M74 usability #4 (2026-10-17): the analyser's internal words — "posture", "hosted", "generation", a verdict code
 * such as {@code POSTURE_CHANGED:} — must not reach the person. A STATIC rule over every string literal in {@code ui/}
 * and in the M74 sources whose words the assistant panel shows, so a new one fails here instead of in a screenshot.
 *
 * <p>It skips what never reaches a reader: comments, the audit record's keys ({@code auditLog.info(…)}), identifier-shaped
 * strings (a key, an enum name), and a verdict code a refusal carries as its prefix, which the node strips before
 * showing ({@code verdictOf}). Everything else is either reworded or on the reviewed exemption list below, each with its
 * reason.
 */
class UserVisibleJargonTest {

    static final Path MAIN = Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser");

    /** The M74 sources outside {@code ui/} whose strings the assistant panel or a dialog shows the person. */
    static final List<String> SHOWN_ELSEWHERE = List.of(
            "session/node/LocalAgentSession.java", "session/node/LocalAgentPermission.java",
            "session/node/AiSetup.java", "session/AiSetupLines.java", "session/node/StartPagePrompt.java",
            "ui/setup/FlowDiagram.java", "ui/setup/DiagramPalette.java",
            "session/node/ActionListener.java", "session/LocalAgentPosture.java",
            "assistant/localagent/LocalAgentAdapter.java", "assistant/localagent/StreamJson.java");

    static final Pattern JARGON = Pattern.compile("posture|hosted|POSTURE_CHANGED|BUDGET_SPENT|\\bgeneration\\b",
            Pattern.CASE_INSENSITIVE);
    static final Pattern LITERAL = Pattern.compile("\"((?:[^\"\\\\\\n]|\\\\.)*)\"");
    static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_.:$-]*");
    /** A refusal's verdict code, parsed off before the words are shown (LocalAgentSession.verdictOf). */
    static final Pattern CODED = Pattern.compile("^[A-Z_]+: .*");

    /**
     * The reviewed exemptions: a file whose strings are not the person's to read, or one literal by its file and text.
     * Each says why; adding one is a review decision.
     */
    static final Map<String, String> EXEMPT_FILES = Map.of(
            "ui/ActionExecutor.java", "verb results and refusals for the MODEL (the action socket's JSON), not the person");
    /**
     * Owner feedback (2026-10-17): the shared canvas's posture is "Work mode" (Investigate / Build) for the person, so it
     * has no exemption any more. Only a string that goes to the MODEL or the API may say "posture" — and none in these
     * files does (the action socket's results are {@link #EXEMPT_FILES}'s).
     */
    static final Map<String, String> EXEMPT_LITERALS = Map.of();

    record Hit(String file, int line, String text) {
    }

    static List<Hit> hits() throws IOException {
        List<Path> files = new ArrayList<>();
        try (Stream<Path> ui = Files.list(MAIN.resolve("ui"))) {
            ui.filter(p -> p.toString().endsWith(".java")).sorted().forEach(files::add);
        }
        for (String f : SHOWN_ELSEWHERE) files.add(MAIN.resolve(f));
        List<Hit> out = new ArrayList<>();
        for (Path f : files) {
            String rel = MAIN.relativize(f).toString().replace('\\', '/');
            if (EXEMPT_FILES.containsKey(rel)) continue;
            List<String> lines = Files.readAllLines(f);
            for (int i = 0; i < lines.size(); i++) {
                String l = lines.get(i);
                String s = l.strip();
                if (s.startsWith("*") || s.startsWith("//") || s.startsWith("/*")) continue;
                if (l.contains("auditLog") || l.contains(".info(")) continue;
                Matcher m = LITERAL.matcher(l);
                while (m.find()) {
                    String text = m.group(1);
                    if (IDENTIFIER.matcher(text).matches() || CODED.matcher(text).matches()) continue;
                    if (!JARGON.matcher(text).find()) continue;
                    if (EXEMPT_LITERALS.containsKey(rel + "|" + text)) continue;
                    out.add(new Hit(rel, i + 1, text));
                }
            }
        }
        return out;
    }

    /**
     * ★ Witness: put back any of the strings the review found — "Safe analysis (default posture)" in
     * {@code LocalAgentPosture.label}, "denied by the posture" in {@code TranscriptStyle}, "server: fluxtion-analyser-
     * hosted" — and this lists it.
     */
    @Test
    @DisplayName("★ no user-visible string in ui/ (or the panel's M74 sources) says posture, hosted, generation or a code")
    void noJargonReachesThePerson() throws IOException {
        List<Hit> found = hits();
        assertTrue(found.isEmpty(), "reword these (or exempt them, with a reason, after review): " + found);
    }

    /** The playground catalogue as served (captured 2026-09-20): its descriptions are what the template dialog shows. */
    static final Path SERVED_CATALOGUE = Path.of("docs/handoff/evidence/project-template-catalogue-2026-09-20/catalogue.json");
    static final Path ASK_CATALOGUE = Path.of(
            "src/main/resources/telamin/fluxtion/audit/analyser/analyser/assistant/AskCatalogue.properties");

    /**
     * ★ Authoring walk (2026-10-18, shot 4): an internal milestone id ("The M19 onboarding bundle…") must not reach the
     * person — not from the catalogue the analyser shows, nor from its own start-page and Ask words. Witness: make
     * {@code TemplateCatalogue.Entry.disclosure} use {@code description} again — the served catalogue's M19 is listed.
     */
    @Test
    @DisplayName("★ no milestone id (M19, M74.3) in a template's shown description or the Ask catalogue's words")
    void noMilestoneIdReachesThePerson() throws IOException {
        var pattern = telamin.fluxtion.audit.analyser.analyser.template.TemplateCatalogue.MILESTONE_ID;
        var served = telamin.fluxtion.audit.analyser.analyser.template.TemplateCatalogue.parse(
                Files.readString(SERVED_CATALOGUE), "test");
        assertTrue(served.templates().stream().anyMatch(t -> pattern.matcher(t.description()).find()),
                "the rule is not blind: the served catalogue carries one");
        List<String> found = new ArrayList<>();
        for (var t : served.templates()) {
            if (pattern.matcher(t.disclosure()).find()) found.add(t.name() + ": " + t.disclosure());
        }
        java.util.Properties ask = new java.util.Properties();
        try (var in = Files.newBufferedReader(ASK_CATALOGUE, java.nio.charset.StandardCharsets.UTF_8)) {
            ask.load(in);
        }
        for (String key : ask.stringPropertyNames()) {
            if (pattern.matcher(ask.getProperty(key)).find()) found.add(key + "=" + ask.getProperty(key));
        }
        assertTrue(found.isEmpty(), "a milestone id reaches the person: " + found);
        assertEquals("The onboarding bundle: a graph (in short).", telamin.fluxtion.audit.analyser.analyser.template
                .TemplateCatalogue.withoutMilestoneIds("The M19 onboarding bundle: a graph (M74.3) (in short)."));
    }

    /** The rule can see what it guards: the scan finds a planted string, and the exemptions still name real text. */
    @Test
    @DisplayName("the rule is not blind: it reads the files it names, and every exemption names a literal that exists")
    void theRuleSeesWhatItGuards() throws IOException {
        assertTrue(JARGON.matcher("Safe analysis (default posture)").find());
        assertTrue(JARGON.matcher("server: fluxtion-analyser-hosted").find());
        assertTrue(JARGON.matcher("the log is now generation 4").find());
        assertFalse(IDENTIFIER.matcher("Safe analysis (default posture)").matches(), "a sentence is never skipped");
        assertTrue(Files.isDirectory(MAIN.resolve("ui")));
        for (String f : SHOWN_ELSEWHERE) assertTrue(Files.isRegularFile(MAIN.resolve(f)), f);
        for (String key : EXEMPT_LITERALS.keySet()) {
            String[] parts = key.split("\\|", 2);
            assertTrue(Files.readString(MAIN.resolve(parts[0])).contains("\"" + parts[1] + "\""), "stale exemption: " + key);
        }
        assertEquals("Safe analysis", telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture.ANALYSER_ASSISTANT
                .label(), "the header chip says the profile's name");
    }
}
