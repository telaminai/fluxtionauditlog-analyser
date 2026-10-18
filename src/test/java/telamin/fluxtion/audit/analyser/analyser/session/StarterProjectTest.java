package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Option 2 (owner, 2026-10-18): Claude proposes a starter, the person confirms it in the review, and the analyser runs the
 * starter flow itself — every decision in {@code onboardingProposal} and {@code starterProject}, on the REAL generated
 * processor with a recording adapter. Each ★ test names the mutation that turns it red.
 */
class StarterProjectTest {

    static final FactRefs REFS = OnboardingProposalTest.REFS;

    static final String SPRING = "fluxtion-spring";
    static final String EMBEDDED = "fluxtion-embedded";

    /** The catalogue as the frame reports it: Spring second in the catalogue, so the node's order is visible. */
    static void catalogueRead(SessionDriver d) {
        d.post(new SessionEvents.StarterCatalogueObserved(0, true, 7, List.of(
                new SessionEvents.StarterFacts(LocalAgentPosture.digest(EMBEDDED), "", "embedded", "interpreted", "run"),
                new SessionEvents.StarterFacts(LocalAgentPosture.digest(SPRING), "spring", "embedded", "aot", "build"))));
    }

    /** A starter item as MainFrame.proposalFact makes it: Claude's words by reference, the id's digest to compare. */
    static SessionEvents.ProposalItemFact starter(String id, String path, boolean namesMalformed) {
        return new SessionEvents.ProposalItemFact("starter", REFS.put(path), LocalAgentPosture.digest(path), 0, null,
                REFS.put("Spring is the preferred route"), false, false, REFS.put(id), LocalAgentPosture.digest(id),
                REFS.put("my-app"), 0, 0, namesMalformed);
    }

    static <T> List<T> of(FakeSessionAdapter a, Class<T> type) {
        return a.starterEffects.stream().filter(type::isInstance).map(type::cast).toList();
    }

    /** Receipt, the preview's end and the template file's defaults: the row is the person's to answer. */
    static String proposeAndPreview(SessionDriver d, FakeSessionAdapter a, SessionEffects.StartLocalAgentEffect start) {
        d.post(new SessionEvents.ProposalReceived(1, start.generation(), REFS.put("a Spring app"),
                List.of(starter(SPRING, null, false))));
        OnboardingProposalTest.previewAll(d, a, false);
        var defaults = of(a, SessionEffects.ReadStarterDefaultsEffect.class);
        assertEquals(1, defaults.size(), "the template file is read at receipt");
        d.post(new SessionEvents.StarterDefaultsObserved(0, defaults.get(0).request(), true, 3, "spring", false, null));
        return d.snapshot().proposal().id();
    }

    @Test
    @DisplayName("the catalogue: Spring first and recommended, the catalogue's order otherwise; not-read until something reads it")
    void theCatalogueIsSpringFirst() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        assertEquals(StarterState.NOT_READ, d.snapshot().starter().catalogue(), "not read before anything asks");
        catalogueRead(d);
        var entries = d.snapshot().starter().entries();
        assertEquals(StarterState.READ, d.snapshot().starter().catalogue());
        assertEquals(LocalAgentPosture.digest(SPRING), entries.get(0).idDigest(), "Spring first");
        assertTrue(entries.get(0).recommended(), "and recommended, with no key found");
        assertFalse(entries.get(1).recommended());
    }

    @Test
    @DisplayName("a hosted session's start reads the catalogue once; a failed spawn reads nothing")
    void aHostedSessionReadsTheCatalogueOnce() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        d.post(new SessionEvents.LocalAgentSpawned(99, SessionEvents.SpawnFailure.NOT_FOUND, 0));
        assertEquals(0, of(a, SessionEffects.ReadStarterCatalogueEffect.class).size(), "a failed spawn reads nothing");
        OnboardingProposalTest.admitted(d, a);
        var reads = of(a, SessionEffects.ReadStarterCatalogueEffect.class);
        assertEquals(1, reads.size());
        assertEquals("session", reads.get(0).purpose());
        d.post(new SessionEvents.StarterCatalogueObserved(reads.get(0).request(), true, 7, List.of()));
        d.post(new SessionEvents.LocalAgentSpawned(99, SessionEvents.SpawnFailure.NONE, 0));
        assertEquals(1, of(a, SessionEffects.ReadStarterCatalogueEffect.class).size(), "read once: it is in context now");
    }

    /**
     * ★ Witness: drop the {@code hasPath} check from {@code OnboardingProposal.starterRefusal} — the row with a path is
     * previewed instead of refused, and the defaults read is requested.
     */
    @Test
    @DisplayName("★ a starter row with a path is refused; so are an unknown id, malformed names and an unread catalogue")
    void aStarterRowWithAPathIsRefused() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = OnboardingProposalTest.admitted(d, a);
        // the catalogue not read yet: refused, nothing read
        d.post(new SessionEvents.ProposalReceived(1, start.generation(), REFS.put(""), List.of(starter(SPRING, null, false))));
        var item = d.snapshot().proposal().items().get(0);
        assertEquals(ProposalState.ItemState.REFUSED, item.state());
        assertTrue(item.why().contains("has not read the starter catalogue"), item.why());
        OnboardingProposalTest.previewAll(d, a, false);        // the (empty) preview ends the proposal
        catalogueRead(d);
        List<String> reasons = new java.util.ArrayList<>();
        long request = 2;
        for (var bad : List.of(starter(SPRING, "/home/me/new-app", false), starter("no-such-starter", null, false),
                starter(SPRING, null, true))) {
            d.post(new SessionEvents.ProposalReceived(request++, start.generation(), REFS.put(""), List.of(bad)));
            var p = d.snapshot().proposal();
            assertEquals(ProposalState.ItemState.REFUSED, p.items().get(0).state(), p.items().get(0).toString());
            reasons.add(p.items().get(0).why());
            OnboardingProposalTest.previewAll(d, a, false);
        }
        assertTrue(reasons.get(0).contains("takes no path"), reasons.get(0));
        assertTrue(reasons.get(1).contains("not a starter in the catalogue"), reasons.get(1));
        assertTrue(reasons.get(2).contains("not a valid identifier"), reasons.get(2));
        assertEquals(0, of(a, SessionEffects.ReadStarterDefaultsEffect.class).size(), "a refused row reads nothing");
        assertEquals(0, of(a, SessionEffects.DownloadStarterEffect.class).size());
    }

    /**
     * ★ Witness: let {@code StarterProject.apply} download without {@code destinationReady()} — the Apply before the
     * folder requests a download and the first assertion fails; or let {@code applyStarter} ignore the No — the No row
     * downloads.
     */
    @Test
    @DisplayName("★ the order: Apply before the folder (or the preview) downloads nothing, and a No row creates nothing")
    void applyBeforeTheFolderDownloadsNothingAndANoRowCreatesNothing() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = OnboardingProposalTest.admitted(d, a);
        catalogueRead(d);
        d.post(new SessionEvents.ProposalReceived(1, start.generation(), REFS.put(""), List.of(starter(SPRING, null, false))));
        // Apply before the preview has answered: refused as stale, nothing downloaded
        d.post(new SessionEvents.ProposalApplyRequested("p-1", List.of("p-1.1"), false));
        assertEquals(0, of(a, SessionEffects.DownloadStarterEffect.class).size(), "before the preview: no download");
        OnboardingProposalTest.previewAll(d, a, false);
        assertEquals("NONE", d.snapshot().proposal().create(), "a starter makes its own project: no empty-create row");
        var defaults = of(a, SessionEffects.ReadStarterDefaultsEffect.class).get(0);
        d.post(new SessionEvents.StarterDefaultsObserved(0, defaults.request(), true, 3, "spring", false, null));
        assertTrue(d.snapshot().proposal().awaitingPerson(), "the row is the person's to answer");
        // Yes, but no folder chosen: declined, nothing downloaded
        d.post(new SessionEvents.ProposalItemAnswered("p-1", "p-1.1", true));
        d.post(new SessionEvents.ProposalApplyRequested("p-1", List.of("p-1.1"), false));
        assertEquals(0, of(a, SessionEffects.DownloadStarterEffect.class).size(), "no folder: no download");
        assertEquals(ProposalState.ItemState.DECLINED, d.snapshot().proposal().items().get(0).state());
        assertEquals("DECLINED", d.snapshot().starter().phase());

        // a second proposal: the folder chosen, the row answered No
        d.post(new SessionEvents.ProposalReceived(2, start.generation(), REFS.put(""), List.of(starter(SPRING, null, false))));
        OnboardingProposalTest.previewAll(d, a, false);
        var defaults2 = of(a, SessionEffects.ReadStarterDefaultsEffect.class).get(1);
        d.post(new SessionEvents.StarterDefaultsObserved(0, defaults2.request(), true, 3, "spring", false, null));
        d.post(new SessionEvents.StarterDestinationChosen("p-2", "p-2.1", REFS.put("/work/new-app"), "…/new-app"));
        assertTrue(d.snapshot().starter().destinationReady(), "an empty folder was observed");
        d.post(new SessionEvents.ProposalItemAnswered("p-2", "p-2.1", false));
        d.post(new SessionEvents.ProposalApplyRequested("p-2", List.of("p-2.1"), false));
        assertEquals(0, of(a, SessionEffects.DownloadStarterEffect.class).size(), "★ a No row creates nothing");
        assertEquals(ProposalState.ItemState.DECLINED, d.snapshot().proposal().items().get(0).state());
    }

    @Test
    @DisplayName("a folder that is not empty keeps Apply from downloading")
    void aFolderThatIsNotEmptyIsNotReady() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        a.starterDirVerdict = "NOT_EMPTY";
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = OnboardingProposalTest.admitted(d, a);
        catalogueRead(d);
        String id = proposeAndPreview(d, a, start);
        d.post(new SessionEvents.StarterDestinationChosen(id, id + ".1", REFS.put("/work/full"), "…/full"));
        assertFalse(d.snapshot().starter().destinationReady());
        d.post(new SessionEvents.ProposalApplyRequested(id, List.of(id + ".1"), false));
        assertEquals(0, of(a, SessionEffects.DownloadStarterEffect.class).size());
    }

    /**
     * ★ Witness: request OpenStarterProjectEffect from {@code onSkillsVendored} (skipping discovery and the review) — the
     * effect order assertion fails.
     */
    @Test
    @DisplayName("★ the chain: Apply → download → skills → discovery → ONE review → open → design, watch and draft")
    void theChainRunsInItsOrder() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = OnboardingProposalTest.admitted(d, a);
        catalogueRead(d);
        String id = proposeAndPreview(d, a, start);
        d.post(new SessionEvents.StarterDestinationChosen(id, id + ".1", REFS.put("/work/new-app"), "…/new-app"));
        d.post(new SessionEvents.ProposalItemAnswered(id, id + ".1", true));
        d.post(new SessionEvents.ProposalApplyRequested(id, List.of(id + ".1"), false));
        var download = of(a, SessionEffects.DownloadStarterEffect.class);
        assertEquals(1, download.size(), "the person's Apply is the download");
        long req = download.get(0).request();
        assertEquals(REFS.put("my-app") > 0, download.get(0).artifactRef() != 0, "the proposal's artifact name travels");
        assertEquals(ProposalState.ItemState.APPLYING, d.snapshot().proposal().items().get(0).state());
        d.post(new SessionEvents.StarterInstalled(req, REFS.put("/work/new-app"), "…/new-app", true, 0));
        var vendor = of(a, SessionEffects.VendorSkillsEffect.class);
        assertEquals("spring", vendor.get(0).authoring());
        assertEquals("embedded", vendor.get(0).host());
        d.post(new SessionEvents.SkillsVendored(req, List.of("load-audit-log", "spring-authoring"), List.of(), List.of(), null));
        assertEquals(1, of(a, SessionEffects.RunDiscoveryEffect.class).size());
        d.post(new SessionEvents.StarterDiscoveryObserved(req, "digest-1", 5, true, 1, 2, 0));
        var review = of(a, SessionEffects.ReviewStarterProjectEffect.class);
        assertEquals(1, review.size(), "ONE combined review");
        assertEquals(List.of("load-audit-log", "spring-authoring"), review.get(0).vendored());
        d.post(new SessionEvents.StarterReviewAnswered(req, true, List.of(), List.of(), 0, "digest-1"));
        var open = of(a, SessionEffects.OpenStarterProjectEffect.class);
        assertEquals(1, open.size());
        assertTrue(open.get(0).profilePresent(), "the starter's own profile opens");
        assertTrue(d.snapshot().starter().phase().equals("OPENING"));
        d.post(new SessionEvents.StarterProjectOpened(req, true, null));
        List<String> order = a.starterEffects.stream().map(SessionDriver::name).toList();
        assertEquals(List.of("readStarterDefaults", "observeStarterDirectory", "downloadStarter", "vendorSkills",
                "runDiscovery", "reviewStarterProject", "openStarterProject", "openStarterDesign", "watchStarterGraphml",
                "armStarterDraft"), order.subList(order.indexOf("readStarterDefaults"), order.size()));
        assertEquals(ProposalState.ItemState.APPLIED, d.snapshot().proposal().items().get(0).state());
        assertTrue(d.snapshot().starter().graphWatch(), "the GraphML watch is armed");
        // the first build's graph is offered once
        d.post(new SessionEvents.StarterGraphmlAppeared(req, REFS.put("/work/new-app/x.graphml"), "…/x.graphml"));
        d.post(new SessionEvents.StarterGraphmlAppeared(req, REFS.put("/work/new-app/x.graphml"), "…/x.graphml"));
        assertEquals(1, of(a, SessionEffects.OfferStarterGraphEffect.class).size(), "offered once");
    }

    @Test
    @DisplayName("Close in the combined review opens nothing; the files stay and the row says so")
    void closingTheReviewOpensNothing() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = OnboardingProposalTest.admitted(d, a);
        catalogueRead(d);
        String id = proposeAndPreview(d, a, start);
        d.post(new SessionEvents.StarterDestinationChosen(id, id + ".1", REFS.put("/work/new-app"), "…/new-app"));
        d.post(new SessionEvents.ProposalApplyRequested(id, List.of(id + ".1"), false));
        long req = of(a, SessionEffects.DownloadStarterEffect.class).get(0).request();
        d.post(new SessionEvents.StarterInstalled(req, REFS.put("/work/new-app"), "…/new-app", false, 0));
        d.post(new SessionEvents.SkillsVendored(req, List.of(), List.of(), List.of(), null));
        d.post(new SessionEvents.StarterDiscoveryObserved(req, "digest-1", 5, false, 1, 0, 0));
        d.post(new SessionEvents.StarterReviewAnswered(req, false, List.of(), List.of(), 0, "digest-1"));
        assertEquals(0, of(a, SessionEffects.OpenStarterProjectEffect.class).size());
        assertEquals("NOT_OPENED", d.snapshot().starter().phase());
        var item = d.snapshot().proposal().items().get(0);
        assertEquals(ProposalState.ItemState.APPLIED, item.state());
        assertTrue(item.why().contains("not opened"), item.why());
    }

    /**
     * ★ One template home. Witness: give the menu its own download (request DownloadStarterEffect from
     * {@code onMenuRequested}) — the picker and form steps are skipped and the order fails.
     */
    @Test
    @DisplayName("★ Project ▸ New project from template… takes the same chain: catalogue → picker → defaults → form → download")
    void theMenuTakesTheSameChain() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        d.post(new SessionEvents.StarterMenuRequested(1));
        var read = of(a, SessionEffects.ReadStarterCatalogueEffect.class);
        assertEquals("menu", read.get(0).purpose());
        d.post(new SessionEvents.StarterCatalogueObserved(read.get(0).request(), true, 7, List.of(
                new SessionEvents.StarterFacts(LocalAgentPosture.digest(SPRING), "spring", "embedded", "aot", "build"))));
        var choose = of(a, SessionEffects.ChooseStarterEffect.class);
        assertEquals(1, choose.size());
        d.post(new SessionEvents.StarterChosen(choose.get(0).request(), LocalAgentPosture.digest(SPRING)));
        var defaults = of(a, SessionEffects.ReadStarterDefaultsEffect.class);
        d.post(new SessionEvents.StarterDefaultsObserved(0, defaults.get(0).request(), true, 3, "spring", true, null));
        var configure = of(a, SessionEffects.ConfigureStarterEffect.class);
        assertEquals(1, configure.size());
        d.post(new SessionEvents.StarterConfigured(configure.get(0).request(), true, 4, REFS.put("/work/menu-app"),
                "…/menu-app", false));
        var download = of(a, SessionEffects.DownloadStarterEffect.class);
        assertEquals(1, download.size(), "the same download effect a proposal's Apply asks for");
        long req = download.get(0).request();
        d.post(new SessionEvents.StarterInstalled(req, REFS.put("/work/menu-app"), "…/menu-app", true, 9));
        d.post(new SessionEvents.SkillsVendored(req, List.of("add-a-node"), List.of(), List.of(), null));
        d.post(new SessionEvents.StarterDiscoveryObserved(req, "d", 5, true, 1, 1, 0));
        d.post(new SessionEvents.StarterReviewAnswered(req, true, List.of(), List.of(), 0, "d"));
        d.post(new SessionEvents.StarterProjectOpened(req, true, null));
        assertEquals(1, of(a, SessionEffects.ShowStarterCommandsEffect.class).size(), "the menu ends with the commands");
        assertEquals(0, of(a, SessionEffects.ArmStarterDraftEffect.class).size(), "and places no Claude draft");
        assertEquals(1, of(a, SessionEffects.OpenStarterDesignEffect.class).size(), "the design opens at once");
    }

    @Test
    @DisplayName("the menu's Cancel at the picker or the form downloads nothing")
    void theMenuCancelledDownloadsNothing() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        d.post(new SessionEvents.StarterMenuRequested(1));
        long req = of(a, SessionEffects.ReadStarterCatalogueEffect.class).get(0).request();
        d.post(new SessionEvents.StarterCatalogueObserved(req, true, 7, List.of(
                new SessionEvents.StarterFacts(LocalAgentPosture.digest(SPRING), "spring", "embedded", "aot", "build"))));
        d.post(new SessionEvents.StarterChosen(req, null));
        assertEquals("CANCELLED", d.snapshot().starter().phase());
        assertEquals(0, of(a, SessionEffects.DownloadStarterEffect.class).size());
    }
}
