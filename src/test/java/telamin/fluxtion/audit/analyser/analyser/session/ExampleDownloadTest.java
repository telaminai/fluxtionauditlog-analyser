package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.assistant.ExampleSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M74 P2b (owner request 2026-10-17, with the owner's addition): an example entry's download, each step decided by
 * {@code exampleDownload} on the real processor — confirm, fetch, verify, unpack, the M73 offer.
 */
class ExampleDownloadTest {

    /** R6: the folders a fact names live here, by reference, as they do in the frame. */
    static final FactRefs REFS = new FactRefs();

    static final String C = "0123456789abcdef0123456789abcdef01234567";
    static final String DIGEST = "c".repeat(64);

    static ExampleSource source(String sha) {
        return ExampleSource.parse("https://github.com/example/demo/archive/" + C + ".zip", sha).source();
    }

    static ExampleDownloadState state(SessionDriver d) {
        return d.snapshot().example();
    }

    /** ★ Witness: request FetchExampleEffect in onRequested — a fetch happens before the confirmation. */
    @Test
    @DisplayName("★ the button asks for the confirmation only; nothing is fetched until the person says yes")
    void nothingIsFetchedBeforeTheConfirmation() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        d.post(new SessionEvents.ExampleDownloadRequested(1, 5, 1, source(null)));
        assertEquals(1, a.exampleConfirms.size());
        assertTrue(a.exampleConfirms.get(0).thirdParty(), "example/demo is not Telamin's");
        assertTrue(a.exampleFetches.isEmpty());
        assertEquals("CONFIRMING", state(d).phase());
        d.post(new SessionEvents.ExampleDownloadConfirmed(1, false, 0, null, null));
        assertEquals("CANCELLED", state(d).phase());
        assertTrue(a.exampleFetches.isEmpty(), "No: nothing is fetched");
    }

    @Test
    @DisplayName("a target that is not empty is refused, by name, before anything is fetched")
    void aPopulatedTargetIsRefused() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        d.post(new SessionEvents.ExampleDownloadRequested(1, 5, 1, source(null)));
        d.post(new SessionEvents.ExampleDownloadConfirmed(1, true, REFS.put("/work/demo"), FactWords.shown("/work/demo"), "EXISTS_NOT_EMPTY"));
        assertEquals("REFUSED", state(d).phase());
        assertTrue(state(d).note().contains("not empty"), state(d).note());
        assertTrue(a.exampleFetches.isEmpty());
    }

    /** ★ Witness: unpack whatever the digest (drop the sha256 comparison in onFetched) — the mismatch is unpacked. */
    @Test
    @DisplayName("★ a digest mismatch is refused with a named reason and the bytes discarded; nothing is unpacked")
    void aDigestMismatchIsRefused() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        d.post(new SessionEvents.ExampleDownloadRequested(1, 5, 1, source(DIGEST)));
        d.post(new SessionEvents.ExampleDownloadConfirmed(1, true, REFS.put("/work/demo"), FactWords.shown("/work/demo"), null));
        assertEquals(1, a.exampleFetches.size());
        d.post(new SessionEvents.ExampleFetched(1, true, 9, "d".repeat(64), 100, null));
        assertEquals("REFUSED", state(d).phase());
        assertTrue(state(d).note().contains("SHA-256") && state(d).note().contains(DIGEST), state(d).note());
        assertTrue(a.exampleUnpacks.isEmpty(), "never unpacked");
        assertEquals(9, a.exampleDiscards.get(0).bytesRef(), "the bytes are discarded");
    }

    /** ★ Witness: skip the offer (no OfferExampleProjectEffect in onUnpacked) — the project-offer assertion fails. */
    @Test
    @DisplayName("★ a matching digest is unpacked into the chosen folder, then the M73 project offer opens it")
    void aMatchIsUnpackedAndOffered() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        d.post(new SessionEvents.ExampleDownloadRequested(1, 5, 1, source(DIGEST)));
        d.post(new SessionEvents.ExampleDownloadConfirmed(1, true, REFS.put("/work/demo"), FactWords.shown("/work/demo"), null));
        d.post(new SessionEvents.ExampleFetched(1, true, 9, DIGEST.toUpperCase(), 100, null));
        assertEquals(1, a.exampleUnpacks.size());
        assertEquals("/work/demo", REFS.get(a.exampleUnpacks.get(0).targetRef()));
        d.post(new SessionEvents.ExampleUnpacked(1, true, REFS.put("/work/demo"), FactWords.shown("/work/demo"), null));
        assertEquals("DONE", state(d).phase());
        assertEquals(1, a.exampleOffers.size());
        assertEquals("/work/demo", REFS.get(a.exampleOffers.get(0).rootRef()));
        assertTrue(state(d).note().contains("nothing was built or run"));
        assertTrue(a.agentConfirms.isEmpty(), "a download never asks — or implies — Claude Code folder trust");
        assertTrue(a.agentTrusts.isEmpty());
    }

    @Test
    @DisplayName("an entry with no digest is unpacked on its pinned commit alone; a stale fact is refused")
    void noDigestAndStaleFacts() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        d.post(new SessionEvents.ExampleDownloadRequested(1, 5, 1, source(null)));
        d.post(new SessionEvents.ExampleFetched(1, true, 9, "e".repeat(64), 100, null));
        assertTrue(a.exampleUnpacks.isEmpty(), "a fetch before the confirmation is stale");
        d.post(new SessionEvents.ExampleDownloadConfirmed(1, true, REFS.put("/work/demo"), FactWords.shown("/work/demo"), null));
        d.post(new SessionEvents.ExampleFetched(1, true, 9, "e".repeat(64), 100, null));
        assertEquals(1, a.exampleUnpacks.size());
        // a second press while busy is refused
        d.post(new SessionEvents.ExampleDownloadRequested(2, 5, 1, source(null)));
        assertEquals(1, a.exampleConfirms.size());
        assertTrue(state(d).note().contains("already in progress"));
    }

    /** ★ Witness: trust the source the fact carries (drop the re-parse in onRequested) — the moving ref is confirmed. */
    @Test
    @DisplayName("★ the node checks the source again: a moving ref or a host off the list is refused before any question")
    void theNodeChecksTheSourceAgain() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        var moving = new ExampleSource("https://github.com/example/demo/archive/main.zip", "github.com", "example",
                "demo", "main0000000000000000000000000000000000000", null);
        d.post(new SessionEvents.ExampleDownloadRequested(1, 5, 1, moving));
        assertEquals("REFUSED", state(d).phase());
        assertTrue(a.exampleConfirms.isEmpty());
        var host = new ExampleSource("https://evil.example/example/demo/archive/" + C + ".zip", "evil.example", "example",
                "demo", C, null);
        d.post(new SessionEvents.ExampleDownloadRequested(2, 5, 1, host));
        assertEquals("REFUSED", state(d).phase());
        assertTrue(state(d).note().contains("host"), state(d).note());
        assertTrue(a.exampleConfirms.isEmpty());
    }

    /**
     * ★ Review S7 (P2, 2026-10-17): a fetch that never answers is FAILED at its deadline, and a late answer is ignored
     * with its bytes discarded. Witness: drop the StartExampleClockEffect request in onConfirmed — no clock, and the
     * phase stays FETCHING for ever.
     */
    @Test
    @DisplayName("★ a stalled fetch fails at its deadline; its late answer is ignored and its bytes discarded")
    void aStalledFetchFailsAtItsDeadline() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        d.post(new SessionEvents.ExampleDownloadRequested(1, 5, 1, source(null)));
        d.post(new SessionEvents.ExampleDownloadConfirmed(1, true, REFS.put("/work/demo"), FactWords.shown("/work/demo"), null));
        assertEquals("FETCHING", state(d).phase());
        assertEquals(1, a.exampleClocks.size(), "a deadline is started with the fetch");
        assertEquals("FETCHING", a.exampleClocks.get(0).phase());
        d.post(new SessionEvents.ExampleDeadlinePassed(1, "FETCHING"));
        assertEquals("FAILED", state(d).phase());
        assertTrue(state(d).note().contains("did not arrive in time"), state(d).note());
        d.post(new SessionEvents.ExampleFetched(1, true, 77, DIGEST, 10, null));
        assertEquals("FAILED", state(d).phase(), "the late answer is stale");
        assertTrue(a.exampleUnpacks.isEmpty(), "nothing is unpacked");
        assertEquals(77, a.exampleDiscards.get(a.exampleDiscards.size() - 1).bytesRef(), "its bytes are not kept");
        d.post(new SessionEvents.ExampleDownloadRequested(2, 5, 1, source(null)));
        assertEquals("CONFIRMING", state(d).phase(), "the entry is not left busy");
    }

    /** ★ Review S7: a deadline for a phase already left is ignored. Witness: drop the stale check in onDeadline. */
    @Test
    @DisplayName("★ a deadline that arrives after the fetch answered changes nothing")
    void aLateDeadlineIsIgnored() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        d.post(new SessionEvents.ExampleDownloadRequested(1, 5, 1, source(null)));
        d.post(new SessionEvents.ExampleDownloadConfirmed(1, true, REFS.put("/work/demo"), FactWords.shown("/work/demo"), null));
        d.post(new SessionEvents.ExampleFetched(1, true, 77, DIGEST, 10, null));
        assertEquals("UNPACKING", state(d).phase());
        d.post(new SessionEvents.ExampleDeadlinePassed(1, "FETCHING"));
        assertEquals("UNPACKING", state(d).phase());
        d.post(new SessionEvents.ExampleDeadlinePassed(1, "UNPACKING"));
        assertEquals("FAILED", state(d).phase(), "the unpack's own deadline does fail it");
        assertTrue(state(d).note().contains("did not finish in time"), state(d).note());
    }

    /**
     * ★ Review S7: the confirmation performer that throws answers CONFIRM_FAILED, and the node leaves CONFIRMING with
     * its words. Witness: drop the CONFIRM_FAILED case in targetWords — the note names a raw code.
     */
    @Test
    @DisplayName("★ a confirmation that could not be shown is refused by name, never left CONFIRMING")
    void aFailedConfirmationIsRefused() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = new SessionDriver(a);
        d.post(new SessionEvents.ExampleDownloadRequested(1, 5, 1, source(null)));
        d.post(new SessionEvents.ExampleDownloadConfirmed(1, true, 0, null, "CONFIRM_FAILED"));
        assertEquals("REFUSED", state(d).phase());
        assertEquals("Nothing was downloaded: the confirmation could not be shown.", state(d).note());
    }
}
