package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.EndpointLine;
import telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentSession;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** M74 P2b (spec §8.2 (h), §5.2, §4.5, §5.9): the carried items the node decides, on the real processor. */
class LocalAgentCarriedItemsTest {

    /** ★ Witness: print the variable (return baseUrl) — the user info, path and query appear. */
    @Test
    @DisplayName("★ the endpoint line: scheme://host[:port] only — never user info, a path or a query")
    void endpointLine() {
        assertEquals("https://proxy.example:8443", EndpointLine.of("https://u:p@proxy.example:8443/v1?k=x"));
        assertEquals("https://api.example", EndpointLine.of("https://API.example/anthropic"));
        assertNull(EndpointLine.of(null));
        assertNull(EndpointLine.of(" "));
        assertEquals("an address the analyser could not read", EndpointLine.of("not a url"));
    }

    @Test
    @DisplayName("the diagnostics counts are the node's, per generation; a stale generation's are refused")
    void diagnostics() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = LocalAgentSessionTest.startToReady(d, a);
        assertFalse(d.snapshot().localAgent().extra().diagnostics(), "nothing shown when all counts are zero");
        d.post(new SessionEvents.LocalAgentDiagnostics(start.generation(), 3, 1, 0));
        var x = d.snapshot().localAgent().extra();
        assertEquals(3, x.unknownEvents());
        assertEquals(1, x.unclassifiedCommands());
        assertTrue(x.diagnostics());
        d.post(new SessionEvents.LocalAgentDiagnostics(start.generation() + 5, 9, 9, 9));
        assertEquals(3, d.snapshot().localAgent().extra().unknownEvents(), "another generation's counts are stale");
    }

    /** ★ Witness: sum the totals (cost = total) — the second turn's cost is the running total, not its own. */
    @Test
    @DisplayName("★ a turn's cost is the difference of successive totals; its time is the result's")
    void perTurnCostAndTime() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = LocalAgentSessionTest.startToReady(d, a);
        d.post(new SessionEvents.LocalAgentCostObserved(start.generation(), 100_000, 1, 4_200));
        d.post(new SessionEvents.LocalAgentCostObserved(start.generation(), 250_000, 2, 1_500));
        var turns = d.snapshot().localAgent().extra().turns();
        assertEquals(new LocalAgentState.Turn(100_000, 4_200), turns.get(1L));
        assertEquals(new LocalAgentState.Turn(150_000, 1_500), turns.get(2L));
    }

    /** ★ Witness: drop trim() from changed() — the 501st entry keeps the first and there is no note. */
    @Test
    @DisplayName("★ the node keeps 500 entries: the 501st trims the first, with one note that says how many")
    void entriesAreTrimmed() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = LocalAgentSessionTest.startToReady(d, a);
        int before = d.snapshot().localAgent().entries().size();
        for (int i = 0; i < LocalAgentSession.MAX_ENTRIES - before; i++) {
            d.post(new SessionEvents.LocalAgentNoted(start.generation(), 0, SessionEvents.AgentNote.COMPACTED, 0));
        }
        List<LocalAgentState.Entry> full = d.snapshot().localAgent().entries();
        assertEquals(LocalAgentSession.MAX_ENTRIES, full.size());
        assertNotEquals("TRIMMED", full.get(0).status());
        d.post(new SessionEvents.LocalAgentNoted(start.generation(), 0, SessionEvents.AgentNote.COMPACTED, 0));
        var trimmed = d.snapshot().localAgent().entries();
        assertEquals(LocalAgentSession.MAX_ENTRIES + 1, trimmed.size(), "500 entries and the one note");
        assertEquals("TRIMMED", trimmed.get(0).status());
        assertEquals("1 earlier entries are not shown", trimmed.get(0).detail());
        d.post(new SessionEvents.LocalAgentNoted(start.generation(), 0, SessionEvents.AgentNote.COMPACTED, 0));
        var again = d.snapshot().localAgent().entries();
        assertEquals(LocalAgentSession.MAX_ENTRIES + 1, again.size());
        assertEquals("2 earlier entries are not shown", again.get(0).detail(), "still one note");
        assertEquals(2, d.snapshot().localAgent().extra().trimmed());
    }

    /** ★ Witness: refuse nothing (accept an EXPOSES_SECRETS folder) — the home folder becomes the set-up folder. */
    @Test
    @DisplayName("★ the set-up folder: a usable one is held for the next spawn; one that reaches credentials is refused")
    void setupDirectory() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        FactRefs refs = new FactRefs();
        d.post(new SessionEvents.LocalAgentSetupDirectoryChosen(1, refs.put("/home/DEMO"), "…/DEMO",
                SessionEvents.FolderVerdict.EXPOSES_SECRETS));
        assertEquals(0, d.snapshot().localAgent().extra().setupDirRef());
        assertEquals("FOLDER_EXPOSES_SECRETS", d.snapshot().localAgent().verdict());
        d.post(new SessionEvents.LocalAgentSetupDirectoryChosen(2, refs.put("/work/medmon"), "…/medmon",
                SessionEvents.FolderVerdict.OK));
        assertEquals("/work/medmon", refs.get(d.snapshot().localAgent().extra().setupDirRef()));
        assertEquals("…/medmon", d.snapshot().localAgent().extra().setupDirShown());
        // with a process running it is a posture change: New chat is offered
        LocalAgentSessionTest.startToReady(d, a);
        d.post(new SessionEvents.LocalAgentSetupDirectoryChosen(3, refs.put("/work/other"), "…/other",
                SessionEvents.FolderVerdict.OK));
        assertTrue(d.snapshot().localAgent().newChatOffered());
        assertEquals("SETUP_DIRECTORY", d.snapshot().localAgent().verdict());
    }
}
