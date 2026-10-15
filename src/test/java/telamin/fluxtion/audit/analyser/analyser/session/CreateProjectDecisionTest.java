package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M73 (spec-create-project.md §3.5, §4, §7) — every decision about a create, on the REAL generated processor. Facts
 * go in as the adapter observed them; what {@code sessionBoundary} asks the adapter to do comes out. No filesystem:
 * the filesystem half is {@code ui.CreateProjectFlowTest}.
 */
class CreateProjectDecisionTest {

    private static final String DIR = "/work/demo";
    private static final String PROFILE = DIR + "/.analyser/project.fluxtion-settings";

    private static final SessionEvents.CreateOffer OFFER = new SessionEvents.CreateOffer(
            List.of(new SessionEvents.OfferedRoot(".", DIR, false),
                    new SessionEvents.OfferedRoot("app/src/main/java", DIR + "/app/src/main/java", false),
                    new SessionEvents.OfferedRoot("linked/src/main/java", DIR + "/linked/src/main/java", true)),
            List.of(new SessionEvents.OfferedSkill("skills/a/SKILL.md", "a", "first", false),
                    new SessionEvents.OfferedSkill("skills/b/SKILL.md", "b", "second", false)),
            "CAN_CREATE", null, "sha256:demo");

    /** Facts with nothing wrong: an absolute existing directory, no profile, switch ON. */
    private static SessionEvents.CreateFacts facts() {
        return new SessionEvents.CreateFacts(DIR, true, true, false, null, false, null, null, List.of(), OFFER, true);
    }

    private static SessionEvents.CreateFacts with(java.util.function.UnaryOperator<Object[]> change) {
        SessionEvents.CreateFacts f = facts();
        Object[] v = {f.dir(), f.absolute(), f.directory(), f.profileExists(), f.analyserProblem(), f.bundleWorkingCopy(),
                f.exchangeDir(), f.enclosingProject(), f.namedProfiles(), f.offer(), f.createAllowed()};
        v = change.apply(v);
        @SuppressWarnings("unchecked") List<String> named = (List<String>) v[8];
        return new SessionEvents.CreateFacts((String) v[0], (Boolean) v[1], (Boolean) v[2], (Boolean) v[3], (String) v[4],
                (Boolean) v[5], (String) v[6], (String) v[7], named, (SessionEvents.CreateOffer) v[9], (Boolean) v[10]);
    }

    private static SessionEvents.CreateSelection select(List<String> roots, List<String> skills, boolean guide,
                                                        String digest) {
        return new SessionEvents.CreateSelection(roots, skills, guide, digest);
    }

    private static final SessionEvents.ProjectAudience PERSON =
            new SessionEvents.ProjectAudience(SessionEvents.OperationOrigin.PERSON, true);
    private static final SessionEvents.ProjectAudience MACHINE =
            new SessionEvents.ProjectAudience(SessionEvents.OperationOrigin.MACHINE, false);

    private static void create(SessionDriver d, SessionEvents.ProjectAudience who, SessionEvents.AssistantActionOrigin origin,
                               SessionEvents.CreateFacts facts, SessionEvents.CreateSelection selection) {
        d.submit(new SessionEvents.OpenProjectRequested(d.nextOpId(), PROFILE, TransitionKind.CREATE, "test", origin, who,
                new SessionEvents.CreateRequest(facts, selection)));
    }

    private static FakeSessionAdapter refused(SessionEvents.ProjectAudience who, SessionEvents.CreateFacts facts,
                                              SessionEvents.CreateSelection selection, String expected) {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        create(d, who, null, facts, selection);
        assertEquals(0, a.creates.size(), "a refused create performs no create");
        assertEquals(0, a.countOf(SessionEffects.ApplyProfileEffect.class), "and applies nothing");
        assertNotNull(a.lastWarning, "the refusal is a warning the caller reads");
        assertTrue(a.lastWarning.contains(expected), a.lastWarning);
        assertTrue(a.lastWarning.contains("nothing was created"), "each refusal says what did not happen: " + a.lastWarning);
        return a;
    }

    private static FakeSessionAdapter admitted(SessionEvents.ProjectAudience who, SessionEvents.AssistantActionOrigin origin,
                                               SessionEvents.CreateFacts facts, SessionEvents.CreateSelection selection) {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        create(d, who, origin, facts, selection);
        assertNull(a.lastWarning, () -> "admitted, but warned: " + a.lastWarning);
        assertEquals(1, a.creates.size(), "exactly one create is performed");
        assertEquals(PROFILE, a.appliedProfile, "and the created project is applied");
        return a;
    }

    // ---- refusals, any origin -------------------------------------------------------------------------

    @Test
    @DisplayName("a profile that exists is refused for an assistant, with the open it should make instead")
    void existsIsRefused() {
        var a = refused(MACHINE, with(v -> { v[3] = true; return v; }), null, "already has a project");
        assertTrue(a.lastWarning.contains("open {project: \"" + DIR + "\"}"), a.lastWarning);
        assertTrue(a.openExistingOffers.isEmpty(), "a socket caller cannot answer a question");
    }

    @Test
    @DisplayName("for a PERSON an existing profile is the open-instead question, never a create")
    void existsForAPersonIsTheOpenInsteadQuestion() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        create(d, PERSON, null, with(v -> { v[3] = true; return v; }), null);
        assertEquals(1, a.openExistingOffers.size(), "the refusal is rendered as \"Open it instead?\"");
        assertEquals(PROFILE, a.openExistingOffers.get(0).profilePath());
        assertEquals(0, a.creates.size());
        assertNull(a.lastWarning);
    }

    @Test
    void notADirectoryIsRefused() {
        refused(PERSON, with(v -> { v[2] = false; return v; }), null, "is not an existing directory");
    }

    @Test
    void aRelativeDirectoryIsRefused() {
        refused(PERSON, with(v -> { v[1] = false; return v; }), null, "must be an absolute path");
    }

    @Test
    void aCreateWithoutItsFactsIsRefused() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        d.submit(new SessionEvents.OpenProjectRequested(d.nextOpId(), PROFILE, TransitionKind.CREATE, "test", null, PERSON));
        assertEquals(0, a.creates.size(), "a create the adapter did not observe is not performed");
        assertTrue(a.lastWarning.contains("must carry what was observed"), a.lastWarning);
    }

    // ---- assistant-only path refusals, and that they are assistant-only ---------------------------------

    @Test
    void aSymlinkedAnalyserDirectoryIsRefusedForAnAssistant() {
        refused(MACHINE, with(v -> { v[4] = "is a symbolic link"; return v; }), null, ".analyser is a symbolic link");
    }

    @Test
    void insideABundleWorkingCopyIsRefusedForAnAssistant() {
        refused(MACHINE, with(v -> { v[5] = true; return v; }), null, "evidence bundle's working copy");
    }

    @Test
    void insideTheExchangeDirectoryIsRefusedForAnAssistant() {
        refused(MACHINE, with(v -> { v[6] = "/work/exchange"; return v; }), null, "exchange directory (/work/exchange)");
    }

    @Test
    void nestedInsideAProjectIsRefusedForAnAssistant_andNamesIt() {
        refused(MACHINE, with(v -> { v[7] = "/work/.analyser/project.fluxtion-settings"; return v; }), null,
                "inside the existing project /work/.analyser/project.fluxtion-settings");
    }

    @Test
    @DisplayName("[R r2-1] the path refusals are the assistant's: a person creating a module project in a monorepo is admitted")
    void pathRefusalsAreAssistantOnly() {
        admitted(PERSON, null, with(v -> {
            v[7] = "/work/.analyser/project.fluxtion-settings";
            v[5] = true;
            v[6] = "/work";
            v[4] = "is a symbolic link";
            return v;
        }), null);
    }

    // ---- consent (D-73.7) ------------------------------------------------------------------------------

    @Test
    @DisplayName("assistant origin with the switch OFF is refused, naming the setting")
    void consentOffRefusesTheAssistant() {
        refused(MACHINE, with(v -> { v[10] = false; return v; }), null,
                "Settings ▸ Assistant ▸ Allow the assistant to create projects is off");
    }

    @Test
    @DisplayName("a person is never gated by the switch")
    void consentOffAdmitsAPerson() {
        admitted(PERSON, null, with(v -> { v[10] = false; return v; }), null);
    }

    @Test
    @DisplayName("the in-app assistant is assistant origin, and is gated")
    void theInAppAssistantIsGated() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        // even with a PERSON audience, an action of the in-app assistant's is the assistant's
        create(d, PERSON, new SessionEvents.AssistantActionOrigin(1, 2), with(v -> { v[10] = false; return v; }), null);
        assertEquals(0, a.creates.size());
        assertTrue(a.lastWarning.contains("may not create projects"), a.lastWarning);
    }

    @Test
    void anAssistantWithTheSwitchOnIsAdmitted() {
        admitted(MACHINE, null, facts(), null);
    }

    // ---- selection − offer (D-73.4) and the digest ---------------------------------------------------------

    @Test
    void anUnofferedRootIsRefused() {
        refused(MACHINE, facts(), select(List.of("elsewhere/src"), List.of(), false, null), "sourceRoot 'elsewhere/src'");
    }

    @Test
    void anUnofferedSkillIsRefused() {
        refused(MACHINE, facts(), select(List.of(), List.of("skills/z/SKILL.md"), false, null), "skill 'skills/z/SKILL.md'");
    }

    @Test
    void aRootThatResolvesOutsideIsNeverAdopted() {
        refused(MACHINE, facts(), select(List.of("linked/src/main/java"), List.of(), false, null), "resolves outside");
    }

    @Test
    void aGuideThatIsNotOfferedIsRefused() {
        var offer = new SessionEvents.CreateOffer(OFFER.sourceRoots(), OFFER.skills(), "EXISTS", null, "sha256:demo");
        refused(MACHINE, with(v -> { v[9] = offer; return v; }), select(List.of(), List.of(), true, null),
                "referenceGuide (the offer says EXISTS)");
    }

    @Test
    void aStaleDigestIsRefused() {
        refused(MACHINE, facts(), select(List.of("."), List.of(), false, "sha256:old"),
                "the offer changed since your preview — preview again");
    }

    @Test
    @DisplayName("G3: the approved selection is in OFFER order, whatever order the caller named it in")
    void approvedInOfferOrder() {
        var a = admitted(MACHINE, null, facts(), select(List.of("app/src/main/java", "."),
                List.of("skills/b/SKILL.md", "skills/a/SKILL.md"), true, "sha256:demo"));
        var approved = a.creates.get(0).approved();
        assertEquals(List.of(DIR, DIR + "/app/src/main/java"), approved.sourceRoots());
        assertEquals(List.of("a", "b"), approved.skills().stream().map(SessionEvents.OfferedSkill::name).toList());
        assertTrue(approved.referenceGuide());
        assertEquals(DIR, approved.root());
    }

    // ---- G2: a create or a load that throws ----------------------------------------------------------------

    @Test
    @DisplayName("G2 [R F1]: a create that throws is a warning, applies nothing, and leaves nothing in flight")
    void aThrownCreateIsAWarning() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        a.createThrows = true;
        create(d, PERSON, null, facts(), null);
        assertEquals(1, a.creates.size(), "the create was attempted");
        assertNotNull(a.lastWarning, "before the EffectFailed arm the caller saw success here");
        assertTrue(a.lastWarning.startsWith("Could not create the project: DEMO the profile could not be written"),
                a.lastWarning);
        assertEquals(0, a.countOf(SessionEffects.ApplyProfileEffect.class), "nothing was applied");
        assertFalse(a.logClosed || a.graphClosed, "a failed create closes nothing");
        // nothing is waiting: a late result for that operation is ignored, and the next request proceeds
        a.forget();
        a.lastWarning = null;
        create(d, PERSON, null, facts(), null);
        assertNull(a.lastWarning);
        assertEquals(1, a.countOf(SessionEffects.ApplyProfileEffect.class), "the next create proceeds");
    }

    @Test
    void aThrownLoadIsAWarningToo() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        a.loadThrows = true;
        d.submit(new SessionEvents.OpenProjectRequested(d.nextOpId(), "/x/.analyser/project.fluxtion-settings",
                TransitionKind.EXPLICIT_SWITCH, "test", null, PERSON));
        assertNotNull(a.lastWarning, "a thrown load used to read as success");
        assertTrue(a.lastWarning.startsWith("Could not open the project: disk went away"), a.lastWarning);
    }

    // ---- D-73.8: a refused create still supersedes --------------------------------------------------------

    @Test
    @DisplayName("D-73.8: a REFUSED create supersedes a pending log load")
    void aRefusedCreateSupersedesAPendingLoad() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        a.pendingOpens = true;
        long open = d.nextOpId();
        d.submit(new SessionEvents.OpenLogRequested(open, "/logs/pending.yaml", null, null, false));
        assertNotNull(d.processor().operationGate.inFlightWhat(), "the load is pending");
        create(d, MACHINE, null, with(v -> { v[10] = false; return v; }), null);    // refused: consent off
        assertNotNull(a.lastWarning, "the create was refused");
        assertNull(d.processor().operationGate.inFlightWhat(), "and still superseded the pending load");
        d.post(new SessionEvents.LogOpened(open, "/logs/pending.yaml", null, java.util.Set.of(), 0, 0, null));
        assertFalse(d.processor().openLog.isOpen(), "the late load is refused, not believed");
    }

    @Test
    @DisplayName("D-73.8: a competing-origin create ends a running in-app turn; the in-app assistant's own does not")
    void createVersusARunningTurn() {
        for (boolean own : new boolean[]{false, true}) {
            FakeSessionAdapter a = new FakeSessionAdapter();
            a.assistantVerbs.put(10L, "open");
            SessionDriver d = new SessionDriver(a);
            d.post(new SessionEvents.AssistantSendRequested(1, 1,
                    new SessionEvents.AssistantRoute("anthropic", "", "", true, true, 3, 20, 100)));
            var ctx = a.assistantContexts.get(0);
            d.post(new SessionEvents.AssistantContextPrepared(ctx.ticket(), 2));
            var r = a.assistantRequests.get(0);
            d.post(new SessionEvents.AssistantCompletionReceived(r.ticket(), 1, 3, List.of(10L)));
            assertEquals("RUNNING_ACTION", d.snapshot().assistant().phase());
            var origin = own ? new SessionEvents.AssistantActionOrigin(r.ticket(), 10) : null;
            create(d, own ? MACHINE : PERSON, origin, with(v -> { v[10] = false; return v; }), null);   // refused either way
            assertEquals(own ? "RUNNING_ACTION" : "SUPERSEDED", d.snapshot().assistant().phase(),
                    own ? "the assistant's own create does not end its turn" : "a person's create ends the turn");
        }
    }
}
