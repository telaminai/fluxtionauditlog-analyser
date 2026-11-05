package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static telamin.fluxtion.audit.analyser.analyser.session.ProjectOutputsState.Kind.*;

/** M81 D: actual generated graph, raw metadata facts and recording effects; no model or real project. */
class ProjectOutputsTest {
    private static class Fixture {
        final FakeSessionAdapter adapter = new FakeSessionAdapter().withProfile("/work/DEMO/profile");
        final SessionDriver driver = LocalAgentSessionTest.driver(adapter);
        final long generation;
        final long watch;
        Fixture(boolean owned) {
            driver.submit(new SessionEvents.OpenProjectRequested(driver.nextOpId(), "/work/DEMO/profile",
                    TransitionKind.EXPLICIT_SWITCH, "test"));
            generation = driver.processor().activeProject.generation();
            var observed = effects(SessionEffects.ObserveProjectOutputsEffect.class);
            assertEquals(1, observed.size(), "an ordinary open project must request general output observations");
            watch = observed.getFirst().watch();
            driver.post(new SessionEvents.ProjectOutputsBasisObserved(0, watch, generation, 10, owned, List.of(20L)));
        }
        <T> List<T> effects(Class<T> type) {
            return adapter.performed.stream().filter(type::isInstance).map(type::cast).toList();
        }
        void scan(long at, ProjectOutputsState.File... files) {
            driver.post(new SessionEvents.ProjectOutputsScanned(watch, generation, at, List.of(files), true));
        }
        ProjectOutputsState state() { return driver.processor().projectOutputs.state(); }
    }
    static ProjectOutputsState.File graph(long revision) { return file(GRAPHML, 31, "graph", revision); }
    static ProjectOutputsState.File log(long revision) { return file(LOG, 32, "log", revision); }
    /** §3.5.1: one logical graph DEMO/P.graphml held at a source path and two build copies, each with its own metadata. */
    static ProjectOutputsState.File copy(long ref, String key, boolean build, long mtime, String content) {
        return new ProjectOutputsState.File(GRAPHML, ref, key, "DEMO.graphml",
                new ProjectOutputsState.Revision(100, mtime, "DEMO-file-key-" + ref, content), "DEMO-logical-graph", build);
    }
    static ProjectOutputsState.File src(long mtime, String content) { return copy(41, "a-src", false, mtime, content); }
    static ProjectOutputsState.File classes(long mtime, String content) { return copy(42, "b-classes", true, mtime, content); }
    static ProjectOutputsState.File staging(long mtime, String content) { return copy(43, "c-staging", true, mtime, content); }

    static ProjectOutputsState.File file(ProjectOutputsState.Kind kind, long ref, String key, long revision) {
        return new ProjectOutputsState.File(kind, ref, key, "DEMO." + kind.name().toLowerCase(),
                new ProjectOutputsState.Revision(revision, revision, "DEMO-file-key"));
    }

    @Test void normalProjectsUseTheGeneralWatch() {
        Fixture f = new Fixture(false);
        var watches = f.effects(SessionEffects.WatchProjectOutputsEffect.class);
        assertEquals(1, watches.size(), "ordinary projects, not just starters, must be watched");
        assertEquals(4000, watches.getFirst().pollMillis());
    }

    @Test void aGraphBurstIsOfferedOnceOnlyAfterFourSecondsStable() {
        Fixture f = new Fixture(false);
        f.scan(0, graph(1));
        f.scan(4000, graph(2));
        assertTrue(f.state().offers().isEmpty(), "a first changed sample is not a settled output");
        f.scan(7999, graph(2));
        assertTrue(f.state().offers().isEmpty(), "four seconds of stability are required, not 3999ms");
        f.scan(8000, graph(2));
        assertEquals(1, f.state().offers().size(), "a settled ordinary-project graph must be offered");
        f.scan(12000, graph(2));
        assertEquals(1, f.effects(SessionEffects.PublishProjectOutputChangedEffect.class).size(), "one change, not one per poll");
        assertEquals(1, f.effects(SessionEffects.OfferProjectOutputEffect.class).size());
    }

    @Test void analyserOwnedRootsNeverRequestAWatch() {
        for (String ownership : List.of("sample", "bundle working copy")) {
            Fixture f = new Fixture(true);
            assertTrue(f.effects(SessionEffects.WatchProjectOutputsEffect.class).isEmpty(), ownership + " must not request a watch");
            f.scan(0); f.scan(4000, graph(1)); f.scan(8000, graph(1));
            assertTrue(f.state().offers().isEmpty(), ownership + " must not offer even a stray callback");
        }
    }

    @Test void growingAndIncompleteScansCannotOfferPartialOutputs() {
        Fixture f = new Fixture(false);
        f.scan(0);
        for (int i = 1; i <= 8; i++) {
            f.scan(i * 4000L, graph(i), log(i));
            assertTrue(f.state().offers().isEmpty(), "a still-growing partial output must not be offered");
        }
        f.driver.post(new SessionEvents.ProjectOutputsScanned(f.watch, f.generation, 36000, List.of(graph(8), log(8)), false));
        assertTrue(f.state().offers().isEmpty(), "an incomplete scan does not confirm stability");
        f.scan(40000, graph(8), log(8));
        assertEquals(2, f.state().offers().size(), "settled outputs can now be offered, never certified whole");
    }

    @Test void onlyThePersonsCurrentOneUseAnswerOpensAndPairs() {
        Fixture f = new Fixture(false);
        f.scan(0); f.scan(4000, graph(1), log(1)); f.scan(8000, graph(1), log(1));
        assertEquals(List.of("graphml", "log"), f.state().pendingKinds());
        assertTrue(f.effects(SessionEffects.OpenProjectOutputEffect.class).isEmpty(), "an output fact must NEVER replace the person's own log");
        var offer = f.state().offers().stream().filter(o -> o.file().kind() == LOG).findFirst().orElseThrow();
        f.driver.post(new SessionEvents.ProjectOutputOfferAnswered(f.watch + 1, offer.id(), true));
        assertTrue(f.effects(SessionEffects.OpenProjectOutputEffect.class).isEmpty(), "an old/different watch cannot accept");
        f.driver.post(new SessionEvents.ProjectOutputOfferAnswered(f.watch, offer.id(), true));
        f.driver.post(new SessionEvents.ProjectOutputOfferAnswered(f.watch, offer.id(), true));
        var opens = f.effects(SessionEffects.OpenProjectOutputEffect.class);
        assertEquals(1, opens.size(), "the person's Open is one-use");
        assertEquals(log(1), opens.getFirst().file());
        assertEquals(graph(1), opens.getFirst().graph(), "accepting a log uses the explicit regenerated graph in the offer");
        assertEquals(List.of("graphml"), f.state().pendingKinds());
        var graphOffer = f.state().offers().getFirst();
        f.driver.post(new SessionEvents.ProjectOutputOfferAnswered(f.watch, graphOffer.id(), false));
        assertTrue(f.state().pendingKinds().isEmpty());
        f.scan(12000, graph(1), log(1));
        assertTrue(f.state().offers().isEmpty(), "Not now consumes this revision, not future changes");
        f.scan(16000, graph(2)); f.scan(20000, graph(2));
        assertEquals(1, f.state().offers().size(), "the next distinct revision may offer again");
    }

    @Test void differentPathsArePreservedAndRewritesSupersedeOldButtons() {
        Fixture f = new Fixture(false);
        var other = file(LOG, 33, "other", 1);
        f.scan(0); f.scan(4000, log(1), other); f.scan(8000, log(1), other);
        assertEquals(2, f.state().offers().size(), "different output paths must not overwrite each other's offers");
        long old = f.state().offers().stream().filter(o -> o.file().pathRef() == 32).findFirst().orElseThrow().id();
        f.scan(12000, log(2), other);
        f.driver.post(new SessionEvents.ProjectOutputOfferAnswered(f.watch, old, true));
        assertTrue(f.effects(SessionEffects.OpenProjectOutputEffect.class).isEmpty(), "a rewritten file invalidates the old button before settling");
        f.scan(16000, log(2), other);
        assertEquals(2, f.state().offers().size());
    }

    @Test void anOpenTurnHoldsOffersAndOnlyItsCompletedExactFileSuppresses() {
        Fixture f = new Fixture(false);
        f.scan(0);
        var mode = LocalAgentPosture.FullMode.DEFAULT;
        var start = LocalAgentFullPostureTest.startFull(f.driver, f.adapter, mode);
        var turn = LocalAgentSessionTest.send(f.driver, f.adapter, 10, LocalAgentFullPostureTest.full(mode));
        f.driver.post(LocalAgentFullPostureTest.fullInit(start, turn.ticket(), "default", List.of(), "connected"));
        f.scan(4000, graph(1)); f.scan(8000, graph(1));
        assertTrue(f.state().offers().isEmpty(), "a hosted turn must hold its output offer until the turn ends");
        assertTrue(f.effects(SessionEffects.OfferProjectOutputEffect.class).isEmpty(), "no mid-turn banner effect");
        f.driver.post(new SessionEvents.HostedProjectOutputOpened(f.watch, f.generation, start.generation(), turn.ticket() + 1, graph(1)));
        f.driver.post(new SessionEvents.LocalAgentTurnEnded(start.generation(), turn.ticket(), SessionEvents.TurnOutcome.COMPLETED));
        assertEquals(1, f.state().offers().size(), "a wrong-ticket completion cannot suppress the held offer");
        f.driver.post(new SessionEvents.HostedProjectOutputOpened(f.watch, f.generation, start.generation(), turn.ticket(), graph(1)));
        assertTrue(f.state().offers().isEmpty(), "only actual successful completion for the original ticket/file/revision suppresses");
        f.scan(12000, graph(2)); f.scan(16000, graph(2));
        assertEquals(1, f.state().offers().size());
        f.driver.post(new SessionEvents.HostedProjectOutputOpened(f.watch, f.generation, start.generation(), turn.ticket(), graph(1)));
        assertEquals(1, f.state().offers().size(), "a late old-revision completion must not clear a newer offer");
    }

    @Test void acceptingDuringAPendingFailedSwitchNeverQueuesAnOldRootOpen() {
        Fixture f = new Fixture(false);
        f.scan(0); f.scan(4000, log(1)); f.scan(8000, log(1));
        long offer = f.state().offers().getFirst().id();
        f.adapter.loadPending = true;
        long op = f.driver.nextOpId();
        f.driver.submit(new SessionEvents.OpenProjectRequested(op, "/work/DEMO-new/profile", TransitionKind.EXPLICIT_SWITCH, "test"));
        f.driver.post(new SessionEvents.ProjectOutputOfferAnswered(f.watch, offer, true));
        assertTrue(f.effects(SessionEffects.OpenProjectOutputEffect.class).isEmpty(), "a pending project load must not lend its context to an old-root offer");
        f.driver.post(new SessionEvents.ProfileLoaded(op, "/work/DEMO-new/profile", false, null, 0, "DEMO unavailable"));
        assertEquals("/work/DEMO/profile", f.driver.processor().activeProject.profilePath(), "a failed switch changes no project");
        assertEquals(f.watch, f.state().watch(), "a failed switch retains the old watch");
        assertEquals(1, f.state().offers().size(), "the unaccepted offer is retained");
        assertTrue(f.effects(SessionEffects.OpenProjectOutputEffect.class).isEmpty(), "load failure does not release a queued open");
        f.driver.post(new SessionEvents.ProjectOutputOfferAnswered(f.watch, offer, true));
        assertEquals(1, f.effects(SessionEffects.OpenProjectOutputEffect.class).size(), "a fresh person click after failure can open");
    }

    @Test void aShortTurnCompletedBetweenPollsUsesOnlyItsExactSuccessfulOpenReceipt() {
        Fixture f = new Fixture(false);
        f.scan(0);
        var mode = LocalAgentPosture.FullMode.DEFAULT;
        var start = LocalAgentFullPostureTest.startFull(f.driver, f.adapter, mode);
        var turn = LocalAgentSessionTest.send(f.driver, f.adapter, 10, LocalAgentFullPostureTest.full(mode));
        f.driver.post(LocalAgentFullPostureTest.fullInit(start, turn.ticket(), "default", List.of(), "connected"));
        f.driver.post(new SessionEvents.HostedProjectOutputOpened(f.watch, f.generation, start.generation(), turn.ticket(), graph(1)));
        f.driver.post(new SessionEvents.LocalAgentTurnEnded(start.generation(), turn.ticket(), SessionEvents.TurnOutcome.COMPLETED));
        f.scan(4000, graph(1)); f.scan(8000, graph(1));
        assertTrue(f.state().offers().isEmpty(), "a completed exact-revision open between polls is not re-offered");
        f.scan(12000, graph(2)); f.scan(16000, graph(2));
        assertEquals(1, f.state().offers().size(), "the old successful receipt cannot suppress a later revision");
        f.driver.post(new SessionEvents.HostedProjectOutputOpened(f.watch + 1, f.generation, start.generation(), turn.ticket(), graph(2)));
        assertEquals(1, f.state().offers().size(), "a different watch cannot lend its ticket to the current offer");
    }

    @Test void anOldCompletionWithinTheSameTurnCannotClearItsNewerRevision() {
        Fixture f = new Fixture(false);
        f.scan(0);
        var mode = LocalAgentPosture.FullMode.DEFAULT;
        var start = LocalAgentFullPostureTest.startFull(f.driver, f.adapter, mode);
        var turn = LocalAgentSessionTest.send(f.driver, f.adapter, 10, LocalAgentFullPostureTest.full(mode));
        f.driver.post(LocalAgentFullPostureTest.fullInit(start, turn.ticket(), "default", List.of(), "connected"));
        f.scan(4000, graph(1)); f.scan(8000, graph(1));
        f.scan(12000, graph(2)); f.scan(16000, graph(2));
        f.driver.post(new SessionEvents.LocalAgentTurnEnded(start.generation(), turn.ticket(), SessionEvents.TurnOutcome.COMPLETED));
        assertEquals(1, f.state().offers().size());
        f.driver.post(new SessionEvents.HostedProjectOutputOpened(f.watch, f.generation, start.generation(), turn.ticket(), graph(1)));
        assertEquals(1, f.state().offers().size(), "an older completed revision in the SAME turn cannot consume the newer offer");
    }

    @Test void aCompleteBoundedScanDoesNotForgetItsHundredSuccessfullyOpenedOutputs() {
        Fixture f = new Fixture(false);
        f.scan(0);
        var mode = LocalAgentPosture.FullMode.DEFAULT;
        var start = LocalAgentFullPostureTest.startFull(f.driver, f.adapter, mode);
        var turn = LocalAgentSessionTest.send(f.driver, f.adapter, 10, LocalAgentFullPostureTest.full(mode));
        f.driver.post(LocalAgentFullPostureTest.fullInit(start, turn.ticket(), "default", List.of(), "connected"));
        var files = new ProjectOutputsState.File[100];
        for (int i = 0; i < files.length; i++) {
            files[i] = file(GRAPHML, 100 + i, "DEMO-graph-" + i, 1);
            f.driver.post(new SessionEvents.HostedProjectOutputOpened(f.watch, f.generation, start.generation(), turn.ticket(), files[i]));
        }
        f.driver.post(new SessionEvents.LocalAgentTurnEnded(start.generation(), turn.ticket(), SessionEvents.TurnOutcome.COMPLETED));
        f.scan(4000, files); f.scan(8000, files);
        assertTrue(f.state().offers().isEmpty(), "the receipt bound must cover all one hundred candidates, not redundantly offer the first thirty-six");
    }

    @Test void aDeferredAcceptanceCannotResurrectAfterANewerFailedOperation() {
        Fixture f = new Fixture(false);
        f.scan(0); f.scan(4000, graph(1)); f.scan(8000, graph(1));
        long offer = f.state().offers().getFirst().id();
        f.adapter.outputOpensPending = true;
        f.driver.post(new SessionEvents.ProjectOutputOfferAnswered(f.watch, offer, true));
        assertTrue(f.driver.processor().projectOutputs.permitsOpen(f.watch, offer, f.generation));
        f.driver.submit(new SessionEvents.OpenProjectRequested(f.driver.nextOpId(), "/work/DEMO-missing/profile", TransitionKind.EXPLICIT_SWITCH, "test"));
        assertFalse(f.driver.processor().projectOutputs.permitsOpen(f.watch, offer, f.generation), "a newer failed operation must not resurrect a deferred old-root open");
        f.driver.post(new SessionEvents.ProjectOutputOpenObserved(0, f.watch, offer, false, "SUPERSEDED"));
        assertEquals(1, f.state().offers().size());
        assertNotEquals(offer, f.state().offers().getFirst().id(), "retry has a fresh one-use button, not the old queued acceptance");
    }

    @Test void lateObservationsCannotResurrectAnOutputsOfferAfterASuccessfulSwitch() {
        Fixture f = new Fixture(false);
        f.scan(0); f.scan(4000, graph(1)); f.scan(8000, graph(1));
        long oldOffer = f.state().offers().getFirst().id();
        f.adapter.withProfile("/work/DEMO-B/profile");
        f.driver.submit(new SessionEvents.OpenProjectRequested(f.driver.nextOpId(), "/work/DEMO-B/profile",
                TransitionKind.EXPLICIT_SWITCH, "test"));
        long nextWatch = f.effects(SessionEffects.ObserveProjectOutputsEffect.class).getLast().watch();
        long nextGeneration = f.driver.processor().activeProject.generation();
        f.driver.post(new SessionEvents.ProjectOutputsBasisObserved(0, nextWatch, nextGeneration, 40, false, List.of()));
        f.driver.post(new SessionEvents.ProjectOutputsScanned(nextWatch, nextGeneration, 12000, List.of(), true));
        f.scan(16000, graph(2)); f.scan(20000, graph(2));
        assertTrue(f.state().offers().isEmpty(), "late old-root scans must not resurrect an offer in the new project");
        f.driver.post(new SessionEvents.ProjectOutputOfferAnswered(f.watch, oldOffer, true));
        assertTrue(f.effects(SessionEffects.OpenProjectOutputEffect.class).isEmpty(), "a switched project's old button cannot open");
        assertEquals(nextWatch, f.state().watch());
    }

    @Test void oneRegenerationCopiedByTheBuildIsOneOfferNamingTheSourceCopy() {
        Fixture f = new Fixture(false);
        f.scan(0, src(1, "v1"), classes(1, "v1"), staging(1, "v1"));
        // generate.sh rewrites the source graph; mvn package then copies the same bytes into target/.
        f.scan(4000, src(2, "v2"), classes(1, "v1"), staging(1, "v1"));
        f.scan(8000, src(2, "v2"), classes(3, "v2"), staging(3, "v2"));
        f.scan(12000, src(2, "v2"), classes(3, "v2"), staging(3, "v2"));
        f.scan(16000, src(2, "v2"), classes(3, "v2"), staging(3, "v2"));
        assertEquals(1, f.state().offers().size(), "three copies of one regenerated graph are one offer, not three banners");
        assertEquals(41, f.state().offers().getFirst().file().pathRef(), "the offer names the source copy, not a build copy");
        assertEquals(1, f.effects(SessionEffects.OfferProjectOutputEffect.class).size());
        f.driver.post(new SessionEvents.ProjectOutputOfferAnswered(f.watch, f.state().offers().getFirst().id(), false));
        // A later package re-copies identical bytes with fresh timestamps.
        f.scan(20000, src(2, "v2"), classes(5, "v2"), staging(5, "v2"));
        f.scan(24000, src(2, "v2"), classes(5, "v2"), staging(5, "v2"));
        assertTrue(f.state().offers().isEmpty(), "a copy of a graph already answered is not a regeneration");
        f.scan(28000, src(6, "v3"), classes(5, "v2"), staging(5, "v2"));
        f.scan(32000, src(6, "v3"), classes(5, "v2"), staging(5, "v2"));
        assertEquals(1, f.state().offers().size(), "changed bytes are a real regeneration and offer once more");
    }

    @Test void aBuildCopySettlingFirstHandsItsOfferToTheSourceCopy() {
        Fixture f = new Fixture(false);
        f.scan(0, src(1, "v1"), staging(1, "v1"));
        f.scan(4000, src(1, "v1"), staging(2, "v2"));
        f.scan(8000, src(1, "v1"), staging(2, "v2"));
        assertEquals(43, f.state().offers().getFirst().file().pathRef(), "only the build copy holds the new bytes so far");
        long id = f.state().offers().getFirst().id();
        f.scan(12000, src(3, "v2"), staging(2, "v2"));
        f.scan(16000, src(3, "v2"), staging(2, "v2"));
        assertEquals(1, f.state().offers().size(), "the source copy of the same bytes adds no second banner");
        assertEquals(41, f.state().offers().getFirst().file().pathRef(), "it takes over the pending offer");
        assertEquals(id, f.state().offers().getFirst().id(), "keeping the button the person can already see");
    }

    @Test void aGraphOnlyInBuildOutputIsStillOffered() {
        Fixture f = new Fixture(false);
        f.scan(0);
        f.scan(4000, classes(1, "v1"));
        f.scan(8000, classes(1, "v1"));
        assertEquals(1, f.state().offers().size(), "a build-generated graph with no source copy must still be offered");
    }

    // Reviews of PR #222.
    @Test void anUnansweredOfferSurvivesAnIdenticalRewriteOfItsCopy() {
        Fixture f = new Fixture(false);
        f.scan(0, src(1, "v1"), staging(1, "v1"));
        f.scan(4000, src(2, "v2"), staging(1, "v1")); f.scan(8000, src(2, "v2"), staging(1, "v1"));
        assertEquals(1, f.state().offers().size());
        // generate.sh runs again before the person answers: same bytes, new mtime.
        f.scan(12000, src(3, "v2"), staging(1, "v1")); f.scan(16000, src(3, "v2"), staging(1, "v1"));
        assertEquals(1, f.state().offers().size(), "an unanswered regeneration must not vanish on an identical rewrite");
        assertEquals(41, f.state().offers().getFirst().file().pathRef());
    }

    @Test void anOfferOnABuildCopySurvivesTheBuildCopyingTheSameBytesAgain() {
        Fixture f = new Fixture(false);
        f.scan(0, src(1, "v1"), staging(1, "v1"));
        f.scan(4000, src(1, "v1"), staging(2, "v2")); f.scan(8000, src(1, "v1"), staging(2, "v2"));
        assertEquals(1, f.state().offers().size());
        f.scan(12000, src(1, "v1"), staging(3, "v2")); f.scan(16000, src(1, "v1"), staging(3, "v2"));
        assertEquals(1, f.state().offers().size(), "re-copying the offered bytes must not drop the offer");
    }

    @Test void anIdenticalRewriteKeepsALogPairedWithItsGraph() {
        Fixture f = new Fixture(false);
        f.scan(0, src(1, "v1"));
        f.scan(4000, src(2, "v2"), log(1)); f.scan(8000, src(2, "v2"), log(1));
        var graphOffer = f.state().offers().stream().filter(o -> o.file().kind() == GRAPHML).findFirst().orElseThrow();
        f.driver.post(new SessionEvents.ProjectOutputOfferAnswered(f.watch, graphOffer.id(), false));
        f.scan(12000, src(3, "v2"), log(1)); f.scan(16000, src(3, "v2"), log(1));
        var logOffer = f.state().offers().stream().filter(o -> o.file().kind() == LOG).findFirst().orElseThrow();
        assertNotNull(logOffer.graph(), "the log keeps its graph after the graph is rewritten with the same bytes");
        assertEquals(41, logOffer.graph().pathRef());
        assertTrue(f.state().offers().stream().noneMatch(o -> o.file().kind() == GRAPHML), "and the answered graph is not re-offered");
    }

    @Test void aStaleBuildCopyAtOpenIsNotARegenerationWhenTheBuildCatchesUp() {
        Fixture f = new Fixture(false);
        f.scan(0, src(1, "v2"), classes(1, "v1"));          // regenerated before the project was opened, not yet packaged
        f.scan(4000, src(1, "v2"), classes(2, "v2")); f.scan(8000, src(1, "v2"), classes(2, "v2"));
        assertTrue(f.state().offers().isEmpty(), "the graph was current at open; packaging it is not a regeneration");
    }

    @Test void differentModulesAreDifferentGraphs() {
        Fixture f = new Fixture(false);
        var a1 = new ProjectOutputsState.File(GRAPHML, 51, "module-a", "DEMO.graphml",
                new ProjectOutputsState.Revision(100, 1, "DEMO-a", "v1"), "DEMO-graph-a", false);
        var b1 = new ProjectOutputsState.File(GRAPHML, 52, "module-b", "DEMO.graphml",
                new ProjectOutputsState.Revision(100, 1, "DEMO-b", "v1"), "DEMO-graph-b", false);
        f.scan(0, a1, b1);
        var a2 = new ProjectOutputsState.File(GRAPHML, 51, "module-a", "DEMO.graphml",
                new ProjectOutputsState.Revision(100, 2, "DEMO-a", "a2"), "DEMO-graph-a", false);
        var b2 = new ProjectOutputsState.File(GRAPHML, 52, "module-b", "DEMO.graphml",
                new ProjectOutputsState.Revision(100, 2, "DEMO-b", "b2"), "DEMO-graph-b", false);
        f.scan(4000, a2, b1); f.scan(8000, a2, b2); f.scan(12000, a2, b2);
        assertEquals(2, f.state().offers().size(), "one module's regeneration never replaces another's offer");
    }

    @Test void aFailedOpenNeverAddsASecondBannerBesideANewerOneForTheSameGraph() {
        Fixture f = new Fixture(false);
        f.scan(0, src(1, "v1"), staging(1, "v1"));
        f.scan(4000, src(1, "v1"), staging(2, "v2")); f.scan(8000, src(1, "v1"), staging(2, "v2"));
        long offer = f.state().offers().getFirst().id();
        f.adapter.outputOpensPending = true;
        f.driver.post(new SessionEvents.ProjectOutputOfferAnswered(f.watch, offer, true));
        assertEquals(1, f.effects(SessionEffects.OpenProjectOutputEffect.class).size());
        f.scan(12000, src(3, "v3"), staging(2, "v2")); f.scan(16000, src(3, "v3"), staging(2, "v2"));
        assertEquals(1, f.state().offers().size());
        f.driver.post(new SessionEvents.ProjectOutputOpenObserved(0, f.watch, offer, false, "DEMO failure"));
        assertEquals(1, f.state().offers().size(), "the failed v2 open must not come back next to the v3 offer");
    }

    @Test void aHostedOpenOfABuildCopyCoversTheGraphAndRetiresWhenTheGraphMoves() {
        Fixture f = new Fixture(false);
        f.scan(0, src(1, "v1"), staging(1, "v1"));
        var mode = LocalAgentPosture.FullMode.DEFAULT;
        var start = LocalAgentFullPostureTest.startFull(f.driver, f.adapter, mode);
        var turn = LocalAgentSessionTest.send(f.driver, f.adapter, 10, LocalAgentFullPostureTest.full(mode));
        f.driver.post(LocalAgentFullPostureTest.fullInit(start, turn.ticket(), "default", List.of(), "connected"));
        f.scan(4000, src(2, "v2"), staging(2, "v2"));
        f.driver.post(new SessionEvents.HostedProjectOutputOpened(f.watch, f.generation, start.generation(), turn.ticket(), staging(2, "v2")));
        f.scan(8000, src(2, "v2"), staging(2, "v2"));
        f.driver.post(new SessionEvents.LocalAgentTurnEnded(start.generation(), turn.ticket(), SessionEvents.TurnOutcome.COMPLETED));
        assertTrue(f.state().offers().isEmpty(), "the assistant opened this graph (via its build copy): no offer for the source copy");
        f.scan(12000, src(3, "v3"), staging(2, "v2")); f.scan(16000, src(3, "v3"), staging(2, "v2"));
        assertEquals(1, f.state().offers().size());
        f.driver.post(new SessionEvents.ProjectOutputOfferAnswered(f.watch, f.state().offers().getFirst().id(), false));
        f.scan(20000, src(4, "v2"), staging(2, "v2")); f.scan(24000, src(4, "v2"), staging(2, "v2"));
        assertEquals(1, f.state().offers().size(), "reverting to v2 is offered: a stale build copy kept no receipt alive");
    }

    @Test void aFailedOpenAfterASameBytesRewriteStillLeavesAnOffer() {
        Fixture f = new Fixture(false);
        f.scan(0, src(1, "v1"));
        f.scan(4000, src(2, "v2")); f.scan(8000, src(2, "v2"));
        long offer = f.state().offers().getFirst().id();
        f.adapter.outputOpensPending = true;
        f.driver.post(new SessionEvents.ProjectOutputOfferAnswered(f.watch, offer, true));
        f.scan(12000, src(3, "v2")); f.scan(16000, src(3, "v2"));     // generate.sh again while the open is in flight
        f.driver.post(new SessionEvents.ProjectOutputOpenObserved(0, f.watch, offer, false, "DEMO failure"));
        assertEquals(1, f.state().offers().size(), "the v2 regeneration was never opened: it must still be offered");
    }

    @Test void anIdenticalRewriteAfterASuccessfulOpenRaisesNothing() {
        Fixture f = new Fixture(false);
        f.scan(0, src(1, "v1"));
        f.scan(4000, src(2, "v2")); f.scan(8000, src(2, "v2"));
        long offer = f.state().offers().getFirst().id();
        f.adapter.outputOpensPending = true;
        f.driver.post(new SessionEvents.ProjectOutputOfferAnswered(f.watch, offer, true));
        f.driver.post(new SessionEvents.ProjectOutputOpenObserved(0, f.watch, offer, true, null));
        f.scan(12000, src(3, "v2")); f.scan(16000, src(3, "v2"));
        assertTrue(f.state().offers().isEmpty(), "the opened content is current: rewriting the same bytes raises nothing");
        f.scan(20000, src(4, "v3")); f.scan(24000, src(4, "v3"));
        assertEquals(1, f.state().offers().size(), "new bytes still offer");
    }
}
