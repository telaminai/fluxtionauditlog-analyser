package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M43.4 — the frontmatter of a skill-shaped runbook, read to OFFER a name and description.
 *
 * <p>Everything here is a suggestion for a dialog a person is looking at. The property that matters is
 * negative and is asserted in {@link RunbookDescriptionTest}: what {@code context} serves comes from the
 * profile, so editing the file afterwards cannot change it.
 */
class SkillFrontmatterTest {

    private static SkillFrontmatter.Suggestion parse(String... lines) {
        return SkillFrontmatter.parse(List.of(lines));
    }

    @Test
    void readsTheDocumentedSkillShape() {
        var s = parse("---",
                "name: restart-quote-service",
                "description: Restart the quote service after a config change; what to check first.",
                "---",
                "1. …");
        assertEquals("restart-quote-service", s.name());
        assertEquals("Restart the quote service after a config change; what to check first.", s.description());
    }

    @Test
    void aFileWithNoFrontmatterSuggestsNothing() {
        assertTrue(parse("# Restart", "1. stop it").isEmpty());
        assertTrue(parse().isEmpty());
    }

    @Test
    void frontmatterMustSTARTtheFile_notAppearLaterInIt() {
        // a `---` rule halfway down a markdown file is a horizontal rule, not a header block
        assertTrue(parse("# Restart", "", "---", "name: not-really").isEmpty());
    }

    @Test
    void quotedValuesAreUnwrapped() {
        var s = parse("---", "name: \"deploy\"", "description: 'Ship it, carefully.'", "---");
        assertEquals("deploy", s.name());
        assertEquals("Ship it, carefully.", s.description());
    }

    @Test
    void keysAfterTheClosingFenceAreNotRead() {
        var s = parse("---", "name: real", "---", "description: this is prose in the body");
        assertEquals("real", s.name());
        assertNull(s.description(), "the body is the runbook, not more frontmatter");
    }

    @Test
    void theFirstOccurrenceWins_andAColonInTheVALUEsurvives() {
        var s = parse("---",
                "description: Restart: stop, verify, start.",
                "description: a second one that must not overwrite",
                "---");
        assertEquals("Restart: stop, verify, start.", s.description());
    }

    // ---- what may be OFFERED is only ever what could be STORED ------------------------------------

    @Test
    void aSuggestedNameIsReducedToOneTheGateAccepts() {
        assertEquals("restart-quote-service", SkillFrontmatter.usableName("restart-quote-service").orElseThrow());
        assertEquals("restart-quote", SkillFrontmatter.usableName("  restart quote  ").orElseThrow());
        assertEquals("deploy", SkillFrontmatter.usableName("--deploy--").orElseThrow());
        assertTrue(SkillFrontmatter.usableName("---").isEmpty(), "nothing usable is left — offer nothing");
        assertTrue(SkillFrontmatter.usableName(null).isEmpty());
    }

    @Test
    void everySuggestedNameOfferedWouldActuallyBeACCEPTED() {
        // the offer must never be a value the gate then refuses: that would put a refusal in front of a
        // user for text they did not type, which reads as the tool being broken
        for (String raw : List.of("restart-quote-service", "restart quote", "Deploy_The_Thing",
                "a".repeat(80), "  spaced  name  ", "weird!!chars??here")) {
            SkillFrontmatter.usableName(raw).ifPresent(name ->
                    assertTrue(Runbooks.refuse(name, "ops/x.md").isEmpty(),
                            "offered '" + name + "' from '" + raw + "' but the gate refuses it"));
        }
    }

    @Test
    void aSuggestedDescriptionTooLongOrMultiLineIsNotOffered() {
        assertTrue(SkillFrontmatter.usableDescription("d".repeat(Runbooks.MAX_DESCRIPTION + 1)).isEmpty());
        assertTrue(SkillFrontmatter.usableDescription("   ").isEmpty());
        assertEquals("Ship it.", SkillFrontmatter.usableDescription("  Ship it.  ").orElseThrow());
    }

    // ---- reading a real file ----------------------------------------------------------------------

    @Test
    void readsFromDiskAndFailsQuietly(@TempDir Path dir) throws Exception {
        Path skill = dir.resolve("SKILL.md");
        Files.writeString(skill, "---\nname: deploy\ndescription: Ship it.\n---\n1. go\n");
        assertEquals("deploy", SkillFrontmatter.read(skill).name());

        // a prefill that cannot be made is not an error the user needs to hear about — they are about to
        // type the fields anyway, and an error dialog here would be noise in front of a working flow
        assertSame(SkillFrontmatter.NONE, SkillFrontmatter.read(dir.resolve("missing.md")));
        assertSame(SkillFrontmatter.NONE, SkillFrontmatter.read(dir));
        assertSame(SkillFrontmatter.NONE, SkillFrontmatter.read(null));
    }

    @Test
    void doesNotReadFarIntoALargeFile(@TempDir Path dir) throws Exception {
        // a runbook pointer could be aimed at anything; the reader must not pull a big file into memory
        Path big = dir.resolve("big.md");
        Files.writeString(big, "# not frontmatter\n" + "filler\n".repeat(200_000));
        assertTrue(SkillFrontmatter.read(big).isEmpty());
    }

    @Test
    void aPointerMayTargetASKILLfileInADotDirectory_becauseTheDOCSsaySoItCan() {
        // "Write runbooks in the skill shape" tells people a pointer can be
        // `.claude/skills/restart/SKILL.md`, so one file is both the team's runbook and a Claude Code
        // skill. Nothing pinned that: tighten the path rules and the docs quietly become a lie, which is
        // worse than a refusal because the reader trusts it.
        for (String path : java.util.List.of(
                ".claude/skills/restart/SKILL.md",
                ".claude/skills/restart-quote-service/SKILL.md",
                ".agents/skills/deploy/SKILL.md",
                "ops/restart-quote-service.md")) {
            assertTrue(Runbooks.refuse("restart", path).isEmpty(),
                    "the docs promise this path works, and the gate refuses it: " + path);
        }
    }

    @Test
    void aDotDotEscapeIsStillRefused_theDotDirectoryAllowanceIsNotAHole() {
        // allowing a leading dot must not have opened the traversal it was never meant to
        assertTrue(Runbooks.refuse("x", ".claude/../../etc/passwd").isPresent());
        assertTrue(Runbooks.refuse("x", "../secrets.md").isPresent());
    }
}
