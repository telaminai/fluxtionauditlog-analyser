package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import telamin.fluxtion.audit.analyser.analyser.net.HostedCredentials;
import telamin.fluxtion.audit.analyser.analyser.session.AssistantMode;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionDriver;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The REAL generated session processor, the REAL {@link LocalAgentAdapter} and {@link ClaudeCodeProvider}, and a FAKE
 * process ({@link FixtureAgent}) replaying recorded fixtures. Everything runs on the test thread, with virtual time.
 */
final class Harness {

    static final long BUDGET = 5_000_000;

    final FixtureAgent agent;
    final ManualClock clock = new ManualClock();
    final LocalAgentTranscript store = new LocalAgentTranscript();
    final HostedCredentials credentials = new HostedCredentials();
    final List<Object> facts = new ArrayList<>();
    final List<SessionEffects> others = new ArrayList<>();
    final Path home;
    final LocalAgentAdapter adapter;
    final SessionDriver driver;
    private long requests;
    final Thread owner = Thread.currentThread();
    final java.util.concurrent.ConcurrentLinkedQueue<Object> offThread = new java.util.concurrent.ConcurrentLinkedQueue<>();
    String model = "haiku";
    /** The consent Start and Send report: the default posture unless a test sets the full one. */
    SessionEvents.LocalAgentConsent consent;
    /** The analyser's own environment as the adapter reads it (the GUI PATH in a full-posture test). */
    Map<String, String> environment;

    /** Deliver facts that arrived on other threads, on this one. */
    void drain() {
        for (Object f; (f = offThread.poll()) != null; ) {
            facts.add(f);
            driver.post(f);
        }
    }

    Harness(FixtureAgent agent, Path home) throws IOException {
        this(agent, home, () -> Runnable::run);
    }

    /** With {@code writers} the adapter's per-process stdin writers (the application's are single threads). */
    Harness(FixtureAgent agent, Path home, java.util.function.Supplier<java.util.concurrent.Executor> writers)
            throws IOException {
        this.agent = agent;
        this.home = home;
        Path bin = Files.createDirectories(home.resolve("bin"));
        Path claude = bin.resolve("claude");
        if (!Files.exists(claude)) {
            Files.writeString(claude, "#!/bin/sh\nexit 0\n");
            Files.setPosixFilePermissions(claude, PosixFilePermissions.fromString("rwx------"));
        }
        Files.createDirectories(home.resolve("proj"));
        SessionDriver[] d = new SessionDriver[1];
        LocalAgentAdapter.Environment env = new LocalAgentAdapter.Environment() {
            @Override public String model() { return model; }
            @Override public Path workingDirectory() {
                return workingDirectory != null ? workingDirectory : home.resolve("proj");
            }
            @Override public String restUrl() { return "http://127.0.0.1:1"; }
            @Override public String restToken() { return "shared-rest-token"; }
            @Override public Optional<List<String>> bridgeLauncher() {
                return Optional.of(List.of("/usr/bin/java", "-jar", "/opt/analyser/analyser.jar"));
            }
            @Override public Map<String, String> environment() {
                return environment != null ? environment : Map.of("PATH", bin.toString());
            }
            @Override public Path userHome() { return home; }
            @Override public void post(Object fact) {
                if (Thread.currentThread() != owner) {          // a process exit on another thread: marshal, as the app does
                    offThread.add(fact);
                    return;
                }
                facts.add(fact);
                d[0].post(fact);
            }
        };
        this.adapter = new LocalAgentAdapter(new ClaudeCodeProvider(), agent, store, credentials, env, Runnable::run, clock,
                writers);
        this.driver = new SessionDriver(effect -> {
            if (LocalAgentAdapter.handles(effect)) return adapter.perform(effect);
            others.add(effect);
            return new SessionEvents.LocalAgentEffectStarted(effect.opId(), 0, "other");
        });
        d[0] = driver;
        driver.post(new SessionEvents.AssistantModeChosen(AssistantMode.LOCAL_AGENT, "test"));
    }

    /** The folder the posture is decided for; a test may move it after Start to witness review M7. */
    volatile Path workingDirectory;

    SessionEvents.LocalAgentConsent consent() {
        // the posture digest of the folder the adapter will use, as the frame observes it (review M7)
        return new SessionEvents.LocalAgentConsent(true, true, false,
                ClaudeCodeProvider.postureRef(home.resolve("proj"),
                        telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture.ANALYSER_ASSISTANT), BUDGET);
    }

    /** The full posture in {@code mode}, its folder confirmed for it (OD-74.12), with no spend limit. */
    SessionEvents.LocalAgentConsent fullConsent(
            telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture.FullMode mode) {
        return new SessionEvents.LocalAgentConsent(true, false, false, ClaudeCodeProvider.postureRef(home.resolve("proj"),
                telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture.full(mode)), 0,
                SessionEvents.FolderVerdict.OK, true, mode, true);
    }

    void start() {
        driver.post(new SessionEvents.LocalAgentStartRequested(++requests, consent == null ? consent() : consent));
    }

    /** Type {@code text} and press Send: the draft goes to the store, its classification to the node. */
    void send(String text) {
        var slash = adapter.classify(text);
        long draft = store.add(LocalAgentTranscript.Kind.USER, slash.line(text));
        driver.post(new SessionEvents.LocalAgentSendRequested(++requests, draft, slash.slash(), slash.command(),
                consent == null ? consent() : consent));
    }

    LocalAgentState state() {
        return driver.snapshot().localAgent();
    }

    <T> List<T> facts(Class<T> type) {
        return facts.stream().filter(type::isInstance).map(type::cast).toList();
    }

    /** The answer texts the tab would render, in order. */
    List<String> answers() {
        return state().entries().stream().filter(e -> LocalAgentState.ANSWER.equals(e.kind()))
                .map(e -> store.text(e.id())).toList();
    }

    List<String> userTurns() {
        return state().entries().stream().filter(e -> LocalAgentState.USER.equals(e.kind()))
                .map(e -> store.text(e.id())).toList();
    }
}
