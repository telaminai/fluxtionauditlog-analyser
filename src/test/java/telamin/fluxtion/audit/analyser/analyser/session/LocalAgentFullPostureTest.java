package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture.FullMode;
import telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentSession;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static telamin.fluxtion.audit.analyser.analyser.session.LocalAgentSessionTest.*;

/**
 * M74 P1b (spec-claude-code-provider.md §8.1, §10 P1b) — the full posture's decisions on the REAL generated processor:
 * the consent it starts under, the separate content-bound folder trust, warm start never firing, the full-posture
 * assertion, hook notes, the denial note, PB-3's auto fallback, and the optional budget (owner, 2026-10-17).
 *
 * <p>Witnesses for the ★ rows were run by breaking the node as each comment says and seeing the named assertion fail;
 * they are recorded in the spec's P1b implementation notes.
 */
class LocalAgentFullPostureTest {

    static SessionEvents.LocalAgentConsent full(FullMode mode, boolean defaultConfirmed, boolean fullConfirmed, long budget) {
        return new SessionEvents.LocalAgentConsent(true, defaultConfirmed, false, true,
                LocalAgentPosture.full(mode).ref("/work/proj", "full-note"), budget, SessionEvents.FolderVerdict.OK, true,
                mode, fullConfirmed);
    }

    static SessionEvents.LocalAgentConsent full(FullMode mode) {
        return full(mode, true, true, BUDGET);
    }

    static final String PERSON_SERVER = "db-tools";

    /** Start and bind in the full posture: the person's own server is beside the hosted bridge. */
    static SessionEffects.StartLocalAgentEffect startFull(SessionDriver d, FakeSessionAdapter a, FullMode mode) {
        d.post(new SessionEvents.LocalAgentStartRequested(1, full(mode), false));
        var start = a.agentStarts.get(a.agentStarts.size() - 1);
        d.post(new SessionEvents.LocalAgentSpawned(start.generation(), SessionEvents.SpawnFailure.NONE, 0));
        d.post(new SessionEvents.LocalAgentInitialized(start.generation(), true, mode.cli(), 7,
                SessionEvents.Billing.SUBSCRIPTION));
        d.post(new SessionEvents.LocalAgentStatusObserved(start.generation(), 480,
                List.of(PERSON_SERVER, LocalAgentPosture.HOSTED_SERVER, LocalAgentPosture.PERSONS_REGISTRATION),
                "connected", LocalAgentPosture.digest(start.nonce())));
        return start;
    }

    /** A first-turn init in the full posture: the person's own built-in and MCP tools, and exactly the hosted verbs. */
    static SessionEvents.LocalAgentInitObserved fullInit(SessionEffects.StartLocalAgentEffect start, long ticket,
                                                         String mode, List<String> extraTools, String personState) {
        List<String> tools = new ArrayList<>(List.of("Bash", "Edit", "Write", "Task", "WebFetch", "Read", "Grep", "Glob",
                "mcp__" + PERSON_SERVER + "__query"));
        tools.addAll(LocalAgentPosture.expectedHostedTools());
        tools.addAll(extraTools);
        return new SessionEvents.LocalAgentInitObserved(start.generation(), ticket,
                LocalAgentPosture.digest(start.sessionId()), mode, tools,
                List.of(PERSON_SERVER + ":" + personState, LocalAgentPosture.HOSTED_SERVER + ":connected",
                        LocalAgentPosture.PERSONS_REGISTRATION + ":connected"),
                LocalAgentPosture.REQUIRED_CAPABILITIES, 0);
    }

    // ---- consent, trust and warm start ----------------------------------------------------------------------------------

    @Test
    @DisplayName("the full consent starts the full posture: the effect carries the posture value and the person's mode")
    void theFullConsentStartsTheFullPosture() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        var start = startFull(d, a, FullMode.ACCEPT_EDITS);
        assertEquals(LocalAgentPosture.full(FullMode.ACCEPT_EDITS), start.posture());
        assertEquals("BOUND", state(d).verdict(), "the person's own servers beside the hosted bridge do not refuse it");
        assertEquals(2, state(d).personServers(), "the hosted bridge is not the person's; the registration is");
        assertTrue(state(d).posture().isFull());
    }

    /**
     * ★ OD-74.12: a folder confirmed for the DEFAULT posture is not confirmed for the full one. Witness: decide on
     * {@code c.folderConfirmed()} in {@code startRefusal} (P1a) — the full Start is admitted and the first assertion
     * fails.
     */
    @Test
    @DisplayName("★ a default-posture trust record does not confirm the full posture: the full dialog is asked for, nothing starts")
    void defaultTrustDoesNotConfirmTheFullPosture() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        d.post(new SessionEvents.LocalAgentStartRequested(1, full(FullMode.DEFAULT, true, false, BUDGET), false));
        assertEquals(0, a.agentStarts.size(), "trusted for the default posture only: nothing starts");
        assertEquals(1, a.agentConfirms.size());
        assertTrue(a.agentConfirms.get(0).full(), "the FULL folder dialog, which names the hooks and servers");
        assertEquals("CONFIRM_FOLDER", state(d).verdict());
        assertTrue(state(d).reason().contains("hooks and MCP servers run at Start"), state(d).reason());

        d.post(new SessionEvents.LocalAgentStartRequested(2, full(FullMode.DEFAULT, true, true, BUDGET), false));
        assertEquals(1, a.agentStarts.size(), "confirmed for the full posture (its digest matches): it starts");
    }

    /**
     * ★ OD-74.12: warm start never fires in the full posture. Witness: the P1a predicate alone (drop the
     * {@code WARM_FULL} refusal) — the warm start in a confirmed folder starts a process.
     */
    @Test
    @DisplayName("★ warm start never fires in the full posture, even in a folder confirmed for it, and says nothing")
    void warmStartNeverFiresInTheFullPosture() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        d.post(new SessionEvents.LocalAgentStartRequested(1, full(FullMode.DEFAULT, true, true, BUDGET), true));
        assertEquals(0, a.agentStarts.size(), "a tab click is too weak an act to run hooks on");
        assertEquals(0, a.agentConfirms.size(), "and it raises no dialog");
        assertEquals("", state(d).verdict(), "silent, as every warm refusal");
        d.post(new SessionEvents.LocalAgentStartRequested(2, consent(), true));
        assertEquals(1, a.agentStarts.size(), "the default posture's warm start in a confirmed folder still starts");
    }

    /**
     * ★ §3: a posture change needs New chat. Witness: leave the mode out of {@code LocalAgentPosture.ref} — the two
     * modes' refs are equal and the second assertion fails.
     */
    @Test
    @DisplayName("★ postureRef differs between the postures and between the modes, so switching either offers New chat")
    void thePostureRefCoversKindAndMode() {
        String folder = "/work/proj";
        String note = "note";
        String def = LocalAgentPosture.ANALYSER_ASSISTANT.ref(folder, note);
        java.util.Set<String> refs = new java.util.HashSet<>(List.of(def));
        for (FullMode m : FullMode.values()) {
            assertTrue(refs.add(LocalAgentPosture.full(m).ref(folder, note)), "full " + m + " has its own ref");
        }
        assertNotEquals(LocalAgentPosture.full(FullMode.DEFAULT).ref(folder, note),
                LocalAgentPosture.full(FullMode.AUTO).ref(folder, note), "the mode is in the digest");

        // and the node acts on it: a resumable conversation under one mode is never resumed under another
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        var start = startFull(d, a, FullMode.DEFAULT);
        d.post(fullInit(start, 0, "default", List.of(), "connected"));
        d.post(new SessionEvents.LocalAgentSendRequested(10, 10, SessionEvents.SlashClass.TEXT, null, full(FullMode.DEFAULT)));
        var sent = a.agentSends.get(0);
        d.post(new SessionEvents.LocalAgentTurnStarted(sent.generation(), sent.ticket()));
        d.post(new SessionEvents.LocalAgentTurnEnded(sent.generation(), sent.ticket(), SessionEvents.TurnOutcome.COMPLETED));
        d.post(new SessionEvents.LocalAgentStopRequested("test"));
        d.post(new SessionEvents.AssistantModeChosen(AssistantMode.IN_APP, "test"));     // stops the process
        d.post(new SessionEvents.AssistantModeChosen(AssistantMode.LOCAL_AGENT, "test"));
        d.post(new SessionEvents.LocalAgentStartRequested(3, full(FullMode.PLAN), false));
        assertEquals("POSTURE_CHANGED", state(d).verdict(), "another mode: New chat, never --resume");
        assertTrue(state(d).newChatOffered());
        assertEquals(1, a.agentStarts.size());
    }

    // ---- the handshake and the assertion ------------------------------------------------------------------------------

    @Test
    @DisplayName("PB-3: auto falling back to default at initialize is MODE_MISMATCH, named for what the evidence showed")
    void autoFallingBackToDefaultIsRefusedByName() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        d.post(new SessionEvents.LocalAgentStartRequested(1, full(FullMode.AUTO), false));
        var start = a.agentStarts.get(0);
        assertEquals("auto", start.posture().permissionMode());
        d.post(new SessionEvents.LocalAgentSpawned(1, SessionEvents.SpawnFailure.NONE, 0));
        d.post(new SessionEvents.LocalAgentInitialized(1, true, "default", 7, SessionEvents.Billing.SUBSCRIPTION));
        assertEquals("MODE_MISMATCH", state(d).verdict());
        assertEquals(LocalAgentSession.AUTO_UNAVAILABLE_TEXT, state(d).reason());
        assertEquals(1, kills(a).size(), "stopped before any turn");
    }

    /**
     * ★ §4.4, full rules. Witnesses: (1) compare the WHOLE tool list as the default posture does — the first init, which
     * carries the person's own tools, is refused; (2) ignore the person's registration — the registration init is
     * admitted; (3) refuse on a change among the person's servers — the diagnostics init stops the session.
     */
    @Test
    @DisplayName("★ full assertion: the person's tools pass; their registration's tools, a wrong hosted set and bypassPermissions kill; their servers changing is a diagnostics line")
    void theFullPostureAssertion() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        var start = startFull(d, a, FullMode.DEFAULT);
        d.post(fullInit(start, 0, "default", List.of(), "failed"));
        assertEquals(start.generation(), state(d).admittedGeneration(),
                "the person's own Bash, Edit, Task and server tools are theirs: the session is admitted");
        assertTrue(notes(d).stream().anyMatch(n -> n.contains("your MCP server " + PERSON_SERVER + " failed to connect")),
                "a person's server that failed is a status line, not a refusal: " + notes(d));
        d.post(fullInit(start, 0, "default", List.of(), "connected"));
        assertEquals(0, kills(a).size(), "a change among the person's servers never stops the session");
        assertTrue(notes(d).stream().anyMatch(n -> n.contains("your own MCP servers changed")), "it is a diagnostics line");

        // the person's own fluxtion-analyser registration is never this analyser
        d.post(fullInit(start, 0, "default", List.of("mcp__fluxtion-analyser__analyser_open"), "connected"));
        assertEquals("TOOLS_MISMATCH", state(d).verdict());
        assertEquals(1, kills(a).size(), "killed at once");

        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = driver(b);
        var s2 = startFull(e, b, FullMode.DEFAULT);
        List<String> wrong = new ArrayList<>(List.of("Bash", LocalAgentPosture.toolName("open")));
        e.post(new SessionEvents.LocalAgentInitObserved(s2.generation(), 0, LocalAgentPosture.digest(s2.sessionId()),
                "default", wrong, List.of(LocalAgentPosture.HOSTED_SERVER + ":connected"),
                LocalAgentPosture.REQUIRED_CAPABILITIES, 0));
        assertEquals("TOOLS_MISMATCH", state(e).verdict(), "a destructive hosted verb visible: the analyser's set is wrong");

        FakeSessionAdapter c = new FakeSessionAdapter();
        SessionDriver f = driver(c);
        var s3 = startFull(f, c, FullMode.ACCEPT_EDITS);
        f.post(fullInit(s3, 0, LocalAgentPosture.BYPASS, List.of(), "connected"));
        assertEquals("MODE_MISMATCH", state(f).verdict());
        assertTrue(state(f).reason().contains("bypassPermissions"), state(f).reason());
        assertEquals(1, kills(c).size(), "bypassPermissions observed, in any posture, kills at once");

        FakeSessionAdapter g = new FakeSessionAdapter();
        SessionDriver h = driver(g);
        var s4 = startFull(h, g, FullMode.DEFAULT);
        h.post(new SessionEvents.LocalAgentInitObserved(s4.generation(), 0, LocalAgentPosture.digest(s4.sessionId()),
                "default", new ArrayList<>(LocalAgentPosture.expectedHostedTools()),
                List.of(LocalAgentPosture.HOSTED_SERVER + ":failed", PERSON_SERVER + ":connected"),
                LocalAgentPosture.REQUIRED_CAPABILITIES, 0));
        assertEquals("SERVERS_MISMATCH", state(h).verdict(), "the hosted bridge disconnected: the session stops");
        assertEquals(1, kills(g).size());
    }

    @Test
    @DisplayName("bypassPermissions observed at initialize is MODE_MISMATCH in the default posture too")
    void bypassAtInitializeInTheDefaultPosture() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        d.post(new SessionEvents.LocalAgentStartRequested(1, consent(), false));
        d.post(new SessionEvents.LocalAgentSpawned(1, SessionEvents.SpawnFailure.NONE, 0));
        d.post(new SessionEvents.LocalAgentInitialized(1, true, LocalAgentPosture.BYPASS, 7));
        assertEquals("MODE_MISMATCH", state(d).verdict());
        assertTrue(state(d).reason().contains("never runs"), state(d).reason());
    }

    // ---- notes ----------------------------------------------------------------------------------------------------------

    /**
     * ★ hook status lines (node half; the adapter half replays the PB fixture). Witness: add a note per fact instead of
     * coalescing — three notes appear for SessionStart and the count assertion fails.
     */
    @Test
    @DisplayName("★ hooks: one note per event, coalesced, naming the event and the outcome; a failure says so")
    void hookNotesAreCoalescedPerEvent() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        d.post(new SessionEvents.LocalAgentStartRequested(1, full(FullMode.DEFAULT), false));
        long g = a.agentStarts.get(0).generation();
        d.post(new SessionEvents.LocalAgentHookObserved(g, 0, SessionEvents.HookEvent.SessionStart, SessionEvents.HookOutcome.STARTED));
        d.post(new SessionEvents.LocalAgentHookObserved(g, 0, SessionEvents.HookEvent.SessionStart, SessionEvents.HookOutcome.STARTED));
        d.post(new SessionEvents.LocalAgentHookObserved(g, 0, SessionEvents.HookEvent.SessionStart, SessionEvents.HookOutcome.FAILED));
        d.post(new SessionEvents.LocalAgentHookObserved(g, 0, SessionEvents.HookEvent.SessionStart, SessionEvents.HookOutcome.OK));
        List<LocalAgentState.Entry> hooks = state(d).entries().stream()
                .filter(e -> e.status().startsWith("HOOK")).toList();
        assertEquals(1, hooks.size(), "one note for the event, however many hooks ran: " + hooks);
        assertEquals("HOOK_FAILED", hooks.get(0).status());
        assertEquals("Hook SessionStart: 1 ran, 1 failed — the hook is yours or the folder's: check it in Claude Code's "
                + "own hook settings", hooks.get(0).detail());
        d.post(new SessionEvents.LocalAgentHookObserved(g + 7, 0, SessionEvents.HookEvent.PreToolUse,
                SessionEvents.HookOutcome.OK));
        assertEquals(1, state(d).entries().stream().filter(e -> e.status().startsWith("HOOK")).count(),
                "a hook of another generation is stale");
    }

    @Test
    @DisplayName("PB-1: in the full posture a request nobody could approve is denied with words saying approvals come later")
    void approvalUnavailableNote() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        var start = startFull(d, a, FullMode.DEFAULT);
        d.post(fullInit(start, 0, "default", List.of(), "connected"));
        d.post(new SessionEvents.LocalAgentNoted(start.generation(), 0, SessionEvents.AgentNote.APPROVAL_UNAVAILABLE, 0));
        assertTrue(notes(d).contains(LocalAgentSession.APPROVAL_UNAVAILABLE_TEXT), notes(d).toString());
        d.post(new SessionEvents.LocalAgentNoted(start.generation(), 0, SessionEvents.AgentNote.PERMISSION_DENIED, 0));
        assertTrue(notes(d).stream().anyMatch(n -> n.contains("your Claude Code permission rules")), notes(d).toString());
    }

    // ---- the optional budget (owner, 2026-10-17) -------------------------------------------------------------------------

    /**
     * ★ No limit by default: no remainder is passed and a spend never refuses Start. Witness: treat budget 0 as a spent
     * limit (P1a's {@code remaining <= 0}) — Start is refused BUDGET_SPENT and no process starts.
     */
    @Test
    @DisplayName("★ with no spend limit set, Start passes no budget and is never refused for spending")
    void noLimitByDefault() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = driver(a);
        d.post(new SessionEvents.LocalAgentStartRequested(1, full(FullMode.DEFAULT, true, true, 0), false));
        assertEquals(1, a.agentStarts.size(), "no limit: it starts");
        assertEquals(0, a.agentStarts.get(0).maxBudgetMicros(), "and the command line carries no --max-budget-usd");
        var start = a.agentStarts.get(0);
        d.post(new SessionEvents.LocalAgentSpawned(start.generation(), SessionEvents.SpawnFailure.NONE, 0));
        d.post(new SessionEvents.LocalAgentInitialized(start.generation(), true, "default", 7, SessionEvents.Billing.API_KEY));
        d.post(new SessionEvents.LocalAgentCostObserved(start.generation(), 99_000_000));
        assertFalse(state(d).budgetLimited());
        assertEquals(SessionEvents.Billing.API_KEY, state(d).billing(), "billing is the node's, from the initialize fact");
        d.post(new SessionEvents.LocalAgentStopRequested("test"));
        d.post(new SessionEvents.AssistantModeChosen(AssistantMode.IN_APP, "test"));
        d.post(new SessionEvents.AssistantModeChosen(AssistantMode.LOCAL_AGENT, "test"));
        d.post(new SessionEvents.LocalAgentStartRequested(2, full(FullMode.DEFAULT, true, true, 0), false));
        assertNotEquals("BUDGET_SPENT", state(d).verdict(), "US$99 spent and no limit: never refused for spending");
        assertEquals(2, a.agentStarts.size());

        // a limit set: the remainder is passed, and spending it refuses
        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = driver(b);
        e.post(new SessionEvents.LocalAgentStartRequested(1, full(FullMode.DEFAULT, true, true, 2_000_000), false));
        assertEquals(2_000_000, b.agentStarts.get(0).maxBudgetMicros());
        e.post(new SessionEvents.LocalAgentCostObserved(1, 2_000_000));
        e.post(new SessionEvents.AssistantModeChosen(AssistantMode.IN_APP, "test"));
        e.post(new SessionEvents.AssistantModeChosen(AssistantMode.LOCAL_AGENT, "test"));
        e.post(new SessionEvents.LocalAgentStartRequested(2, full(FullMode.DEFAULT, true, true, 2_000_000), false));
        assertEquals("BUDGET_SPENT", state(e).verdict());
    }

    static List<String> notes(SessionDriver d) {
        return state(d).entries().stream().filter(e -> LocalAgentState.NOTE.equals(e.kind()))
                .map(LocalAgentState.Entry::detail).toList();
    }
}
