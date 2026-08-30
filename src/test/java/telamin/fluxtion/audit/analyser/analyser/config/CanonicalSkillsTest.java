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
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
        Map<String, Object> v1 = (Map<String, Object>) Json.parse(Files.readString(PUBLISHED_INDEX));
        assertEquals("m19-skills/1", v1.get("contract"));
        assertEquals(PUBLISHED_REVISION, v1.get("revision"), "v1 is pinned; add a version, never edit one");

        Map<String, Object> v2 = v2Index();
        assertEquals("m19-skills/2", v2.get("contract"));
        assertFalse(((List<String>) v2.get("common")).isEmpty(), "common is always selected");

        Map<String, Object> specialisations = (Map<String, Object>) v2.get("specialisations");
        assertTrue(specialisations.keySet().containsAll(Set.of("mongoose", "embedded", "spring", "replay")));
        for (Map.Entry<String, Object> entry : specialisations.entrySet()) {
            Map<String, Object> spec = (Map<String, Object>) entry.getValue();
            assertTrue(spec.get("why") instanceof String why && !why.isBlank(),
                    entry.getKey() + ": a specialisation must say when it applies, or a template author"
                            + " picking one is guessing");
        }

        List<String> everyPath = v2Paths();
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
    @SuppressWarnings("unchecked")
    void aPUBLISHEDindexMustPinARevisionContainingEverySelectedByte() throws Exception {
        // review F2: v2's first cut named a revision at which two of its own skills did not exist, and the
        // test checked worktree existence only, so nothing caught it.
        //
        // Refined by C1: v2 is now DRAFT and makes NO provenance claim. That is deliberate — a pinned draft
        // goes stale on the next edit and then either lies or forces a red commit to re-pin. The rule this
        // enforces is therefore conditional on publication, which is when reproducibility actually matters.
        Map<String, Object> v2 = v2Index();
        boolean draft = String.valueOf(v2.get("status")).startsWith("DRAFT");
        if (draft) {
            assertNull(v2.get("revision"), "a draft must not advertise provenance it cannot keep current");
            assertNull(v2.get("sha256"), "same for hashes");
            return;
        }

        String revision = (String) v2.get("revision");
        assertTrue(revision != null && revision.matches("[0-9a-f]{40}"),
                "a published index must pin a full source revision");
        Map<String, Object> pinned = (Map<String, Object>) v2.get("sha256");
        assertEquals(Set.copyOf(v2Paths()), pinned.keySet(),
                "every selected path must be hash-pinned, and nothing else");
        for (String relative : v2Paths()) {
            String blob = gitShow(revision + ":docs/skills/" + relative);
            assertNotNull(blob, relative + " does not exist at the declared revision " + revision
                    + " — the index names a source a consumer cannot fetch");
            assertEquals(pinned.get(relative), sha256(blob),
                    relative + " differs from its pinned hash at " + revision);
            assertEquals(sha256(Files.readString(ROOT.resolve(relative))), pinned.get(relative),
                    relative + " has drifted in the worktree since the declared revision");
        }
    }

    @Test
    void theRevisionCheckFAILSwhenAPathIsAbsentThere() throws Exception {
        // review F2 asked for a negative test. Without one, v2sDeclaredRevisionCONTAINSeverySelectedByte
        // could be passing because gitShow always succeeds, which is how the original defect survived:
        // the old v2 test checked worktree existence only, so a revision naming nothing was still green.
        String v1Revision = (String) ((Map<String, Object>) Json.parse(Files.readString(PUBLISHED_INDEX)))
                .get("revision");
        assertNull(gitShow(v1Revision + ":docs/skills/common/guided-start/SKILL.md"),
                "guided-start post-dates v1's revision, so this lookup MUST return null — if it does not,"
                        + " the revision check cannot detect a revision that predates its own skills");
        assertNotNull(gitShow(v1Revision + ":docs/skills/common/load-audit-log/SKILL.md"),
                "and a path that DID exist there must resolve, or the check fails for the wrong reason");
    }

    /**
     * A SAMPLE selection, as a fixture. Review C1: templates are the playground's catalogue, so this index
     * must not own one — but the selection RULE still needs exercising, and a fixture does that without
     * becoming a second source of truth.
     */
    private static final List<String> SAMPLE_SPECIALISATIONS = List.of("spring", "mongoose");

    @Test
    @SuppressWarnings("unchecked")
    void aSampleTemplateResolvesToACompleteSelection() throws Exception {
        Map<String, Object> v2 = v2Index();
        assertNull(v2.get("templates"),
                "templates are the playground's catalogue (review C1); this index must not own one");

        Map<String, Object> specialisations = (Map<String, Object>) v2.get("specialisations");
        List<String> selected = new java.util.ArrayList<>((List<String>) v2.get("common"));
        for (String name : SAMPLE_SPECIALISATIONS) {
            assertTrue(specialisations.containsKey(name), "undeclared specialisation: " + name);
            selected.addAll((List<String>) ((Map<String, Object>) specialisations.get(name)).get("skills"));
        }
        for (String relative : selected) {
            assertTrue(Files.isRegularFile(ROOT.resolve(relative)), "selects a missing file: " + relative);
        }
        assertFalse(selected.contains("common/replay-a-run/SKILL.md"),
                "a template that does not declare a replay entry point must not select replay-a-run — its "
                        + "TODO(bundle) could not be substituted");
    }

    @Test
    @SuppressWarnings("unchecked")
    void aPUBLISHEDindexRecordsWHYItIsPublishable() throws Exception {
        // My first version said v2 must stay DRAFT while any selected skill carries a TODO(bundle) marker.
        // That rule was WRONG and contradicted this library's own README: a marker HERE is an instruction
        // to whoever generates the bundle, and the gate refuses only a marker that SURVIVES into a
        // generated bundle. Marker-freeness in the library was never the condition, and holding v2 draft
        // on it would have blocked publication forever.
        //
        // The real condition is a fact about the world — a consumer proved it substitutes them — which no
        // test here can observe. So what is enforced is that publishing carries its justification, rather
        // than being promoted by someone quietly editing one field.
        Map<String, Object> v2 = v2Index();
        if (String.valueOf(v2.get("status")).startsWith("DRAFT")) return;

        String comment = String.join(" ", (List<String>) v2.get("$comment"));
        assertTrue(comment.contains("PUBLISHED"), "a published index must say it is published, and when");
        assertTrue(comment.contains("generator") || comment.contains("consumed"),
                "and must name the evidence that lifted the draft — a consumer proving substitution end to "
                        + "end is the only thing that can, and it is not observable from this repository");
        assertNotNull(v2.get("revision"), "publishing binds the provenance guarantee");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> v2Index() throws Exception {
        return (Map<String, Object>) Json.parse(Files.readString(ROOT.resolve("m19-skills/2/index.json")));
    }

    @SuppressWarnings("unchecked")
    private static List<String> v2Paths() throws Exception {
        Map<String, Object> v2 = v2Index();
        List<String> paths = new java.util.ArrayList<>((List<String>) v2.get("common"));
        for (Object spec : ((Map<String, Object>) v2.get("specialisations")).values()) {
            paths.addAll((List<String>) ((Map<String, Object>) spec).get("skills"));
        }
        return paths;
    }

    /** @return the blob at a git revision, or null when the path does not exist there. */
    private static String gitShow(String spec) throws Exception {
        Process p = new ProcessBuilder("git", "show", spec).redirectErrorStream(false).start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        return p.waitFor() == 0 ? out : null;
    }

    private static String sha256(String text) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(text.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }
}
