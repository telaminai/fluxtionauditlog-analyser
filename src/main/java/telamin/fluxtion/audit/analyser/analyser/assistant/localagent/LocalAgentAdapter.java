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

        /**
         * OD-74.22 (P2): the working directory is the analyser's scratch folder standing in for the active project's own
         * root, which is one the analyser owns and never starts Claude Code in (the sample project, a bundle's working
         * copy). The header says so.
         */
        default boolean scratchForProject() {
            return false;
        }

        /**
         * Holistic review B7 (2026-10-17): this machine's full-posture trust records ({@code <real path>|<digest>}), read
         * at Start — so the spawn can check the folder's FULL trust digest (settings, .mcp.json and instruction files),
         * not only the posture reference. No default: an environment must say what it trusts.
         */
        List<String> fullTrustRecords();
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
    private final java.util.function.Supplier<Executor> writers;
    private final Clock clock;
    /** Review M3: how long a spawn waits for this analyser's own previous process of the same conversation to stop. */
    long handoverWaitMs = KILL_GRACE.toMillis() + 3_000;
    private final Map<Long, Run> runs = new ConcurrentHashMap<>();
    private volatile List<StreamJson.Command> lastCommands = List.of();
    private volatile int unknownEvents;

    public LocalAgentAdapter(LocalAgentProvider provider, AgentProcess.Launcher launcher, LocalAgentTranscript store,
                             HostedCredentials credentials, Environment env, Executor worker, Clock clock) {
        this(provider, launcher, store, credentials, env, worker, clock, LocalAgentAdapter::singleWriter);
    }

    /**
     * @param writers one stdin writer per process: a single thread, so lines keep their order and no write ever blocks
     *                the event thread on a full pipe (review S3)
     */
    public LocalAgentAdapter(LocalAgentProvider provider, AgentProcess.Launcher launcher, LocalAgentTranscript store,
                             HostedCredentials credentials, Environment env, Executor worker, Clock clock,
                             java.util.function.Supplier<Executor> writers) {
        this.writers = writers;
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
                || effect instanceof SessionEffects.StopLocalAgentEffect
                || effect instanceof SessionEffects.AnswerPermissionEffect
                || effect instanceof SessionEffects.StartPermissionClockEffect;
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
            case SessionEffects.AnswerPermissionEffect e -> answerPermission(e);
            case SessionEffects.StartPermissionClockEffect e -> {
                long gen = e.generation();
                long ref = e.requestRef();
                clock.schedule(e.deadlineMs(), () -> env.post(new SessionEvents.LocalAgentPermissionDeadline(gen, ref)));
                yield started(e.opId(), gen, "permissionClock");
            }
            default -> throw new IllegalArgumentException("not a local-agent effect: " + effect);
        };
    }

    static Executor singleWriter() {
        return java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "analyser-local-agent-stdin");
            t.setDaemon(true);
            return t;
        });
    }

    private static SessionEvents.LocalAgentEffectStarted started(long opId, long generation, String what) {
        return new SessionEvents.LocalAgentEffectStarted(opId, generation, what);
    }

    // ---- start ------------------------------------------------------------------------------------------------------

    private SessionEvents.Result start(SessionEffects.StartLocalAgentEffect e) {
        // captured NOW, on the session's thread, as immutable values: the worker never reads live configuration
        Spawn s = new Spawn(e, env.model(), env.workingDirectory(), env.restUrl(), env.restToken(), env.bridgeLauncher(),
                Map.copyOf(env.environment()), env.userHome(),
                List.copyOf(env.fullTrustRecords() == null ? List.of() : env.fullTrustRecords()));
        Run run = new Run(e.generation(), e.sessionId(), e.nonce(), writers.get());
        runs.put(e.generation(), run);
        worker.execute(() -> spawn(run, s));
        return started(e.opId(), e.generation(), "start");
    }

    private record Spawn(SessionEffects.StartLocalAgentEffect effect, String model, Path cwd, String restUrl,
                         String restToken, Optional<List<String>> launcher, Map<String, String> environment, Path home,
                         List<String> fullTrust) {
    }

    private void spawn(Run run, Spawn s) {
        long gen = run.generation;
        // review M7: the node decided this spawn for the posture digest it carries, which names the REAL folder. The
        // folder read now must digest to the same, or a project opened or closed since the decision would hand Claude
        // a folder nobody confirmed
        if (s.cwd() == null || !ClaudeCodeProvider.postureRef(s.cwd(), s.effect().posture()).equals(s.effect().postureRef())) {
            fail(run, SessionEvents.SpawnFailure.FOLDER_CHANGED, 0);
            return;
        }
        // holistic review B7: the full posture runs the folder's hooks, servers and instructions — the folder read NOW must
        // still be the one the person trusted, by its FULL digest (a hook added after the confirmation is refused here)
        if (s.effect().posture() != null && s.effect().posture().isFull() && !FullFolderTrust.confirms(s.fullTrust(),
                ClaudeCodeProvider.realPath(s.cwd()), FullFolderTrust.digest(s.cwd()))) {
            fail(run, SessionEvents.SpawnFailure.TRUST_CHANGED, 0);
            return;
        }
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
        if (!ClaudeCodeProvider.validModel(s.model())) {
            fail(run, SessionEvents.SpawnFailure.INVALID_MODEL, 0);
            return;
        }
        run.restToken = s.restToken();
        // review M3: our OWN previous process of this conversation may still hold the lock while its tree stops; wait
        // (bounded) for it to be cleaned up, and never call that "another analyser window"
        for (Run other : List.copyOf(runs.values())) {
            if (other == run || !other.sessionId.equals(run.sessionId)) continue;
            try {
                other.cleaned.get(handoverWaitMs, java.util.concurrent.TimeUnit.MILLISECONDS);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                fail(run, SessionEvents.SpawnFailure.PREVIOUS_STILL_STOPPING, 0);
                return;
            } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException ex) {
                fail(run, SessionEvents.SpawnFailure.PREVIOUS_STILL_STOPPING, 0);
                return;
            }
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
            run.hosted = hosted;
            // review finding 6: the hosted file carries no shared REST token; the bridge sends its credential alone
            new RestEndpointFile(run.endpointFile).writeHosted(s.restUrl(), null, run.nonce, hosted);
        } catch (IOException | RuntimeException ex) {
            fail(run, SessionEvents.SpawnFailure.ENDPOINT_FAILED, 0);
            return;
        }
        var posture = s.effect().posture();
        List<String> command = provider.command(new LocalAgentProvider.LaunchSpec(found.executable(), run.sessionId,
                s.effect().resume(), s.model(), s.cwd(), s.launcher().get(), run.endpointFile, s.effect().maxBudgetMicros(),
                posture));
        // OD-74.11: computed once from the analyser's own environment, never by a shell, and used for both the child's
        // environment and the header; the default posture's environment is unchanged
        ChildPath.Extension path = posture.isFull() ? ChildPath.extend(s.environment(), s.home()) : null;
        Map<String, String> childEnv = provider.environment(posture, path);
        // review finding 1: a host Claude Code session's variables never reach the child, in either posture
        java.util.Set<String> removedEnv = ChildEnvironment.removed(s.environment(), posture);
        if (path != null) {
            List<String> shown = new ArrayList<>();
            for (String d : path.appended()) shown.add(ChildPath.shown(d, s.home()));
            run.pathAdded = List.copyOf(shown);
        }
        run.startedAt = clock.nowMillis();
        run.folder = s.cwd().toString();
        // P2b (§5.2): where requests go, as far as the analyser can see — the base URL it passed, host only
        run.endpoint = removedEnv.contains("ANTHROPIC_BASE_URL") ? null : EndpointLine.of(
                childEnv.getOrDefault("ANTHROPIC_BASE_URL", s.environment().get("ANTHROPIC_BASE_URL")));
        run.scratchForProject = env.scratchForProject();
        run.configuredModel = s.model() == null || s.model().isBlank() ? ClaudeCodeProvider.DEFAULT_MODEL : s.model().trim();
        try {
            AgentProcess p = launcher.start(command, childEnv, removedEnv, s.cwd(), line -> onLineGuarded(run, line));
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
        long ticket = e.ticket();
        write(run, StreamJson.userLine(uuid, store.text(e.draft())),
                () -> endTurn(run, ticket, SessionEvents.TurnOutcome.PROCESS_EXIT));
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

    /** Queue one stdin line on the run's own writer: the caller (often the event thread) never waits for the pipe. */
    private void write(Run run, String line) {
        write(run, line, null);
    }

    /** As {@link #write(Run, String)}; {@code onFailure} runs on the writer when the line could not be written. */
    private void write(Run run, String line, Runnable onFailure) {
        Runnable task = () -> {
            AgentProcess p;
            synchronized (run) {
                p = run.process;
            }
            boolean ok = false;
            if (p != null) {
                try {
                    p.writeLine(line);
                    ok = true;
                } catch (IOException ex) {
                    ok = false;
                }
            }
            if (!ok && onFailure != null) onFailure.run();
        };
        try {
            run.writer.execute(task);
        } catch (java.util.concurrent.RejectedExecutionException ex) {
            // review R1: the run was cleaned up (its writer shut down) — a late write is a write to a gone process
            if (onFailure != null) onFailure.run();
        }
    }

    // ---- reading the stream ---------------------------------------------------------------------------------------

    /**
     * Review P3 S2 (2026-10-17): the stdout reader must never die silently — a line that throws (an Error included) is
     * counted as unknown, as an unparsable line is, and the reader reads the next one.
     */
    void onLineGuarded(Run run, String line) {
        try {
            onLine(run, line);
        } catch (RuntimeException | StackOverflowError ex) {
            unknownEvents++;
            synchronized (run) {
                run.unknown++;
            }
        }
    }

    void onLine(Run run, String line) {
        StreamJson.Event event = provider.parse(line);
        long gen = run.generation;
        switch (event) {
            case StreamJson.ControlResponse r -> onControlResponse(run, r);
            case StreamJson.ControlRequest r -> onControlRequest(run, r);
            case StreamJson.ControlCancelRequest c -> {
                // P3-2: the CLI withdrew a request itself (an interrupt while one was pending); the node closes the dialog
                Long ref = run.permissionByRequestId.get(c.requestId() == null ? "" : c.requestId());
                if (ref != null) env.post(new SessionEvents.LocalAgentPermissionCancelled(gen, ref));
            }
            case StreamJson.SystemInit i -> {
                long headerRef;
                synchronized (run) {
                    run.header = (run.header == null ? new LocalAgentTranscript.Header(null, null, "", null, List.of(),
                            run.folder, run.configuredModel, run.pathAdded, run.scratchForProject, run.endpoint) : run.header)
                            .withInit(i.model(), i.cliVersion());
                    headerRef = store.putHeader(run.header);
                }
                env.post(new SessionEvents.LocalAgentInitObserved(gen, run.runningTicket,
                        LocalAgentPosture.digest(i.sessionId()), i.permissionMode(), i.tools(), i.servers(),
                        i.capabilities(), headerRef, i.skills(), i.plugins()));
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
                boolean changed;
                synchronized (run) {
                    changed = a.toolUse() != null ? run.toolUse(a.messageId(), a.toolUse(), a.toolUseId(), a.toolInput())
                            : run.block(a.messageId(), a.text());
                }
                if (changed) textChanged(run);
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
                // the tool line's mark follows the CLI's own reason (an enum, review finding 10); the node decides the note
                String outcome = p.reason().words();
                boolean marked;
                synchronized (run) {
                    marked = run.toolOutcome(p.toolUseId(), outcome);
                }
                if (marked) textChanged(run);
                SessionEvents.AgentNote note = p.approvalUnavailable() ? SessionEvents.AgentNote.APPROVAL_UNAVAILABLE
                        : SessionEvents.AgentNote.PERMISSION_DENIED;
                // scorecard R2: the note names the tool, from a fixed vocabulary, once per tool and turn
                String tool = ToolVocabulary.word(p.toolName());
                if (run.deniedNoted.add(run.runningTicket + ":" + note + ":" + tool)) {
                    env.post(new SessionEvents.LocalAgentNoted(gen, run.runningTicket, note, 0, tool));
                }
            }
            // the mode the CLI now reports, mid-session: the node judges it against the posture (review finding 2)
            case StreamJson.ModeStatus m -> env.post(new SessionEvents.LocalAgentModeObserved(gen, run.runningTicket,
                    m.permissionMode()));
            // a hook's event and outcome only: its output never left the parser (§4.4 step 1)
            case StreamJson.Hook h -> env.post(new SessionEvents.LocalAgentHookObserved(gen, run.runningTicket,
                    SessionEvents.HookEvent.of(h.event()), h.started() ? SessionEvents.HookOutcome.STARTED
                    : h.succeeded() ? SessionEvents.HookOutcome.OK : SessionEvents.HookOutcome.FAILED));
            case StreamJson.Unknown u -> {
                unknownEvents++;
                synchronized (run) {
                    run.unknown++;
                }
            }
            // a replay, a tool result or the compaction summary is never the person's turn: their words are the send
            // record, so a user event is shown as nothing at all (§4.5)
            case StreamJson.UserMessage u -> {
                boolean marked = false;
                synchronized (run) {
                    for (var r : u.toolResults().entrySet()) {
                        marked |= run.toolOutcome(r.getKey(), r.getValue() ? "refused" : "✓");
                    }
                }
                if (marked) textChanged(run);
            }
            case StreamJson.ConversationReset c -> { }          // the next init's session id is what the node judges
            case StreamJson.Ignored i -> { }
        }
    }

    /**
     * P3 (§5.7): a {@code can_use_tool} is OBSERVED here and decided by {@code localAgentPermission}. Its words go to the
     * bounded store; the fact carries the store reference, the tool's class and vocabulary name, the canonical input's
     * digest, the {@code tool_use_id}'s digest and whether the input can be shown in full. Any other request from the CLI
     * is denied, never granted.
     */
    private void onControlRequest(Run run, StreamJson.ControlRequest r) {
        if (!r.canUseTool() || r.requestId() == null) {
            write(run, StreamJson.deny(r.requestId(), "Denied: the analyser does not answer this request"));
            return;
        }
        ToolVocabulary.Named named = ToolVocabulary.of(r.toolName());
        telamin.fluxtion.audit.analyser.analyser.llm.CanonicalJson.Judged judged =
                telamin.fluxtion.audit.analyser.analyser.llm.CanonicalJson.judge(r.inputRaw());
        long ref = store.putPermission(new LocalAgentTranscript.Permission(r.toolName(), r.serverName(), r.displayName(),
                r.inputRaw()));
        long ticket;
        synchronized (run) {
            run.pendingPermissions.put(ref, new String[]{r.requestId(), r.inputRaw(), r.toolUseId()});
            run.permissionByRequestId.put(r.requestId(), ref);
            ticket = run.runningTicket;
        }
        env.post(new SessionEvents.LocalAgentPermissionRequested(run.generation, ticket, ref, named.toolClass(),
                named.name(), judged.digest(), r.toolUseId() == null ? null : LocalAgentPosture.digest(r.toolUseId()),
                judged.showable()));
    }

    /**
     * The node's answer, written exactly: allow with {@code updatedInput} = the stored input (the raw text the request
     * carried, which the person was shown), or deny with the node's message. The tool line is marked with who decided.
     * A grant's 60 s are measured from here.
     */
    private SessionEvents.Result answerPermission(SessionEffects.AnswerPermissionEffect e) {
        Run run = runs.get(e.generation());
        String[] pending = null;
        if (run != null) {
            synchronized (run) {
                pending = run.pendingPermissions.remove(e.requestRef());
                if (pending != null) run.permissionByRequestId.remove(pending[0]);
            }
        }
        boolean written = run != null && pending != null;
        if (written) {
            boolean allow = e.allow() && pending[1] != null && !pending[1].isBlank();
            // the line is marked BEFORE the answer is written: the tool's own result can follow the answer at once
            boolean marked;
            synchronized (run) {
                marked = run.toolMark(pending[2], markWords(allow, e.by()), allow);
            }
            if (marked) textChanged(run);
            write(run, allow ? StreamJson.allow(pending[0], pending[1])
                    : StreamJson.deny(pending[0], e.message() == null ? "Denied in the analyser" : e.message()));
            if (allow && e.grantId() != 0) {
                long gen = e.generation();
                long grant = e.grantId();
                clock.schedule(GRANT_MS, () -> env.post(new SessionEvents.LocalAgentGrantExpired(gen, grant)));
            }
        }
        return new SessionEvents.LocalAgentPermissionAnswered(e.opId(), e.generation(), e.ticket(), e.requestRef(),
                e.toolClass(), e.name(), e.allow() && written, e.by(), written);
    }

    /** §8.3 UI: the tool line's mark, by who decided. */
    static String markWords(boolean allow, SessionEvents.PermissionBy by) {
        if (allow) return "approved by you";
        return switch (by == null ? SessionEvents.PermissionBy.POLICY : by) {
            case PERSON -> "denied by you";
            case TIMEOUT -> "denied: not answered within 120 s";
            case STOP -> "denied: Stop";
            case BUSY -> "denied: another approval was waiting";
            case TOO_LARGE -> "denied: too large to show you in full";
            case CANCELLED -> "withdrawn by Claude Code";
            case POLICY -> "denied: not asked in this posture";
        };
    }

    /** How long a grant lives after the person's Allow (OD-74.17); the node decides what its expiry means. */
    static final long GRANT_MS = telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentPermission.GRANT_MS;

    private void onControlResponse(Run run, StreamJson.ControlResponse r) {
        long gen = run.generation;
        String id = r.requestId() == null ? "" : r.requestId();
        if (id.equals("init-" + gen) && r.initialize() != null) {
            StreamJson.Initialize init = r.initialize();
            lastCommands = init.commands();
            // review finding 11: an API key in force is what is billed, even beside a subscription
            String billing = init.apiKeySource() != null ? "API key (" + init.apiKeySource() + ")"
                    : init.subscriptionType() != null ? init.subscriptionType() : "";
            long headerRef;
            synchronized (run) {
                run.header = new LocalAgentTranscript.Header(null, null, billing, init.apiProvider(),
                        ClaudeCodeProvider.unclassified(init.commands()), run.folder, run.configuredModel, run.pathAdded,
                        run.scratchForProject, run.endpoint);
                run.unclassified = ClaudeCodeProvider.unclassified(init.commands()).size();
                headerRef = store.putHeader(run.header);
            }
            // how it is paid for, as an enum: a Claude plan, or an API key — never the plan's or the source's value
            SessionEvents.Billing paid = init.apiKeySource() != null ? SessionEvents.Billing.API_KEY
                    : init.subscriptionType() != null ? SessionEvents.Billing.SUBSCRIPTION : SessionEvents.Billing.UNKNOWN;
            env.post(new SessionEvents.LocalAgentInitialized(gen, init.loggedIn(), init.permissionMode(), headerRef, paid));
            diagnostics(run);
        } else if (id.startsWith("status-" + gen + "-") && r.status() != null) {
            List<String> names = new ArrayList<>();
            String hostedStatus = null;
            String nonceDigest = null;
            for (StreamJson.Server s : r.status().servers()) {
                if (s.name() == null) continue;
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

    /** §5.2: the diagnostics counts as a coalesced fact — posted only when one changed. */
    private void diagnostics(Run run) {
        int u;
        int c;
        int d;
        synchronized (run) {
            u = run.unknown;
            c = run.unclassified;
            d = run.dropped;
            String now = u + "/" + c + "/" + d;
            if (now.equals(run.reportedDiagnostics)) return;
            run.reportedDiagnostics = now;
        }
        env.post(new SessionEvents.LocalAgentDiagnostics(run.generation, u, c, d));
    }

    private void onResult(Run run, StreamJson.Result r) {
        long gen = run.generation;
        Long t = r.userMessageUuid() == null ? null : run.ticketByUuid.get(r.userMessageUuid());
        long ticket = t != null ? t : run.runningTicket;
        // P2b (§5.9): the running total, with the turn it closes and the turn's time
        if (r.totalCostMicros() >= 0 || r.durationMs() >= 0) {
            env.post(new SessionEvents.LocalAgentCostObserved(gen, r.totalCostMicros(), ticket, r.durationMs()));
        }
        diagnostics(run);
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
        // review S4: the exact secrets this run holds are redacted too, wherever the child echoed them
        String tail = p == null ? "" : telamin.fluxtion.audit.analyser.analyser.core.Redaction.all(p.stderrTail(), run.restToken, run.hosted);
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
        if (run.writer instanceof java.util.concurrent.ExecutorService es) es.shutdown();
        run.cleaned.complete(null);
    }

    // ---- one generation -----------------------------------------------------------------------------------------

    /** What one spawn holds. Guarded by its own monitor: the reader thread and the session's thread both touch it. */
    static final class Run {
        final long generation;
        final String sessionId;
        final String nonce;
        final Map<String, Long> ticketByUuid = new ConcurrentHashMap<>();
        final Set<Long> ended = ConcurrentHashMap.newKeySet();
        final Set<String> deniedNoted = ConcurrentHashMap.newKeySet();
        /** P3: requests waiting for the node's answer, by store reference: {request id, raw input, tool_use_id}. */
        final Map<Long, String[]> pendingPermissions = new LinkedHashMap<>();
        final Map<String, Long> permissionByRequestId = new ConcurrentHashMap<>();
        final Executor writer;
        /** Completes when this run's lock, credential and endpoint file have been released (review M3). */
        final java.util.concurrent.CompletableFuture<Void> cleaned = new java.util.concurrent.CompletableFuture<>();
        volatile String restToken;
        volatile String hosted;
        volatile String folder;
        /** P2b (§5.2): {@code scheme://host[:port]} of the base URL passed to the child, or null. */
        volatile String endpoint;
        /** P2b (§5.2): this run's diagnostics counts, and what was last reported (a fact only when one changes). */
        int unknown;
        int unclassified;
        int dropped;
        String reportedDiagnostics = "0/0/0";
        volatile boolean scratchForProject;
        volatile String configuredModel;
        /** The directories appended to the child's PATH, as the header shows them (OD-74.11). */
        volatile List<String> pathAdded = List.of();
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

        Run(long generation, String sessionId, String nonce, Executor writer) {
            this.writer = writer;
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
            toolLines.clear();
            toolKeys.clear();
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

        /** The tool lines of this turn, by tool-use id: the key of the line in {@link #blocks}. */
        final Map<String, String> toolLines = new LinkedHashMap<>();
        final Set<String> toolKeys = new HashSet<>();

        /**
         * One compact line per tool use: {@code ▸ analyser_read {"count":3}}. The name loses the hosted server's
         * prefix; the input is already bounded by the parser. It is the model's data, shown as text and never run.
         *
         * @return true (the answer changed)
         */
        boolean toolUse(String messageId, String name, String id, String input) {
            String msg = messageId == null ? "?" : messageId;
            int index = blocksSeen.merge(msg, 1, Integer::sum) - 1;
            String key = msg + "#" + index;
            String shown = name.startsWith("mcp__" + LocalAgentPosture.HOSTED_SERVER + "__")
                    ? name.substring(("mcp__" + LocalAgentPosture.HOSTED_SERVER + "__").length()) : name;
            blocks.put(key, new StringBuilder("▸ " + shown + (input == null ? "" : " " + input)));
            finals.add(key);
            toolKeys.add(key);
            if (id != null) toolLines.put(id, key);
            return true;
        }

        /**
         * P3: mark the line of tool use {@code id} with the permission's outcome. {@code keep}: the line stays open for the
         * tool's own result (an approval is followed by ✓ or refused); a denial closes it.
         */
        boolean toolMark(String id, String outcome, boolean keep) {
            String key = id == null ? null : keep ? toolLines.get(id) : toolLines.remove(id);
            if (key == null || !blocks.containsKey(key)) return false;
            blocks.get(key).append(" — ").append(outcome);
            return true;
        }

        /** Mark the line of tool use {@code id} with its outcome, once; false when there is no such line. */
        boolean toolOutcome(String id, String outcome) {
            String key = id == null ? null : toolLines.remove(id);
            if (key == null || !blocks.containsKey(key)) return false;
            blocks.get(key).append(" — ").append(outcome);
            return true;
        }

        void localOutput(String text) {
            localOutput = text;
        }

        /** The answer: text blocks a paragraph apart, consecutive tool lines one line apart. */
        String compose() {
            StringBuilder out = new StringBuilder();
            boolean previousTool = false;
            for (var e : blocks.entrySet()) {
                if (e.getValue().isEmpty()) continue;
                boolean tool = toolKeys.contains(e.getKey());
                if (!out.isEmpty()) out.append(tool && previousTool ? "\n" : "\n\n");
                out.append(e.getValue());
                previousTool = tool;
            }
            if (out.isEmpty() && localOutput != null) out.append(localOutput);
            return out.toString();
        }
    }

    /** The node's bound, used for the deadline the adapter measures (the node decides what it means). */
    static final class LocalAgentSessionLimits {
        static final long START_LIMIT_MS = telamin.fluxtion.audit.analyser.analyser.session.node.LocalAgentSession.START_LIMIT_MS;
    }
}
