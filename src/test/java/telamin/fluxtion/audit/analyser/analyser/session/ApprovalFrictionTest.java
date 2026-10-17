package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.llm.Json;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpTools;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.HostedCallKind;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.PermissionBy;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.ToolClass;
import telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentPermission;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Review M74 usability #10 (2026-10-17): approval friction in Safe analysis, on the REAL generated processor, WITHOUT
 * weakening the model — (a) a write the settings would refuse is refused up front, never asked and then refused; (b) a
 * sub-action that changes nothing stored is allowed without a dialog, every mutating one is still asked; (c) a request
 * arriving while a dialog is open waits, and is still denied if the turn ends or Stop is pressed first. There is no bulk
 * "allow for this turn" (an owner decision, pending).
 */
class ApprovalFrictionTest {

    static SessionEvents.LocalAgentPermissionRequested request(long gen, long ref, String verb, HostedCallKind kind) {
        return new SessionEvents.LocalAgentPermissionRequested(gen, 1, ref, ToolClass.HOSTED_DESTRUCTIVE, verb,
                LocalAgentPermissionTest.INPUT, LocalAgentPermissionTest.TOOL_USE, true, kind);
    }

    /** ★ (a) Witness: drop the WRITES_FILE check in {@code onRequested} — a dialog opens for a write that would be refused. */
    @Test
    @DisplayName("★ (a) with file exchange off, a report or screenshot to a file is refused with the reason, never asked")
    void aFutileWriteIsNeverAsked() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = LocalAgentPermissionTest.ready(d, a);
        d.post(new SessionEvents.AssistantFileExchangeObserved(false));
        d.post(request(start.generation(), 301, "screenshot", HostedCallKind.WRITES_FILE));
        assertEquals(0, a.permissionDialogs.size(), "no dialog for a write the settings refuse");
        var answer = LocalAgentPermissionTest.lastAnswer(a);
        assertFalse(answer.allow());
        assertEquals(PermissionBy.POLICY, answer.by());
        assertEquals(LocalAgentPermission.DENIED_FILE_EXCHANGE, answer.message(), "the model is told why");
        assertTrue(d.snapshot().localAgent().entries().stream().anyMatch(e -> e.detail() != null
                && e.detail().contains("file exchange is off")), "and the person reads why, and where to turn it on");

        d.post(new SessionEvents.AssistantFileExchangeObserved(true));
        d.post(request(start.generation(), 302, "screenshot", HostedCallKind.WRITES_FILE));
        assertEquals(1, a.permissionDialogs.size(), "with file exchange on, the person is asked as before");
        d.post(new SessionEvents.LocalAgentPermissionChosen(302, false));
        d.post(new SessionEvents.AssistantFileExchangeObserved(false));
        d.post(request(start.generation(), 303, "report", HostedCallKind.CHANGES));
        assertEquals(2, a.permissionDialogs.size(), "a report kept in the project writes no file: still asked");
    }

    /**
     * ★ (b) Witness: allow every HOSTED_DESTRUCTIVE request without asking — the delete is allowed with no dialog; or
     * drop the NON_MUTATING branch — the play opens a dialog.
     */
    @Test
    @DisplayName("★ (b) walk {play} is allowed without a dialog and without a grant; walk {delete} is asked")
    void aNonMutatingSubActionIsNotAsked() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = LocalAgentPermissionTest.ready(d, a);
        d.post(request(start.generation(), 311, "walk", HostedCallKind.NON_MUTATING));
        assertEquals(0, a.permissionDialogs.size(), "a play changes nothing stored: no dialog");
        var allowed = LocalAgentPermissionTest.lastAnswer(a);
        assertTrue(allowed.allow());
        assertEquals(PermissionBy.NON_MUTATING, allowed.by(), "recorded as the analyser's own decision, by name");
        assertEquals(0, allowed.grantId(), "no grant: the server admits only non-mutating calls without one");
        assertEquals(0, d.snapshot().permission().liveGrants());

        d.post(request(start.generation(), 312, "walk", HostedCallKind.CHANGES));
        assertEquals(1, a.permissionDialogs.size(), "a delete is asked");
    }

    /** ★ (c) Witness: deny the queued requests nowhere on Stop — the waiting one opens a dialog after the turn was ended. */
    @Test
    @DisplayName("★ (c) a waiting request is denied when Stop is pressed or its turn ends; past the bound it is denied as busy")
    void aWaitingRequestEndsWithItsTurn() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionDriver d = LocalAgentSessionTest.driver(a);
        var start = LocalAgentPermissionTest.ready(d, a);
        long g = start.generation();
        d.post(request(g, 321, "report", HostedCallKind.CHANGES));
        d.post(request(g, 322, "report", HostedCallKind.CHANGES));
        assertEquals(1, d.snapshot().permission().queued());
        d.post(new SessionEvents.LocalAgentStopRequested("test"));
        assertEquals(1, a.permissionDialogs.size(), "Stop: the waiting one is never shown");
        assertTrue(a.permissionAnswers.stream().anyMatch(x -> x.requestRef() == 322 && !x.allow()
                && x.by() == PermissionBy.STOP), "it is denied, by Stop");
        assertEquals(0, d.snapshot().permission().queued());

        FakeSessionAdapter b = new FakeSessionAdapter();
        SessionDriver e = LocalAgentSessionTest.driver(b);
        var s2 = LocalAgentPermissionTest.ready(e, b);
        long g2 = s2.generation();
        e.post(request(g2, 331, "report", HostedCallKind.CHANGES));
        for (int i = 0; i < LocalAgentPermission.MAX_QUEUED; i++) e.post(request(g2, 340 + i, "report", HostedCallKind.CHANGES));
        assertEquals(LocalAgentPermission.MAX_QUEUED, e.snapshot().permission().queued());
        e.post(request(g2, 399, "report", HostedCallKind.CHANGES));
        assertEquals(PermissionBy.BUSY, LocalAgentPermissionTest.lastAnswer(b).by(), "past the bound: denied as busy");
        e.post(new SessionEvents.LocalAgentTurnEnded(g2, 1, SessionEvents.TurnOutcome.COMPLETED));
        assertEquals(0, e.snapshot().permission().queued(), "the turn's end denies every waiting request");
        assertFalse(e.snapshot().permission().waiting());
    }

    /**
     * ★ The classification the adapter and the server share, verified against the code that performs each call.
     * Witness: classify {@code play} by its key alone (ignore the other fields) — the mixed play-and-delete is
     * non-mutating and this fails.
     */
    @Test
    @DisplayName("★ which sub-actions change nothing, which write a file, and that everything else changes something")
    void theClassification() {
        assertEquals(HostedCallKind.NON_MUTATING, kind("walk", "{\"name\":\"x\",\"play\":true}"));
        assertEquals(HostedCallKind.NON_MUTATING, kind("walk", "{\"name\":\"x\",\"play\":true,\"step\":2}"));
        assertEquals(HostedCallKind.NON_MUTATING, kind("walk", "{\"end\":true}"));
        assertEquals(HostedCallKind.NON_MUTATING, kind("walk", "{\"restore\":true}"));
        assertEquals(HostedCallKind.NON_MUTATING, kind("report", "{\"restore\":true}"));
        assertEquals(HostedCallKind.CHANGES, kind("walk", "{\"name\":\"x\",\"play\":true,\"delete\":true}"));
        assertEquals(HostedCallKind.CHANGES, kind("walk", "{\"name\":\"x\",\"play\":false}"));
        assertEquals(HostedCallKind.CHANGES, kind("walk", "{\"name\":\"x\",\"delete\":true}"));
        assertEquals(HostedCallKind.CHANGES, kind("walk", "{\"restore\":\"x\"}"));
        assertEquals(HostedCallKind.CHANGES, kind("walk", "{\"name\":\"x\",\"steps\":[]}"));
        assertEquals(HostedCallKind.CHANGES, kind("report", "{\"name\":\"y\",\"sections\":[]}"));
        assertEquals(HostedCallKind.CHANGES, kind("report", "{\"name\":\"y\",\"delete\":true}"));
        assertEquals(HostedCallKind.WRITES_FILE, kind("report", "{\"name\":\"y\",\"sections\":[],\"path\":\"y.pdf\"}"));
        assertEquals(HostedCallKind.WRITES_FILE, kind("report", "{\"recordIndex\":1,\"path\":\"f.pdf\"}"));
        assertEquals(HostedCallKind.WRITES_FILE, kind("report", "{\"bundle\":{\"path\":\"c.fexp\"}}"));
        assertEquals(HostedCallKind.WRITES_FILE, kind("walk", "{\"name\":\"x\",\"reel\":\"x.html\"}"));
        assertEquals(HostedCallKind.WRITES_FILE, kind("screenshot", "{\"path\":\"s.png\"}"));
        assertEquals(HostedCallKind.CHANGES, kind("open", "{\"log\":\"a.yaml\"}"));
        assertEquals(HostedCallKind.CHANGES, kind("import", "{\"bundle\":\"b.fexp\"}"),
                "an import preview reads a file the model names: kept asked");
        assertEquals(HostedCallKind.CHANGES, kind("source_root", "{}"),
                "listing the source roots shows machine paths: kept asked");
    }

    static HostedCallKind kind(String verb, String params) {
        return McpTools.hostedCallKind(verb, Json.parse(params));
    }
}
