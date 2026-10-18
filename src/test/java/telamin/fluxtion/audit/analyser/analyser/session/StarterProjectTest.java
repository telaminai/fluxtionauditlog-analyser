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
     * effect order assertion fails. Pass 0 for {@code commandsRef} or "" for {@code keyNeed} in {@code onInstalled}, or
     * {@code false} for the design row's default in {@code onDiscoveryObserved} — the vendor or review assertion fails.
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
        d.post(new SessionEvents.StarterInstalled(req, REFS.put("/work/new-app"), "…/new-app", true, 41));
        var vendor = of(a, SessionEffects.VendorSkillsEffect.class);
        assertEquals("spring", vendor.get(0).authoring());
        assertEquals("embedded", vendor.get(0).host());
        // ★ add-a-node's marker is filled from the starter's first-run record and its key declaration (owner, 2026-10-18)
        assertEquals(41, vendor.get(0).commandsRef(), "the first-run record travels to the vendoring");
        assertEquals("build", vendor.get(0).keyNeed(), "the template's keyNeed travels to the vendoring");
        d.post(new SessionEvents.SkillsVendored(req, List.of("load-audit-log", "spring-authoring"), List.of(), List.of(), null));
        assertEquals(1, of(a, SessionEffects.RunDiscoveryEffect.class).size());
        d.post(new SessionEvents.StarterDiscoveryObserved(req, "digest-1", 5, true, 1, 2, 0));
        var review = of(a, SessionEffects.ReviewStarterProjectEffect.class);
        assertEquals(1, review.size(), "ONE combined review");
        assertEquals(List.of("load-audit-log", "spring-authoring"), review.get(0).vendored());
        assertTrue(review.get(0).designDefaultYes(), "★ the design folder's row starts at Yes: the node decides it");
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
        d.post(new SessionEvents.StarterGraphOfferAnswered(req, true));
        d.post(new SessionEvents.StarterGraphOfferAnswered(req, true));
        assertEquals(1, of(a, SessionEffects.OpenStarterGraphEffect.class).size(), "a current Yes opens exactly once");
    }

    @Test
    @DisplayName("a discovery failure settles the proposal and permits a new starter request")
    void discoveryFailureDoesNotStrandTheStarter() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = OnboardingProposalTest.admitted(d, a);
        catalogueRead(d);
        String id = proposeAndPreview(d, a, start);
        d.post(new SessionEvents.StarterDestinationChosen(id, id + ".1", REFS.put("/work/new-app"), "…/new-app"));
        d.post(new SessionEvents.ProposalApplyRequested(id, List.of(id + ".1"), false));
        long req = of(a, SessionEffects.DownloadStarterEffect.class).get(0).request();
        d.post(new SessionEvents.StarterInstalled(req, REFS.put("/work/new-app"), "…/new-app", true, 0));
        d.post(new SessionEvents.SkillsVendored(req, List.of(), List.of(), List.of(), null));
        assertEquals("DISCOVERING", d.snapshot().starter().phase());
        // This is the fact MainFrame.runStarterDiscovery posts when its real background reader throws.
        d.post(new SessionEvents.StarterInstallFailed(req + 1, "a stale discovery"));
        assertEquals("DISCOVERING", d.snapshot().starter().phase(), "another request cannot settle this one");
        d.post(new SessionEvents.StarterInstallFailed(req, "the project folder is no longer held"));
        assertEquals("FAILED", d.snapshot().starter().phase(), "discovery failure must not leave the chain busy");
        assertEquals(ProposalState.ItemState.FAILED, d.snapshot().proposal().items().get(0).state());
        assertTrue(d.snapshot().starter().note().contains("unpacked"), "the downloaded files still exist");
        assertEquals(0, of(a, SessionEffects.OpenStarterProjectEffect.class).size());
        d.post(new SessionEvents.StarterDiscoveryObserved(req, "late", 5, true, 1, 0, 0));
        assertEquals("FAILED", d.snapshot().starter().phase(), "a late success must not reopen the failed chain");
        d.post(new SessionEvents.StarterMenuRequested(91));
        assertEquals("CATALOGUE", d.snapshot().starter().phase(), "a new creation is no longer refused as busy");
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

    @Test
    void aProjectSwitchCancelsEveryPendingStarterStage() {
        for (int stage = 0; stage < 5; stage++) {
            FakeSessionAdapter a = new FakeSessionAdapter();
            SessionDriver d = LocalAgentSessionTest.driver(a);
            var start = OnboardingProposalTest.admitted(d, a);
            catalogueRead(d);
            String id = proposeAndPreview(d, a, start);
            d.post(new SessionEvents.StarterDestinationChosen(id, id + ".1", REFS.put("/work/DEMO-A"), "DEMO-A"));
            d.post(new SessionEvents.ProposalApplyRequested(id, List.of(id + ".1"), false));
            long req = of(a, SessionEffects.DownloadStarterEffect.class).get(0).request();
            if (stage > 0) d.post(new SessionEvents.StarterInstalled(req, REFS.put("/work/DEMO-A"), "DEMO-A", true, 0));
            if (stage > 1) d.post(new SessionEvents.SkillsVendored(req, List.of(), List.of(), List.of(), null));
            if (stage > 2) d.post(new SessionEvents.StarterDiscoveryObserved(req, "offer", 5, true, 1, 0, 0));
            if (stage > 3) d.post(new SessionEvents.StarterReviewAnswered(req, true, List.of(), List.of(), 0, "offer"));
            String phase = d.snapshot().starter().phase();
            d.submit(new SessionEvents.OpenProjectRequested(d.nextOpId(), "/work/DEMO-missing/profile",
                    TransitionKind.EXPLICIT_SWITCH, "review"));
            assertEquals(phase, d.snapshot().starter().phase(), "a failed switch has not changed the project");
            a.withProfile("/work/DEMO-A/profile").withProfile("/work/DEMO-B/profile");
            d.submit(new SessionEvents.OpenProjectRequested(d.nextOpId(), "/work/DEMO-B/profile",
                    TransitionKind.EXPLICIT_SWITCH, "review"));
            assertEquals("CANCELLED", d.snapshot().starter().phase(),
                    "project switch must cancel starter continuation at stage " + stage);
            assertFalse(d.snapshot().proposal().awaitingPerson(), "the cancelled proposal must not remain actionable");
            int effects = a.starterEffects.size();
            d.post(new SessionEvents.StarterInstalled(req, REFS.put("/work/DEMO-A"), "DEMO-A", true, 0));
            d.post(new SessionEvents.SkillsVendored(req, List.of(), List.of(), List.of(), null));
            d.post(new SessionEvents.StarterDiscoveryObserved(req, "offer", 5, true, 1, 0, 0));
            d.post(new SessionEvents.StarterReviewAnswered(req, true, List.of(), List.of(), 0, "offer"));
            d.post(new SessionEvents.StarterProjectOpened(req, true, null));
            assertEquals(effects, a.starterEffects.size(), "late results must not open, watch or draft");
            d.submit(new SessionEvents.OpenProjectRequested(d.nextOpId(), "/work/DEMO-A/profile",
                    TransitionKind.EXPLICIT_SWITCH, "starter", null, null, null, req));
            assertEquals("/work/DEMO-B/profile", a.appliedProfile, "a queued starter open cannot replace the new project");
            assertTrue(d.snapshot().starter().note().contains("files"), "cancellation does not remove downloaded files");
        }
    }

    @Test
    void partialSkillWriteDoesNotClaimNothingWasAdded(@org.junit.jupiter.api.io.TempDir java.nio.file.Path tmp) throws Exception {
        var skills = java.nio.file.Files.createDirectories(tmp.resolve(".claude/skills/spring-authoring"));
        java.nio.file.Files.writeString(skills.resolve("reference"), "DEMO blocks the support directory");
        var report = new telamin.fluxtion.audit.analyser.analyser.template.StarterSkills().vendor(tmp, "spring", "embedded");
        assertNotNull(report.refusal(), "the real writer must encounter the blocked directory");
        assertFalse(report.written().isEmpty(), "earlier skills really were added before the failure");
        assertTrue(java.nio.file.Files.isRegularFile(tmp.resolve(".claude/skills/add-a-node/SKILL.md")));
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = OnboardingProposalTest.admitted(d, a);
        catalogueRead(d);
        String id = proposeAndPreview(d, a, start);
        d.post(new SessionEvents.StarterDestinationChosen(id, id + ".1", REFS.put("/work/DEMO"), "DEMO"));
        d.post(new SessionEvents.ProposalApplyRequested(id, List.of(id + ".1"), false));
        long req = of(a, SessionEffects.DownloadStarterEffect.class).get(0).request();
        d.post(new SessionEvents.StarterInstalled(req, REFS.put("/work/DEMO"), "DEMO", true, 0));
        d.post(new SessionEvents.SkillsVendored(req, report.written(), report.kept(), report.digests(),
                FactWords.withoutPaths(report.refusal())));
        assertFalse(d.snapshot().starter().note().contains("No skills were added"),
                "a partial write must not be reported as zero writes");
        assertTrue(d.snapshot().starter().note().contains("incomplete"), d.snapshot().starter().note());
        assertEquals(d.snapshot().starter().note(), a.lastStatus, "the partial outcome is presented, not only stored");
        assertEquals("DISCOVERING", d.snapshot().starter().phase(), "the existing continue-without-skills policy is unchanged");
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
