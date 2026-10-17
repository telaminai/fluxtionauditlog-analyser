package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 P2b (spec §5.6, §10 P2): Claude proposes, the analyser applies — every decision in {@code onboardingProposal}, on
 * the REAL generated processor with a recording adapter.
 */
class OnboardingProposalTest {

    static final String SETUP = "/work/medmon";

    /** R6: Claude's words reach a fact only by reference; the test keeps them as the frame would. */
    static final FactRefs REFS = new FactRefs();

    /** An item as the frame makes its fact (MainFrame.proposalFact): words by reference, digests to compare by. */
    static SessionEvents.ProposalItemFact fact(String kind, String path, String className, String reason, boolean tooLong,
                                               boolean malformed) {
        return new SessionEvents.ProposalItemFact(kind, REFS.put(path), LocalAgentPosture.digest(path), REFS.put(className),
                LocalAgentPosture.digest(className), REFS.put(reason), tooLong, malformed);
    }

    static SessionEvents.ProposalItemFact item(String kind, String path) {
        return fact(kind, path, null, "because DEMO", false, false);
    }

    static SessionEvents.ProposalItemFact processor(String className) {
        return fact("processor", null, className, "the DEMO processor", false, false);
    }

    /** A bound, posture-checked session: its generation is admitted, so a hosted propose of it is taken. */
    static SessionEffects.StartLocalAgentEffect admitted(SessionDriver d, FakeSessionAdapter a) {
        var start = LocalAgentSessionTest.startToReady(d, a);
        LocalAgentSessionTest.send(d, a, 10);
        d.post(LocalAgentSessionTest.goodInit(start, 1));
        assertEquals(start.generation(), d.snapshot().localAgent().admittedGeneration());
        return start;
    }

    static ProposalState proposal(SessionDriver d) {
        return d.snapshot().proposal();
    }

    /** Preview every item as found, inside, with a verdict word; then the preview's end. */
    static void previewAll(SessionDriver d, FakeSessionAdapter a, boolean projectExists) {
        var preview = a.proposalPreviews.get(a.proposalPreviews.size() - 1);
        for (var i : preview.items()) {
            d.post(new SessionEvents.ProposalItemPreviewed(preview.proposalId(), i.itemId(), "READABLE", false, null));
        }
        d.post(new SessionEvents.ProposalPreviewDone(preview.proposalId(), REFS.put(SETUP), FactWords.shown(SETUP), projectExists));
    }

    /**
     * ★ Witness: apply on receipt (request an ApplyProposalItemEffect in onReceived) — the "no effect but the preview"
     * assertion fails.
     */
    @Test
    @DisplayName("★ propose stores an offer and changes nothing: no effect but the preview, no profile, no open")
    void proposeStoresAnOfferAndChangesNothing() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = admitted(d, a);
        int before = a.performed.size();
        d.post(new SessionEvents.ProposalReceived(1, start.generation(), REFS.put("DEMO set-up"),
                List.of(item("sourceRoot", "src/main/java"), item("graphml", "DEMO.graphml"))));
        var effects = a.performed.subList(before, a.performed.size());
        assertEquals(1, effects.size(), "only the preview: " + effects);
        assertInstanceOf(SessionEffects.PreviewProposalEffect.class, effects.get(0));
        assertTrue(a.proposalApplies.isEmpty(), "nothing applied on receipt");
        assertNull(a.appliedProfile, "no profile applied");
        var p = proposal(d);
        assertTrue(p.answers().get(1L).accepted());
        assertEquals("p-1", p.answers().get(1L).proposalId());
        assertFalse(p.awaitingPerson(), "the banner waits for the analyser's own check");
        previewAll(d, a, true);
        assertTrue(proposal(d).awaitingPerson(), "checked: the person's to answer");
        assertTrue(proposal(d).items().stream().allMatch(i -> i.state() == ProposalState.ItemState.PENDING));
    }

    /** ★ Witness: drop any one rule from {@code itemRefusal} — its row's assertion fails. */
    @Test
    @DisplayName("★ item refusals, each with its reason; an outside path is OUTSIDE and never applicable")
    void itemRefusalsEachWithTheirReason() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = admitted(d, a);
        d.post(new SessionEvents.ProposalReceived(1, start.generation(), REFS.put(""), List.of(
                item("bogus", "x"),                                                                  // 1 unknown kind
                item("graphml", null),                                                               // 2 no path
                fact("runbook", "r.md", null, "r", true, false),       // 3 too long
                fact("processor", null, "not a name!", "r", false, true), // 4 malformed
                processor("com.acme.DemoProcessor"),                                                  // 5 ok
                processor("com.acme.Other"),                                                          // 6 second processor
                item("sourceRoot", "src"),                                                            // 7 ok
                item("sourceRoot", "src"),                                                            // 8 duplicate
                item("logToOpen", "a.log"),                                                           // 9 ok
                item("logToOpen", "b.log"))));                                                        // 10 second log
        var items = proposal(d).items();
        assertTrue(items.get(0).why().contains("unknown kind"), items.get(0).why());
        assertTrue(items.get(1).why().contains("needs a path"), items.get(1).why());
        assertTrue(items.get(2).why().contains("over its length"), items.get(2).why());
        assertTrue(items.get(3).why().contains("qualified name"), items.get(3).why());
        assertEquals(ProposalState.ItemState.CHECKING, items.get(4).state());
        assertTrue(items.get(5).why().contains("second processor"), items.get(5).why());
        assertEquals(ProposalState.ItemState.CHECKING, items.get(6).state());
        assertTrue(items.get(7).why().contains("duplicate"), items.get(7).why());
        assertEquals(ProposalState.ItemState.CHECKING, items.get(8).state());
        assertTrue(items.get(9).why().contains("second logToOpen"), items.get(9).why());
        for (int i : new int[]{0, 1, 2, 3, 5, 7, 9}) assertEquals(ProposalState.ItemState.REFUSED, items.get(i).state());
        // only the three that passed are previewed; one comes back outside
        var preview = a.proposalPreviews.get(0);
        assertEquals(List.of("p-1.5", "p-1.7", "p-1.9"), preview.items().stream().map(SessionEffects.ProposalItemRef::itemId).toList());
        d.post(new SessionEvents.ProposalItemPreviewed("p-1", "p-1.5", "RESOLVES", false, null));
        d.post(new SessionEvents.ProposalItemPreviewed("p-1", "p-1.7", "OUTSIDE", true, null));
        d.post(new SessionEvents.ProposalItemPreviewed("p-1", "p-1.9", "NOT_FOUND", false, "NOT_FOUND"));
        d.post(new SessionEvents.ProposalPreviewDone("p-1", REFS.put(SETUP), FactWords.shown(SETUP), true));
        items = proposal(d).items();
        assertEquals(ProposalState.ItemState.OUTSIDE, items.get(6).state());
        assertFalse(items.get(6).applicable(), "an outside item is never applicable");
        assertEquals(ProposalState.ItemState.REFUSED, items.get(8).state());
        // the person answers Yes to the outside item: it is not applied
        d.post(new SessionEvents.ProposalApplyRequested("p-1", List.of("p-1.5", "p-1.7"), false));
        assertEquals(List.of("p-1.5"), a.proposalApplies.stream().map(x -> x.item().itemId()).toList(),
                "only the applicable Yes item");
    }

    /** ★ Witness: drop the {@code open()} check in onReceived — the second proposal is recorded. */
    @Test
    @DisplayName("★ a second proposal while one is open is refused, with words that name what to do")
    void aSecondProposalIsRefused() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = admitted(d, a);
        d.post(new SessionEvents.ProposalReceived(1, start.generation(), REFS.put(""), List.of(item("sourceRoot", "src"))));
        d.post(new SessionEvents.ProposalReceived(2, start.generation(), REFS.put(""), List.of(item("graphml", "g.graphml"))));
        var second = proposal(d).answers().get(2L);
        assertFalse(second.accepted());
        assertTrue(second.refusal().contains("already waiting"), second.refusal());
        assertEquals("p-1", proposal(d).id());
        assertEquals(1, a.proposalPreviews.size());
    }

    /**
     * ★ Witness: admit any generation (drop the admittedGeneration comparison in onReceived) — the stale session's
     * proposal is recorded.
     */
    @Test
    @DisplayName("★ a propose from a generation that is not the admitted one is refused, nothing recorded")
    void anUnadmittedGenerationIsRefused() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = admitted(d, a);
        d.post(new SessionEvents.ProposalReceived(1, start.generation() + 7, REFS.put(""), List.of(item("sourceRoot", "src"))));
        assertFalse(proposal(d).answers().get(1L).accepted());
        assertFalse(proposal(d).exists());
        assertTrue(a.proposalPreviews.isEmpty());
    }

    /**
     * ★ Witness: apply the items in Claude's order instead of {@link
     * telamin.fluxtion.audit.analyser.analyser.session.node.OnboardingProposal}'s fixed order — the order assertion fails;
     * treat a No as a Yes — the declined assertion fails.
     */
    @Test
    @DisplayName("★ every item starts No; Apply chosen applies only Yes items, in the fixed order, one at a time")
    void applyOnlyYesInTheFixedOrder() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = admitted(d, a);
        d.post(new SessionEvents.ProposalReceived(1, start.generation(), REFS.put(""), List.of(
                item("logToOpen", "logs/demo.log"), item("graphml", "DEMO.graphml"), item("runbook", "RUNBOOK.md"),
                processor("com.acme.DemoProcessor"), item("sourceRoot", "src/main/java"), item("sourceRoot", "lib"))));
        previewAll(d, a, false);
        assertEquals("PENDING", proposal(d).create(), "no project here: the analyser's create row");
        d.post(new SessionEvents.ProposalApplyRequested("p-1",
                List.of("p-1.1", "p-1.2", "p-1.3", "p-1.4", "p-1.5"), true));
        assertEquals(List.of("create", "p-1.5", "p-1.4", "p-1.3", "p-1.2", "p-1.1"),
                a.proposalApplies.stream().map(x -> x.item().itemId()).toList(),
                "create, source roots, processor, runbooks, GraphML, log");
        assertEquals(SETUP, REFS.get(a.proposalApplies.get(0).setupDirRef()));
        var p = proposal(d);
        assertEquals("APPLIED", p.create());
        assertEquals(ProposalState.ItemState.DECLINED, p.items().get(5).state(), "the item left at No");
        assertEquals(5, p.count(ProposalState.ItemState.APPLIED));
        assertEquals(1, a.proposalChecks.size(), "the analyser's own check after the last item");
        assertEquals("UNKNOWN", p.pairing());
    }

    /** ★ Witness: never disable the project-needing items (drop needsProject in onApplyRequested) — one is applied. */
    @Test
    @DisplayName("★ an item that needs a project is not applied when no project exists and Create was not chosen")
    void anItemNeedingAProjectWaitsForTheCreate() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = admitted(d, a);
        d.post(new SessionEvents.ProposalReceived(1, start.generation(), REFS.put(""), List.of(item("sourceRoot", "src"),
                item("graphml", "DEMO.graphml"))));
        previewAll(d, a, false);
        d.post(new SessionEvents.ProposalApplyRequested("p-1", List.of("p-1.1", "p-1.2"), false));
        assertEquals(List.of("p-1.2"), a.proposalApplies.stream().map(x -> x.item().itemId()).toList(),
                "a GraphML opens without a project; a source root needs one");
        assertEquals("DECLINED", proposal(d).create());
        assertTrue(proposal(d).items().get(0).why().contains("needs a project"));
    }

    /** ★ Witness: copy the stored item (with its reason) into contextRows — the reason assertion fails. */
    @Test
    @DisplayName("★ context.proposals carries ids, kinds, states and the analyser's verdicts — never Claude's reasons")
    void contextNeverCarriesReasons() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = admitted(d, a);
        d.post(new SessionEvents.ProposalReceived(1, start.generation(), REFS.put("SUMMARY-MARKER"), List.of(
                fact("sourceRoot", "PATH-MARKER", null, "REASON-MARKER", false, false))));
        previewAll(d, a, true);
        String rows = proposal(d).contextRows().toString();
        assertTrue(rows.contains("p-1.1") && rows.contains("sourceRoot") && rows.contains("pending") && rows.contains("READABLE"), rows);
        assertFalse(rows.contains("REASON-MARKER"), rows);
        assertFalse(rows.contains("SUMMARY-MARKER"), rows);
        assertFalse(rows.contains("PATH-MARKER"), rows);
    }

    /**
     * ★ Witness: hold the session's own create too (drop {@code applyingOwnCreate()} from SessionBoundary.holds) — the
     * create is offered as a switch and the no-offer assertion fails.
     */
    @Test
    @DisplayName("★ accepting Create a project: no switch offer, the session is kept, New chat offered, items survive New chat")
    void theSessionsOwnCreateIsNotASwitch() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = admitted(d, a);
        d.post(new SessionEvents.ProposalReceived(1, start.generation(), REFS.put(""), List.of(item("sourceRoot", "src"),
                item("graphml", "DEMO.graphml"))));
        previewAll(d, a, false);
        // the apply's create effect is answered by the performer's real path: a person's CREATE request
        d.post(new SessionEvents.ProposalApplyRequested("p-1", List.of("p-1.1"), true));
        assertEquals("create", a.proposalApplies.get(0).item().itemId());
        // replay what the performer does for the create: a person-origin CREATE — while the session runs
        var b = new FakeSessionAdapter();
        SessionDriver e = LocalAgentSessionTest.driver(b);
        var s2 = admitted(e, b);
        e.post(new SessionEvents.ProposalReceived(1, s2.generation(), REFS.put(""), List.of(item("sourceRoot", "src"))));
        previewAll(e, b, false);
        b.holdApplies = true;
        e.post(new SessionEvents.ProposalApplyRequested("p-1", List.of("p-1.1"), true));
        assertTrue(e.processor().onboardingProposal.applyingOwnCreate());
        e.submit(new SessionEvents.OpenProjectRequested(e.nextOpId(), SETUP + "/.analyser/project.fluxtion-settings",
                TransitionKind.CREATE, "proposal", null, new SessionEvents.ProjectAudience(
                SessionEvents.OperationOrigin.PERSON, true), new SessionEvents.CreateRequest(
                CreateProjectDecisionTestFacts.facts(SETUP), SessionEvents.CreateSelection.empty())));
        assertTrue(b.switchOffers.isEmpty(), "the session's own create is never offered as a switch");
        assertEquals(1, b.creates.size(), "it is created");
        e.post(new SessionEvents.ProposalItemApplied(0, "p-1", "create", "APPLIED", ""));
        assertTrue(e.snapshot().localAgent().running(), "Keep: the session keeps running");
        assertTrue(e.snapshot().localAgent().newChatOffered(), "New chat starts a session in the project");
        assertTrue(e.snapshot().localAgent().reason().contains("before the project existed"));
        e.post(new SessionEvents.LocalAgentNewChatRequested("test"));
        assertEquals("p-1", e.snapshot().proposal().id(), "the proposal survives the New chat");
    }

    /** ★ Witness: a text-scanning proposal reader — any answer holding propose-shaped JSON would record an offer. */
    @Test
    @DisplayName("★ a free-text 'proposal' in an answer records nothing: only the verb does")
    void freeTextIsNotAProposal() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = admitted(d, a);
        var sent = a.agentSends.get(a.agentSends.size() - 1);     // the turn admitted() opened
        d.post(new SessionEvents.LocalAgentTextUpdated(start.generation(), sent.ticket(), 99, 1));
        d.post(new SessionEvents.LocalAgentTurnEnded(start.generation(), sent.ticket(), SessionEvents.TurnOutcome.COMPLETED));
        assertFalse(proposal(d).exists());
        assertTrue(a.proposalPreviews.isEmpty());
    }
}
