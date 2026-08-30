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
    void exactlyTheCanonicalSkillsAreDiscoverableWithRequiredFrontmatter() throws Exception {
        SkillDiscovery.Found found = SkillDiscovery.find(ROOT, Map.of());
        assertEquals(Set.of("load-audit-log", "replay-a-run", "run-embedded", "run-mongoose-server",
                        "add-a-node", "guided-start"),
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

    @Test
    @SuppressWarnings("unchecked")
    void v2IsCommonPlusSPECIALISATIONS_andV1StaysByteIdentical() throws Exception {
        // v1 is consumed by a released playground build. Extending selection must not edit it.
        Map<String, Object> v1 = (Map<String, Object>) Json.parse(Files.readString(PUBLISHED_INDEX));
        assertEquals("m19-skills/1", v1.get("contract"));
        assertEquals(PUBLISHED_REVISION, v1.get("revision"), "v1 is pinned; add a version, never edit one");

        Map<String, Object> v2 = (Map<String, Object>) Json.parse(
                Files.readString(ROOT.resolve("m19-skills/2/index.json")));
        assertEquals("m19-skills/2", v2.get("contract"));

        List<String> common = (List<String>) v2.get("common");
        Map<String, Object> specialisations = (Map<String, Object>) v2.get("specialisations");
        assertFalse(common.isEmpty(), "common is always selected, so it cannot be empty");
        assertTrue(specialisations.keySet().containsAll(Set.of("mongoose", "embedded", "spring")));

        List<String> everyPath = new java.util.ArrayList<>(common);
        for (Map.Entry<String, Object> entry : specialisations.entrySet()) {
            Map<String, Object> spec = (Map<String, Object>) entry.getValue();
            assertTrue(spec.get("why") instanceof String why && !why.isBlank(),
                    entry.getKey() + ": a specialisation must say when it applies, or a template author"
                            + " picking one is guessing");
            everyPath.addAll((List<String>) spec.get("skills"));
        }

        for (String relative : everyPath) {
            Path source = ROOT.resolve(relative).normalize();
            assertTrue(source.startsWith(ROOT) && Files.isRegularFile(source), relative);
        }
        assertEquals(everyPath.size(), Set.copyOf(everyPath).size(),
                "a path in two places means one template selects the same skill twice");

        // the whole library must be reachable: a skill in no list is authored, reviewed and never shipped
        Set<String> indexed = Set.copyOf(everyPath);
        for (SkillDiscovery.Candidate candidate : SkillDiscovery.find(ROOT, Map.of()).candidates()) {
            assertTrue(indexed.contains(candidate.path()),
                    candidate.path() + " exists in the library but no index entry ships it");
        }
    }

    @Test
    void theSpringSkillPOINTSatTheAgreedResourcesRatherThanRestatingThem() throws Exception {
        String text = Files.readString(ROOT.resolve("spring/add-a-node/SKILL.md"));
        // D-AX1b: it may state what the published resources do NOT — the silent nodeBeans case and the
        // audit contract — and must send the reader upstream for what they DO cover.
        assertTrue(text.contains("fluxtion-playground.dev/CLAUDE.md"),
                "the field-error triage is published; link it rather than copying it");
        assertTrue(text.contains("referenced children are still discovered"),
                "the transitive nodeBeans rule is the silent case this skill exists for");
        assertTrue(text.contains("EventLogSource"),
                "the audit contract is in none of the agreed resources (fluxtion#22), so it belongs here");
        assertFalse(text.contains("transient") || text.contains("@FluxtionIgnore"),
                "the field remedies ARE published — restating them is the duplication D-AX1b forbids");
    }

    @Test
    void theGuidedStartSkillPOINTSratherThanTELLS_andCannotClobberOpenWork() throws Exception {
        String text = Files.readString(ROOT.resolve("common/guided-start/SKILL.md"));

        // D-G2. A tutor that narrates is testimony, which is the thing this product argues you should not
        // have to trust — so a tour where the assistant is the source of every claim demonstrates the
        // OPPOSITE of the product. These lines are the feature; losing them makes it a chatbot demo.
        assertTrue(text.contains("You point; the screen proves"), "the rule must be stated, not implied");
        assertTrue(text.contains("analyser_context"),
                "it must verify the view before saying 'as you can see' — otherwise it is guessing");

        // D-G7. Opening a project or another log closes what the person has open. A tour that destroys
        // work in progress is a support ticket, not an introduction.
        assertTrue(text.contains("Never open") || text.contains("never open"),
                "the warm path must refuse to open over the user's own work");
        assertTrue(text.contains("demo data"), "the demo must be labelled as demo when it is used");

        // it must use the TRACED demo log: absence is only proof at a level that captures the node
        assertTrue(text.contains("demo-quote-audit-traced.yaml"),
                "coverage on the untraced log would present silence as absence");

        // every analyser_* tool it names must be a real verb — inventing one is the failure mode this
        // whole library exists to avoid, and it fails on the user's screen
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("analyser_([a-z_]+)").matcher(text);
        Set<String> verbs = Set.of("aggregate", "context", "coverage", "filter", "flag", "goto", "graph",
                "open", "read", "report", "screenshot", "series", "source_root", "topology");
        while (m.find()) {
            assertTrue(verbs.contains(m.group(1)),
                    "guided-start names a verb that does not exist: " + m.group(1));
        }
    }

    @Test
    void theGuidedStartPageWritesEveryCommandOut() throws Exception {
        // D-G4: an install prompt is an instruction set a stranger executes. No fetch-and-run step, ever —
        // "run the script at $URL" is exactly the shape this must never take.
        String page = Files.readString(Path.of("docs/site/guided-start.md"));
        assertTrue(page.contains("jbang app install analyser@telaminai/fluxtionauditlog-analyser"),
                "the real install command must be present verbatim");
        // Check the COMMANDS, not the prose. The page legitimately explains that it contains no
        // fetch-and-run step, and an earlier version of this assertion matched that explanation — the
        // same trap CLAUDE.md rule 1 documents: a mechanical rule cannot tell a mention from a leak.
        StringBuilder commands = new StringBuilder();
        boolean inFence = false;
        for (String line : page.split("\n", -1)) {
            if (line.startsWith("```")) { inFence = !inFence; continue; }
            if (inFence) commands.append(line).append('\n');
        }
        assertTrue(commands.length() > 0, "the page must actually contain commands");
        for (String forbidden : List.of("curl", "wget", "| sh", "|sh", "| bash", "eval ", "<(")) {
            assertFalse(commands.toString().contains(forbidden),
                    "a command fetches or evaluates remote content (" + forbidden + "): every step must be"
                            + " readable before it is run (D-G4)");
        }
        assertTrue(page.contains("You do **not** need a Fluxtion API key"),
                "the keyless claim is the point of a first run and must be stated");
        // review F5: a real cold install stops on a JBang trust prompt that self-cancels, then may leave
        // `analyser` off PATH until a new shell. An agent that meets either undocumented stalls or fails.
        assertTrue(page.contains("TRUST") || page.contains("trust"),
                "the JBang trust prompt must be documented — it self-cancels and the decision is the "
                        + "human's, not the agent's");
        assertTrue(page.contains("~/.jbang/bin/analyser"),
                "the first-shell PATH boundary must have a concrete escape, not just a warning");
    }

    private static String sha256(String text) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
