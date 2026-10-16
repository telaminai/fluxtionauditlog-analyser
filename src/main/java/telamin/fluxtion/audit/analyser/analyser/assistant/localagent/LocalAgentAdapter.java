package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import telamin.fluxtion.audit.analyser.analyser.net.HostedCredentials;
import telamin.fluxtion.audit.analyser.analyser.net.RestEndpointFile;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;

/**
 * Performs what the {@code localAgentSession} node asks (spec-claude-code-provider.md §4) and reports what the CLI said
 * as facts carrying the generation and the ticket. It DECIDES nothing: not whether a start may happen, not whether the
 * login, the binding or the posture is acceptable, not when a turn has ended for the session, not when to give up on a
 * Stop (rule 9). It parses, maps the CLI's uuids to the node's tickets, keeps the words in the bounded
 * {@link LocalAgentTranscript}, and measures time for the deadlines the node judges.
 *
 * <p><b>Never Swing.</b> This class touches no Swing or AWT type; a one-home rule pins that. Outcomes leave through
 * {@link Environment#post}, which marshals to the session's thread.
 *
 * <p><b>Credentials.</b> The hosted credential is minted per spawn, written only to that spawn's per-instance endpoint
 * file (mode 600) and revoked when the generation stops or exits. The REST token is read when a spawn is performed and
 * written to the same file. Neither ever enters a fact, the transcript store or an error.
 */
public final class LocalAgentAdapter {

    /** What the adapter reads from, and reports to, the running application. Read on the session's thread. */
    public interface Environment {
        /** The Claude Code model setting (machine tier). */
        String model();

        /** The confirmed working directory: the project root, or the analyser's private scratch directory. */
        Path workingDirectory();

        /** The live REST transport, or null when it is off. */
        String restUrl();

        String restToken();

        /** The hosted bridge's launcher: the running jar first, JBang only when it cannot be launched (r6 §4.2). */
        Optional<List<String>> bridgeLauncher();

        /** The analyser's own environment (for {@code PATH} and the npm prefix). */
        Map<String, String> environment();

        Path userHome();

        /** Report a fact to the session, on its thread. */
        void post(Object fact);
    }

    /** Time and delays: real in the application, virtual in the tests. */
    public interface Clock {
        long nowMillis();

        void schedule(long delayMs, Runnable task);
    }

    /** How long the process tree is given to end on SIGTERM before the survivors are killed. */
    static final Duration KILL_GRACE = Duration.ofSeconds(2);
    static final long INTERRUPT_ACK_MS = 5_000;
    static final long INTERRUPT_END_MS = 15_000;
    static final long TEXT_COALESCE_MS = 150;

    private final LocalAgentProvider provider;
    private final AgentProcess.Launcher launcher;
    private final LocalAgentTranscript store;
    private final HostedCredentials credentials;
    private final Environment env;
    private final Executor worker;
    private final Clock clock;
    private final Map<Long, Run> runs = new ConcurrentHashMap<>();
    private volatile List<StreamJson.Command> lastCommands = List.of();
    private volatile int unknownEvents;

    public LocalAgentAdapter(LocalAgentProvider provider, AgentProcess.Launcher launcher, LocalAgentTranscript store,
                             HostedCredentials credentials, Environment env, Executor worker, Clock clock) {
        this.provider = provider;
        this.launcher = launcher;
        this.store = store;
        this.credentials = credentials;
        this.env = env;
        this.worker = worker;
        this.clock = clock;
    }

    public LocalAgentTranscript store() {
        return store;
    }

    /** How a draft reads as a command, against the CLI's last {@code initialize.commands}. */
    public LocalAgentProvider.Slash classify(String draft) {
        return provider.classify(draft, lastCommands);
    }

    /** Lines of a kind this analyser does not know, tolerated and counted (§4.5). */
    public int unknownEvents() {
        return unknownEvents;
    }

    /** Whether {@code effect} is one this adapter performs. */
    public static boolean handles(SessionEffects effect) {
        return effect instanceof SessionEffects.StartLocalAgentEffect
                || effect instanceof SessionEffects.PollLocalAgentStatusEffect
                || effect instanceof SessionEffects.SendLocalAgentTurnEffect
                || effect instanceof SessionEffects.StopLocalAgentEffect;
    }

    /** Perform one local-agent effect: the answer is immediate, the outcome is posted later. */
    public SessionEvents.Result perform(SessionEffects effect) {
        return switch (effect) {
            case SessionEffects.StartLocalAgentEffect e -> start(e);
            case SessionEffects.PollLocalAgentStatusEffect e -> {
                Run run = runs.get(e.generation());
                if (run != null) clock.schedule(e.delayMs(), () -> requestStatus(run));
                yield started(e.opId(), e.generation(), run == null ? "poll:no-process" : "poll");
            }
            case SessionEffects.SendLocalAgentTurnEffect e -> send(e);
            case SessionEffects.StopLocalAgentEffect e -> stop(e);
            default -> throw new IllegalArgumentException("not a local-agent effect: " + effect);
        };
    }

    private static SessionEvents.LocalAgentEffectStarted started(long opId, long generation, String what) {
        return new SessionEvents.LocalAgentEffectStarted(opId, generation, what);
    }

    // ---- start ------------------------------------------------------------------------------------------------------

    private SessionEvents.Result start(SessionEffects.StartLocalAgentEffect e) {
        // captured NOW, on the session's thread, as immutable values: the worker never reads live configuration
        Spawn s = new Spawn(e, env.model(), env.workingDirectory(), env.restUrl(), env.restToken(), env.bridgeLauncher(),
                Map.copyOf(env.environment()), env.userHome());
        Run run = new Run(e.generation(), e.sessionId(), e.nonce());
        runs.put(e.generation(), run);
        worker.execute(() -> spawn(run, s));
        return started(e.opId(), e.generation(), "start");
    }

    private record Spawn(SessionEffects.StartLocalAgentEffect effect, String model, Path cwd, String restUrl,
                         String restToken, Optional<List<String>> launcher, Map<String, String> environment, Path home) {
    }

    private void spawn(Run run, Spawn s) {
        long gen = run.generation;
        ClaudeResolver.Resolution found = provider.resolve(s.environment(), s.home());
        if (!found.found()) {
            fail(run, SessionEvents.SpawnFailure.NOT_FOUND, store.add(LocalAgentTranscript.Kind.DETAIL, found.searchedText()));
            return;
        }
        if (s.launcher().isEmpty()) {
            fail(run, SessionEvents.SpawnFailure.LAUNCHER_UNAVAILABLE, 0);
            return;
        }
        if (s.restUrl() == null || s.restToken() == null || s.cwd() == null) {
            fail(run, SessionEvents.SpawnFailure.ENDPOINT_FAILED, 0);
            return;
        }
        Path dir = LocalAgentLock.directory(s.home());
        try {
            Optional<LocalAgentLock> lock = LocalAgentLock.tryAcquire(dir, run.sessionId);
            if (lock.isEmpty()) {
                fail(run, SessionEvents.SpawnFailure.LOCKED, 0);
                return;
            }
            run.lock = lock.get();
        } catch (IOException ex) {
            fail(run, SessionEvents.SpawnFailure.ENDPOINT_FAILED, 0);
            return;
        }
        run.endpointFile = dir.resolve("endpoint-" + ProcessHandle.current().pid() + "-" + gen + ".json");
        try {
            String hosted = credentials.mint(gen);
            new RestEndpointFile(run.endpointFile).writeHosted(s.restUrl(), s.restToken(), run.nonce, hosted);
        } catch (IOException | RuntimeException ex) {
            fail(run, SessionEvents.SpawnFailure.ENDPOINT_FAILED, 0);
            return;
        }
        List<String> command = provider.command(new LocalAgentProvider.LaunchSpec(found.executable(), run.sessionId,
                s.effect().resume(), s.model(), s.cwd(), s.launcher().get(), run.endpointFile, s.effect().maxBudgetMicros()));
        run.startedAt = clock.nowMillis();
        try {
            AgentProcess p = launcher.start(command, provider.environment(), s.cwd(), line -> onLine(run, line));
            synchronized (run) {
                run.process = p;
            }
            p.onExit().thenAccept(code -> onExit(run, code == null ? -1 : code));
        } catch (IOException | RuntimeException ex) {
            fail(run, SessionEvents.SpawnFailure.LAUNCH_FAILED, 0);
            return;
        }
        env.post(new SessionEvents.LocalAgentSpawned(gen, SessionEvents.SpawnFailure.NONE, 0));
        if (run.killed) {                         // stopped while it was starting
            terminate(run);
            return;
        }
        write(run, StreamJson.controlRequest("init-" + gen, "initialize"));
        clock.schedule(LocalAgentSessionLimits.START_LIMIT_MS, () -> env.post(new SessionEvents.LocalAgentStartDeadline(gen)));
    }

    private void fail(Run run, SessionEvents.SpawnFailure failure, long detail) {
        cleanup(run);
        env.post(new SessionEvents.LocalAgentSpawned(run.generation, failure, detail));
    }

    // ---- polling the binding --------------------------------------------------------------------------------------

    private void requestStatus(Run run) {
        if (run.killed) return;
        write(run, StreamJson.controlRequest("status-" + run.generation + "-" + (++run.statusSeq), "mcp_status"));
    }

    // ---- turns --------------------------------------------------------------------------------------------------

    private SessionEvents.Result send(SessionEffects.SendLocalAgentTurnEffect e) {
        Run run = runs.get(e.generation());
        if (run == null) {
            env.post(new SessionEvents.LocalAgentTurnEnded(e.generation(), e.ticket(), SessionEvents.TurnOutcome.PROCESS_EXIT));
            return started(e.opId(), e.generation(), "send:no-process");
        }
        String uuid = UUID.randomUUID().toString();       // a fresh uuid per turn: a reused one never runs [P0-27]
        synchronized (run) {
            run.ticketByUuid.put(uuid, e.ticket());
            run.beginTurn(e.ticket());
        }
        if (!write(run, StreamJson.userLine(uuid, store.text(e.draft())))) {
            endTurn(run, e.ticket(), SessionEvents.TurnOutcome.PROCESS_EXIT);
        }
        return started(e.opId(), e.generation(), "send");
    }

    private SessionEvents.Result stop(SessionEffects.StopLocalAgentEffect e) {
        Run run = runs.get(e.generation());
        if (run == null) return started(e.opId(), e.generation(), "stop:no-process");
        if (e.how() == SessionEffects.StopHow.INTERRUPT) {
            long gen = e.generation();
            long ticket = e.ticket();
            write(run, StreamJson.controlRequest("interrupt-" + gen + "-" + ticket, "interrupt"));
            clock.schedule(INTERRUPT_ACK_MS, () -> env.post(new SessionEvents.LocalAgentStopDeadline(gen, ticket,
                    SessionEvents.StopDeadline.ACK)));
            clock.schedule(INTERRUPT_END_MS, () -> env.post(new SessionEvents.LocalAgentStopDeadline(gen, ticket,
                    SessionEvents.StopDeadline.END)));
            return started(e.opId(), gen, "interrupt");
        }
        run.killed = true;
        credentials.revoke(run.generation);              // revoked at stop, before the tree is even signalled
        worker.execute(() -> terminate(run));
        return started(e.opId(), e.generation(), "kill");
    }

    private void terminate(Run run) {
        AgentProcess p;
        synchronized (run) {
            p = run.process;
        }
        if (p != null) p.terminateTree(KILL_GRACE);
        else cleanup(run);
    }

    /** End the analyser: every process tree, now. */
    public void shutdown() {
        for (Run run : List.copyOf(runs.values())) {
            run.killed = true;
            credentials.revoke(run.generation);
            AgentProcess p;
            synchronized (run) {
                p = run.process;
            }
            if (p != null) p.terminateTree(KILL_GRACE);
            cleanup(run);
        }
    }

    /** §4.7: a shutdown hook does what Stop does, so a closed analyser leaves no Claude Code or bridge running. */
    public void installShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown, "analyser-local-agent-shutdown"));
    }

    private boolean write(Run run, String line) {
        AgentProcess p;
        synchronized (run) {
            p = run.process;
        }
        if (p == null) return false;
        try {
            p.writeLine(line);
            return true;
        } catch (IOException ex) {
            return false;
        }
    }

    // ---- reading the stream ---------------------------------------------------------------------------------------

    void onLine(Run run, String line) {
        StreamJson.Event event = provider.parse(line);
        long gen = run.generation;
        switch (event) {
            case StreamJson.ControlResponse r -> onControlResponse(run, r);
            case StreamJson.ControlRequest r -> {
                // P1a asks nobody: dontAsk never sends one [P0b-3]; should a CLI ever do so, it is denied, never granted
                write(run, StreamJson.deny(r.requestId(), "Denied: the analyser assistant asks nobody in this posture"));
            }
            case StreamJson.SystemInit i -> {
                long headerRef;
                synchronized (run) {
                    run.header = (run.header == null ? new LocalAgentTranscript.Header(null, null, "", null, List.of())
                            : run.header).withInit(i.model(), i.cliVersion());
                    headerRef = store.putHeader(run.header);
                }
                env.post(new SessionEvents.LocalAgentInitObserved(gen, run.runningTicket,
                        LocalAgentPosture.digest(i.sessionId()), i.permissionMode(), i.tools(), i.servers(),
                        i.capabilities(), headerRef));
            }
            case StreamJson.Lifecycle l -> {
                Long ticket = run.ticketByUuid.get(l.commandUuid());
                if (ticket == null) return;               // not a line this generation sent
                switch (l.state() == null ? "" : l.state()) {
                    case "started" -> env.post(new SessionEvents.LocalAgentTurnStarted(gen, ticket));
                    case "completed" -> endTurn(run, ticket, SessionEvents.TurnOutcome.COMPLETED);
                    case "cancelled" -> endTurn(run, ticket, SessionEvents.TurnOutcome.CANCELLED);
                    default -> { }
                }
            }
            case StreamJson.Result r -> onResult(run, r);
            case StreamJson.MessageStart m -> {
                synchronized (run) {
                    run.currentMessage = m.messageId();
                }
            }
            case StreamJson.TextDelta d -> {
                synchronized (run) {
                    run.delta(d.index(), d.text());
                }
                textChanged(run);
            }
            case StreamJson.AssistantMessage a -> {
                boolean text;
                synchronized (run) {
                    text = run.block(a.messageId(), a.text());
                }
                if (text) textChanged(run);
            }
            case StreamJson.CompactBoundary c -> env.post(new SessionEvents.LocalAgentNoted(gen, run.runningTicket,
                    SessionEvents.AgentNote.COMPACTED, 0));
            case StreamJson.RateLimit r -> {
                if (r.status() != null && !"allowed".equals(r.status())) {
                    env.post(new SessionEvents.LocalAgentNoted(gen, run.runningTicket, SessionEvents.AgentNote.RATE_LIMITED,
                            r.resetsAt()));
                }
            }
            case StreamJson.PermissionDenied p -> {
                if (run.deniedNoted.add(run.runningTicket)) {
                    env.post(new SessionEvents.LocalAgentNoted(gen, run.runningTicket,
                            SessionEvents.AgentNote.PERMISSION_DENIED, 0));
                }
            }
            case StreamJson.Unknown u -> unknownEvents++;
            // a replay, a tool result or the compaction summary is never the person's turn: their words are the send
            // record, so a user event is shown as nothing at all (§4.5)
            case StreamJson.UserMessage u -> { }
            case StreamJson.ConversationReset c -> { }          // the next init's session id is what the node judges
            case StreamJson.Ignored i -> { }
        }
    }

    private void onControlResponse(Run run, StreamJson.ControlResponse r) {
        long gen = run.generation;
        String id = r.requestId() == null ? "" : r.requestId();
        if (id.equals("init-" + gen) && r.initialize() != null) {
            StreamJson.Initialize init = r.initialize();
            lastCommands = init.commands();
            String billing = init.subscriptionType() != null ? init.subscriptionType()
                    : init.apiKeySource() != null ? "API key (" + init.apiKeySource() + ")" : "";
            long headerRef;
            synchronized (run) {
                run.header = new LocalAgentTranscript.Header(null, null, billing, init.apiProvider(),
                        ClaudeCodeProvider.unclassified(init.commands()));
                headerRef = store.putHeader(run.header);
            }
            env.post(new SessionEvents.LocalAgentInitialized(gen, init.loggedIn(), init.permissionMode(), headerRef));
        } else if (id.startsWith("status-" + gen + "-") && r.status() != null) {
            List<String> names = new ArrayList<>();
            String hostedStatus = null;
            String nonceDigest = null;
            for (StreamJson.Server s : r.status().servers()) {
                names.add(s.name());
                if (LocalAgentPosture.HOSTED_SERVER.equals(s.name())) {
                    hostedStatus = s.status();
                    String v = s.version() == null ? "" : s.version();
                    int at = v.indexOf("+bind.");
                    nonceDigest = at < 0 ? null : LocalAgentPosture.digest(v.substring(at + "+bind.".length()));
                }
            }
            env.post(new SessionEvents.LocalAgentStatusObserved(gen, clock.nowMillis() - run.startedAt, names,
                    hostedStatus, nonceDigest));
        } else if (id.startsWith("interrupt-" + gen + "-")) {
            try {
                env.post(new SessionEvents.LocalAgentInterruptAcked(gen,
                        Long.parseLong(id.substring(("interrupt-" + gen + "-").length()))));
            } catch (NumberFormatException ignored) {
                // not one of ours
            }
        }
    }

    private void onResult(Run run, StreamJson.Result r) {
        long gen = run.generation;
        Long t = r.userMessageUuid() == null ? null : run.ticketByUuid.get(r.userMessageUuid());
        long ticket = t != null ? t : run.runningTicket;
        if (r.totalCostMicros() >= 0) env.post(new SessionEvents.LocalAgentCostObserved(gen, r.totalCostMicros()));
        if (ticket == 0) return;
        boolean shown;
        synchronized (run) {
            // §4.5: text comes from assistant events, never from result — except a turn that had none, such as a local
            // command's output, which the CLI reports only in result
            shown = run.ticket == ticket && run.anyText;
            if (!shown && run.ticket == ticket && r.text() != null && !r.text().isBlank()) run.localOutput(r.text());
        }
        if (!shown) textChanged(run);
        if (r.authFailure()) {
            env.post(new SessionEvents.LocalAgentNoted(gen, ticket, SessionEvents.AgentNote.AUTH_FAILED, 0));
        }
        SessionEvents.TurnOutcome outcome;
        if ("error_max_budget_usd".equals(r.subtype())) outcome = SessionEvents.TurnOutcome.BUDGET;
        else if ("error_during_execution".equals(r.subtype())
                || (r.terminalReason() != null && r.terminalReason().startsWith("aborted"))) {
            outcome = SessionEvents.TurnOutcome.CANCELLED;
        } else if (r.isError()) outcome = SessionEvents.TurnOutcome.ERROR;
        else outcome = SessionEvents.TurnOutcome.COMPLETED;
        endTurn(run, ticket, outcome);
    }

    /** The first end signal of a ticket wins; later ones for the same ticket are ignored (§4.5). */
    private void endTurn(Run run, long ticket, SessionEvents.TurnOutcome outcome) {
        synchronized (run) {
            if (!run.ended.add(ticket)) return;
        }
        flushText(run);
        synchronized (run) {
            if (run.runningTicket == ticket) run.runningTicket = 0;
        }
        env.post(new SessionEvents.LocalAgentTurnEnded(run.generation, ticket, outcome));
    }

    /** Streaming is COALESCED: at most one text fact per {@link #TEXT_COALESCE_MS}, so the audit ring is not flooded. */
    private void textChanged(Run run) {
        boolean schedule;
        synchronized (run) {
            run.textDirty = true;
            schedule = !run.flushScheduled;
            run.flushScheduled = true;
        }
        if (schedule) clock.schedule(TEXT_COALESCE_MS, () -> flushText(run));
    }

    private void flushText(Run run) {
        long ticket;
        long ref;
        long revision;
        synchronized (run) {
            run.flushScheduled = false;
            if (!run.textDirty || run.ticket == 0) return;
            run.textDirty = false;
            String text = run.compose();
            if (text.isEmpty()) return;
            if (run.answerRef == 0) run.answerRef = store.add(LocalAgentTranscript.Kind.ANSWER, text);
            else store.replace(run.answerRef, text);
            ticket = run.ticket;
            ref = run.answerRef;
            revision = ++run.revision;
        }
        env.post(new SessionEvents.LocalAgentTextUpdated(run.generation, ticket, ref, revision));
    }

    private void onExit(Run run, int code) {
        long ticket;
        synchronized (run) {
            ticket = run.runningTicket;
        }
        if (ticket != 0) endTurn(run, ticket, SessionEvents.TurnOutcome.PROCESS_EXIT);
        AgentProcess p;
        synchronized (run) {
            p = run.process;
        }
        String tail = p == null ? "" : p.stderrTail();
        long stderrRef = 0;
        String last = lastLine(tail);
        if (!last.isEmpty()) stderrRef = store.add(LocalAgentTranscript.Kind.STDERR, last);
        cleanup(run);
        env.post(new SessionEvents.LocalAgentExited(run.generation, code, stderrRef));
    }

    static String lastLine(String text) {
        if (text == null) return "";
        String[] lines = text.strip().split("\\R");
        String last = lines.length == 0 ? "" : lines[lines.length - 1].strip();
        return last.length() > 300 ? last.substring(0, 297) + "…" : last;
    }

    /** Release everything a generation held: its credential, its lock, its endpoint file. Idempotent. */
    private void cleanup(Run run) {
        credentials.revoke(run.generation);
        synchronized (run) {
            if (run.lock != null) {
                run.lock.close();
                run.lock = null;
            }
            if (run.endpointFile != null) {
                try {
                    Files.deleteIfExists(run.endpointFile);
                } catch (IOException ignored) {
                    // best effort; the file names a dead process
                }
            }
        }
        runs.remove(run.generation, run);
    }

    // ---- one generation -----------------------------------------------------------------------------------------

    /** What one spawn holds. Guarded by its own monitor: the reader thread and the session's thread both touch it. */
    static final class Run {
        final long generation;
        final String sessionId;
        final String nonce;
        final Map<String, Long> ticketByUuid = new ConcurrentHashMap<>();
        final Set<Long> ended = ConcurrentHashMap.newKeySet();
        final Set<Long> deniedNoted = ConcurrentHashMap.newKeySet();
        AgentProcess process;
        LocalAgentLock lock;
        Path endpointFile;
        volatile boolean killed;
        volatile long runningTicket;
        long startedAt;
        int statusSeq;
        LocalAgentTranscript.Header header;
        // the answer being assembled for the running turn
        long ticket;
        long answerRef;
        long revision;
        boolean anyText;
        boolean textDirty;
        boolean flushScheduled;
        String currentMessage;
        final Map<String, Integer> blocksSeen = new LinkedHashMap<>();
        final LinkedHashMap<String, StringBuilder> blocks = new LinkedHashMap<>();
        final Set<String> finals = new HashSet<>();
        String localOutput;

        Run(long generation, String sessionId, String nonce) {
            this.generation = generation;
            this.sessionId = sessionId;
            this.nonce = nonce;
        }

        void beginTurn(long t) {
            ticket = t;
            runningTicket = t;
            answerRef = 0;
            revision = 0;
            anyText = false;
            textDirty = false;
            currentMessage = null;
            blocksSeen.clear();
            blocks.clear();
            finals.clear();
            localOutput = null;
        }

        /** A provisional delta for block {@code index} of the current message. */
        void delta(int index, String text) {
            if (currentMessage == null) return;
            String key = currentMessage + "#" + index;
            if (finals.contains(key)) return;
            blocks.computeIfAbsent(key, k -> new StringBuilder()).append(text);
        }

        /**
         * A complete block of message {@code messageId}: blocks arrive one per {@code assistant} event in order, so
         * its index is the count seen so far. Its text REPLACES the provisional text of the same message and index.
         *
         * @return whether it carried text
         */
        boolean block(String messageId, String text) {
            String msg = messageId == null ? "?" : messageId;
            int index = blocksSeen.merge(msg, 1, Integer::sum) - 1;
            if (text == null) return false;
            String key = msg + "#" + index;
            blocks.put(key, new StringBuilder(text));
            finals.add(key);
            anyText = true;
            return true;
        }

        void localOutput(String text) {
            localOutput = text;
        }

        String compose() {
            List<String> parts = new ArrayList<>();
            for (StringBuilder b : blocks.values()) if (!b.isEmpty()) parts.add(b.toString());
            if (parts.isEmpty() && localOutput != null) parts.add(localOutput);
            return String.join("\n\n", parts);
        }
    }

    /** The node's bound, used for the deadline the adapter measures (the node decides what it means). */
    static final class LocalAgentSessionLimits {
        static final long START_LIMIT_MS = telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentSession.START_LIMIT_MS;
    }
}
