package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import telamin.fluxtion.audit.analyser.analyser.llm.Json;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OD-74.12: full-posture folder trust is SEPARATE from the default posture's and BOUND TO CONTENT. In the full posture
 * Start itself runs the folder's hooks and connects its {@code .mcp.json} servers [P0-28, PB-2], so the confirmation
 * names what will run and is recorded with a digest of the three files that decide it —
 * {@code .claude/settings.json}, {@code .claude/settings.local.json} and {@code .mcp.json}, an absent file counted as
 * absent. When the digest changes (a {@code git pull} adds a hook) the record no longer confirms the folder, and Start
 * asks again.
 *
 * <p>What is read for the dialog is bounded: each file at most {@link #MAX_BYTES}, JSON only, and only NAMES — the hook
 * events (with how many hooks each) and the server names; never a command. A file that is too large or not JSON is
 * said to be unreadable, never hidden. The digest covers every byte. Pure: the frame performs, the session decides.
 */
public final class FullFolderTrust {

    private FullFolderTrust() {
    }

    /** §5.2: each file is read for names only up to this size. */
    public static final int MAX_BYTES = 256 * 1024;

    /** The three files, in the digest's order. */
    public static final List<String> FILES = List.of(".claude/settings.json", ".claude/settings.local.json", ".mcp.json");

    /** How a file was found. */
    public enum State { ABSENT, READ, TOO_LARGE, NOT_JSON, UNREADABLE }

    /**
     * One file's names: hook events with their counts and the plugins, marketplaces and command-valued keys of a settings
     * file, the server names of {@code .mcp.json}, and every top-level key the analyser does not interpret (review
     * finding 5: those may run other commands, and the dialog says so rather than "nothing runs").
     */
    public record FileScan(String file, State state, Map<String, Integer> hooks, List<String> servers,
                           List<String> plugins, List<String> marketplaces, List<String> commands, List<String> unknown) {
        public FileScan {
            // the file's own order, as the person would read it there
            hooks = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(hooks == null ? Map.of() : hooks));
            servers = List.copyOf(servers == null ? List.of() : servers);
            plugins = List.copyOf(plugins == null ? List.of() : plugins);
            marketplaces = List.copyOf(marketplaces == null ? List.of() : marketplaces);
            commands = List.copyOf(commands == null ? List.of() : commands);
            unknown = List.copyOf(unknown == null ? List.of() : unknown);
        }

        FileScan(String file, State state) {
            this(file, state, Map.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }

        boolean runsSomething() {
            return !hooks.isEmpty() || !servers.isEmpty() || !plugins.isEmpty() || !marketplaces.isEmpty()
                    || !commands.isEmpty() || !unknown.isEmpty();
        }
    }

    /** Settings keys whose value is a command Claude Code runs (review finding 5). */
    static final List<String> COMMAND_KEYS = List.of("apiKeyHelper", "awsAuthRefresh", "awsCredentialExport",
            "otelHeadersHelper", "statusLine");

    /** Settings keys the analyser understands to run nothing by themselves; anything else is named as "may run". */
    static final java.util.Set<String> INERT_SETTINGS = java.util.Set.of("$schema", "permissions", "model", "outputStyle",
            "includeCoAuthoredBy", "cleanupPeriodDays", "theme", "enableAllProjectMcpServers", "enabledMcpjsonServers",
            "disabledMcpjsonServers", "spinnerTipsEnabled");

    /** The folder's three files, scanned, and their digest. */
    public record Scan(List<FileScan> files, String digest) {
        public Scan {
            files = List.copyOf(files);
        }

        public boolean nothingRuns() {
            for (FileScan f : files) if (f.runsSomething() || f.state() != State.ABSENT && f.state() != State.READ) return false;
            return true;
        }

        /** None of the three files exists (the scratch folder's trust, review finding 3). */
        public boolean allAbsent() {
            for (FileScan f : files) if (f.state() != State.ABSENT) return false;
            return true;
        }

        public boolean anyUnreadable() {
            for (FileScan f : files) {
                if (f.state() == State.TOO_LARGE || f.state() == State.NOT_JSON || f.state() == State.UNREADABLE) return true;
            }
            return false;
        }
    }

    public static Scan scan(Path dir) {
        List<FileScan> out = new ArrayList<>();
        for (String name : FILES) out.add(scanFile(dir.resolve(name), name));
        return new Scan(out, digest(dir));
    }

    /**
     * The digest of the three files: each file's name and either {@code absent} or the SHA-256 of all its bytes.
     * A changed, added or removed file changes it.
     */
    public static String digest(Path dir) {
        try {
            MessageDigest all = MessageDigest.getInstance("SHA-256");
            for (String name : FILES) {
                Path f = dir.resolve(name);
                all.update(name.getBytes(StandardCharsets.UTF_8));
                all.update((byte) 0);
                if (!Files.exists(f, java.nio.file.LinkOption.NOFOLLOW_LINKS) && !Files.exists(f)) {
                    all.update("absent".getBytes(StandardCharsets.UTF_8));
                } else if (!Files.isRegularFile(f)) {
                    // review finding 13: present but not a regular file — UNREADABLE in the scan, never "absent" here
                    all.update("unreadable".getBytes(StandardCharsets.UTF_8));
                } else {
                    // bounded (review finding 13): at most MAX_BYTES are read; a larger file is told apart by its size
                    // and modification time as well as its first MAX_BYTES (the scan already calls it unreadable)
                    MessageDigest one = MessageDigest.getInstance("SHA-256");
                    try (InputStream in = Files.newInputStream(f)) {
                        long size = Files.size(f);
                        if (size > MAX_BYTES) {
                            one.update(("large:" + size + ":" + Files.getLastModifiedTime(f).toMillis())
                                    .getBytes(StandardCharsets.UTF_8));
                        }
                        byte[] buf = new byte[8192];
                        long left = MAX_BYTES;
                        for (int n; left > 0 && (n = in.read(buf, 0, (int) Math.min(buf.length, left))) > 0; ) {
                            one.update(buf, 0, n);
                            left -= n;
                        }
                    } catch (IOException e) {
                        one.update("unreadable".getBytes(StandardCharsets.UTF_8));
                    }
                    all.update(one.digest());
                }
                all.update((byte) 0);
            }
            return HexFormat.of().formatHex(all.digest(), 0, 16);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static FileScan scanFile(Path f, String name) {
        // the same rule as the digest: absent only when neither the name nor a link of that name exists
        if (!Files.exists(f, java.nio.file.LinkOption.NOFOLLOW_LINKS) && !Files.exists(f)) {
            return new FileScan(name, State.ABSENT);
        }
        String text;
        try {
            if (!Files.isRegularFile(f)) return new FileScan(name, State.UNREADABLE);
            if (Files.size(f) > MAX_BYTES) return new FileScan(name, State.TOO_LARGE);
            text = Files.readString(f, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return new FileScan(name, State.UNREADABLE);
        }
        Object root;
        try {
            root = Json.parse(text);
        } catch (RuntimeException e) {
            return new FileScan(name, State.NOT_JSON);
        }
        if (!(root instanceof Map<?, ?> m)) return new FileScan(name, State.NOT_JSON);
        Map<String, Integer> hooks = new LinkedHashMap<>();
        List<String> servers = new ArrayList<>();
        if (m.get("hooks") instanceof Map<?, ?> h) {
            for (var e : h.entrySet()) {
                int count = 0;
                if (e.getValue() instanceof List<?> matchers) {
                    for (Object matcher : matchers) {
                        if (matcher instanceof Map<?, ?> mm && mm.get("hooks") instanceof List<?> l) count += l.size();
                        else count++;
                    }
                }
                hooks.put(String.valueOf(e.getKey()), Math.max(count, 1));
            }
        }
        if (m.get("mcpServers") instanceof Map<?, ?> s) for (Object k : s.keySet()) servers.add(String.valueOf(k));
        List<String> plugins = new ArrayList<>();
        List<String> marketplaces = new ArrayList<>();
        List<String> commands = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        boolean mcpFile = name.equals(".mcp.json");
        for (Object k : m.keySet()) {
            String key = String.valueOf(k);
            Object v = m.get(k);
            if (key.equals("hooks") && !mcpFile || key.equals("mcpServers")) continue;
            if (mcpFile) {
                unknown.add(key);
            } else if (key.equals("enabledPlugins")) {
                if (v instanceof Map<?, ?> pm) {
                    for (var e : pm.entrySet()) if (!Boolean.FALSE.equals(e.getValue())) plugins.add(String.valueOf(e.getKey()));
                } else if (v instanceof List<?> pl) {
                    for (Object x : pl) plugins.add(String.valueOf(x));
                } else {
                    unknown.add(key);
                }
            } else if (key.equals("extraKnownMarketplaces")) {
                if (v instanceof Map<?, ?> mm) for (Object x : mm.keySet()) marketplaces.add(String.valueOf(x));
                else unknown.add(key);
            } else if (COMMAND_KEYS.contains(key)) {
                commands.add(key);
            } else if (!INERT_SETTINGS.contains(key)) {
                unknown.add(key);
            }
        }
        return new FileScan(name, State.READ, hooks, servers, plugins, marketplaces, commands, unknown);
    }

    /**
     * A name read from the folder, as plain text for the dialog: control and format characters made visible, at most 80
     * characters. It is shown by a plain-text component, never one that renders HTML.
     */
    public static String plain(String name) {
        StringBuilder sb = new StringBuilder();
        name.codePoints().limit(80).forEach(cp -> {
            int t = Character.getType(cp);
            if (Character.isISOControl(cp) || t == Character.FORMAT || t == Character.LINE_SEPARATOR
                    || t == Character.PARAGRAPH_SEPARATOR) {
                sb.append(String.format("\\u%04X", cp));
            } else {
                sb.appendCodePoint(cp);
            }
        });
        if (name.codePointCount(0, name.length()) > 80) sb.append('…');
        return sb.toString();
    }

    private static String names(List<String> names) {
        List<String> parts = new ArrayList<>();
        for (String n : names) parts.add(plain(n));
        return String.join(", ", parts);
    }

    /** §5.2's full-posture dialog, in words: what will run, from which file, and the question. */
    public static String dialogText(Path dir, Scan scan) {
        StringBuilder sb = new StringBuilder();
        sb.append("Full Claude Code will run this folder's hooks and connect its MCP servers when it starts, and load its ")
                .append("settings and CLAUDE.md. Your own user-level hooks and servers run too.\n\n");
        boolean any = false;
        for (FileScan f : scan.files()) {
            switch (f.state()) {
                case ABSENT -> { }
                case TOO_LARGE -> {
                    any = true;
                    sb.append(f.file()).append(": could not be read (over 256 KB), so what it runs is not shown\n");
                }
                case NOT_JSON -> {
                    any = true;
                    sb.append(f.file()).append(": could not be read (not JSON), so what it runs is not shown\n");
                }
                case UNREADABLE -> {
                    any = true;
                    sb.append(f.file()).append(": could not be read, so what it runs is not shown\n");
                }
                case READ -> {
                    if (!f.hooks().isEmpty()) {
                        any = true;
                        List<String> parts = new ArrayList<>();
                        for (var h : f.hooks().entrySet()) parts.add(plain(h.getKey()) + " (" + h.getValue() + ")");
                        sb.append("Hooks in this folder: ").append(String.join(", ", parts)).append(" — from ")
                                .append(f.file()).append('\n');
                    }
                    if (!f.servers().isEmpty()) {
                        any = true;
                        sb.append("MCP servers in this folder: ").append(names(f.servers())).append(" — from ")
                                .append(f.file()).append('\n');
                    }
                    if (!f.plugins().isEmpty()) {
                        any = true;
                        sb.append("Plugins enabled in this folder: ").append(names(f.plugins())).append(" — from ")
                                .append(f.file()).append(" (their hooks, servers and commands run too)\n");
                    }
                    if (!f.marketplaces().isEmpty()) {
                        any = true;
                        sb.append("Plugin marketplaces added by this folder: ").append(names(f.marketplaces()))
                                .append(" — from ").append(f.file()).append('\n');
                    }
                    if (!f.commands().isEmpty()) {
                        any = true;
                        sb.append("Commands this folder's settings run: ").append(names(f.commands())).append(" — from ")
                                .append(f.file()).append('\n');
                    }
                    if (!f.unknown().isEmpty()) {
                        any = true;
                        sb.append("Settings the analyser does not interpret: ").append(names(f.unknown()))
                                .append(" — from ").append(f.file()).append("; they may run other commands\n");
                    }
                }
            }
        }
        if (!any) sb.append("Nothing in this folder's settings or .mcp.json runs.\n");
        sb.append("\nTrust ").append(dir).append(" for full Claude Code in the analyser? This is asked again if these files ")
                .append("change.\n\nRemembered on this machine only; never in a project, a shared settings file or a bundle.");
        return sb.toString();
    }

    /** The trust record's value: {@code <real path>|<digest>} ({@code assistant.claudeCode.trustedFull.N}). */
    public static String record(String realPath, String digest) {
        return realPath + "|" + digest;
    }

    /** Whether {@code records} confirm {@code realPath} with exactly {@code digest} — never the path alone. */
    public static boolean confirms(List<String> records, String realPath, String digest) {
        if (records == null || realPath == null || digest == null) return false;
        return records.contains(record(realPath, digest));
    }

    /** {@code records} with any older record of {@code realPath} replaced by the one for {@code digest}. */
    public static List<String> recorded(List<String> records, String realPath, String digest) {
        List<String> out = new ArrayList<>();
        for (String r : records == null ? List.<String>of() : records) {
            int bar = r.lastIndexOf('|');
            if (bar >= 0 && r.substring(0, bar).equals(realPath)) continue;
            out.add(r);
        }
        out.add(record(realPath, digest));
        return out;
    }
}
