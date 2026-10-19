package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.llm.Json;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Real adapter, generated permission node and fixture process; no script or model is executed. */
class LocalAgentScriptTrustReplayTest {
    @TempDir Path home;

    private interface Prepare { void apply(Path project) throws Exception; }

    private Harness running(boolean trusted) throws Exception { return running(trusted, project -> { }); }

    private Harness running(boolean trusted, Prepare prepare) throws Exception {
        var agent = new FixtureAgent("multiturn-partial-replay-midturn.jsonl");
        agent.conformInit = true;
        agent.rewrite = line -> {
            if (line.contains("command_lifecycle") && line.contains("completed") || line.contains("\"type\": \"result\"")) return null;
            return line.replace("\"permissionMode\": \"dontAsk\"", "\"permissionMode\": \"default\"")
                    .replace("\"permissionMode\":\"dontAsk\"", "\"permissionMode\":\"default\"")
                    .replace("\"current_permission_mode\": \"dontAsk\"", "\"current_permission_mode\": \"default\"")
                    .replace("\"current_permission_mode\":\"dontAsk\"", "\"current_permission_mode\":\"default\"");
        };
        var h = new Harness(agent, home);
        h.scriptProject = home.resolve("proj");
        Files.writeString(h.scriptProject.resolve("mvnw"), "DEMO wrapper A");
        Files.writeString(h.scriptProject.resolve("deploy.sh"), "DEMO undeclared");
        if (trusted) h.scriptTrust = List.of(TrustedProjectScripts.record(TrustedProjectScripts.scan(h.scriptProject)));
        prepare.apply(h.scriptProject);
        h.consent = consent(h, trusted, false);
        h.start();
        assertEquals("READY", h.state().phase(), h.state().reason());
        h.send("Check the DEMO project build.");
        assertEquals("BUSY", h.state().phase(), h.state().reason());
        h.others.clear();
        return h;
    }

    private static SessionEvents.LocalAgentConsent consent(Harness h, boolean trusted, boolean changed) {
        var old = h.fullConsent(LocalAgentPosture.FullMode.DEFAULT);
        return new SessionEvents.LocalAgentConsent(old.allowed(), old.folderConfirmed(), false, old.postureRef(), 0,
                old.folder(), true, old.fullMode(), true, old.model(), LocalAgentPosture.digest(ClaudeCodeProvider.realPath(h.scriptProject)),
                trusted, changed, true, trusted ? 1 : 0);
    }

    private static void request(Harness h, String id, String command) {
        h.agent.last().emit(Json.write(Map.of("type", "control_request", "request_id", id, "request",
                Map.of("subtype", "can_use_tool", "tool_name", "Bash", "input", Map.of("command", command),
                        "tool_use_id", "DEMO-" + id))));
    }

    private static long dialogs(Harness h) {
        return h.others.stream().filter(SessionEffects.ShowPermissionDialogEffect.class::isInstance).count();
    }

    private static <T> List<T> results(Harness h, Class<T> type) {
        return h.results.stream().filter(type::isInstance).map(type::cast).toList();
    }

    @Test
    void aTickedFullProjectsOwnScriptIsAllowedOnlyByTheAnalyser() throws Exception {
        var h = running(true);
        request(h, "DEMO-trusted", "./mvnw test");
        assertEquals(0, dialogs(h), "★ B1: the explicitly trusted script is unasked");
        var answer = results(h, SessionEvents.LocalAgentPermissionAnswered.class).getLast();
        assertTrue(answer.allow() && answer.written());
        assertEquals(SessionEvents.PermissionBy.PRE_ALLOWED, answer.by());
        assertEquals(1, results(h, SessionEvents.LocalAgentScriptTrustChecked.class).size(), "one live check for this call");
        var command = h.agent.commands.getFirst();
        int allowed = command.indexOf("--allowedTools");
        assertTrue(allowed < 0 || !command.get(allowed + 1).contains("Bash") && !command.get(allowed + 1).contains("mvnw"),
                "no CLI allowedTools rule bypasses the node (the unchanged system note still names ./mvnw)");
        request(h, "DEMO-trusted-2", "./mvnw clean install -q");
        assertEquals(2, results(h, SessionEvents.LocalAgentScriptTrustChecked.class).size(), "EVERY eligible call rechecks");
        assertEquals(0, dialogs(h));
    }

    @Test
    void anUntickedProjectAsksExactlyAsBefore() throws Exception {
        var h = running(false);
        request(h, "DEMO-unticked", "./mvnw test");
        assertEquals(1, dialogs(h), "★ B2: unticked build script asks");
        assertTrue(results(h, SessionEvents.LocalAgentPermissionAnswered.class).isEmpty(), "no silent allow");
    }

    @Test
    void anUndeclaredProgramAndASecondCommandAlwaysAsk() throws Exception {
        var h = running(true);
        for (String command : List.of("./deploy.sh", "./mvnw test; echo pwned")) {
            request(h, "DEMO-other-" + h.facts.size(), command);
            assertEquals(1, dialogs(h), "★ B3: another program or compound command asks");
            long ref = h.driver.snapshot().permission().pendingRef();
            h.driver.post(new SessionEvents.LocalAgentPermissionChosen(ref, false));
            h.others.clear();
        }
    }

    @Test
    void aChangedByteAsksInTheSameRunningSessionThenSendReasksAboutTrust() throws Exception {
        var h = running(true);
        request(h, "DEMO-before", "./mvnw test");
        Files.writeString(h.scriptProject.resolve("mvnw"), "DEMO wrapper B");
        request(h, "DEMO-after", "./mvnw test");
        assertEquals(1, dialogs(h), "★ B4: the very next call asks after one changed byte");
        h.driver.post(new SessionEvents.LocalAgentPermissionChosen(h.driver.snapshot().permission().pendingRef(), false));
        h.driver.post(new SessionEvents.LocalAgentTurnEnded(h.state().generation(), h.state().openTicket(), SessionEvents.TurnOutcome.COMPLETED));
        h.consent = consent(h, false, true);
        h.send("Check the DEMO project again.");
        assertTrue(h.others.stream().anyMatch(SessionEffects.ConfirmLocalAgentFolderEffect.class::isInstance),
                "★ B4: next Send re-asks while preserving this conversation");
        assertEquals(1, h.agent.commands.size(), "review does not secretly restart the process");
        assertTrue(h.state().reason().contains("build scripts changed"));
        var review = h.others.stream().filter(SessionEffects.ConfirmLocalAgentFolderEffect.class::isInstance)
                .map(SessionEffects.ConfirmLocalAgentFolderEffect.class::cast).toList().getLast();
        h.driver.post(new SessionEvents.LocalAgentFolderAnswered(review.request(), true, true, h.consent, false, 1));
        assertEquals("Check the DEMO project again.", h.userTurns().getLast(), "the reviewed Send is not lost");
        assertEquals(1, h.userTurns().stream().filter("Check the DEMO project again."::equals).count());
        assertEquals(1, h.agent.commands.size(), "review keeps the old process and capture");
    }

    @Test
    void liveRevocationAndRegrantCannotWidenAnExistingCapture() throws Exception {
        var h = running(true);
        h.scriptTrust = List.of();
        request(h, "DEMO-revoked", "./mvnw test");
        assertEquals(1, dialogs(h), "revocation takes effect before the next call");
        h.driver.post(new SessionEvents.LocalAgentPermissionChosen(h.driver.snapshot().permission().pendingRef(), false));
        Files.writeString(h.scriptProject.resolve("mvnw"), "DEMO wrapper B");
        h.scriptTrust = List.of(TrustedProjectScripts.record(TrustedProjectScripts.scan(h.scriptProject)));
        h.others.clear();
        request(h, "DEMO-new-grant", "./mvnw test");
        assertEquals(1, dialogs(h), "new content trust applies only to a new conversation");
    }

    @Test
    void aChangeAfterClassificationIsCaughtByTheNodesFreshRead() throws Exception {
        var h = running(true);
        h.beforeScriptCheck = e -> {
            try { Files.writeString(h.scriptProject.resolve("mvnw"), "DEMO wrapper B"); }
            catch (java.io.IOException failed) { throw new AssertionError(failed); }
        };
        request(h, "DEMO-race", "./mvnw test");
        assertEquals(SessionEvents.HostedCallKind.PRE_ALLOWED,
                h.facts(SessionEvents.LocalAgentPermissionRequested.class).getLast().kind(), "classifier saw the old bytes");
        assertFalse(results(h, SessionEvents.LocalAgentScriptTrustChecked.class).getLast().matches(),
                "★ B4 live re-check: the node's separate read observes the changed bytes");
        assertEquals(1, dialogs(h), "a cached classification cannot allow the changed script");
    }

    @Test
    void aFreshSessionNeverTrustsAScriptLinkedOutsideItsProject(@TempDir Path outside) throws Exception {
        Path target = outside.resolve("DEMO-wrapper");
        Files.writeString(target, "DEMO wrapper A");
        var h = running(true, project -> {
            Files.delete(project.resolve("mvnw"));
            Files.createSymbolicLink(project.resolve("mvnw"), target);
        });
        request(h, "DEMO-outside", "./mvnw test");
        assertEquals(1, dialogs(h), "★ B5: fresh capture follows the real target, not its trusted apparent name");
        assertTrue(results(h, SessionEvents.LocalAgentPermissionAnswered.class).isEmpty());
    }
}
