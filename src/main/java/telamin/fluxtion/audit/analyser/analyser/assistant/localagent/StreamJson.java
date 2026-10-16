package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import telamin.fluxtion.audit.analyser.analyser.llm.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Claude Code's {@code stream-json} protocol, read and written (spec-claude-code-provider.md §4.4, §4.5), as P0 and P0b
 * recorded it from CLI 2.1.282.
 *
 * <p><b>The parse boundary is where personal data stops.</b> Every line is parsed into one of the typed {@link Event}s
 * below and the parsed map is dropped. The {@code initialize} answer's {@code account} carries the person's e-mail and
 * organisation; {@link Initialize} has <b>no field for either</b>, so they are never read, never stored and cannot reach
 * a fact, the transcript store, the audit record or a bundle. A test pins the record's components.
 *
 * <p>Unknown event kinds are tolerated: they parse to {@link Unknown} and are counted by the reader, never thrown.
 */
public final class StreamJson {

    private StreamJson() {
    }

    /** One line of the CLI's stdout, typed. */
    public sealed interface Event permits SystemInit, Lifecycle, MessageStart, TextDelta, AssistantMessage, UserMessage,
            Result, CompactBoundary, RateLimit, PermissionDenied, ConversationReset, ControlResponse, ControlRequest,
            Ignored, Unknown {
    }

    /** {@code system/init}, emitted at the start of every turn [P0-4]. {@code servers} are {@code name:status}. */
    public record SystemInit(String sessionId, String model, String permissionMode, List<String> tools,
                             List<String> servers, List<String> capabilities, String cliVersion) implements Event {
    }

    /** {@code command_lifecycle}: {@code queued → started → completed / cancelled}, keyed by the line's own uuid [P0-9]. */
    public record Lifecycle(String commandUuid, String state) implements Event {
    }

    /** A {@code stream_event message_start}: the message id the following deltas belong to. */
    public record MessageStart(String messageId, String model) implements Event {
    }

    /** A {@code stream_event content_block_delta} of type {@code text_delta}: provisional text [P0-8]. */
    public record TextDelta(int index, String text) implements Event {
    }

    /** One complete block of an {@code assistant} message: its text (null for thinking or a tool use). */
    public record AssistantMessage(String messageId, String model, String text, String toolUse) implements Event {
    }

    /**
     * A {@code user} event: a replay of a sent line, a tool result, or a SYNTHETIC message — the compaction summary
     * [P0-12] — which is never the person's words.
     */
    public record UserMessage(String uuid, boolean replay, boolean synthetic, boolean toolResult) implements Event {
    }

    /**
     * A {@code result}. {@code isError} is authoritative: a not-logged-in result is {@code subtype:"success"} with
     * {@code is_error:true} [P0-37]. {@code totalCostMicros} is the SESSION's running total [P0-22]; -1 when absent.
     * {@code text} is shown only for a turn that produced no assistant text (a local command's output).
     */
    public record Result(String subtype, boolean isError, long totalCostMicros, String terminalReason,
                         String userMessageUuid, String text, int apiErrorStatus) implements Event {

        /** An authentication failure: an invalid inherited API key, or a login that is missing or expired (§9). */
        public boolean authFailure() {
            if (!isError) return false;
            if (apiErrorStatus == 401 || apiErrorStatus == 403) return true;
            String t = text == null ? "" : text.toLowerCase(java.util.Locale.ROOT);
            return t.contains("not logged in") || t.contains("invalid api key") || t.contains("authentication");
        }
    }

    /** {@code system/compact_boundary}: the conversation was compacted ({@code auto} or {@code manual}). */
    public record CompactBoundary(String trigger) implements Event {
    }

    /** {@code rate_limit_event}: {@code allowed}, or a limit with its reset time (epoch seconds). */
    public record RateLimit(String status, long resetsAt) implements Event {
    }

    /** {@code system/permission_denied}: the posture denied a tool, without blocking [P0-21]. */
    public record PermissionDenied(String toolName) implements Event {
    }

    /** {@code conversation_reset}: {@code /clear} changed the session under the analyser [P0b-4]. */
    public record ConversationReset(String trigger) implements Event {
    }

    /** A {@code control_response} to one of the analyser's requests, typed by what it carries. */
    public record ControlResponse(String requestId, boolean success, Initialize initialize, McpStatus status,
                                  boolean interruptAck) implements Event {
    }

    /** A {@code control_request} from the CLI (P3's {@code can_use_tool}); P1a answers every one with a denial. */
    public record ControlRequest(String requestId, String subtype) implements Event {
    }

    /** A known kind the analyser does not act on (thinking estimates, status, hooks, a block start/stop, …). */
    public record Ignored(String kind) implements Event {
    }

    /** A kind this analyser does not know: tolerated, counted in a diagnostics line. */
    public record Unknown(String kind) implements Event {
    }

    /**
     * What the analyser reads from {@code initialize} [P0b-5] — and nothing else. There is deliberately no field for the
     * account's e-mail or organisation.
     */
    public record Initialize(String subscriptionType, String apiProvider, String apiKeySource, String tokenSource,
                             String permissionMode, List<Command> commands) {
        public Initialize {
            commands = List.copyOf(commands == null ? List.of() : commands);
        }

        /** Not logged in: {@code tokenSource:"none"} with neither a subscription nor an API-key source [P0b-5]. */
        public boolean loggedIn() {
            boolean noToken = tokenSource == null || "none".equals(tokenSource);
            return !(noToken && subscriptionType == null && apiKeySource == null);
        }
    }

    /** One entry of {@code initialize.commands}: its name, aliases and whether it is the CLI's own. */
    public record Command(String name, List<String> aliases, boolean builtin) {
        public Command {
            aliases = List.copyOf(aliases == null ? List.of() : aliases);
        }
    }

    /** {@code mcp_status}: each configured server's name, status and reported {@code serverInfo.version}. */
    public record McpStatus(List<Server> servers) {
        public McpStatus {
            servers = List.copyOf(servers == null ? List.of() : servers);
        }
    }

    public record Server(String name, String status, String version) {
    }

    // ---- reading ------------------------------------------------------------------------------------------------------

    /** Parse one stdout line. Never throws: a line that is not JSON is {@link Unknown}. */
    public static Event parse(String line) {
        Object root;
        try {
            root = Json.parse(line);
        } catch (RuntimeException e) {
            return new Unknown("not-json");
        }
        if (!(root instanceof Map<?, ?> m)) return new Unknown("not-an-object");
        String type = str(m.get("type"));
        if (type == null) return new Unknown("no-type");
        return switch (type) {
            case "system" -> system(m);
            case "command_lifecycle" -> new Lifecycle(str(m.get("command_uuid")), str(m.get("state")));
            case "stream_event" -> streamEvent(m);
            case "assistant" -> assistant(m);
            case "user" -> user(m);
            case "result" -> result(m);
            case "rate_limit_event" -> rateLimit(m);
            case "conversation_reset" -> new ConversationReset(str(m.get("trigger")));
            case "control_response" -> controlResponse(m);
            case "control_request" -> {
                Map<?, ?> req = map(m.get("request"));
                yield new ControlRequest(str(m.get("request_id")), req == null ? null : str(req.get("subtype")));
            }
            case "keep_alive" -> new Ignored(type);
            default -> new Unknown(type);
        };
    }

    private static Event system(Map<?, ?> m) {
        String sub = str(m.get("subtype"));
        if (sub == null) return new Unknown("system");
        return switch (sub) {
            case "init" -> {
                List<String> servers = new ArrayList<>();
                for (Object o : list(m.get("mcp_servers"))) {
                    if (o instanceof Map<?, ?> s) servers.add(str(s.get("name")) + ":" + str(s.get("status")));
                }
                yield new SystemInit(str(m.get("session_id")), str(m.get("model")), str(m.get("permissionMode")),
                        strings(m.get("tools")), servers, strings(m.get("capabilities")), str(m.get("claude_code_version")));
            }
            case "compact_boundary" -> {
                Map<?, ?> meta = map(m.get("compact_metadata"));
                yield new CompactBoundary(meta == null ? null : str(meta.get("trigger")));
            }
            case "permission_denied" -> new PermissionDenied(str(m.get("tool_name")));
            case "status", "thinking_tokens", "hook_started", "hook_response", "background_tasks_changed",
                 "commands_changed" -> new Ignored("system/" + sub);
            default -> new Unknown("system/" + sub);
        };
    }

    private static Event streamEvent(Map<?, ?> m) {
        Map<?, ?> ev = map(m.get("event"));
        if (ev == null) return new Unknown("stream_event");
        String t = str(ev.get("type"));
        if (t == null) return new Unknown("stream_event");
        return switch (t) {
            case "message_start" -> {
                Map<?, ?> msg = map(ev.get("message"));
                yield new MessageStart(msg == null ? null : str(msg.get("id")), msg == null ? null : str(msg.get("model")));
            }
            case "content_block_delta" -> {
                Map<?, ?> d = map(ev.get("delta"));
                if (d != null && "text_delta".equals(str(d.get("type")))) {
                    yield new TextDelta(num(ev.get("index")), str(d.get("text")) == null ? "" : str(d.get("text")));
                }
                yield new Ignored("stream_event/" + t + "/" + (d == null ? "?" : str(d.get("type"))));
            }
            case "content_block_start", "content_block_stop", "message_delta", "message_stop" ->
                    new Ignored("stream_event/" + t);
            default -> new Unknown("stream_event/" + t);
        };
    }

    private static Event assistant(Map<?, ?> m) {
        Map<?, ?> msg = map(m.get("message"));
        if (msg == null) return new Unknown("assistant");
        String text = null;
        String tool = null;
        StringBuilder joined = null;
        for (Object o : list(msg.get("content"))) {
            if (!(o instanceof Map<?, ?> block)) continue;
            String bt = str(block.get("type"));
            if ("text".equals(bt)) {
                if (joined == null) joined = new StringBuilder();
                joined.append(str(block.get("text")) == null ? "" : str(block.get("text")));
            } else if ("tool_use".equals(bt)) {
                tool = str(block.get("name"));
            }
        }
        if (joined != null) text = joined.toString();
        return new AssistantMessage(str(msg.get("id")), str(msg.get("model")), text, tool);
    }

    private static Event user(Map<?, ?> m) {
        boolean toolResult = false;
        Map<?, ?> msg = map(m.get("message"));
        if (msg != null) {
            for (Object o : list(msg.get("content"))) {
                if (o instanceof Map<?, ?> block && "tool_result".equals(str(block.get("type")))) toolResult = true;
            }
        }
        return new UserMessage(str(m.get("uuid")), Boolean.TRUE.equals(m.get("isReplay")),
                Boolean.TRUE.equals(m.get("isSynthetic")), toolResult);
    }

    private static Event result(Map<?, ?> m) {
        long cost = m.get("total_cost_usd") instanceof Number n ? Math.round(n.doubleValue() * 1_000_000d) : -1;
        return new Result(str(m.get("subtype")), Boolean.TRUE.equals(m.get("is_error")), cost,
                str(m.get("terminal_reason")), str(m.get("user_message_uuid")), str(m.get("result")),
                num(m.get("api_error_status")));
    }

    private static Event rateLimit(Map<?, ?> m) {
        Map<?, ?> info = map(m.get("rate_limit_info"));
        if (info == null) return new RateLimit(null, 0);
        long resets = info.get("resetsAt") instanceof Number n ? n.longValue() : 0;
        return new RateLimit(str(info.get("status")), resets);
    }

    private static Event controlResponse(Map<?, ?> m) {
        Map<?, ?> r = map(m.get("response"));
        if (r == null) return new Unknown("control_response");
        String id = str(r.get("request_id"));
        boolean ok = "success".equals(str(r.get("subtype")));
        Map<?, ?> body = map(r.get("response"));
        Initialize init = null;
        McpStatus status = null;
        boolean ack = false;
        if (body != null) {
            if (body.containsKey("commands") || body.containsKey("account") || body.containsKey("current_permission_mode")) {
                init = initialize(body);
            }
            if (body.get("mcpServers") instanceof List<?> servers) {
                List<Server> out = new ArrayList<>();
                for (Object o : servers) {
                    if (!(o instanceof Map<?, ?> s)) continue;
                    Map<?, ?> info = map(s.get("serverInfo"));
                    out.add(new Server(str(s.get("name")), str(s.get("status")), info == null ? null : str(info.get("version"))));
                }
                status = new McpStatus(out);
            }
            ack = body.containsKey("still_queued") || body.containsKey("cancelled");
        }
        return new ControlResponse(id, ok, init, status, ack);
    }

    /** Reads exactly four account keys, the permission mode and the commands; everything else is never looked at. */
    private static Initialize initialize(Map<?, ?> body) {
        Map<?, ?> account = map(body.get("account"));
        String subscription = account == null ? null : str(account.get("subscriptionType"));
        String provider = account == null ? null : str(account.get("apiProvider"));
        String keySource = account == null ? null : str(account.get("apiKeySource"));
        String tokenSource = account == null ? null : str(account.get("tokenSource"));
        List<Command> commands = new ArrayList<>();
        for (Object o : list(body.get("commands"))) {
            if (o instanceof Map<?, ?> c && str(c.get("name")) != null) {
                commands.add(new Command(str(c.get("name")), strings(c.get("aliases")), Boolean.TRUE.equals(c.get("builtin"))));
            }
        }
        return new Initialize(subscription, provider, keySource, tokenSource, str(body.get("current_permission_mode")),
                commands);
    }

    // ---- writing ------------------------------------------------------------------------------------------------------

    /** One user turn: the stdin shape P0 confirmed [P0-1], with a fresh uuid as its ticket [P0-27]. */
    public static String userLine(String uuid, String text) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("role", "user");
        message.put("content", text);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "user");
        m.put("message", message);
        m.put("parent_tool_use_id", null);
        m.put("uuid", uuid);
        return Json.write(m);
    }

    /** A control request the analyser sends: {@code initialize}, {@code mcp_status} or {@code interrupt}. */
    public static String controlRequest(String requestId, String subtype) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "control_request");
        m.put("request_id", requestId);
        m.put("request", Map.of("subtype", subtype));
        return Json.write(m);
    }

    /** The answer to a {@code control_request} P1a never grants: deny, without suggestions or permission updates. */
    public static String deny(String requestId, String message) {
        Map<String, Object> inner = new LinkedHashMap<>();
        inner.put("behavior", "deny");
        inner.put("message", message);
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("subtype", "success");
        response.put("request_id", requestId);
        response.put("response", inner);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("type", "control_response");
        m.put("response", response);
        return Json.write(m);
    }

    // ---- helpers ------------------------------------------------------------------------------------------------------

    private static String str(Object o) {
        return o == null ? null : o.toString();
    }

    private static int num(Object o) {
        return o instanceof Number n ? n.intValue() : 0;
    }

    private static Map<?, ?> map(Object o) {
        return o instanceof Map<?, ?> m ? m : null;
    }

    private static List<?> list(Object o) {
        return o instanceof List<?> l ? l : List.of();
    }

    private static List<String> strings(Object o) {
        List<String> out = new ArrayList<>();
        for (Object x : list(o)) if (x != null) out.add(x.toString());
        return out;
    }
}
