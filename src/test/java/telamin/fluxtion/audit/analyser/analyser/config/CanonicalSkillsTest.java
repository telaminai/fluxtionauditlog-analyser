package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.llm.Json;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** M19.10 — the source library itself, before the playground substitutes project-owned values. */
class CanonicalSkillsTest {

    private static final Path ROOT = Path.of("docs/skills");
    private static final Path PUBLISHED_INDEX = ROOT.resolve("m19-skills/1/index.json");
    private static final String PUBLISHED_REVISION = "f5efe17e1b234bdb6c55cd8fada27d2bdc8d2bc8";

    @Test
    void exactlyTheFourCanonicalSkillsAreDiscoverableWithRequiredFrontmatter() throws Exception {
        SkillDiscovery.Found found = SkillDiscovery.find(ROOT, Map.of());
        assertEquals(Set.of("load-audit-log", "replay-a-run", "run-embedded", "run-mongoose-server"),
                found.candidates().stream().map(SkillDiscovery.Candidate::name).collect(Collectors.toSet()));
        assertFalse(found.truncated());
        for (SkillDiscovery.Candidate skill : found.candidates()) {
            assertTrue(skill.description() != null && !skill.description().isBlank(), skill.path());
            String text = Files.readString(ROOT.resolve(skill.path()));
            assertTrue(text.contains("x-analyser-min-version: 1.12.0"), skill.path());
        }
    }

    @Test
    void mongooseTierUsesRegistryAndYamlExport_notTheRejectedFileSinkStory() throws Exception {
        String text = Files.readString(ROOT.resolve("mongoose/run-mongoose-server/SKILL.md"));
        assertTrue(text.contains("~/.mongoose/servers/"));
        assertTrue(text.contains("/api/audit/file/{id}/export?format=yaml"));
        assertTrue(text.contains("Chronicle"));
        assertTrue(text.contains("TODO(bundle): substitute the exact clean-stop command"));
        assertFalse(text.contains("YAML deployment descriptor"));
        assertFalse(text.contains("addSink"));
    }

    @Test
    void loadLogSkillOpensAProjectBeforeTheLogBecauseTheSwitchEndsTheSession() throws Exception {
        String text = Files.readString(ROOT.resolve("common/load-audit-log/SKILL.md"));
        int projectCall = text.indexOf("analyser_open {\"project\"");
        int logCall = text.indexOf("analyser_open {\"log\"");
        assertTrue(projectCall >= 0 && logCall > projectCall,
                "the project call must be a distinct earlier step, not combined with the log");
        assertTrue(text.contains("analyser_context.project.active"));
        assertTrue(text.contains("Do not combine `project` with `log` or `graphml`"));
        assertTrue(text.contains("session boundary"));
        assertTrue(text.contains("ignored"));
    }

    @Test
    void embeddedTierCannotAccidentallyGraduateWithoutChangingItsExplicitGate() throws Exception {
        String text = Files.readString(ROOT.resolve("embedded/run-embedded/SKILL.md"));
        assertTrue(text.contains("STATUS: NOT PUBLISHABLE"));
        assertTrue(text.contains("setAuditLogProcessor"));
        assertTrue(text.contains("addSink") && text.contains("business output"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void publishedM19IndexPinsTheAcceptedMongooseSubsetAndItsExactBytes() throws Exception {
        Map<String, Object> index = (Map<String, Object>) Json.parse(Files.readString(PUBLISHED_INDEX));
        assertEquals("m19-skills/1", index.get("contract"));
        assertEquals(PUBLISHED_REVISION, index.get("revision"));

        List<Map<String, Object>> skills = (List<Map<String, Object>>) index.get("skills");
        Set<String> entries = skills.stream()
                .map(skill -> skill.get("tier") + ":" + skill.get("path"))
                .collect(Collectors.toSet());
        assertEquals(Set.of(
                "common:common/load-audit-log/SKILL.md",
                "mongoose:mongoose/run-mongoose-server/SKILL.md"), entries);
        assertEquals(2, skills.size(), "duplicate index entries are not a second selected skill");

        Map<String, String> expectedHashes = Map.of(
                "common/load-audit-log/SKILL.md",
                "936950b36a9cd123eaaaf76d9b3730ca2616645d26224397013f629548faefa8",
                "mongoose/run-mongoose-server/SKILL.md",
                "f2737e2c92c9b4e2ec1cb2776ad04c945da87346c309f48e3e67eef6bddda928");
        for (Map<String, Object> skill : skills) {
            String relative = (String) skill.get("path");
            Path source = ROOT.resolve(relative).normalize();
            assertTrue(source.startsWith(ROOT) && Files.isRegularFile(source), relative);
            assertEquals(expectedHashes.get(relative), sha256(Files.readString(source)), relative);
        }
    }

    private static String sha256(String text) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
