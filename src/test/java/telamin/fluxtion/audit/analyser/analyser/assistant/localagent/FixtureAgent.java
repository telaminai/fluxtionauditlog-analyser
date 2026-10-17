package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import telamin.fluxtion.audit.analyser.analyser.llm.Json;
import telamin.fluxtion.audit.analyser.analyser.net.RestEndpointFile;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.UnaryOperator;

/**
 * A FAKE Claude Code: it replays the sanitised P0/P0b fixtures (docs/specs/evidence/m74-p0/fixtures) in answer to
 * what the adapter writes, so the adapter and the real session processor are exercised without a live CLI.
 *
 * <p>How a fixture is replayed: its turns are cut at {@code command_lifecycle started → completed/cancelled}; the n-th
 * user line the adapter writes gets the n-th turn, with the fixture's command uuid replaced by the adapter's fresh one
 * and the fixture's session id by the adapter's. {@code initialize} and {@code mcp_status} are answered from the
 * fixture's own control responses when it has them (re-keyed to the adapter's request id), else from
 * {@code control-initialize.jsonl} and a {@code connected} status bound to the nonce the adapter wrote into its
 * endpoint file. With {@link #conformInit}, each turn's {@code system/init} is rewritten to the default posture's tools
 * and server, because most P0 runs used a stub server of other names; the rule under test is then the parser's.
 */
final class FixtureAgent implements AgentProcess.Launcher {

    static final Path FIXTURES = Path.of("docs/specs/evidence/m74-p0/fixtures");
    /** The P1b probes' isolated captures (PB-report.md). */
    static final Path PB_FIXTURES = Path.of("docs/specs/evidence/m74-p1b/fixtures");
    static final String FIXTURE_SESSION = "00000000-0000-4000-8000-000000000000";

    final List<List<String>> commands = new ArrayList<>();
    final List<Map<String, String>> environments = new ArrayList<>();
    /** The names the adapter asked to be removed from the child's environment, per start. */
    final List<java.util.Set<String>> removedEnvironments = new ArrayList<>();
    final List<Path> workingDirs = new ArrayList<>();
    final List<Proc> processes = new ArrayList<>();

    private final Deque<List<String>> turns = new ArrayDeque<>();
    private final List<String> initializeLines = new ArrayList<>();
    private final Deque<String> statusLines = new ArrayDeque<>();
    /** Lines the CLI emits at spawn, before any turn (hook events): replayed before the first initialize answer. */
    private final List<String> spawnLines = new ArrayList<>();
    private final Path dir;
    boolean conformInit;
    /**
     * P1b: rewrite each turn's {@code system/init} so its HOSTED tools are the real pre-allowed verbs (the probe's stub
     * advertised {@code context}/{@code echo}), keeping everything else the CLI reported — the person's built-in tools,
     * any registration tools, the servers and the mode. The rule under test is then the full-posture assertion's.
     */
    boolean conformHostedTools;
    boolean ackInterrupts = true;
    boolean cancelOnInterrupt = true;
    boolean exitOnKill = true;
    /** Replaces the nonce the bound status reports: null keeps the endpoint file's (an exact bind). */
    UnaryOperator<String> statusVersion;
    /** Applied to every replayed line, for rewrites a test needs (an e-mail in the account, a dropped result). */
    UnaryOperator<String> rewrite = UnaryOperator.identity();
    /** Given to every process started from now on: its stdin writes wait for this latch. */
    java.util.concurrent.CountDownLatch blockNewWrites;

    FixtureAgent(String fixture) throws IOException {
        this(FIXTURES, fixture);
    }

    FixtureAgent(Path dir, String fixture) throws IOException {
        this.dir = dir;
        load(fixture);
    }

    static List<String> lines(String fixture) throws IOException {
        return lines(FIXTURES, fixture);
    }

    static List<String> lines(Path dir, String fixture) throws IOException {
        List<String> out = new ArrayList<>();
        for (String l : Files.readAllLines(dir.resolve(fixture))) if (!l.isBlank()) out.add(l);
        return out;
    }

    private void load(String fixture) throws IOException {
        List<String> current = null;
        String currentUuid = null;
        List<String> loose = new ArrayList<>();
        for (String line : lines(dir, fixture)) {
            Map<?, ?> m = (Map<?, ?>) Json.parse(line);
            String type = String.valueOf(m.get("type"));
            if (current == null && "system".equals(type) && String.valueOf(m.get("subtype")).startsWith("hook_")) {
                spawnLines.add(line);
                continue;
            }
            if ("control_response".equals(type)) {
                Map<?, ?> body = (Map<?, ?>) ((Map<?, ?>) m.get("response")).get("response");
                if (body != null && body.containsKey("mcpServers")) statusLines.add(line);
                else if (body != null && (body.containsKey("commands") || body.containsKey("account"))) initializeLines.add(line);
                continue;
            }
            if ("command_lifecycle".equals(type)) {
                String uuid = String.valueOf(m.get("command_uuid"));
                String state = String.valueOf(m.get("state"));
                if ("started".equals(state) && current == null) {
                    current = new ArrayList<>();
                    currentUuid = uuid;
                    current.add(line.replace("\"started\"", "\"queued\""));
                    current.add(line);
                    continue;
                }
                if (current != null && uuid.equals(currentUuid) && ("completed".equals(state) || "cancelled".equals(state))) {
                    current.add(line);
                    turns.add(rekey(current, currentUuid));
                    current = null;
                    currentUuid = null;
                }
                continue;                         // lifecycle of a queued other turn: never replayed
            }
            if (current != null) current.add(line);
            else loose.add(line);
        }
        if (current != null) turns.add(rekey(current, currentUuid));   // a turn the run never ended (a kill)
        if (turns.isEmpty() && !loose.isEmpty()) turns.add(rekey(loose, null));
        if (initializeLines.isEmpty()) {
            for (String l : lines("control-initialize.jsonl")) initializeLines.add(l);
        }
    }

    private static List<String> rekey(List<String> segment, String uuid) {
        List<String> out = new ArrayList<>();
        for (String l : segment) out.add(uuid == null ? l : l.replace(uuid, "@@UUID@@"));
        return out;
    }

    int turnsLeft() {
        return turns.size();
    }

    @Override
    public AgentProcess start(List<String> command, Map<String, String> extraEnvironment,
                              java.util.Set<String> removedEnvironment, Path workingDir, Consumer<String> stdoutLine) {
        commands.add(command);
        environments.add(extraEnvironment);
        removedEnvironments.add(removedEnvironment);
        workingDirs.add(workingDir);
        Proc p = new Proc(command, stdoutLine);
        p.blockWrites = blockNewWrites;
        processes.add(p);
        return p;
    }

    Proc last() {
        return processes.get(processes.size() - 1);
    }

    final class Proc implements AgentProcess {
        final List<String> stdin = new ArrayList<>();
        final CompletableFuture<Integer> exit = new CompletableFuture<>();
        final Consumer<String> out;
        final String sessionId;
        final Path endpointFile;
        String runningUuid;
        int terminated;
        String stderr = "";

        Proc(List<String> command, Consumer<String> out) {
            this.out = out;
            int s = Math.max(command.indexOf("--session-id"), command.indexOf("--resume"));
            this.sessionId = command.get(s + 1);
            Map<?, ?> mcp = (Map<?, ?>) Json.parse(command.get(command.indexOf("--mcp-config") + 1));
            Map<?, ?> server = (Map<?, ?>) ((Map<?, ?>) mcp.get("mcpServers")).get(LocalAgentPosture.HOSTED_SERVER);
            List<?> args = (List<?>) server.get("args");
            this.endpointFile = Path.of(String.valueOf(args.get(args.size() - 1)));
        }

        /** Emit a line exactly as the CLI would, after the session id and rewrites. */
        final List<String> emitted = new java.util.concurrent.CopyOnWriteArrayList<>();
        /** When set, every stdin write waits for it (a pipe the child is not reading). */
        java.util.concurrent.CountDownLatch blockWrites;

        void emit(String line) {
            String l = rewrite.apply(line.replace(FIXTURE_SESSION, sessionId));
            if (l != null) {
                emitted.add(l);
                out.accept(l);
            }
        }

        List<String> userLines() {
            return stdin.stream().filter(l -> l.contains("\"type\":\"user\"")).toList();
        }

        @Override
        public void writeLine(String line) throws IOException {
            if (blockWrites != null) {
                try {
                    blockWrites.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new IOException("interrupted");
                }
            }
            if (exit.isDone()) throw new IOException("stdin closed");
            stdin.add(line);
            Map<?, ?> m = (Map<?, ?>) Json.parse(line);
            if ("control_request".equals(m.get("type"))) {
                String id = String.valueOf(m.get("request_id"));
                String subtype = String.valueOf(((Map<?, ?>) m.get("request")).get("subtype"));
                switch (subtype) {
                    case "initialize" -> {
                        for (String l : spawnLines) emit(l);          // the hooks ran at spawn [P0-28, PB-2]
                        spawnLines.clear();
                        emit(rekeyResponse(initializeLines.get(0), id));
                    }
                    case "mcp_status" -> emit(rekeyResponse(nextStatus(), id));
                    case "interrupt" -> {
                        if (ackInterrupts) emit("{\"type\":\"control_response\",\"response\":{\"subtype\":\"success\","
                                + "\"request_id\":\"" + id + "\",\"response\":{\"still_queued\":[]}}}");
                        if (cancelOnInterrupt && runningUuid != null) {
                            emit("{\"type\":\"result\",\"subtype\":\"error_during_execution\",\"is_error\":true,"
                                    + "\"terminal_reason\":\"aborted_streaming\",\"total_cost_usd\":0.001,"
                                    + "\"user_message_uuid\":\"" + runningUuid + "\"}");
                            emit("{\"type\":\"command_lifecycle\",\"command_uuid\":\"" + runningUuid
                                    + "\",\"state\":\"cancelled\"}");
                            runningUuid = null;
                        }
                    }
                    default -> { }
                }
                return;
            }
            if ("user".equals(m.get("type"))) {
                String uuid = String.valueOf(m.get("uuid"));
                List<String> turn = turns.poll();
                if (turn == null) return;
                runningUuid = uuid;
                for (String l : turn) {
                    if (runningUuid == null) break;     // interrupted mid-turn
                    String replayed = l.replace("@@UUID@@", uuid);
                    if (conformInit && replayed.contains("\"subtype\": \"init\"")) replayed = conform(replayed);
                    if (conformHostedTools && replayed.contains("\"subtype\": \"init\"")) replayed = conformHosted(replayed);
                    emit(replayed);
                }
            }
        }

        /** The next status answer: the fixture's sequence (its last repeats), else a bound {@code connected}. */
        private String nextStatus() {
            String fixture = statusLines.size() > 1 ? statusLines.poll() : statusLines.peek();
            String nonce = new RestEndpointFile(endpointFile).read().nonce();
            String version = statusVersion == null ? "1.35.0+bind." + nonce : statusVersion.apply(nonce);
            if (fixture == null) {
                return "{\"type\":\"control_response\",\"response\":{\"subtype\":\"success\",\"request_id\":\"x\","
                        + "\"response\":{\"mcpServers\":[{\"name\":\"" + LocalAgentPosture.HOSTED_SERVER
                        + "\",\"status\":\"connected\",\"serverInfo\":{\"name\":\"" + LocalAgentPosture.HOSTED_SERVER
                        + "\",\"version\":\"" + version + "\"}}]}}}";
            }
            // the fixture's own status, with its recorded nonce replaced by this spawn's (or the test's choice)
            return fixture.replaceAll("\"version\": \"[^\"]*\\+bind\\.[0-9a-f]+\"", "\"version\": \"" + version + "\"");
        }

        private String conform(String initLine) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) Json.parse(initLine)).entrySet()) m.put(String.valueOf(e.getKey()), e.getValue());
            m.put("tools", new ArrayList<>(LocalAgentPosture.expectedTools()));
            m.put("mcp_servers", List.of(Map.of("name", LocalAgentPosture.HOSTED_SERVER, "status", "connected",
                    "source", "dynamic")));
            m.put("permissionMode", LocalAgentPosture.PERMISSION_MODE);
            List<Object> caps = new ArrayList<>(m.get("capabilities") instanceof List<?> l ? l : List.of());
            for (String c : LocalAgentPosture.REQUIRED_CAPABILITIES) if (!caps.contains(c)) caps.add(c);
            m.put("capabilities", caps);
            return Json.write(m);
        }

        private String conformHosted(String initLine) {
            Map<String, Object> m = new LinkedHashMap<>();
            for (Map.Entry<?, ?> e : ((Map<?, ?>) Json.parse(initLine)).entrySet()) m.put(String.valueOf(e.getKey()), e.getValue());
            List<Object> tools = new ArrayList<>();
            for (Object t : m.get("tools") instanceof List<?> l ? l : List.of()) {
                if (!LocalAgentPosture.isHostedTool(String.valueOf(t))) tools.add(t);
            }
            tools.addAll(LocalAgentPosture.expectedHostedTools());
            m.put("tools", tools);
            return Json.write(m);
        }

        @Override
        public long pid() {
            return -1;
        }

        @Override
        public CompletableFuture<Integer> onExit() {
            return exit;
        }

        @Override
        public String stderrTail() {
            return stderr;
        }

        @Override
        public void terminateTree(Duration grace) {
            terminated++;
            if (exitOnKill) exit(143);
        }

        void exit(int code) {
            exit.complete(code);
        }
    }

    static String rekeyResponse(String line, String requestId) {
        return line.replaceFirst("\"request_id\": ?\"[^\"]*\"", "\"request_id\": \"" + requestId + "\"");
    }
}
