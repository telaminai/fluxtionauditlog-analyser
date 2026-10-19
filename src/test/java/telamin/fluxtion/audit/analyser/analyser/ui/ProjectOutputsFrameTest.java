package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.session.*;
import telamin.fluxtion.audit.analyser.analyser.assistant.localagent.HostedAdmission;
import telamin.fluxtion.audit.analyser.analyser.llm.Json;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpTools;
import telamin.fluxtion.audit.analyser.analyser.net.ActionServer;
import telamin.fluxtion.audit.analyser.analyser.net.HostedCredentials;
import javax.swing.*;
import java.awt.Component;
import java.awt.Container;
import java.awt.GraphicsEnvironment;
import java.nio.file.*;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeFalse;
import static telamin.fluxtion.audit.analyser.analyser.ui.AsyncOpenInterleavingFrameTest.*;
import static telamin.fluxtion.audit.analyser.analyser.ui.StarterProposalFrameTest.awaitSnapshot;
import static telamin.fluxtion.audit.analyser.analyser.ui.StarterProposalFrameTest.awaitTrue;

/** M81 D: real four-second observation timer, visible offers and their real buttons. No provider or model. */
class ProjectOutputsFrameTest {
    @Test void onlyAnActuallyCompletedHostedHttpOpenSuppressesItsExactOutput(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        Path root = Files.createDirectories(tmp.resolve("DEMO"));
        Path profile = Files.createDirectories(root.resolve(".analyser")).resolve("project.fluxtion-settings");
        Files.writeString(profile, "share.version=1\n");
        try (Frame f = new Frame(tmp)) {
            HostedAdmission[] admission = new HostedAdmission[1];
            SessionDriver[] driver = new SessionDriver[1];
            onEdt(() -> {
                try {
                    var perform = MainFrame.class.getDeclaredMethod("performSessionEffect", SessionEffects.class);
                    perform.setAccessible(true);
                    driver[0] = ReportTestSession.create(effect -> effect instanceof SessionEffects.AnswerHostedCallEffect answer
                            ? admission[0].answer(answer) : (SessionEvents.Result) perform.invoke(f.frame, effect));
                    var slot = MainFrame.class.getDeclaredField("session"); slot.setAccessible(true); slot.set(f.frame, driver[0]);
                    admission[0] = new HostedAdmission(fact -> SwingUtilities.invokeLater(() -> driver[0].post(fact)), 5000);
                    // This journey uses a simulated Full CLI and real local graph/display effects, never a process/model.
                    ((AppConfig) field(f.frame, "config")).claudeCodeFull = true;
                    ((AppConfig) field(f.frame, "config")).assistantExports = true;
                    ((AppConfig) field(f.frame, "config")).assistantExportDir = root.toString();
                    render(f.ex, "open", Map.of("project", profile.toString()));
                } catch (ReflectiveOperationException failure) { throw new AssertionError(failure); }
            });
            long generation = driver[0].snapshot().localAgent().admittedGeneration();
            long ticket = driver[0].snapshot().localAgent().openTicket();
            assertTrue(generation != 0 && ticket != 0, "the simulated Full turn is actually admitted and open");
            awaitTrue(() -> {
                final boolean[] ready = {false};
                onEdt(() -> ready[0] = (boolean) field(driver[0].processor().projectOutputs, "baseline"));
                return ready[0];
            }, "the hosted journey's real watcher baseline");
            HostedCredentials credentials = new HostedCredentials();
            String credential = credentials.mint(generation); // isolated test credential; never a real endpoint/token
            ActionServer server = new ActionServer(f.frame.actionDispatcher(), "fixture-only", 20, 1000);
            server.setHostedGate(credentials.gate(admission[0]));
            server.setHostedScopes(g -> {
                var state = driver[0].snapshot().projectOutputs();
                return new McpTools.OpenScope(root, true, null, generation, null,
                        new McpTools.OutputScope(state.watch(), state.projectGeneration()));
            });
            server.start();
            try {
                Path graph = Files.writeString(root.resolve("build.graphml"), graph("nodeA"));
                var client = java.net.http.HttpClient.newHttpClient();
                var request = java.net.http.HttpRequest.newBuilder(java.net.URI.create(server.url() + "/action"))
                        .header(ActionServer.HOSTED_HEADER, credential)
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(Json.write(
                                Map.of("action", "open", "params", Map.of("graphml", graph.toString()))))).build();
                assertEquals(200, client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode(),
                        "the real HTTP admission/EDT open succeeds");
                onEdt(() -> {
                    assertTrue(driver[0].snapshot().graphOpen(), "the successful receipt follows actual graph application");
                    var receipts = driver[0].auditSink().matching("event: HostedProjectOutputOpened");
                    assertEquals(1, receipts.size(), "exactly one actual completed hosted open is observed");
                    assertTrue(receipts.getFirst().contains("ticket=" + ticket), "the original admitted ticket survives HTTP to EDT");
                });
                Path invalid = Files.writeString(root.resolve("invalid.graphml"), "not a GraphML document");
                var failed = java.net.http.HttpRequest.newBuilder(java.net.URI.create(server.url() + "/action"))
                        .header(ActionServer.HOSTED_HEADER, credential)
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(Json.write(
                                Map.of("action", "open", "params", Map.of("graphml", invalid.toString()))))).build();
                assertEquals(400, client.send(failed, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode(),
                        "an admitted in-project path still has to parse/apply successfully");
                Files.delete(invalid);
                onEdt(() -> {
                    assertEquals(1, driver[0].auditSink().matching("event: HostedProjectOutputOpened").size(),
                            "admission followed by failed graph application is NOT a successful-open receipt");
                    driver[0].post(new SessionEvents.LocalAgentTurnEnded(generation, ticket, SessionEvents.TurnOutcome.COMPLETED));
                });
                Thread.sleep(9000); // two real four-second polls, not a hand-made stable-change fact
                onEdt(() -> assertTrue(driver[0].snapshot().projectOutputs().offers().isEmpty(),
                        "a file already actually opened by this Full turn is not re-offered"));
                Files.writeString(graph, graph("nodeA") + "\n<!-- DEMO newer revision -->\n");
                awaitSnapshot(f.frame, s -> s.projectOutputs().pendingKinds().contains("graphml"), "a newer revision remains independently offerable");
                Path external = Files.writeString(root.resolve("external.graphml"), graph("nodeA"));
                var outsideClient = java.net.http.HttpRequest.newBuilder(java.net.URI.create(server.url() + "/action"))
                        .header("X-Analyser-Token", "fixture-only")
                        .POST(java.net.http.HttpRequest.BodyPublishers.ofString(Json.write(
                                Map.of("action", "open", "params", Map.of("graphml", external.toString()))))).build();
                assertEquals(200, client.send(outsideClient, java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode());
                onEdt(() -> {
                    assertEquals(1, driver[0].auditSink().matching("event: HostedProjectOutputOpened").size(),
                            "an actual external HTTP open must never manufacture a hosted completion receipt");
                    assertTrue(driver[0].snapshot().projectOutputs().pendingKinds().contains("graphml"),
                            "an external open cannot consume the hosted turn's unanswered newer revision");
                });
            } finally { server.stop(); }
        }
    }

    @Test void realAnalyserSampleAndBundleRootsNeverStartAnOutputWatch(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        try (Frame f = new Frame(tmp)) {
            for (String name : java.util.List.of("demo/DEMO", "bundles/DEMO")) {
                Path root = Files.createDirectories(tmp.resolve("home/.fluxtion-analyser").resolve(name));
                Path profile = Files.createDirectories(root.resolve(".analyser")).resolve("project.fluxtion-settings");
                Files.writeString(profile, "share.version=1\n");
                onEdt(() -> {
                    render(f.ex, "open", Map.of("project", profile.toString()));
                    SessionDriver driver = (SessionDriver) field(f.frame, "session");
                    assertTrue(driver.processor().activeProject.isAt(profile.toString()));
                    assertFalse(driver.snapshot().projectOutputs().watching(), "analyser-owned " + name + " is never watched");
                    assertNull(field(f.frame, "projectOutputsTimer"), "no polling timer for an analyser-owned root");
                });
            }
        }
    }

    @Test void ordinaryProjectOffersNeverSwitchThePersonsLogAndRealButtonsOpenPairOrDismiss(@TempDir Path tmp) throws Exception {
        assumeFalse(GraphicsEnvironment.isHeadless(), "a real display is required");
        Path root = Files.createDirectories(tmp.resolve("DEMO"));
        Path logs = Files.createDirectories(root.resolve("logs"));
        Path profile = Files.createDirectories(root.resolve(".analyser")).resolve("project.fluxtion-settings");
        Files.writeString(profile, "share.version=1\nenvironment.count=1\nenvironment.0.name=DEMO\n"
                + "environment.0.provenance=DEMO\nenvironment.0.logDir=logs\n");
        Path own = Files.writeString(logs.resolve("own.yaml"), log("nodeA"));
        try (Frame f = new Frame(tmp)) {
            onEdt(() -> { f.frame.setSize(1280, 820); f.frame.setVisible(true); });
            onEdt(() -> render(f.ex, "open", Map.of("project", profile.toString())));
            onEdt(() -> render(f.ex, "open", Map.of("log", own.toString())));
            awaitLoaded(f.ex);
            SessionDriver driver = (SessionDriver) field(f.frame, "session");
            awaitTrue(() -> {
                final boolean[] ready = {false};
                onEdt(() -> ready[0] = (boolean) field(driver.processor().projectOutputs, "baseline"));
                return ready[0];
            }, "the ordinary project's initial metadata baseline");
            onEdt(() -> assertFalse(((AppConfig) field(f.frame, "config")).assistantExports,
                    "local person acceptance is exercised with assistant file exchange OFF"));

            Path graph = root.resolve("build.graphml");
            // Keep rewriting through a poll: a partial, still-growing file cannot acquire a settled offer.
            for (int i = 0; i < 6; i++) {
                Files.writeString(graph, "<graphml><!-- still writing " + i + " ".repeat(i + 1));
                Thread.sleep(1000);
                onEdt(() -> assertTrue(driver.snapshot().projectOutputs().offers().isEmpty(),
                        "a still-writing graph must not produce an Open button"));
            }
            Files.writeString(graph, graph("nodeA"));
            var graphState = awaitSnapshot(f.frame, s -> s.projectOutputs().pendingKinds().contains("graphml"), "a settled graph offer");
            long graphOffer = graphState.projectOutputs().offers().getFirst().id();
            onEdt(() -> {
                assertFalse(driver.snapshot().graphOpen(), "stability alone never opens a graph");
                assertEquals(own.toString(), driver.processor().openLog.logPath(), "the person's log remains open");
                assertTrue(StarterProposalFrameTest.text(f.frame).contains("The graph was regenerated — open it?"));
                assertTrue(button(f.frame, "projectOutputOpen-" + graphOffer).isShowing());
                button(f.frame, "projectOutputOpen-" + graphOffer).doClick();
            });
            awaitSnapshot(f.frame, SessionSnapshot::graphOpen, "the person's graph Open button");

            // Make pairing causal: a DIFFERENT graph is on screen before the log's Open click.
            // Merely retaining the already-open matching graph must not make the pairing witness pass.
            Path different = Files.writeString(tmp.resolve("other.graphml"), graph("nodeB")).toRealPath();
            onEdt(() -> render(f.ex, "open", Map.of("graphml", different.toString())));

            Path next = Files.writeString(logs.resolve("next.yaml"), log("nodeA")).toRealPath();
            var logState = awaitSnapshot(f.frame, s -> s.projectOutputs().pendingKinds().contains("log"), "the new audit-log offer");
            var logOffer = logState.projectOutputs().offers().stream()
                    .filter(o -> o.file().kind() == ProjectOutputsState.Kind.LOG).findFirst().orElseThrow();
            assertNotNull(logOffer.graph(), "the log offer explicitly names the regenerated graph");
            onEdt(() -> {
                assertEquals(own.toString(), driver.processor().openLog.logPath(), "a new log offer must NEVER auto-switch the person's log");
                assertEquals(different.toString(), driver.snapshot().graphPath(), "an unanswered offer must not switch the graph either");
                assertEquals(java.util.List.of("log"), contextPending(f.ex));
                button(f.frame, "projectOutputOpen-" + logOffer.id()).doClick();
            });
            try {
                awaitSnapshot(f.frame, s -> next.toString().equals(s.logPath()) && s.graphOpen(), "the accepted log with its graph");
            } catch (AssertionError failure) {
                onEdt(() -> {
                    var s = driver.snapshot();
                    throw new AssertionError("accepted output did not land: log=" + s.logPath() + " graph=" + s.graphOpen()
                            + " pending=" + s.pending() + " offers=" + s.projectOutputs() + " status=" + f.status()
                            + " output events=" + driver.auditSink().matching("ProjectOutputOpenObserved")
                            + " load events=" + driver.auditSink().matching("LogOpenFailed"), failure);
                });
            }
            String offeredGraphPath = graph.toRealPath().toString();
            onEdt(() -> {
                assertEquals(offeredGraphPath, driver.snapshot().graphPath(),
                        "the accepted log really opens the OFFERED graph, not the different graph already on screen");
                assertNotNull(driver.snapshot().pairing());
                assertTrue(driver.snapshot().pairing().applies());
            });
            onEdt(() -> assertTrue(contextPending(f.ex).isEmpty(), "the accepted offer disappears from context too"));

            Files.writeString(graph, graph("nodeA") + "\n<!-- DEMO next build -->\n");
            var again = awaitSnapshot(f.frame, s -> s.projectOutputs().pendingKinds().contains("graphml"), "the next build can offer again");
            long dismiss = again.projectOutputs().offers().getFirst().id();
            onEdt(() -> button(f.frame, "projectOutputNotNow-" + dismiss).doClick());
            awaitSnapshot(f.frame, s -> s.projectOutputs().offers().isEmpty(), "the real Not now button consumes only this revision");
            onEdt(() -> assertEquals(next.toString(), driver.processor().openLog.logPath()));
        }
    }

    @SuppressWarnings("unchecked")
    static java.util.List<String> contextPending(ActionExecutor ex) {
        Object project = find(render(ex, "context", Map.of()), "project");
        if (!(project instanceof Map<?, ?> row)) return java.util.List.of();
        Object pending = row.get("pendingOutputOffers");
        return pending instanceof java.util.List<?> list ? (java.util.List<String>) list : java.util.List.of();
    }
    static JButton button(Container parent, String name) {
        for (Component c : parent.getComponents()) {
            if (c instanceof JButton b && name.equals(b.getName())) return b;
            if (c instanceof Container inner) { JButton found = findButton(inner, name); if (found != null) return found; }
        }
        throw new AssertionError("missing real output button " + name);
    }
    private static JButton findButton(Container parent, String name) {
        for (Component c : parent.getComponents()) {
            if (c instanceof JButton b && name.equals(b.getName())) return b;
            if (c instanceof Container inner) { JButton found = findButton(inner, name); if (found != null) return found; }
        }
        return null;
    }
}
