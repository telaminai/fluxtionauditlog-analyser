package telamin.fluxtion.audit.analyser.bundle;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Evidence bundle v1, B3: the two skills are procedures an agent follows literally, so what they NAME must exist. The
 * cheap static check (rule 8): every {@code analyser --flag} they name is a flag the binary routes, every flag the
 * binary routes is named by one of them, every {@code context} field they depend on is one the analyser publishes, and
 * neither ever claims more than the bundle can: authentication or replay.
 *
 * <p>They live in {@code docs/evidence-bundle/}, not {@code docs/skills/}: that library is what the playground seeds a
 * generated project with, through a pinned template index (CanonicalSkillsTest), and whether a generated project
 * should carry these is that contract's decision (tracker ▸ EB follow-ups), not this delivery's.
 */
class EvidenceBundleSkillsTest {

    static final Path DIR = Path.of("docs/evidence-bundle");
    static final List<String> SKILLS = List.of("capture-evidence-bundle", "open-evidence-bundle");
    static final Path MAIN = Path.of("src/main/java/telamin/fluxtion/audit/analyser/Main.java");
    static final Path FRAME = Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java");
    static final Path FACTS = Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/llm/SessionFacts.java");
    static final Path DRIVER = Path.of("tools/evidence-bundle-demo.py");

    static String skill(String name) throws Exception {
        return Files.readString(DIR.resolve(name).resolve("SKILL.md"));
    }

    @Test
    @DisplayName("each skill has its name, a description and the analyser version that has --pack and the context fields")
    void frontmatter() throws Exception {
        for (String name : SKILLS) {
            String text = skill(name);
            assertTrue(text.startsWith("---\nname: " + name + "\n"), name);
            assertTrue(text.matches("(?s).*\ndescription: \\S.*"), name + " has a description");
            assertTrue(text.contains("x-analyser-min-version: 1.27.0"), name + ": the first version with bundles");
        }
    }

    @Test
    @DisplayName("the flags the skills name are exactly the bundle flags the binary routes")
    void theFlagsExist() throws Exception {
        Set<String> named = new TreeSet<>();
        for (String name : SKILLS) {
            Matcher m = Pattern.compile("analyser (--[a-z-]+)").matcher(skill(name));
            while (m.find()) named.add(m.group(1));
        }
        assertEquals(new TreeSet<>(telamin.fluxtion.audit.analyser.MainAccess.bundleFlags()), named,
                "a skill naming a flag the binary lacks fails at the recipient's terminal; a flag no skill names is undocumented");
    }

    @Test
    @DisplayName("every context field the skills depend on is one MainFrame publishes")
    void theContextFieldsArePublished() throws Exception {
        String capture = skill("capture-evidence-bundle");
        String open = skill("open-evidence-bundle");
        String frame = Files.readString(FRAME);
        // field named in a skill -> the producer's text in MainFrame.context
        String[][] fields = {
                {"log.following", "log.put(\"following\""},
                {"log.path", "out.put(\"path\"", "SessionFacts"},
                {"log.identity.state", "log.put(\"identity\""},
                {"log.freshness.state", "log.put(\"freshness\""},
                {"log.generation", "log.put(\"generation\""},
                {"project.unsavedEdits", "proj.put(\"unsavedEdits\""},
                {"project.settings", "proj.put(\"settings\""},
                {"graphPairing.graphPath", "pair.put(\"graphPath\""},
                {"`inFlight`", "out.put(\"inFlight\""},
        };
        String facts = Files.readString(FACTS);
        for (String[] f : fields) {
            assertTrue(capture.contains(f[0]), "the capture skill depends on " + f[0]);
            String producer = f.length > 2 ? facts : frame;
            assertTrue(producer.contains(f[1]), f[0] + " is published: " + f[1]);
        }
        assertTrue(open.contains("walks.showing") && frame.contains("out.put(\"showing\""), "the open skill reads walks.showing");
    }

    @Test
    @DisplayName("neither skill claims authentication or replay, and each names the driver that runs it")
    void noOverclaimAndAnExecutableReference() throws Exception {
        for (String name : SKILLS) {
            for (String line : skill(name).split("\n")) {
                String l = line.toLowerCase(java.util.Locale.ROOT);
                if (l.contains("authentic") || l.contains("proves who")) {
                    assertTrue(l.contains("not") || l.contains("never") || l.contains("only thing that ties"),
                            name + " may mention authentication only to deny it: " + line);
                }
            }
            assertTrue(skill(name).contains("tools/evidence-bundle-demo.py"), name + " names its executable reference");
        }
        String driver = Files.readString(DRIVER);
        for (String fn : List.of("def capture_refusal(", "def capture(", "def open_bundle(", "def play(")) {
            assertTrue(driver.contains(fn), "the driver still has " + fn);
        }
    }
}
