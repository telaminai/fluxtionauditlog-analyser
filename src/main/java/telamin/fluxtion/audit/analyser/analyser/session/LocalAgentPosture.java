package telamin.fluxtion.audit.analyser.analyser.session;

import telamin.fluxtion.audit.analyser.analyser.llm.VerbSchemas;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpTools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * M74 §3/§4.4: the default posture as data, and the check of what the CLI says it is running against it. Pure and
 * deterministic, so the command line ({@code ClaudeCodeProvider}) and the posture assertion ({@code localAgentSession})
 * are computed from ONE definition, the explicit {@link #PRE_ALLOWED} list: a verb added to {@link VerbSchemas} lands in
 * the disallow list, out of the expected tools and outside the hosted bridge's admission until someone lists it.
 */
public final class LocalAgentPosture {

    private LocalAgentPosture() {
    }

    /** The hosted bridge's server name: distinct from M42's {@code fluxtion-analyser}, so the person's rules never apply. */
    public static final String HOSTED_SERVER = "fluxtion-analyser-hosted";

    /** P1a and P2: anything not pre-allowed is denied without blocking [P0-21]. */
    public static final String PERMISSION_MODE = "dontAsk";

    /** The built-in allow-list, in the order {@code --tools} names it. */
    public static final List<String> BUILT_IN_TOOLS = List.of("Read", "Grep", "Glob");

    /** Features the session needs, detected rather than version-sniffed [P0-40]. */
    public static final List<String> REQUIRED_CAPABILITIES = List.of("interrupt_receipt_v1", "msg_lifecycle_v1");

    /**
     * A short digest of an identifier the node compares but must not record: the session id and the binding nonce reach
     * the session as this, so the session audit record (which records every fact) never holds either.
     */
    public static String digest(String id) {
        if (id == null) return null;
        try {
            byte[] h = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(id.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(h, 0, 8);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** What the posture check found. */
    public enum Verdict { OK, MODE_MISMATCH, TOOLS_MISMATCH, SERVERS_MISMATCH, CAPABILITY_MISSING, SESSION_CHANGED }

    /**
     * The pre-allow list, BY NAME (review H5): the single source of truth for what the hosted bridge may call without
     * asking. A verb is pre-allowed only if it is listed here; a verb added to {@link VerbSchemas} is therefore hidden
     * from Claude ({@code --disallowedTools}), absent from the expected tool set, and refused by {@code ActionServer}
     * for the hosted credential until someone lists it. It was "every verb minus the destructive ones", a deny-list,
     * which auto-allowed every new verb on both sides.
     */
    public static final List<String> PRE_ALLOWED = McpTools.HOSTED_PRE_ALLOWED;

    private static final Set<String> PRE_ALLOWED_SET = Set.copyOf(PRE_ALLOWED);

    /** Whether {@code verb} is on the pre-allow list. */
    public static boolean preAllowed(String verb) {
        return verb != null && PRE_ALLOWED_SET.contains(verb);
    }

    /** The pre-allowed verbs of today's schema list, in {@code VerbSchemas} order. */
    public static List<String> preAllowedVerbs() {
        return preAllowedVerbs(VerbSchemas.all().keySet());
    }

    /** The verbs of {@code schemaVerbs} that are on the pre-allow list, in their order; nothing else ever is. */
    public static List<String> preAllowedVerbs(Collection<String> schemaVerbs) {
        List<String> out = new ArrayList<>();
        for (String v : schemaVerbs) if (preAllowed(v)) out.add(v);
        return List.copyOf(out);
    }

    /** Every verb of {@code schemaVerbs} that is not pre-allowed: hidden from the model with {@code --disallowedTools}. */
    public static List<String> disallowedVerbs(Collection<String> schemaVerbs) {
        List<String> out = new ArrayList<>();
        for (String v : schemaVerbs) if (!preAllowed(v)) out.add(v);
        return List.copyOf(out);
    }

    public static List<String> disallowedVerbs() {
        return disallowedVerbs(VerbSchemas.all().keySet());
    }

    /** The name the CLI gives a hosted verb: {@code mcp__fluxtion-analyser-hosted__analyser_<verb>}. */
    public static String toolName(String verb) {
        return "mcp__" + HOSTED_SERVER + "__" + McpTools.PREFIX + verb;
    }

    /** {@code system/init.tools} must be exactly this set: the built-ins plus the pre-allowed hosted verbs. */
    public static Set<String> expectedTools() {
        return expectedTools(VerbSchemas.all().keySet());
    }

    /** The expected tool set for a given schema list (the tests add a verb to the real inputs through this). */
    public static Set<String> expectedTools(Collection<String> schemaVerbs) {
        Set<String> out = new TreeSet<>(BUILT_IN_TOOLS);
        for (String v : preAllowedVerbs(schemaVerbs)) out.add(toolName(v));
        return out;
    }

    /**
     * Check a turn's {@code system/init} against the posture the session was started with. The order is the order of
     * severity: a changed mode or tool set is a session already running with powers it was not started with.
     *
     * @param servers       {@code name:status} for each MCP server the CLI reports
     * @param sessionDigest {@link #digest} of the session id the CLI reports
     */
    public static Verdict check(String expectedSessionId, String permissionMode, Collection<String> tools,
                                Collection<String> servers, Collection<String> capabilities, String sessionDigest) {
        return check(VerbSchemas.all().keySet(), expectedSessionId, permissionMode, tools, servers, capabilities,
                sessionDigest);
    }

    /** {@link #check} against a given schema list (the tests add a verb to the real inputs through this). */
    public static Verdict check(Collection<String> schemaVerbs, String expectedSessionId, String permissionMode,
                                Collection<String> tools, Collection<String> servers, Collection<String> capabilities,
                                String sessionDigest) {
        Set<String> expected = expectedTools(schemaVerbs);
        if (!PERMISSION_MODE.equals(permissionMode)) return Verdict.MODE_MISMATCH;
        if (!new TreeSet<>(tools).equals(expected) || tools.size() != expected.size()) {
            return Verdict.TOOLS_MISMATCH;
        }
        if (servers.size() != 1 || !servers.contains(HOSTED_SERVER + ":connected")) return Verdict.SERVERS_MISMATCH;
        if (!capabilities.containsAll(REQUIRED_CAPABILITIES)) return Verdict.CAPABILITY_MISSING;
        if (expectedSessionId == null || !digest(expectedSessionId).equals(sessionDigest)) return Verdict.SESSION_CHANGED;
        return Verdict.OK;
    }
}
