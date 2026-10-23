package telamin.fluxtion.audit.analyser.analyser.assistant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M79-7 (spec-first-minute §19, owner 2026-10-19): the three Fluxtion philosophy packs published on the docs site are
 * droppable as they stand, name no unshipped teaching conversation, and name the same diamond run §6.4 records.
 * Each is a ★ test (rule 8); the witnesses are in the PR description.
 */
class PhilosophyPacksTest {

    static final Path PACKS = Path.of("docs/site/start/packs");
    static final Path SPEC = Path.of("docs/specs/spec-first-minute.md");
    static final Path README = Path.of("docs/proposals/packs/fluxtion-philosophy/README.md");
    static final String[] PUBLISHED = {"what-is-fluxtion.md", "local-knowledge-global-behaviour.md",
            "audit-logs-and-evidence.md"};

    static PromptPack.Result parse(String file) throws Exception {
        return PromptPack.parse(Files.readAllBytes(PACKS.resolve(file)));
    }

    /** ★ A7.1: each published pack parses as written (theDocsSampleIsValid's pattern), canonical, with §19.2's count. */
    @ParameterizedTest
    @CsvSource({"what-is-fluxtion.md, What is Fluxtion?, 8",
            "local-knowledge-global-behaviour.md, 'Local knowledge, global behaviour', 9",
            "audit-logs-and-evidence.md, Audit logs and evidence, 9"})
    void eachPublishedPackIsValid(String file, String title, int entries) throws Exception {
        var r = parse(file);
        assertTrue(r.ok(), () -> file + ": " + r.refusal().words());
        assertEquals(title, r.pack().title());
        assertEquals(entries, r.pack().entries().size(), file);
        assertTrue(r.pack().entries().size() <= PromptPack.MAX_ENTRIES);
        assertEquals("", r.pack().normalisedWords(), file + " is published in the canonical shape");
        assertTrue(r.pack().entries().get(0).isExample(), file + ": entry 1 is the teaching example");
    }

    /** ★ A7.2: a teach: entry added to a philosophy pack before a teaching conversation ships is refused by line. */
    @Test
    void aTeachEntryNamingAnUnshippedConversationIsRefused() throws Exception {
        String pack = Files.readString(PACKS.resolve("what-is-fluxtion.md"), StandardCharsets.UTF_8);
        String withTeach = pack.stripTrailing() + "\n9. **Watch it explained** — teach: the-diamond\n";
        int line = (int) withTeach.lines().count();
        var r = PromptPack.parse(withTeach.getBytes(StandardCharsets.UTF_8));
        assertFalse(r.ok(), "a teach: entry naming an unshipped conversation was accepted");
        assertEquals(line, r.refusal().line());
        assertEquals(PromptPack.Cause.TEACH_NOT_SHIPPED, r.refusal().cause());
        // the loose label forms reach the same check (the forgiving reader never turns teach: into prose)
        var loose = PromptPack.parse((pack.stripTrailing() + "\n- Watch it - teach: the-diamond\n")
                .getBytes(StandardCharsets.UTF_8));
        assertEquals(PromptPack.Cause.TEACH_NOT_SHIPPED, loose.refusal().cause());
    }

    /**
     * ★ A7.3: the commit every published pack's entry 1 pins is the one §6.4 records as byte-identical to the generic run
     * at the pin, and the philosophy README names the same commit.
     */
    @Test
    void thePinnedRunMatchesTheSpecsPin() throws Exception {
        String spec = Files.readString(SPEC, StandardCharsets.UTF_8);
        int from = spec.indexOf("### 6.4 The pin and its provenance");
        int to = spec.indexOf("\n## 7.", from);
        assertTrue(from >= 0 && to > from, "§6.4 not found in " + SPEC);
        String section = spec.substring(from, to);
        Matcher pin = Pattern.compile("`main` at \\*\\*`([0-9a-f]{40})`\\*\\*").matcher(section);
        assertTrue(pin.find(), "§6.4 states no 40-character pin");
        Matcher same = Pattern.compile("byte-identical\\s+at\\s+`([0-9a-f]{7,40})`,\\s+the\\s+commit\\s+the\\s+M74").matcher(section);
        assertTrue(same.find(), "§6.4 records no commit byte-identical to the generic run at the pin");
        String packCommit = same.group(1);
        for (String file : PUBLISHED) {
            var r = parse(file);
            assertTrue(r.ok(), () -> file + ": " + r.refusal().words());
            ExampleSource example = r.pack().entries().get(0).example();
            assertEquals("telaminai", example.owner(), file);
            assertEquals("fluxtion-diamond", example.repo(), file);
            assertTrue(example.commit().startsWith(packCommit), () -> file + " pins " + example.commit()
                    + ", but §6.4 records " + packCommit + " as the run matching the pin " + pin.group(1));
        }
        assertTrue(Files.readString(README, StandardCharsets.UTF_8).contains("`" + packCommit),
                "the philosophy README names a different diamond commit than §6.4");
    }
}
