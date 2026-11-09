package telamin.fluxtion.audit.analyser.analyser.config;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.llm.Json;
import telamin.fluxtion.audit.analyser.analyser.template.SkillRegistry;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    void installAcceptanceGuidanceKeepsEvidenceAndAuthenticationHonest() throws Exception {
        String run = Files.readString(ROOT.resolve("mongoose/run-mongoose-server/SKILL.md"));
        assertTrue(run.contains("one server run by default"));
        assertTrue(run.contains("ALL_RUNS=1"));
        assertTrue(run.contains("saved build credential"));
        assertTrue(run.contains("does not refresh the authoring receipt"));
        assertFalse(run.contains("The export is cumulative"));
        assertFalse(run.contains("It reads `FLUXTION_API_KEY`"));
        String add = Files.readString(ROOT.resolve("spring/add-a-node/SKILL.md"));
        assertTrue(add.contains("Recover a partial generation attempt"));
        assertTrue(add.contains("valueType"));
        assertTrue(add.contains("not a success/failure result"));
        assertTrue(add.contains("cannot infer a new class"));
        for (String skill : List.of("guided-start", "point-at-the-fault")) {
            String text = Files.readString(ROOT.resolve("common/" + skill + "/SKILL.md"));
            assertTrue(text.contains("record screenshot placement as unverified"));
            assertTrue(text.contains("Do not change that permission silently"));
        }
    }

    @Test
    void serverFirstRunAndRegenerationHaveDistinctAuthenticationGuidance() throws Exception {
        String run = Files.readString(ROOT.resolve("mongoose/run-mongoose-server/SKILL.md"));
        String opening = run.substring(0, run.indexOf("## Steps"));
        assertTrue(opening.contains("initial build and run need no compiler"));
        assertTrue(opening.contains("Do not make an authentication preflight a prerequisite"));
        assertTrue(opening.contains("Compiler sign-in"));
        assertTrue(opening.contains("`RUNBOOK.md`, then `./generate.sh`"));
        for (String path : List.of("common/load-audit-log/SKILL.md", "common/guided-start/SKILL.md",
                "common/point-at-the-fault/SKILL.md", "mongoose/run-mongoose-server/SKILL.md",
                "spring/add-a-node/SKILL.md")) {
            String text = Files.readString(ROOT.resolve(path));
            for (String obsolete : List.of("the check belongs immediately before it", "key preflight",
                    "whether it needs a Fluxtion API key", "./check-fluxtion-key.sh"))
                assertFalse(text.contains(obsolete), path + ": obsolete authentication gate: " + obsolete);
        }
    }

    @Test
    void exactlyTheCanonicalSkillsAreDiscoverableWithRequiredFrontmatter() throws Exception {
        SkillDiscovery.Found found = SkillDiscovery.find(ROOT, Map.of());
        assertEquals(Set.of("load-audit-log", "replay-a-run", "run-embedded", "run-mongoose-server",
                        "add-a-node", "guided-start", "point-at-the-fault"),
                found.candidates().stream().map(SkillDiscovery.Candidate::name).collect(Collectors.toSet()));
        assertFalse(found.truncated());
        for (SkillDiscovery.Candidate skill : found.candidates()) {
            assertTrue(skill.description() != null && !skill.description().isBlank(), skill.path());
            String text = Files.readString(ROOT.resolve(skill.path()));
            assertTrue(text.contains("x-analyser-min-version: 1.12.0"), skill.path());
        }
    }

    /**
     * Stranger trial r2: runs read these skills with cat and never loaded them, and 0 of 10 flagged the proving record.
     * So the evidence procedure must stand in the first lines of the file, flag included.
     */
    @Test
    void theEvidenceProcedureIsInTheFirstLinesAndEndsInAFlag() throws Exception {
        for (String skill : List.of("common/load-audit-log/SKILL.md", "common/point-at-the-fault/SKILL.md")) {
            List<String> lines = Files.readAllLines(ROOT.resolve(skill));
            String head = String.join("\n", lines.subList(0, Math.min(25, lines.size())));
            int spot = head.indexOf("analyser_spotlight"), flag = head.indexOf("analyser_flag");
            assertTrue(spot >= 0 && flag > spot, skill + ": spotlight then flag within the first 25 lines");
        }
    }

    /**
     * Flag kind default change: a flag without {@code kind} now saves a neutral note, so the fault-found path must say
     * {@code "kind": "fault"} or a real fault is recorded as a note. The proving-a-claim line explicitly confirms correct behaviour and explains older defaults.
     */
    @Test
    void pointAtTheFaultFlagsAFoundFaultAsAFault() throws Exception {
        String text = Files.readString(ROOT.resolve("common/point-at-the-fault/SKILL.md"));
        String last = text.substring(text.indexOf("### And make it last"), text.indexOf("## What your captions"));
        assertTrue(last.contains("analyser_flag {\"recordIndexes\": [<n>], \"kind\": \"fault\""),
                "the fault-found flag must carry kind fault");
        String example = text.substring(text.indexOf("## Worked example"), text.indexOf("And the same runbook"));
        int flag = example.indexOf("analyser_flag");
        assertTrue(flag >= 0 && example.indexOf("\"kind\": \"fault\"", flag) > flag,
                "the worked example's flag must carry kind fault");
        String claim = text.substring(text.indexOf("## Showing which record proves a claim"),
                text.indexOf("## If you are fixing a fault"));
        assertFalse(claim.contains("analyser_flag {\"recordIndexes\": [<n>], \"kind\": \"fault\""),
                "proving a claim is not a fault by default");
        assertTrue(claim.contains("\"kind\": \"confirmation\"") && claim.contains("neutral note"));
        assertTrue(claim.contains("1.40.3 and earlier") && claim.contains("do not accept `note`"));
        assertTrue(claim.contains("tool's schema lists `note`") && claim.contains("explanation instead"));
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
    void springSkillOwnsTheAuthoringProofLoop_withoutInventingAnAnalyserWorkflowVerb() throws Exception {
        String text = Files.readString(ROOT.resolve("spring/add-a-node/SKILL.md"));
        assertTrue(text.contains("Before running the changed application, write down predictions"));
        assertTrue(text.contains("`run-mongoose-server`"));
        assertTrue(text.contains("`load-audit-log`"));
        assertTrue(text.contains("`analyser_context.graphPairing`"));
        assertTrue(text.contains("The exported **Fluxtion audit log**, not Mongoose's ordinary server log"));
        assertTrue(text.contains("host-level rather than application-semantic evidence"));
        assertTrue(text.contains("The model owns this sequence and its judgement"));
        assertTrue(text.contains("Do not introduce a compound analyser verb for this workflow"));
    }

    @Test
    void springSkillAddsANodeXmlFirst_sinkAndReferencesDeclaredAndTheShellRegenerated() throws Exception {
        // Owner cycle 5 (5.7, 5.8): an author hand-wrote the class and wired its sink with a hand-written
        // @ServiceRegistered callback, so the design XML lost the sink, then extracted jars to find imports.
        String text = Files.readString(ROOT.resolve("spring/add-a-node/SKILL.md"));
        for (String route : List.of("FluxtionSpringConfig.SinkBinding", "<property name=\"sinks\">",
                "FluxtionSpringConfig.ReferenceBinding", "<property name=\"mode\" value=\"DATA\"/>",
                "<property name=\"parentUpdateCallback\" value=\"true\"/>"))
            assertTrue(text.contains(route), "the XML route must be shown: " + route);
        // PR 137 review: a ReferenceBinding's referencedBean must itself be in nodeBeans. A parent reached only
        // through a constructor-arg ref is not a node, and starter 1.0.79's offline validate refuses the binding
        // with SPRING_REFERENCE_UNKNOWN_BEAN.
        assertTrue(text.contains("`fluxtionSpringConfig`'s `nodeBeans`**, and any bean a binding names."),
                "step 2 must say a bean a binding names is listed in nodeBeans");
        assertTrue(text.contains("Then **regenerate** — a graph change needs it"), "the regenerate step is pinned");
        assertTrue(text.contains("**Regeneration writes the class shell**"));
        assertTrue(text.contains("there are no imports to find"));
        assertTrue(text.contains("**Do not write the class yourself, and do not wire a sink outside the XML**"));
        assertTrue(text.contains("mark each DATA field `@NoTriggerReference`"));
        assertTrue(text.contains("disagrees with the XML is refused\n(`SPRING_RECONCILE_CONFLICT`)"));
        assertTrue(text.contains("is not declared in the design"));
        for (String taskName : List.of("alertNode", "riskCheck", "csvRejections", "\"alerts\""))
            assertFalse(text.contains(taskName), "the snippet must not carry a ladder task's own names: " + taskName);
        assertFalse(text.contains("**Write the class.**"), "the hand-written-class step is gone");
    }

    @Test
    void springSkillProvesAChangeByComparingBusinessRecords_andNamesTheScriptItShips() throws Exception {
        String text = Files.readString(ROOT.resolve("spring/add-a-node/SKILL.md"));
        assertTrue(text.contains("## Prove a change by comparing audit logs"));
        assertTrue(text.contains("## Verify from the audit log, not from generated code"));
        assertTrue(text.contains("python3 audit-compare.py before/audit-<processor>.yaml after/audit-<processor>.yaml"));
        for (String key : List.of("`thread`", "`method`", "`annotation`", "`forkedExecution`", "`asyncMethod`"))
            assertTrue(text.contains(key), "the framework keys the script separates must be named: " + key);
        assertTrue(text.contains("comparison **only**"),
                "the verdict comes from the business comparison alone");
        assertTrue(Files.isRegularFile(ROOT.resolve("spring/add-a-node/audit-compare.py")),
                "the skill names a script that must ship beside it");
    }

    @Test
    void noSkillTellsTheAuthorToReadGeneratedCode() throws Exception {
        // G14 grid 2026-10-12: an author verifying its change read the generated processor, the builder and the
        // scripts' source - about a fifth of all tool output - and none of it was needed; the skill told it to.
        // The author reads the Spring XML, the compiler's own messages and the audit log.
        // Review of PR 127: the first guard knew four verbs and exempted any sentence containing "not"; each of
        // these evasions passed it. They are the witnesses the guard must catch.
        for (String evasion : List.of(
                "Look at the generated processor to confirm the order.",
                "Review the generated Java before you run.",
                "Examine the GraphML to see the edges.",
                "Open target/generated-sources/fluxtion/Processor.java and find your node.",
                "Confirm the dispatch order in the generated onEvent body.",
                "Read the processor the generator wrote.",
                "Read the generated processor if the receipt does not list your node.",
                "cat the builder to see the wiring.",
                "Start by reading the generated processor.",
                "Grep the generated processor for your node.",
                "Search the generated source for onTrigger.",
                "Look through the generated code.",
                "Study the regenerated dispatch code.",
                "Open the GraphML in a text editor.",
                "Open src/main/java/com/acme/generated/QuoteProcessor.java.",
                "It is not enough to check the XML, read the generated processor."))
            assertTrue(tellsTheAuthorToReadGeneratedCode(evasion), "the guard must catch: " + evasion);
        for (String allowed : List.of(
                "Do not read the regenerated processor, its GraphML text or the builder to confirm the wiring.",
                "Never read the generated processor.",
                "Follow `load-audit-log` to open that export with the generated GraphML.",
                "That is the evidence for dispatch order, not a reading of the generated `onEvent` body.",
                "Check a source the builder reads."))
            assertFalse(tellsTheAuthorToReadGeneratedCode(allowed), "a prohibition or an analyser step is allowed: " + allowed);
        try (var skills = Files.walk(ROOT)) {
            for (Path skill : skills.filter(f -> f.getFileName().toString().equals("SKILL.md")).toList()) {
                for (String sentence : Files.readString(skill).split("(?<=[.:;])\\s+")) {
                    assertFalse(tellsTheAuthorToReadGeneratedCode(sentence),
                            ROOT.relativize(skill) + " tells the author to read generated code: " + sentence.strip());
                }
            }
        }
        String addANode = Files.readString(ROOT.resolve("spring/add-a-node/SKILL.md"));
        assertTrue(addANode.contains("read only the compiler's own messages"), "the replacement instruction is present");
    }

    private static final java.util.regex.Pattern READ_VERB = java.util.regex.Pattern.compile(
            "(?i)\\b(read(?:s|ing)?|inspect(?:s|ing)?|open(?:s|ing)?|check(?:s|ing)?|look(?:s|ing)? (?:at|in|through)"
                    + "|review(?:s|ing)?|examin(?:e|es|ing)|confirm(?:s|ing)?|view(?:s|ing)?|cat|grep(?:s|ping)?"
                    + "|search(?:es|ing)?|stud(?:y|ies|ying))\\b");
    private static final java.util.regex.Pattern GENERATED_OBJECT = java.util.regex.Pattern.compile(
            "(?i)^[^.\\n]{0,60}?(\\b(re)?generated[ -](processor|sources?|code|java|file|onEvent|dispatch)|generated-sources"
                    + "|/generated/|\\bprocessor the generator wrote|\\bGraphML\\b)");
    /** The builder only as the verb's direct object: "a source the builder reads" (fluxtion-builder, the tool) is not. */
    private static final java.util.regex.Pattern BUILDER_OBJECT = java.util.regex.Pattern.compile(
            "(?i)^\\s+((the|its|generated)\\s+)*builder\\b");
    /** Negation that governs the matched verb: directly before it, at most two words between. */
    private static final java.util.regex.Pattern NEGATED = java.util.regex.Pattern.compile(
            "(?is).*\\b(do not|don't|never|nor|not)\\s+(\\w+\\s+){0,2}$");

    /**
     * True when the sentence instructs reading generated output: SOME reading verb in it (each one is tested, so
     * a negated verb cannot shield a later one) has a generated object after it and no negation governing it.
     * Opening the GraphML is the analyser step and is allowed, unless the sentence says to read it as text.
     */
    static boolean tellsTheAuthorToReadGeneratedCode(String sentence) {
        var verbs = READ_VERB.matcher(sentence);
        while (verbs.find()) {
            String verb = verbs.group(1).toLowerCase(java.util.Locale.ROOT);
            String after = sentence.substring(verbs.end());
            var object = GENERATED_OBJECT.matcher(after);
            boolean generated = object.find();
            String lower = sentence.toLowerCase(java.util.Locale.ROOT);
            if (generated && object.group(1).equalsIgnoreCase("GraphML")) {
                boolean opens = verb.startsWith("open") || verb.startsWith("view");
                boolean asText = lower.contains("text editor") || lower.contains("as text") || lower.contains("graphml text");
                boolean viaAnalyser = lower.contains("analyser") || lower.contains("load-audit-log");
                // opening the GraphML (with a log, in the analyser) is the analyser step; reading it as text is not
                if (opens ? !asText : viaAnalyser) generated = false;
            }
            if (!generated && !BUILDER_OBJECT.matcher(after).find()) continue;
            if (!NEGATED.matcher(sentence.substring(0, verbs.start())).matches()) return true;
        }
        return false;
    }

    @Test
    void everySkillThatNamesTheExportSaysEachProcessorHasItsOwn() throws Exception {
        // issue #126: a bundle may host several processors; one export path read as the whole server hid the rest
        for (String skill : List.of("common/load-audit-log/SKILL.md", "mongoose/run-mongoose-server/SKILL.md",
                "spring/add-a-node/SKILL.md")) {
            assertTrue(Files.readString(ROOT.resolve(skill)).contains("`logs/audit-<processor>.yaml`"), skill);
        }
    }

    @Test
    void auditEvidenceRunbookAndSkillShareTheVersionedLimitsAndDeliveryBoundary() throws Exception {
        String skill = Files.readString(ROOT.resolve("mongoose/run-mongoose-server/SKILL.md"));
        String runbook = Files.readString(Path.of("docs/runbooks/mongoose-audit-evidence.md"));
        String section = skill.substring(skill.indexOf("## Audit evidence"), skill.indexOf("## Do not start a second instance")).strip();
        assertTrue(runbook.contains(section), "the generated guidance sources must not contradict each other");
        for (String required : List.of("mongoose-plugins 1.0.43", "/api/audit/file/{id}/export?format=yaml",
                "D12", "D13", "pending", "UP-RDR-01", "TA-5b, still open", "snapshot export/reopen"))
            assertTrue(section.contains(required), "missing audit evidence requirement: " + required);
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

    /**
     * M72 L4 (iii): diagnosis runs in a fresh context seeded with a brief. The brief may carry pointers and the
     * question but never the evidence, so the qualifications that travel with audit facts (spec §2.5) must be
     * read live by the fresh context and come back in its reply. Each requirement below is one way the brief
     * could hide evidence or rot into a second diagnosis in the long context.
     */
    private static final List<String> DIAGNOSIS_BRIEF_REQUIRES = List.of(
            "subagent_type: \"general-purpose\"",                 // same model and tools as the author: measurable
            "run_in_background: false",                            // 2.1.296 backgrounded a subagent unasked
            "Wait for its reply and do nothing in the analyser meanwhile",   // one canvas, one driver at a time
            "at most 2,000 characters",                            // the size bound
            "Do not delegate", "edit files", "read an export\nas text",
            "audit-compare.py on a before/after pair is allowed",  // add-a-node's proof stays available
            "For each log in turn (the analyser holds one open log)",        // review P2-3: per log, not once
            "analyser_context {\"sections\": [\"log\", \"pairing\"]}",
            "log.identity", "graphPairing", "timeOrder", "producer", "inFlight", "dispatchOrder",
            "recordIndex", "Reply, per processor", "its step 2 qualifications", "what the log cannot show",
            "never the evidence", "for each log while that log is open", "with those\nqualifications",
            "send\na new brief");
    private static final int DIAGNOSIS_BRIEF_TEMPLATE_MAX = 1_300;

    /** Requirements the skill's fresh-context section does not meet; empty when it meets them all. */
    static List<String> diagnosisBriefGaps(String skill) {
        int start = skill.indexOf("## Diagnose in a fresh context");
        if (start < 0) return List.of("section ## Diagnose in a fresh context");
        int end = skill.indexOf("\n## ", start + 1);
        String section = skill.substring(start, end < 0 ? skill.length() : end);
        List<String> gaps = new java.util.ArrayList<>();
        for (String required : DIAGNOSIS_BRIEF_REQUIRES)
            if (!section.contains(required)) gaps.add(required);
        int open = section.indexOf("```\n");
        int close = open < 0 ? -1 : section.indexOf("```", open + 4);
        if (close < 0) gaps.add("the brief template as a fenced block");
        else if (close - open - 4 > DIAGNOSIS_BRIEF_TEMPLATE_MAX)
            gaps.add("template within " + DIAGNOSIS_BRIEF_TEMPLATE_MAX + " characters, leaving room to fill it");
        if (!skill.contains("stop after\n   this step and follow *Diagnose in a fresh context*"))
            gaps.add("step 1 routes a design conversation to the section");
        if (!skill.contains("needs more than one analyser\n   read after opening") || !skill.contains("A single check stays here."))
            gaps.add("the routing says which diagnosis goes to a subagent and which stays");
        return gaps;
    }

    @Test
    void loadLogSkillDiagnosesInAFreshContextWithABriefThatCarriesNoEvidence() throws Exception {
        String text = Files.readString(ROOT.resolve("common/load-audit-log/SKILL.md"));
        assertEquals(List.of(), diagnosisBriefGaps(text));
        // witnesses: dropping any one requirement, the routing line or the section must turn the check red
        for (String required : DIAGNOSIS_BRIEF_REQUIRES)
            assertTrue(diagnosisBriefGaps(text.replace(required, "")).contains(required),
                    "the check must catch a brief that drops: " + required);
        assertFalse(diagnosisBriefGaps(text.replace("stop after\n   this step", "")).isEmpty());
        assertFalse(diagnosisBriefGaps(text.replace("A single check stays here.", "")).isEmpty(),
                "routing every diagnosis, however small, to a subagent must fail");
        assertFalse(diagnosisBriefGaps(text.replace("## Diagnose in a fresh context", "## Diagnose")).isEmpty());
        String padded = text.replace("question: <", "question: <" + "x".repeat(DIAGNOSIS_BRIEF_TEMPLATE_MAX));
        assertFalse(diagnosisBriefGaps(padded).isEmpty(), "an unbounded template must fail");
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
        // v1 pins the bytes AT ITS REVISION, which is what a consumer of v1 fetches. The worktree copy is
        // v2's concern (it moved on 2026-09-16 when both skills learned from a session report); until then
        // this compared the worktree, which was only ever true because neither file had changed since v1.
        requireFullHistory();
        for (Map<String, Object> skill : skills) {
            String relative = (String) skill.get("path");
            Path source = ROOT.resolve(relative).normalize();
            assertTrue(source.startsWith(ROOT) && Files.isRegularFile(source), relative);
            String atRevision = gitShow(PUBLISHED_REVISION + ":docs/skills/" + relative);
            assertNotNull(atRevision, relative + " must exist at v1's revision");
            assertEquals(expectedHashes.get(relative), sha256(atRevision), relative);
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
            // a supporting file beside a skill (a script it tells the model to run) ships through `files`
            Path dir = ROOT.resolve(candidate.path()).getParent();
            try (var siblings = Files.list(dir)) {
                for (Path sibling : siblings.filter(Files::isRegularFile).toList()) {
                    String relative = ROOT.relativize(sibling).toString().replace('\\', '/');
                    if (relative.equals(candidate.path())) continue;
                    assertTrue(indexed.contains(relative), relative + " sits beside " + candidate.path()
                            + " but no specialisation's `files` ships it, so a generated bundle would lack it");
                }
            }
        }
        for (Object spec : ((Map<String, Object>) v2.get("specialisations")).values()) {
            Map<String, Object> entry = (Map<String, Object>) spec;
            Set<String> skillDirs = ((List<String>) entry.get("skills")).stream()
                    .map(p -> p.substring(0, p.lastIndexOf('/'))).collect(Collectors.toSet());
            for (String file : (List<String>) entry.getOrDefault("files", List.of())) {
                assertTrue(skillDirs.contains(file.substring(0, file.lastIndexOf('/'))),
                        file + " must sit beside a skill its own specialisation selects, or nothing says where it goes");
                assertTrue(Files.isRegularFile(ROOT.resolve(file)), "ships a missing file: " + file);
            }
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

        requireFullHistory();
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
        requireFullHistory();
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
            // supporting files a skill ships with are selected and pinned exactly like the skill itself
            paths.addAll((List<String>) ((Map<String, Object>) spec).getOrDefault("files", List.of()));
        }
        return paths;
    }

    /**
     * The revision checks read git HISTORY. A shallow clone — actions/checkout's default fetch-depth of 1 — has
     * none, so every lookup returns null and the checks fail for a reason that is not the index's. Fail on the
     * real cause instead: CI must check out with {@code fetch-depth: 0} (ci.yml and release.yml do).
     */
    private static void requireFullHistory() throws Exception {
        Process p = new ProcessBuilder("git", "rev-parse", "--is-shallow-repository").start();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        assertTrue(!"true".equals(out), "this is a shallow clone: the pinned revision cannot be read from history."
                + " Check out with fetch-depth: 0 (the workflows do) before trusting a failure of the revision checks");
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

    /**
     * ★ Option 2 (owner, 2026-10-18): the skills a starter project gets are written from the analyser's own JAR — the pom
     * copies {@code docs/skills} to {@code /starter-skills/} — so every file v2 can select must be on the classpath and
     * byte-identical to the index's sha256, and the playground's two pages must match their pins. Witness: edit a byte
     * of a bundled copy (or drop the pom's resource) — this fails before any project is written.
     */
    @Test
    @SuppressWarnings("unchecked")
    void theJarCarriesEverySelectableSkillAtItsPinnedDigest() throws Exception {
        Map<String, Object> v2 = (Map<String, Object>) telamin.fluxtion.audit.analyser.analyser.llm.Json.parse(
                Files.readString(ROOT.resolve("m19-skills/2/index.json")));
        Map<String, Object> pins = (Map<String, Object>) v2.get("sha256");
        assertFalse(pins.isEmpty());
        for (var e : pins.entrySet()) {
            try (var in = getClass().getResourceAsStream("/starter-skills/" + e.getKey())) {
                assertNotNull(in, "the jar carries " + e.getKey());
                assertEquals(e.getValue(), telamin.fluxtion.audit.analyser.analyser.template.StarterSkills.sha256(
                        in.readAllBytes()), "the jar's copy of " + e.getKey() + " is the pinned one");
            }
        }
        try (var in = getClass().getResourceAsStream("/starter-skills/m19-skills/2/index.json")) {
            assertNotNull(in, "the index ships too");
        }
        for (var e : telamin.fluxtion.audit.analyser.analyser.template.StarterSkills.PLAYGROUND.values()) {
            try (var in = getClass().getResourceAsStream("/starter-skills/" + e[1])) {
                assertNotNull(in, "the jar carries " + e[0]);
                assertEquals(e[2], telamin.fluxtion.audit.analyser.analyser.template.StarterSkills.sha256(in.readAllBytes()),
                        e[0] + " is the copy fetched and pinned on 2026-10-18");
            }
        }
    }

    // ---------------- m19-skills/3: versioned skills, additive to /2 (skill-registry spec, step 1) ----------------

    private static final Path V3_INDEX = ROOT.resolve("m19-skills/3/index.json");
    private static final java.util.regex.Pattern SHA256 = java.util.regex.Pattern.compile("[0-9a-f]{64}");
    private static final java.util.regex.Pattern REVISION = java.util.regex.Pattern.compile("[0-9a-f]{40}");
    private static final Set<String> V3_VERSION_KEYS = Set.of("version", "revision", "files", "compatibility", "evidence", "yanked");
    private static final String V2_PIN_EVIDENCE = SkillRegistry.V2_PIN_EVIDENCE;

    @SuppressWarnings("unchecked")
    private static Map<String, Object> v3Index() throws Exception {
        return (Map<String, Object>) Json.parse(Files.readString(V3_INDEX));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> copy(Map<String, Object> index) {
        return (Map<String, Object>) Json.parse(Json.write(index));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, List<Map<String, Object>>> v3Versions(Map<String, Object> index) {
        Map<String, List<Map<String, Object>>> out = new java.util.LinkedHashMap<>();
        for (var e : ((Map<String, Object>) index.get("skills")).entrySet())
            out.put(e.getKey(), (List<Map<String, Object>>) ((Map<String, Object>) e.getValue()).get("versions"));
        return out;
    }

    /** The skill names /3 selects from: `common` plus every specialisation's `skills`, in order, duplicates kept. */
    @SuppressWarnings("unchecked")
    private static List<String> v3Listed(Map<String, Object> index) {
        List<String> names = new java.util.ArrayList<>((List<String>) index.get("common"));
        for (Object spec : ((Map<String, Object>) index.get("specialisations")).values())
            names.addAll((List<String>) ((Map<String, Object>) spec).get("skills"));
        return names;
    }

    /** Every structural problem in a /3 index — the shape a consumer relies on — or an empty list. */
    @SuppressWarnings("unchecked")
    static List<String> v3Problems(Map<String, Object> index) {
        List<String> problems = new java.util.ArrayList<>();
        if (!SkillRegistry.CONTRACT.equals(index.get("contract"))) problems.add("contract is not m19-skills/3");
        if (!(index.get("common") instanceof List<?>) || !(index.get("specialisations") instanceof Map<?, ?> specs)
                || !(index.get("skills") instanceof Map<?, ?> skills)) {
            problems.add("common, specialisations and skills are required");
            return problems;
        }
        for (var e : ((Map<String, Object>) specs).entrySet()) {
            Map<String, Object> spec = (Map<String, Object>) e.getValue();
            if (!(spec.get("why") instanceof String why) || why.isBlank()) problems.add(e.getKey() + ": no why");
            if (!(spec.get("skills") instanceof List<?>)) problems.add(e.getKey() + ": no skills list");
        }
        if (!problems.isEmpty()) return problems;
        List<String> listed = v3Listed(index);
        if (listed.size() != Set.copyOf(listed).size()) problems.add("a skill is listed twice: " + listed);
        if (!Set.copyOf(listed).equals(skills.keySet()))
            problems.add("listed skills " + Set.copyOf(listed) + " differ from the skills map " + skills.keySet());
        for (var e : ((Map<String, Object>) skills).entrySet()) {
            String name = e.getKey();
            Object versions = ((Map<String, Object>) e.getValue()).get("versions");
            if (!(versions instanceof List<?> list) || list.isEmpty()) {
                problems.add(name + ": no versions");
                continue;
            }
            Set<String> seen = new java.util.HashSet<>();
            for (Map<String, Object> v : (List<Map<String, Object>>) list) {
                String at = name + "@" + v.get("version");
                if (!V3_VERSION_KEYS.containsAll(v.keySet())) problems.add(at + ": unknown fields " + v.keySet());
                if (!(v.get("version") instanceof String s) || SkillRegistry.Semver.tryParse(s) == null)
                    problems.add(at + ": version is not valid semver");
                else if (!seen.add(SkillRegistry.Semver.parse(s).toString()))
                    problems.add(at + ": the same version twice (by precedence)");
                if (!(v.get("revision") instanceof String r) || !REVISION.matcher(r).matches())
                    problems.add(at + ": revision must be a full commit id");
                String compat = SkillRegistry.compatibilityProblem(v.get("compatibility"));
                if (compat != null) problems.add(at + ": " + compat);
                if (v.containsKey("yanked") && !(v.get("yanked") instanceof Boolean)) problems.add(at + ": yanked is not a boolean");
                if (v.containsKey("evidence")) problems.addAll(evidenceProblems(at, v));
                // a /2 pin is evidence that /2 served these bytes to every project its selection reached, with no
                // Mongoose or plugin constraint; it is not evidence that any range was tested, so it backs none
                if (v.get("evidence") instanceof Map<?, ?> ev && V2_PIN_EVIDENCE.equals(ev.get("kind"))
                        && v.get("compatibility") instanceof Map<?, ?> c
                        && !("*".equals(c.get("mongoose")) && "*".equals(c.get("mongoosePlugins"))))
                    problems.add(at + ": a /2-pin version reproduces /2, which constrains no Mongoose or plugin version;"
                            + " a range needs gate evidence");
                problems.addAll(fileProblems(at, name, v.get("files")));
            }
        }
        return problems;
    }

    /** Evidence rules live in {@link SkillRegistry#evidenceProblem}, so the index check and selection cannot disagree. */
    private static List<String> evidenceProblems(String at, Map<String, Object> version) {
        String problem = SkillRegistry.evidenceProblem(version.get("evidence"), version.get("revision"));
        return problem == null ? List.of() : List.of(at + ": " + problem);
    }

    @SuppressWarnings("unchecked")
    private static List<String> fileProblems(String at, String name, Object files) {
        if (!(files instanceof List<?> list) || list.isEmpty()) return List.of(at + ": no files");
        List<String> problems = new java.util.ArrayList<>();
        String skillDir = null;
        int skillFiles = 0;
        Set<String> paths = new java.util.HashSet<>();
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> f) || !f.keySet().equals(Set.of("path", "sha256"))
                    || !(f.get("path") instanceof String p) || !(f.get("sha256") instanceof String h)) {
                problems.add(at + ": a file is exactly {path, sha256}");
                continue;
            }
            if (p.startsWith("/") || p.contains("..") || p.contains("\\") || p.contains(":") || p.isBlank())
                problems.add(at + ": path escapes the root: " + p);
            if (!SHA256.matcher(h).matches()) problems.add(at + ": " + p + " sha256 is not 64 lowercase hex");
            if (!paths.add(p)) problems.add(at + ": " + p + " listed twice");
            if (p.endsWith("/SKILL.md")) {
                skillFiles++;
                skillDir = p.substring(0, p.lastIndexOf('/'));
                if (!skillDir.substring(skillDir.lastIndexOf('/') + 1).equals(name))
                    problems.add(at + ": " + p + " is not the skill named " + name);
            }
        }
        if (skillFiles != 1) problems.add(at + ": exactly one SKILL.md, found " + skillFiles);
        else for (String p : paths)
            if (!p.substring(0, Math.max(0, p.lastIndexOf('/'))).equals(skillDir))
                problems.add(at + ": " + p + " does not sit beside its SKILL.md");
        return problems;
    }

    /** Every file whose bytes at its version's revision do not hash to its pin (or do not exist there). */
    @SuppressWarnings("unchecked")
    static List<String> v3DigestProblems(Map<String, Object> index) throws Exception {
        List<String> problems = new java.util.ArrayList<>();
        for (var e : v3Versions(index).entrySet()) {
            for (Map<String, Object> v : e.getValue()) {
                String revision = (String) v.get("revision");
                for (Map<String, Object> f : (List<Map<String, Object>>) v.get("files")) {
                    byte[] blob = gitShowBytes(revision + ":docs/skills/" + f.get("path"));
                    String at = e.getKey() + "@" + v.get("version") + " " + f.get("path");
                    if (blob == null) problems.add(at + ": absent at " + revision);
                    else if (!f.get("sha256").equals(sha256(blob))) problems.add(at + ": digest differs from the bytes at " + revision);
                }
            }
        }
        return problems;
    }

    @Test
    void v3IndexIsWellFormed() throws Exception {
        assertEquals(List.of(), v3Problems(v3Index()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void v3WellFormednessCheckCatchesEachBreak() throws Exception {
        Map<String, Object> index = v3Index();
        String skill = v3Listed(index).get(0);
        java.util.function.BiConsumer<String, java.util.function.Consumer<Map<String, Object>>> mustCatch = (why, edit) -> {
            Map<String, Object> broken = copy(index);
            edit.accept(v3Versions(broken).get(skill).get(0));
            assertFalse(v3Problems(broken).isEmpty(), "the check must catch: " + why);
        };
        mustCatch.accept("a non-semver version", v -> v.put("version", "1.0"));
        mustCatch.accept("a v-prefixed version", v -> v.put("version", "v1.0.0"));
        mustCatch.accept("an abbreviated revision", v -> v.put("revision", "cb7a741f"));
        mustCatch.accept("an unknown authoring", v -> ((Map<String, Object>) v.get("compatibility")).put("authoring", "kotlin"));
        mustCatch.accept("an unknown host", v -> ((Map<String, Object>) v.get("compatibility")).put("host", "cloud"));
        mustCatch.accept("a caret range", v -> ((Map<String, Object>) v.get("compatibility")).put("mongoose", "^1.1.0"));
        mustCatch.accept("one range for both axes", v -> ((Map<String, Object>) v.get("compatibility")).remove("mongoosePlugins"));
        mustCatch.accept("an analyser range, not a minimum", v -> ((Map<String, Object>) v.get("compatibility")).put("analyser", ">=1.12.0"));
        mustCatch.accept("a string yank", v -> v.put("yanked", "true"));
        mustCatch.accept("dateless evidence", v -> ((Map<String, Object>) v.get("evidence")).remove("date"));
        mustCatch.accept("evidence of no known kind", v -> ((Map<String, Object>) v.get("evidence")).put("kind", "trust-me"));
        mustCatch.accept("a path escaping the root", v -> ((List<Map<String, Object>>) v.get("files")).get(0).put("path", "../x/SKILL.md"));
        mustCatch.accept("a short digest", v -> ((List<Map<String, Object>>) v.get("files")).get(0).put("sha256", "abc"));
        mustCatch.accept("an unknown field", v -> v.put("channel", "beta"));
        mustCatch.accept("a /2-pin version claiming a tested plugin range",
                v -> ((Map<String, Object>) v.get("compatibility")).put("mongoosePlugins", ">=1.0.43"));
        mustCatch.accept("a /2-pin version claiming a tested Mongoose range",
                v -> ((Map<String, Object>) v.get("compatibility")).put("mongoose", ">=1.1.0 <1.2.0"));
        Map<String, Object> twice = copy(index);
        List<Map<String, Object>> versions = v3Versions(twice).get(skill);
        versions.add(copy(versions.get(0)));
        assertFalse(v3Problems(twice).isEmpty(), "the same version twice must be caught");
        Map<String, Object> unlisted = copy(index);
        ((List<String>) unlisted.get("common")).remove(skill);
        assertFalse(v3Problems(unlisted).isEmpty(), "a skill no list selects must be caught");
    }

    @Test
    void v3EveryDigestMatchesTheBytesAtItsRevision() throws Exception {
        requireFullHistory();
        assertEquals(List.of(), v3DigestProblems(v3Index()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void v3DigestCheckFailsOnAFlippedDigestOrAWrongRevision() throws Exception {
        requireFullHistory();
        Map<String, Object> flipped = copy(v3Index());
        Map<String, Object> file = ((List<Map<String, Object>>) v3Versions(flipped).values().iterator().next().get(0).get("files")).get(0);
        String sha = (String) file.get("sha256");
        file.put("sha256", (sha.charAt(0) == '0' ? "1" : "0") + sha.substring(1));
        assertEquals(1, v3DigestProblems(flipped).size(), "one flipped digest is one problem");
        Map<String, Object> early = copy(v3Index());
        String v1Revision = (String) ((Map<String, Object>) Json.parse(Files.readString(PUBLISHED_INDEX))).get("revision");
        v3Versions(early).get("guided-start").get(0).put("revision", v1Revision);
        assertFalse(v3DigestProblems(early).isEmpty(), "guided-start does not exist at v1's revision");
    }

    @Test
    @SuppressWarnings("unchecked")
    void v3SkillFrontmatterAtEachRevisionAgreesWithTheEntry() throws Exception {
        requireFullHistory();
        for (var e : v3Versions(v3Index()).entrySet()) {
            for (Map<String, Object> v : e.getValue()) {
                String skillPath = ((List<Map<String, Object>>) v.get("files")).stream().map(f -> (String) f.get("path"))
                        .filter(p -> p.endsWith("/SKILL.md")).findFirst().orElseThrow();
                byte[] blob = gitShowBytes(v.get("revision") + ":docs/skills/" + skillPath);
                assertNotNull(blob, skillPath);
                String text = new String(blob, StandardCharsets.UTF_8);
                assertTrue(text.startsWith("---\nname: " + e.getKey() + "\n"), skillPath + " is the skill named " + e.getKey());
                var min = java.util.regex.Pattern.compile("(?m)^x-analyser-min-version: (\\S+)$").matcher(text);
                if (min.find())   // the front-matter field /3 replaces must not contradict it while both exist
                    assertEquals(min.group(1), ((Map<String, Object>) v.get("compatibility")).get("analyser"), skillPath);
            }
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void v3AVersionWithoutEvidenceIsFlaggedNotSelectable() throws Exception {
        Map<String, Object> index = v3Index();
        for (var e : v3Versions(index).entrySet()) {
            boolean anySelectable = false;
            for (Map<String, Object> v : e.getValue()) {
                String reason = SkillRegistry.notSelectableReason(v);
                if (!v.containsKey("evidence"))
                    assertTrue(reason != null && reason.contains("not selectable"), e.getKey() + "@" + v.get("version"));
                anySelectable |= reason == null;
            }
            assertTrue(anySelectable, e.getKey() + " is listed but no version of it is selectable");
        }
        // witness: the same index with its evidence removed selects nothing, for any pins
        Map<String, Object> stripped = copy(index);
        List<SkillRegistry.ProjectPins> everyProject = List.of(
                new SkillRegistry.ProjectPins("1.41.2", "spring", "mongoose", "1.1.3", "1.1.2"),
                new SkillRegistry.ProjectPins("1.41.2", "java", "mongoose", "1.1.3", "1.1.2"),
                new SkillRegistry.ProjectPins("1.41.2", "spring", "embedded", null, null),
                new SkillRegistry.ProjectPins("1.41.2", "java", "embedded", null, null));
        for (var e : v3Versions(stripped).entrySet()) {
            assertTrue(everyProject.stream().anyMatch(pins -> SkillRegistry.select(e.getValue(), pins).isPresent()),
                    e.getKey() + " is selectable for some project before stripping");
            e.getValue().forEach(v -> v.remove("evidence"));
            for (Map<String, Object> v : e.getValue())
                assertTrue(SkillRegistry.notSelectableReason(v).contains("no evidence, so not selectable"));
            for (SkillRegistry.ProjectPins pins : everyProject)
                assertTrue(SkillRegistry.select(e.getValue(), pins).isEmpty(), e.getKey() + " selected without evidence");
        }
    }

    /**
     * While /2 is served, /3 must not disagree with it. Every /2 selection list is in /3 unchanged (/3 may add
     * specialisations, as step 2 adds the Java-authoring skills), and every /2 pin is in /3 as a version with the same
     * revision and the same digests (the skill's SKILL.md plus the supporting files beside it). That version must be
     * selectable: yanking in /3 a version /2 still serves would leave /2 consumers on bytes /3 calls bad, so a yank of a
     * /2 pin goes with a /2 re-pin in the same change.
     */
    @SuppressWarnings("unchecked")
    static List<String> v2v3ParityProblems(Map<String, Object> v2, Map<String, Object> v3) {
        List<String> problems = new java.util.ArrayList<>();
        java.util.function.Function<String, String> nameOf = p -> p.substring(0, p.lastIndexOf('/'))
                .substring(p.substring(0, p.lastIndexOf('/')).lastIndexOf('/') + 1);
        if (!((List<String>) v2.get("common")).stream().map(nameOf).toList().equals(v3.get("common")))
            problems.add("common differs: /2 " + v2.get("common") + ", /3 " + v3.get("common"));
        Map<String, Object> specs2 = (Map<String, Object>) v2.get("specialisations");
        Map<String, Object> specs3 = (Map<String, Object>) v3.get("specialisations");
        for (String spec : specs2.keySet()) {
            Map<String, Object> a = (Map<String, Object>) specs2.get(spec), b = (Map<String, Object>) specs3.get(spec);
            if (b == null) {
                problems.add("/3 drops the specialisation " + spec);
                continue;
            }
            if (!Objects.equals(a.get("why"), b.get("why"))) problems.add(spec + ": why differs");
            if (!((List<String>) a.get("skills")).stream().map(nameOf).toList().equals(b.get("skills")))
                problems.add(spec + ": skills differ: /2 " + a.get("skills") + ", /3 " + b.get("skills"));
        }
        // the per-skill pin /2 serves: its SKILL.md and every supporting file in that directory
        List<String> v2Paths = new java.util.ArrayList<>((List<String>) v2.get("common"));
        for (Object spec : specs2.values()) {
            v2Paths.addAll((List<String>) ((Map<String, Object>) spec).get("skills"));
            v2Paths.addAll((List<String>) ((Map<String, Object>) spec).getOrDefault("files", List.of()));
        }
        Map<String, String> v2Sha = (Map<String, String>) v2.get("sha256");
        Map<String, Map<String, String>> v2Pins = new java.util.TreeMap<>();
        for (String path : v2Paths)
            if (path.endsWith("/SKILL.md")) v2Pins.put(nameOf.apply(path), new java.util.TreeMap<>());
        for (String path : v2Paths) {
            String dir = path.substring(0, path.lastIndexOf('/'));
            String owner = v2Paths.stream().filter(p -> p.equals(dir + "/SKILL.md")).map(nameOf).findFirst().orElseThrow();
            v2Pins.get(owner).put(path, v2Sha.get(path));
        }
        Map<String, List<Map<String, Object>>> v3Versions = v3Versions(v3);
        for (var pin : v2Pins.entrySet()) {
            List<Map<String, Object>> versions = v3Versions.get(pin.getKey());
            if (versions == null) {
                problems.add(pin.getKey() + ": /2 serves it but /3 has no versions of it");
                continue;
            }
            boolean found = false;
            for (Map<String, Object> v : versions) {
                if (!v2.get("revision").equals(v.get("revision"))) continue;
                String at = pin.getKey() + "@" + v.get("version");
                Map<String, String> files = new java.util.TreeMap<>();
                for (Map<String, Object> f : (List<Map<String, Object>>) v.get("files"))
                    files.put((String) f.get("path"), (String) f.get("sha256"));
                if (!pin.getValue().equals(files)) problems.add(at + " disagrees with /2 at the same revision: " + files);
                String unselectable = SkillRegistry.notSelectableReason(v);
                if (unselectable != null) problems.add(at + " is /2's pin but /3 will not select it (" + unselectable
                        + "); re-pin /2 in the same change");
                found = true;
            }
            if (!found) problems.add(pin.getKey() + ": /2 pins revision " + v2.get("revision") + " but /3 has no version at it");
        }
        return problems;
    }

    @Test
    void v2AndV3AgreeForEverySkillBothList() throws Exception {
        assertEquals(List.of(), v2v3ParityProblems(v2Index(), v3Index()));
    }

    @Test
    @SuppressWarnings("unchecked")
    void v2v3ParityCatchesEachDisagreement_andAllowsAnAddedSpecialisation() throws Exception {
        Map<String, Object> v2 = v2Index(), base = v3Index();
        java.util.function.BiConsumer<String, java.util.function.Consumer<Map<String, Object>>> mustCatch = (why, edit) -> {
            Map<String, Object> v3 = copy(base);
            edit.accept(v3);
            assertFalse(v2v3ParityProblems(v2, v3).isEmpty(), "parity must catch: " + why);
        };
        mustCatch.accept("a yanked /2 pin", v3 -> v3Versions(v3).get("load-audit-log").get(0).put("yanked", true));
        mustCatch.accept("a /2 pin without evidence", v3 -> v3Versions(v3).get("load-audit-log").get(0).remove("evidence"));
        mustCatch.accept("a different digest", v3 -> ((List<Map<String, Object>>) v3Versions(v3).get("add-a-node").get(0)
                .get("files")).get(1).put("sha256", "0".repeat(64)));
        mustCatch.accept("a dropped supporting file", v3 -> ((List<Map<String, Object>>) v3Versions(v3).get("add-a-node").get(0)
                .get("files")).remove(1));
        mustCatch.accept("another revision", v3 -> v3Versions(v3).get("guided-start").get(0).put("revision", "0".repeat(40)));
        mustCatch.accept("a dropped specialisation", v3 -> ((Map<String, Object>) v3.get("specialisations")).remove("replay"));
        mustCatch.accept("a skill moved out of common", v3 -> ((List<String>) v3.get("common")).remove("guided-start"));
        Map<String, Object> added = copy(base);
        ((Map<String, Object>) added.get("specialisations")).put("java",
                Map.of("why", "the graph is authored in Java", "skills", List.of("fluxtion-java-graph")));
        assertEquals(List.of(), v2v3ParityProblems(v2, added), "/3 may add a specialisation /2 does not have");
    }

    /** @return the exact bytes at a git revision, or null when the path does not exist there. */
    private static byte[] gitShowBytes(String spec) throws Exception {
        Process p = new ProcessBuilder("git", "show", spec).redirectErrorStream(false).start();
        byte[] out = p.getInputStream().readAllBytes();
        return p.waitFor() == 0 ? out : null;
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
