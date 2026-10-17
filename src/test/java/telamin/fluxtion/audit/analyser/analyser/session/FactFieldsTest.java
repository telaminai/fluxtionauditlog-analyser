package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 (spec §4.1; the scorecard's R6): <b>a fact never carries a model's words or an absolute path.</b> The session
 * processor records every fact's {@code toString} in its audit record, which is exported and shared, so a field is a
 * promise about that record.
 *
 * <p>The rule is an INVENTORY, so it catches the next field rather than the known ones: every component of every fact
 * record in {@link SessionEvents} that is not a number, a flag or an enum is listed here — the M74 ones each with what it
 * holds, the earlier ones frozen as they were. A component the inventory does not know fails until someone says what it
 * holds; a component typed {@link Path} fails in the M74 facts outright; and an M74 component whose name says it holds a
 * place or prose ({@code path}, {@code dir}, {@code root}, {@code summary}, {@code reason}, {@code text}, …) must be a
 * {@code …/name} form or the analyser's own words with paths removed ({@link FactWords#withoutPaths}). Model words live
 * in the frame's {@link FactRefs}, named by reference.
 */
class FactFieldsTest {

    /** What an M74 fact component may hold. */
    enum Holds {
        /** A word from a fixed vocabulary (a verb, a kind, a mode, an origin, a code, an effect's name). */
        VOCABULARY,
        /** A digest or an opaque id (sha-256 prefixes, proposal and item ids). */
        DIGEST,
        /** {@code …/name}: a folder's last segment, never its path ({@link FactWords#shown}). */
        SHOWN_PATH,
        /** The analyser's own words, any absolute path in them shown as {@code …/name} ({@link FactWords#withoutPaths}). */
        ANALYSER_WORDS,
        /** Names from the person's own configuration (MCP servers, skills, plugins) — the CLI reports them; never contents. */
        PERSON_CONFIG,
        /** A prompt pack's entry the person dropped (its URL and label) — the person's, not a model's. */
        PERSON_PACK,
        /** A nested fact record, itself checked here. */
        STRUCTURE
    }

    static final Map<String, Holds> M74 = new LinkedHashMap<>();

    static {
        for (String c : List.of("AssistantModeChosen.origin", "LocalAgentSendRequested.command",
                "LocalAgentStopRequested.origin", "LocalAgentNewChatRequested.origin", "LocalAgentCallAdmissionRequested.verb",
                "HostedCallRefusedByServer.verb", "HostedCallRefusedByServer.reason", "LocalAgentEffectStarted.what",
                "LocalAgentInitialized.permissionMode", "LocalAgentStatusObserved.hostedStatus",
                "LocalAgentInitObserved.permissionMode",
                "LocalAgentInitObserved.capabilities", "LocalAgentNoted.tool", "LocalAgentModeObserved.permissionMode",
                "LocalAgentDraftComposed.questionId", "LocalAgentDraftComposed.origin", "AssistantDraftComposed.questionId",
                "AssistantDraftComposed.origin", "StartPagePromptSent.entryId", "ReturnToMainOptions.origin",
                "StartPageEffectStarted.what", "ProposalItemFact.kind", "ProposalItemPreviewed.verdict",
                "ProposalItemPreviewed.refusal", "ProposalItemApplied.outcome", "ProposalChecked.pairing",
                "ProposalChecked.fit", "ProposalEffectStarted.what", "LocalAgentPermissionRequested.name",
                "LocalAgentPermissionAnswered.name", "HostedCallPresented.verb", "ExampleDownloadConfirmed.targetProblem",
                "ExampleEffectStarted.what", "ExampleDeadlinePassed.phase",
                // M74 UX: the assistant profile's facts — who opened or closed the cards (an origin word)
                "AssistantProfilesRequested.origin", "AssistantProfilesClosed.origin")) {
            M74.put(c, Holds.VOCABULARY);
        }
        for (String c : List.of("LocalAgentConsent.postureRef", "LocalAgentStatusObserved.hostedNonceDigest",
                "LocalAgentInitObserved.sessionDigest", "LocalAgentDraftComposed.logIdentity",
                "AssistantDraftComposed.logIdentity", "ProposalItemFact.pathDigest", "ProposalItemFact.classNameDigest",
                "ProposalItemPreviewed.proposalId", "ProposalItemPreviewed.itemId", "ProposalPreviewDone.proposalId",
                "ProposalItemAnswered.proposalId", "ProposalItemAnswered.itemId", "ProposalApplyRequested.proposalId",
                "ProposalApplyRequested.yes", "ProposalDismissed.proposalId", "ProposalItemApplied.proposalId",
                "ProposalItemApplied.itemId", "ProposalChecked.proposalId", "ProposalEffectStarted.proposalId",
                "LocalAgentPermissionRequested.inputDigest", "LocalAgentPermissionRequested.toolUseIdDigest",
                "HostedCallPresented.inputDigest", "HostedCallPresented.toolUseIdDigest", "ExampleFetched.sha256",
                // review S2: the composed drafts the composer holds, by opaque draft id
                "LocalAgentSendRequested.draftBindings", "AssistantSendRequested.draftBindings")) {
            M74.put(c, Holds.DIGEST);
        }
        for (String c : List.of("ProposalPreviewDone.setupDirShown", "LocalAgentSetupDirectoryChosen.dirShown",
                "ExampleDownloadConfirmed.targetShown", "ExampleUnpacked.rootShown")) {
            M74.put(c, Holds.SHOWN_PATH);
        }
        for (String c : List.of("DraftPlaced.reason", "ProposalItemApplied.why", "ExampleFetched.failure",
                "ExampleUnpacked.failure", "AssistantProfileApplied.reason",
                // review P2 N-b: the walk bindings a load dropped, the analyser's words with paths removed
                "ProfileApplied.droppedBindings")) {
            M74.put(c, Holds.ANALYSER_WORDS);
        }
        // review P3 N3: the tools the CLI reports are the person's configuration (their MCP servers' tools), not vocabulary
        for (String c : List.of("LocalAgentInitObserved.tools", "LocalAgentStatusObserved.servers", "LocalAgentInitObserved.servers",
                "LocalAgentInitObserved.skills", "LocalAgentInitObserved.plugins")) {
            M74.put(c, Holds.PERSON_CONFIG);
        }
        M74.put("ExampleDownloadRequested.source", Holds.PERSON_PACK);
        for (String c : List.of("LocalAgentStartRequested.consent", "LocalAgentSendRequested.consent",
                "LocalAgentConsentChanged.consent", "LocalAgentFolderAnswered.consent", "LocalAgentDraftComposed.consent",
                "StartPagePromptSent.consent", "ProposalReceived.items")) {
            M74.put(c, Holds.STRUCTURE);
        }
    }

    /**
     * The facts written before M74, frozen as they were (not M74's to change; spec §4.1 records the scope): the person's
     * own files and the analyser's own words. A new component of one of these is a new decision and fails here too.
     */
    static final Set<String> PRE_M74 = Set.of("OpenProjectRequested.profilePath", "OpenProjectRequested.kind",
            "OpenProjectRequested.source", "OpenProjectRequested.assistantOrigin", "OpenProjectRequested.audience",
            "OpenProjectRequested.create", "OfferedRoot.relative", "OfferedRoot.absolute", "OfferedSkill.path",
            "OfferedSkill.name", "OfferedSkill.description", "CreateOffer.sourceRoots", "CreateOffer.skills",
            "CreateOffer.referenceGuide", "CreateOffer.projectKind", "CreateOffer.digest", "CreateFacts.dir",
            "CreateFacts.analyserProblem", "CreateFacts.exchangeDir", "CreateFacts.enclosingProject",
            "CreateFacts.namedProfiles", "CreateFacts.offer", "CreateSelection.sourceRoots", "CreateSelection.skills",
            "CreateSelection.offerDigest", "CreateRequest.facts", "CreateRequest.selection", "CreateApproved.root",
            "CreateApproved.sourceRoots", "CreateApproved.skills", "CreateApproved.projectKind", "ProjectAudience.origin",
            "ProjectReopenRequested.profilePath", "ProjectReopenRequested.audience", "ProjectReopenReady.profilePath",
            "ProjectReopenReady.label", "ProjectReopenReady.candidates", "OpenRequestReceived.supplied",
            "OpenLogRequested.location", "OpenLogRequested.format", "OpenLogRequested.provenance",
            "OpenLogRequested.assistantOrigin", "CloseRequested.target", "CloseRequested.assistantOrigin", "Pending.what",
            "LogOpened.logPath", "LogOpened.provenance", "LogOpened.loggedNodeIds", "LogOpened.mostVerboseLevel",
            "LogOpened.provenanceSource", "LogOpenFailed.location", "LogOpenFailed.reason", "ProfileLoaded.profilePath",
            "ProfileLoaded.name", "ProfileLoaded.reason", "ProfileLoaded.bundlePlan", "ProfileLoaded.contentDigest",
            "SourceRootsObserved.roots", "BundleRootsRestored.bundleSource", "BundleRootsRestored.remembered",
            "BundleRootsRestored.visible", "BundlePlan.profilePath", "BundlePlan.graphPath", "BundlePlan.logPath",
            "BundlePlan.identity", "BundlePlan.workingCopy", "BundlePlan.limits", "BundlePlan.notes", "BundlePlan.source",
            "BundlePlan.processor", "BundlePlan.profileDigest", "ProfileApplied.profilePath", "ProfileApplied.name",
            "EffectFailed.effect", "EffectFailed.reason", "StatusShown.kind", "GraphOpened.graphPath",
            "GraphOpened.source", "GraphOpened.declaredNodeIds", "GraphOpened.nodeTypes", "GraphOpened.assistantOrigin",
            "GraphCleared.assistantOrigin", "LogCleared.assistantOrigin", "LogAppended.loggedNodeIds",
            "LogAppended.mostVerboseLevel", "MembershipCompared.echo", "ViewFilterChanged.filterKey",
            "ViewFilterChanged.assistantOrigin", "LogIdentityObserved.verdict", "LogIdentityObserved.reason",
            "LogContentObserved.streamEnd", "LogContentObserved.readFailure", "ProducerFindingsObserved.findings",
            "TimeOrderObserved.report", "WalkTargetState.target", "WalkTargetState.caption", "WalkTargetState.state",
            "WalkTargetState.reason", "WalkPlayRequested.walk", "WalkPlayRequested.origin", "WalkDefinitionChanged.name",
            "WalkDefinitionChanged.now", "WalkDefinitionChanged.renamedTo", "WalkEndRequested.reason",
            "WalkStepPrepared.targets", "WalkStepPrepared.note", "WalkViewApplied.reason", "WalkTargetsLit.reason",
            "WalkAcknowledged.what", "BundleCaptureRequested.path", "BundleCaptureRequested.notes",
            "BundleCaptureRequested.from", "BundleCaptureRequested.to", "BundleCaptureRequested.observedIdentity",
            "BundleCaptureRequested.freshness", "BundleCaptureRequested.origin", "BundleCaptureRequested.replay",
            "BundleCaptureRequested.replayProblem", "BundleCaptureRequested.replaySha256", "BundleDeleted.reason",
            "BundleWritten.path", "BundleWritten.identity", "BundleWritten.lines", "BundleWriteFailed.reason",
            "AssistantRoute.provider", "AssistantRoute.model", "AssistantRoute.baseUrl", "AssistantSendRequested.route",
            "AssistantCancelRequested.reason", "AssistantNewChatRequested.reason", "AssistantHostRequested.origin",
            "AssistantHandoffRequested.origin", "AssistantEffectStarted.what", "AssistantHostShown.reason",
            "AssistantContextFailed.reason", "AssistantCompletionReceived.actions", "AssistantCompletionFailed.reason",
            "AssistantActionFinished.verb", "AssistantOpenApplied.assistantOrigin",
            "AssistantOpenApplyFailed.assistantOrigin");

    /**
     * Review P3 N3 (2026-10-17): VOCABULARY components whose NAME matches {@link #PLACE_OR_PROSE} — each a fixed word, and
     * each listed here by name so a new one cannot shelter under the heading.
     */
    static final Set<String> VOCABULARY_NAME_EXEMPT = Set.of(
            "HostedCallRefusedByServer.reason");   // a code: GRAPH_DELETE, NOT_LISTED, BAD_CREDENTIAL (scorecard R1)

    /** A name that says the field holds a place or prose. */
    static final Pattern PLACE_OR_PROSE = Pattern.compile("(?i).*(path|dir|root|folder|summary|reason|text|words|caption"
            + "|note|failure|why|description|message|label)$");

    /** The M74 facts: the records declared after the M74 marker in SessionEvents' source. */
    static Set<String> m74Records() throws Exception {
        String src = Files.readString(Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/session/SessionEvents.java"));
        int at = src.indexOf("// ---- M74: the local agent");
        assertTrue(at > 0, "the M74 marker in SessionEvents moved — update this rule");
        Set<String> out = new TreeSet<>();
        Matcher m = Pattern.compile("record\\s+(\\w+)\\s*\\(").matcher(src.substring(at));
        while (m.find()) out.add(m.group(1));
        return out;
    }

    /** The rule, over any set of record classes: what is wrong with each component that holds more than a number or enum. */
    static List<String> offenders(List<Class<?>> records, Set<String> m74, Map<String, Holds> known, Set<String> frozen) {
        List<String> out = new ArrayList<>();
        for (Class<?> r : records) {
            for (RecordComponent c : r.getRecordComponents()) {
                Class<?> t = c.getType();
                if (t.isPrimitive() || t.isEnum() || Number.class.isAssignableFrom(t) || t == Boolean.class) continue;
                String key = r.getSimpleName() + "." + c.getName();
                boolean isM74 = m74.contains(r.getSimpleName());
                if (isM74 && (Path.class.isAssignableFrom(t) || java.io.File.class.isAssignableFrom(t))) {
                    out.add(key + ": an M74 fact never carries a Path — name the folder by a FactRefs reference");
                    continue;
                }
                Holds h = known.get(key);
                if (h == null && !(frozen.contains(key) && !isM74)) {
                    out.add(key + " (" + c.getGenericType().getTypeName() + "): not in the inventory — say what it holds "
                            + "(and never a model's words or an absolute path: spec §4.1, scorecard R6)");
                    continue;
                }
                if (isM74 && h != null && PLACE_OR_PROSE.matcher(c.getName()).matches()
                        && h != Holds.SHOWN_PATH && h != Holds.ANALYSER_WORDS
                        && !(h == Holds.VOCABULARY && VOCABULARY_NAME_EXEMPT.contains(key))) {
                    out.add(key + ": its name says a place or prose, held as " + h);
                }
                // review P3 N3: a STRUCTURE is a nested fact record of SessionEvents (itself checked), or a List of them
                if (h == Holds.STRUCTURE && !nestedFact(c)) {
                    out.add(key + ": held as STRUCTURE but its type " + c.getGenericType().getTypeName()
                            + " is not a SessionEvents record (or a List of them)");
                }
            }
        }
        return out;
    }

    /** A record nested in SessionEvents, or a List whose element type is one. */
    static boolean nestedFact(RecordComponent c) {
        Class<?> t = c.getType();
        if (List.class.isAssignableFrom(t) && c.getGenericType() instanceof java.lang.reflect.ParameterizedType p
                && p.getActualTypeArguments().length == 1 && p.getActualTypeArguments()[0] instanceof Class<?> e) {
            t = e;
        }
        return t.isRecord() && t.getEnclosingClass() == SessionEvents.class;
    }

    static List<Class<?>> factRecords() {
        List<Class<?>> out = new ArrayList<>();
        for (Class<?> c : SessionEvents.class.getDeclaredClasses()) if (c.isRecord()) out.add(c);
        return out;
    }

    @Test
    @DisplayName("★ R6: every fact component is in the inventory; no M74 fact carries a model's words or an absolute path")
    void everyFactFieldIsKnown() throws Exception {
        Set<String> m74 = m74Records();
        List<String> wrong = offenders(factRecords(), m74, M74, PRE_M74);
        assertEquals(List.of(), wrong, String.join("\n", wrong));
        // and no inventory entry is stale
        Set<String> present = new TreeSet<>();
        for (Class<?> r : factRecords()) for (RecordComponent c : r.getRecordComponents()) present.add(r.getSimpleName() + "." + c.getName());
        Set<String> stale = new TreeSet<>(M74.keySet());
        stale.addAll(PRE_M74);
        stale.removeAll(present);
        assertEquals(Set.of(), stale, "an inventory entry no longer exists — remove it");
    }

    /** The shapes the rule exists for — each flagged; the fixed shape passes. */
    record ProposalReceivedP2b(long request, String summary, List<String> reasons) { }

    record SetupChosenP2b(long request, Path dir) { }

    record ProposalReceivedNow(long request, long summaryRef) { }

    @Test
    @DisplayName("★ R6 witness: P2b's own shapes — Claude's summary as a field, a folder as a Path — are flagged")
    void theRuleCatchesP2bsShapes() {
        Set<String> m74 = Set.of("ProposalReceivedP2b", "SetupChosenP2b", "ProposalReceivedNow");
        List<String> wrong = offenders(List.of(ProposalReceivedP2b.class, SetupChosenP2b.class, ProposalReceivedNow.class),
                m74, M74, PRE_M74);
        assertEquals(3, wrong.size(), String.join("\n", wrong));
        assertTrue(wrong.get(0).startsWith("ProposalReceivedP2b.summary"), wrong.get(0));
        assertTrue(wrong.get(2).startsWith("SetupChosenP2b.dir: an M74 fact never carries a Path"), wrong.get(2));
        // a free-text field placed in the inventory under the wrong heading is caught by its name
        Map<String, Holds> lied = new LinkedHashMap<>(M74);
        lied.put("ProposalReceivedP2b.summary", Holds.DIGEST);
        lied.put("ProposalReceivedP2b.reasons", Holds.DIGEST);
        assertTrue(offenders(List.of(ProposalReceivedP2b.class), m74, lied, PRE_M74).stream()
                .anyMatch(s -> s.contains("its name says a place or prose")));
    }

    /** A STRUCTURE that is not a fact record, and a prose-named VOCABULARY field: both shapes the inventory used to wave through. */
    record EvadesP3(long request, List<String> items, String reason) { }

    /**
     * ★ Review P3 N3: a String list held as STRUCTURE, and a field named {@code reason} held as VOCABULARY, are flagged.
     * Witness: drop the {@code nestedFact} check, or exempt VOCABULARY from the name rule again — the count falls.
     */
    @Test
    @DisplayName("★ N3 witness: STRUCTURE must be a nested fact record; VOCABULARY obeys the name rule unless listed")
    void structureAndVocabularyCannotEvade() {
        Map<String, Holds> lied = new LinkedHashMap<>(M74);
        lied.put("EvadesP3.items", Holds.STRUCTURE);
        lied.put("EvadesP3.reason", Holds.VOCABULARY);
        List<String> wrong = offenders(List.of(EvadesP3.class), Set.of("EvadesP3"), lied, PRE_M74);
        assertEquals(2, wrong.size(), String.join("\n", wrong));
        assertTrue(wrong.stream().anyMatch(w -> w.startsWith("EvadesP3.items: held as STRUCTURE")));
        assertTrue(wrong.stream().anyMatch(w -> w.startsWith("EvadesP3.reason: its name says a place or prose")));
    }

    @Test
    @DisplayName("★ R6 witness: the facts the frame makes hold neither Claude's words nor an absolute path")
    void theFramesFactsHoldNoWordsOrPaths() {
        FactRefs refs = new FactRefs();
        var call = new telamin.fluxtion.audit.analyser.analyser.llm.ProposeShape.Call("SUMMARY-MARKER", List.of(
                new telamin.fluxtion.audit.analyser.analyser.llm.ProposeShape.Item("sourceRoot",
                        "/home/DEMO/secret/src", null, "REASON-MARKER", false, false),
                new telamin.fluxtion.audit.analyser.analyser.llm.ProposeShape.Item("processor", null,
                        "com.acme.MARKER.Processor", "REASON-TWO", false, false)));
        var fact = telamin.fluxtion.audit.analyser.analyser.ui.MainFrame.proposalFact(7, 3, call, refs);
        String recorded = fact.toString();
        for (String never : List.of("SUMMARY-MARKER", "REASON-MARKER", "REASON-TWO", "/home/DEMO", "MARKER.Processor")) {
            assertFalse(recorded.contains(never), never + " reached the session record: " + recorded);
        }
        assertEquals("SUMMARY-MARKER", refs.get(fact.summaryRef()), "the words are kept by reference");
        assertEquals("/home/DEMO/secret/src", refs.get(fact.items().get(0).pathRef()));
        assertEquals("…/src", FactWords.shown("/home/DEMO/secret/src"));
        assertEquals("could not read …/x.yaml (denied)", FactWords.withoutPaths("could not read /home/DEMO/a/x.yaml (denied)"));
        assertEquals("and/or a word", FactWords.withoutPaths("and/or a word"), "not a path");
        assertFalse(FactWords.holdsPath(FactWords.withoutPaths("C:\\Users\\DEMO\\x and ~/y/z")));
    }
}
