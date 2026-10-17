package telamin.fluxtion.audit.analyser.analyser.session;

import telamin.fluxtion.audit.analyser.analyser.llm.VerbSchemas;
import telamin.fluxtion.audit.analyser.analyser.mcp.McpTools;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * M74 §3/§4.3/§4.4: the posture a local-agent session is started in, AS A VALUE, and the check of what the CLI says it
 * is running against it. Pure and deterministic, so the command line ({@code ClaudeCodeProvider}) and the posture
 * assertion ({@code localAgentSession}) are computed from ONE definition, the explicit {@link #PRE_ALLOWED} list: a verb
 * added to {@link VerbSchemas} lands in the disallow list, out of the expected tools and outside the hosted bridge's
 * admission until someone lists it (review H5).
 *
 * <p>Two kinds. {@link Kind#ANALYSER_ASSISTANT} (the default): Read, Grep and Glob confined to one folder, only the
 * hosted bridge, {@code dontAsk}. {@link Kind#FULL} (P1b, OD-74.10): the person's own Claude Code in the mode they chose
 * — nothing confines it but their own rules and the mode, so the check asserts only what the analyser owns: the mode,
 * the hosted tool set, the absence of the person's own {@code fluxtion-analyser} registration, the hosted server and the
 * session id (§4.4, §11 "the full posture cannot be asserted exactly").
 *
 * @param kind     which posture
 * @param fullMode the mode the person chose for the full posture; null for the analyser assistant
 */
public record LocalAgentPosture(Kind kind, FullMode fullMode) {

    /** Which posture a session runs in (§3). */
    public enum Kind { ANALYSER_ASSISTANT, FULL }

    /**
     * The four modes the full posture offers (§5.1). {@code bypassPermissions} and {@code dontAsk} are NOT values of this
     * enum, so no full-posture command line can carry either: an unknown or unoffered stored value reads as
     * {@link #DEFAULT} ({@code ConfigStore}).
     */
    public enum FullMode {
        DEFAULT("default"), ACCEPT_EDITS("acceptEdits"), AUTO("auto"), PLAN("plan");

        private final String cli;

        FullMode(String cli) {
            this.cli = cli;
        }

        /** The value {@code --permission-mode} takes and {@code initialize}/{@code system/init} report. */
        public String cli() {
            return cli;
        }

        /** The stored setting read back: one of the four CLI names (any case), else empty — never passed through. */
        public static Optional<FullMode> parse(String stored) {
            if (stored == null) return Optional.empty();
            String s = stored.trim();
            for (FullMode m : values()) if (m.cli.equalsIgnoreCase(s)) return Optional.of(m);
            return Optional.empty();
        }
    }

    public LocalAgentPosture {
        if (kind == null) kind = Kind.ANALYSER_ASSISTANT;
        if (kind == Kind.ANALYSER_ASSISTANT) fullMode = null;
        else if (fullMode == null) fullMode = FullMode.DEFAULT;
    }

    /** The default posture: the analyser assistant. */
    public static final LocalAgentPosture ANALYSER_ASSISTANT = new LocalAgentPosture(Kind.ANALYSER_ASSISTANT, null);

    public static LocalAgentPosture full(FullMode mode) {
        return new LocalAgentPosture(Kind.FULL, mode);
    }

    public boolean isFull() {
        return kind == Kind.FULL;
    }

    /** The hosted bridge's server name: distinct from M42's {@code fluxtion-analyser}, so the person's rules never apply. */
    public static final String HOSTED_SERVER = "fluxtion-analyser-hosted";

    /** The person's own M42 registration's server name: hidden in the full posture, never this analyser [P0-30, P0-31]. */
    public static final String PERSONS_REGISTRATION = "fluxtion-analyser";

    /** P1a and P2, default posture: anything not pre-allowed is denied without blocking [P0-21]. */
    public static final String PERMISSION_MODE = "dontAsk";

    /** A mode the analyser never starts, offers or accepts: observed in any posture it is {@code MODE_MISMATCH} (§4.4). */
    public static final String BYPASS = "bypassPermissions";

    /** The built-in allow-list of the default posture, in the order {@code --tools} names it. */
    public static final List<String> BUILT_IN_TOOLS = List.of("Read", "Grep", "Glob");

    /** Features the session needs, detected rather than version-sniffed [P0-40]. */
    public static final List<String> REQUIRED_CAPABILITIES = List.of("interrupt_receipt_v1", "msg_lifecycle_v1");

    /** The mode {@code --permission-mode} sets, and the one {@code initialize} and every {@code system/init} must report. */
    public String permissionMode() {
        return isFull() ? fullMode.cli() : PERMISSION_MODE;
    }

    /** The name the posture is shown under. */
    public String label() {
        // owner, 2026-10-17: the full posture is a PREVIEW until P3's approvals ship
        return isFull() ? "full Claude Code (preview, " + fullMode.cli() + ")" : "analyser assistant (default posture)";
    }

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

    /**
     * §3 "a posture change needs New chat": the digest of everything fixed for a session id — the kind, the full mode,
     * the real working folder, the system note and the pre-allow list. A change of any of them makes a different ref,
     * and the node never resumes a conversation under another one.
     */
    public String ref(String realFolder, String systemNote) {
        return digest(kind + "|" + (fullMode == null ? "-" : fullMode.cli()) + "|" + realFolder + "|" + systemNote + "|"
                + preAllowedVerbs());
    }

    /** What the posture check found. */
    public enum Verdict { OK, MODE_MISMATCH, TOOLS_MISMATCH, SERVERS_MISMATCH, CAPABILITY_MISSING, SESSION_CHANGED }

    /**
     * The pre-allow list, BY NAME (review H5): the single source of truth for what the hosted bridge may call without
     * asking, in both postures. A verb is pre-allowed only if it is listed; a verb added to {@link VerbSchemas} is
     * therefore hidden from Claude ({@code --disallowedTools}), absent from the expected tool set, and refused by
     * {@code ActionServer} for the hosted credential until someone lists it. It was "every verb minus the destructive
     * ones", a deny-list, which auto-allowed every new verb on both sides.
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

    /**
     * {@code --disallowedTools}, by name (never a wildcard [P0-30]): in the full posture the person's own registration at
     * server level first, which removes only that server's tools (PB-1), then the hidden hosted verbs.
     */
    public List<String> disallowedTools() {
        return disallowedTools(VerbSchemas.all().keySet());
    }

    /** {@code --disallowedTools} for a given schema list. */
    public List<String> disallowedTools(Collection<String> schemaVerbs) {
        List<String> out = new ArrayList<>();
        if (isFull()) out.add("mcp__" + PERSONS_REGISTRATION);
        for (String v : disallowedVerbs(schemaVerbs)) out.add(toolName(v));
        return List.copyOf(out);
    }

    /** The name the CLI gives a hosted verb: {@code mcp__fluxtion-analyser-hosted__analyser_<verb>}. */
    public static String toolName(String verb) {
        return "mcp__" + HOSTED_SERVER + "__" + McpTools.PREFIX + verb;
    }

    private static final String HOSTED_PREFIX = "mcp__" + HOSTED_SERVER + "__";
    private static final String REGISTRATION_PREFIX = "mcp__" + PERSONS_REGISTRATION + "__";

    /** The hosted tools the model may see in P1b and P2: the pre-allowed verbs, in both postures. */
    public static Set<String> expectedHostedTools() {
        return expectedHostedTools(VerbSchemas.all().keySet());
    }

    /** The hosted tools for a given schema list (the tests add a verb to the real inputs through this). */
    public static Set<String> expectedHostedTools(Collection<String> schemaVerbs) {
        Set<String> out = new TreeSet<>();
        for (String v : preAllowedVerbs(schemaVerbs)) out.add(toolName(v));
        return out;
    }

    /** The default posture's {@code system/init.tools}, exactly: the built-ins plus the pre-allowed hosted verbs. */
    public static Set<String> expectedTools() {
        return expectedTools(VerbSchemas.all().keySet());
    }

    /** The default posture's expected tool set for a given schema list. */
    public static Set<String> expectedTools(Collection<String> schemaVerbs) {
        Set<String> out = new TreeSet<>(BUILT_IN_TOOLS);
        out.addAll(expectedHostedTools(schemaVerbs));
        return out;
    }

    /** Whether {@code tool} is one of the hosted bridge's (the analyser's own). */
    public static boolean isHostedTool(String tool) {
        return tool != null && tool.startsWith(HOSTED_PREFIX);
    }

    /** Whether {@code tool} is the person's own {@code fluxtion-analyser} registration's — never this analyser. */
    public static boolean isRegistrationTool(String tool) {
        return tool != null && tool.startsWith(REGISTRATION_PREFIX);
    }

    /** Whether {@code reported} is the mode this posture expects (never {@code bypassPermissions}, in any posture). */
    public boolean modeMatches(String reported) {
        return reported != null && !BYPASS.equals(reported) && permissionMode().equals(reported);
    }

    /** The default posture's check: kept for the P1a call sites and fixtures. */
    public static Verdict check(String expectedSessionId, String permissionMode, Collection<String> tools,
                                Collection<String> servers, Collection<String> capabilities, String sessionDigest) {
        return ANALYSER_ASSISTANT.verdict(expectedSessionId, permissionMode, tools, servers, capabilities, sessionDigest);
    }

    /** The default posture's check against a given schema list. */
    public static Verdict check(Collection<String> schemaVerbs, String expectedSessionId, String permissionMode,
                                Collection<String> tools, Collection<String> servers, Collection<String> capabilities,
                                String sessionDigest) {
        return ANALYSER_ASSISTANT.verdict(schemaVerbs, expectedSessionId, permissionMode, tools, servers, capabilities,
                sessionDigest);
    }

    /**
     * Check a turn's {@code system/init} against this posture. The order is the order of severity: a changed mode or
     * tool set is a session already running with powers it was not started with.
     *
     * @param servers       {@code name:status} for each MCP server the CLI reports
     * @param sessionDigest {@link #digest} of the session id the CLI reports
     */
    public Verdict verdict(String expectedSessionId, String permissionMode, Collection<String> tools,
                           Collection<String> servers, Collection<String> capabilities, String sessionDigest) {
        return verdict(VerbSchemas.all().keySet(), expectedSessionId, permissionMode, tools, servers, capabilities,
                sessionDigest);
    }

    /** {@link #verdict} against a given schema list (the tests add a verb to the real inputs through this). */
    public Verdict verdict(Collection<String> schemaVerbs, String expectedSessionId, String permissionMode,
                           Collection<String> tools, Collection<String> servers, Collection<String> capabilities,
                           String sessionDigest) {
        if (!modeMatches(permissionMode)) return Verdict.MODE_MISMATCH;
        if (isFull()) {
            // the person's built-in and MCP tools are theirs and vary: only the analyser's own are asserted
            List<String> hosted = new ArrayList<>();
            for (String t : tools) {
                if (isRegistrationTool(t)) return Verdict.TOOLS_MISMATCH;
                if (isHostedTool(t)) hosted.add(t);
            }
            Set<String> expected = expectedHostedTools(schemaVerbs);
            if (!new TreeSet<>(hosted).equals(expected) || hosted.size() != expected.size()) return Verdict.TOOLS_MISMATCH;
            if (!servers.contains(HOSTED_SERVER + ":connected")) return Verdict.SERVERS_MISMATCH;
        } else {
            Set<String> expected = expectedTools(schemaVerbs);
            if (!new TreeSet<>(tools).equals(expected) || tools.size() != expected.size()) return Verdict.TOOLS_MISMATCH;
            if (servers.size() != 1 || !servers.contains(HOSTED_SERVER + ":connected")) return Verdict.SERVERS_MISMATCH;
        }
        if (!capabilities.containsAll(REQUIRED_CAPABILITIES)) return Verdict.CAPABILITY_MISSING;
        if (expectedSessionId == null || !digest(expectedSessionId).equals(sessionDigest)) return Verdict.SESSION_CHANGED;
        return Verdict.OK;
    }

    /** The person's own servers among {@code name:status} (or bare name) entries: everything but the hosted bridge. */
    public static List<String> personsServers(Collection<String> servers) {
        List<String> out = new ArrayList<>();
        for (String s : servers) {
            if (s == null) continue;
            int colon = s.lastIndexOf(':');
            String name = colon < 0 ? s : s.substring(0, colon);
            if (!HOSTED_SERVER.equals(name)) out.add(s);
        }
        return out;
    }

    @Override
    public String toString() {
        return kind.name().toLowerCase(Locale.ROOT) + (fullMode == null ? "" : ":" + fullMode.cli());
    }
}
