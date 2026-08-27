package telamin.fluxtion.audit.analyser.analyser.mcp;

import telamin.fluxtion.audit.analyser.analyser.llm.Json;
import telamin.fluxtion.audit.analyser.analyser.net.RestEndpointFile;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.LongSupplier;

/**
 * A non-mutating self-check for the local MCP route (M42.1).
 *
 * <p>The probe starts the same {@code --mcp} command registered with a client, discovers tools over
 * stdio and calls read-only {@code analyser_context}. It proves command → bridge → endpoint file →
 * tokened REST → current app, without needing to impersonate Codex or Claude.
 */
public final class McpConnectionProbe {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(5);
    private static final int STDERR_LIMIT = 8 * 1024;

    public enum Status {
        VERIFIED,
        REST_OFF,
        OTHER_INSTANCE,
        LAUNCH_FAILED,
        PROTOCOL_FAILED,
        ACTION_FAILED
    }

    /** Modern is preferred; legacy is an intentional compatibility fallback for an older bridge. */
    public enum Era { NONE, MODERN, LEGACY }

    /** A safe, bounded diagnostic; it never includes the endpoint token or a raw tool response. */
    public record Result(Status status, Era era, String detail) {
        public boolean verified() {
            return status == Status.VERIFIED;
        }
    }

    @FunctionalInterface
    interface EndpointSource {
        RestEndpointFile.Endpoint read();
    }

    @FunctionalInterface
    interface EndpointLiveness {
        boolean alive(RestEndpointFile.Endpoint endpoint);
    }

    @FunctionalInterface
    interface ProcessStarter {
        Process start(List<String> command) throws IOException;
    }

    private final McpLaunchCommand command;
    private final EndpointSource endpoints;
    private final EndpointLiveness liveness;
    private final LongSupplier thisPid;
    private final ProcessStarter starter;
    private final Duration timeout;

    public McpConnectionProbe(McpLaunchCommand command, RestEndpointFile endpointFile) {
        this(command, endpointFile::read, RestEndpointFile.Endpoint::alive,
                () -> ProcessHandle.current().pid(), c -> new ProcessBuilder(c).start(), DEFAULT_TIMEOUT);
    }

    McpConnectionProbe(McpLaunchCommand command, EndpointSource endpoints, EndpointLiveness liveness,
                       LongSupplier thisPid, ProcessStarter starter, Duration timeout) {
        this.command = command;
        this.endpoints = endpoints;
        this.liveness = liveness;
        this.thisPid = thisPid;
        this.starter = starter;
        this.timeout = timeout;
    }

    /** Run one bounded health check. Nothing it sends is a render or persistent action. */
    public Result probe() {
        RestEndpointFile.Endpoint endpoint = endpoints.read();
        if (endpoint == null || !liveness.alive(endpoint)) {
            return result(Status.REST_OFF, Era.NONE, "no live local analyser endpoint");
        }
        if (endpoint.pid() != thisPid.getAsLong()) {
            return result(Status.OTHER_INSTANCE, Era.NONE, "another analyser process owns the local MCP endpoint");
        }

        Process process;
        try {
            process = starter.start(command.command());
        } catch (IOException | RuntimeException e) {
            return result(Status.LAUNCH_FAILED, Era.NONE, "could not launch the configured MCP bridge");
        }

        Result result = talkTo(process);
        // McpBridge deliberately re-reads the endpoint before every tool call so it survives an app
        // restart. That creates a narrow last-writer-wins window after our preflight: another analyser
        // can publish between it and the context call. Re-check before we ever claim THIS window works.
        if (result.verified()) {
            RestEndpointFile.Endpoint after = endpoints.read();
            if (after != null && liveness.alive(after) && after.pid() != thisPid.getAsLong()) {
                return result(Status.OTHER_INSTANCE, Era.NONE,
                        "another analyser process took ownership of the local MCP endpoint during the check");
            }
        }
        return result;
    }

    private Result talkTo(Process process) {
        ExecutorService readers = Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "analyser-mcp-probe");
            t.setDaemon(true);
            return t;
        });
        Future<String> stderr = readers.submit(() -> drain(process.getErrorStream()));
        try (BufferedReader out = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
             BufferedWriter in = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8))) {
            Era era = discover(in, out, readers);
            requestTools(in, out, readers, era);
            return requestContext(in, out, readers, era);
        } catch (RestUnavailable e) {
            return result(Status.REST_OFF, Era.NONE, "the bridge could not reach this analyser's local transport");
        } catch (ProbeFailure e) {
            return result(e.status, e.era, e.getMessage());
        } catch (IOException | RuntimeException e) {
            return result(Status.PROTOCOL_FAILED, Era.NONE, "the MCP bridge did not complete its protocol exchange");
        } finally {
            closeProcess(process);
            readers.shutdownNow();
            // Drain stderr while the process is alive so it cannot block on a full pipe. It is intentionally
            // not shown in a successful result; a later UI may offer the redacted tail for diagnostics.
            try {
                stderr.get(100, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (ExecutionException | TimeoutException ignore) {
                // bounded best-effort diagnostics must never turn a failed probe into a hung UI action
            }
        }
    }

    private Era discover(BufferedWriter in, BufferedReader out, ExecutorService readers)
            throws IOException, ProbeFailure, RestUnavailable {
        Object modern = exchange(in, out, readers, "server/discover", "discover", Map.of("_meta", meta()));
        if (errorCode(modern) == -32601 || errorCode(modern) == -32022) {
            Object legacy = exchange(in, out, readers, "initialize", "initialize", Map.of(
                    "protocolVersion", McpBridge.LEGACY.get(0), "capabilities", Map.of(),
                    "clientInfo", Map.of("name", "fluxtion-analyser-loopback", "version", "1")));
            requireResult(legacy, Era.LEGACY, "legacy initialize");
            return Era.LEGACY;
        }
        requireResult(modern, Era.MODERN, "server discovery");
        return Era.MODERN;
    }

    private void requestTools(BufferedWriter in, BufferedReader out, ExecutorService readers, Era era)
            throws IOException, ProbeFailure, RestUnavailable {
        Object tools = exchange(in, out, readers, "tools/list", "tools", paramsFor(era, Map.of()));
        Map<?, ?> result = requireResult(tools, era, "tool discovery");
        if (!(result.get("tools") instanceof List<?> list)
                || list.stream().noneMatch(t -> t instanceof Map<?, ?> tool
                && "analyser_context".equals(tool.get("name")))) {
            throw new ProbeFailure(Status.PROTOCOL_FAILED, era, "the MCP bridge did not advertise analyser_context");
        }
    }

    private Result requestContext(BufferedWriter in, BufferedReader out, ExecutorService readers, Era era)
            throws IOException, ProbeFailure, RestUnavailable {
        Object call = exchange(in, out, readers, "tools/call", "context", paramsFor(era, Map.of(
                "name", "analyser_context", "arguments", Map.of())));
        int code = errorCode(call);
        if (code == McpBridge.ERR_ANALYSER_UNREACHABLE) throw new RestUnavailable();
        Map<?, ?> result = requireResult(call, era, "analyser_context");
        if (Boolean.TRUE.equals(result.get("isError"))) {
            return result(Status.ACTION_FAILED, era, "analyser_context reached the app but returned an error");
        }
        return result(Status.VERIFIED, era, "the configured bridge reached this analyser");
    }

    private Object exchange(BufferedWriter in, BufferedReader out, ExecutorService readers, String method,
                            String id, Map<String, Object> params) throws IOException, ProbeFailure {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("id", id);
        request.put("method", method);
        request.put("params", params);
        in.write(Json.write(request));
        in.write('\n');
        in.flush();
        String line = readLine(out, readers);
        try {
            return Json.parse(line);
        } catch (RuntimeException e) {
            throw new ProbeFailure(Status.PROTOCOL_FAILED, Era.NONE, "the MCP bridge returned invalid JSON");
        }
    }

    private String readLine(BufferedReader reader, ExecutorService readers) throws ProbeFailure {
        Future<String> line = readers.submit((Callable<String>) reader::readLine);
        try {
            String value = line.get(Math.max(1, timeout.toMillis()), TimeUnit.MILLISECONDS);
            if (value == null) throw new ProbeFailure(Status.PROTOCOL_FAILED, Era.NONE, "the MCP bridge closed its output");
            return value;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ProbeFailure(Status.PROTOCOL_FAILED, Era.NONE, "the MCP probe was interrupted");
        } catch (ExecutionException | TimeoutException e) {
            line.cancel(true);
            throw new ProbeFailure(Status.PROTOCOL_FAILED, Era.NONE, "the MCP bridge did not reply in time");
        }
    }

    private static Map<String, Object> paramsFor(Era era, Map<String, Object> values) {
        if (era == Era.LEGACY) return values;
        Map<String, Object> withMeta = new LinkedHashMap<>(values);
        withMeta.put("_meta", meta());
        return withMeta;
    }

    private static Map<String, Object> meta() {
        return Map.of(McpBridge.META_PROTOCOL_VERSION, McpBridge.MODERN,
                "io.modelcontextprotocol/clientInfo", Map.of("name", "fluxtion-analyser-loopback", "version", "1"));
    }

    private static Map<?, ?> requireResult(Object response, Era era, String stage) throws ProbeFailure, RestUnavailable {
        int code = errorCode(response);
        if (code == McpBridge.ERR_ANALYSER_UNREACHABLE) throw new RestUnavailable();
        if (!(response instanceof Map<?, ?> message) || !(message.get("result") instanceof Map<?, ?> result)) {
            throw new ProbeFailure(Status.PROTOCOL_FAILED, era, "the MCP bridge rejected " + stage);
        }
        return result;
    }

    private static int errorCode(Object response) {
        if (!(response instanceof Map<?, ?> message) || !(message.get("error") instanceof Map<?, ?> error)) return 0;
        return error.get("code") instanceof Number n ? n.intValue() : 0;
    }

    private static void closeProcess(Process process) {
        try {
            process.getOutputStream().close();
        } catch (IOException ignore) {
            // shutdown best-effort
        }
        try {
            if (!process.waitFor(100, TimeUnit.MILLISECONDS)) process.destroy();
            if (!process.waitFor(100, TimeUnit.MILLISECONDS)) process.destroyForcibly();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
        }
    }

    private static String drain(InputStream stream) throws IOException {
        ByteArrayOutputStream kept = new ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        for (int count; (count = stream.read(buffer)) >= 0; ) {
            if (kept.size() < STDERR_LIMIT) kept.write(buffer, 0, Math.min(count, STDERR_LIMIT - kept.size()));
        }
        return kept.toString(StandardCharsets.UTF_8);
    }

    private static Result result(Status status, Era era, String detail) {
        return new Result(status, era, detail);
    }

    private static final class RestUnavailable extends Exception { }

    private static final class ProbeFailure extends Exception {
        private final Status status;
        private final Era era;

        ProbeFailure(Status status, Era era, String message) {
            super(message);
            this.status = status;
            this.era = era;
        }
    }
}
