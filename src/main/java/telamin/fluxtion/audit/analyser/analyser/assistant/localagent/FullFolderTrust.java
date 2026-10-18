package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import telamin.fluxtion.audit.analyser.analyser.llm.Json;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;

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
 *
 * <p><b>Review H6: instruction files are trusted content too.</b> Full Claude Code reads the folder's {@code CLAUDE.md},
 * {@code CLAUDE.local.md}, {@code .claude/CLAUDE.md} as instructions and loads {@code .claude/commands},
 * {@code .claude/skills}, {@code .claude/agents}, {@code .claude/rules} and {@code .claude/output-styles} (review of PR
 * #147, finding 2: rules load as project memory, an output style changes the system prompt), and the scripts in
 * {@code .claude/hooks} that hooks run — a {@code git pull} that rewrites one changes what the agent does as surely as a
 * new hook. So the digest is {@code <settings digest>.<instruction digest>}: the instruction part covers
 * those files in a stable (sorted) order, bounded ({@link #MAX_INSTRUCTION_FILES} files, {@link #MAX_INSTRUCTION_BYTES}
 * read in all, each file at most {@link #MAX_BYTES}, {@link #MAX_INSTRUCTION_DIRS} folders walked; past a bound a file
 * counts by its size and modification time), and a link is never followed out of the folder — whether the link is the
 * file, the tree or {@code .claude} itself (its target's NAME is digested, and the dialog says so). When the
 * dialog asks again it says which part changed ({@link #changeNote}).
 */
public final class FullFolderTrust {

    private FullFolderTrust() {
    }

    /** §5.2: each file is read for names only up to this size. */
    public static final int MAX_BYTES = 256 * 1024;

    /** The three files, in the digest's order. */
    public static final List<String> FILES = List.of(".claude/settings.json", ".claude/settings.local.json", ".mcp.json");

    /** The instruction files Claude Code reads from the folder (review H6). */
    public static final List<String> INSTRUCTION_FILES = List.of("CLAUDE.md", "CLAUDE.local.md", ".claude/CLAUDE.md");

    /** The instruction trees Claude Code loads from the folder (review H6). */
    public static final List<String> INSTRUCTION_TREES = List.of(".claude/commands", ".claude/skills", ".claude/agents",
            ".claude/rules", ".claude/output-styles", ".claude/hooks");

    /** The folder whose link out of the folder means none of the instruction files under it are read (finding 7). */
    static final String CLAUDE_DIR = ".claude";

    /** Bounds on the instruction scan. */
    public static final int MAX_INSTRUCTION_FILES = 1_000;
    public static final long MAX_INSTRUCTION_BYTES = 8L * 1024 * 1024;
    /**
     * Review of PR #147, finding 8: the folders walked are bounded too — the scan runs on the event thread at every Start,
     * Send and Settings apply, so an empty-directory tree must not freeze the window. Past it the scan stops and says so.
     */
    public static final int MAX_INSTRUCTION_DIRS = 2_000;
    static final int MAX_DEPTH = 12;

    /**
     * What the instruction scan found (review H6): which top-level instruction files exist, how many files each tree
     * holds, the links pointing out of the folder (named, never followed), whether a bound was reached, and the digest.
     */
    public record InstructionScan(List<String> files, Map<String, Integer> trees, List<String> linksOutside,
                                  boolean truncated, String digest) {
        public InstructionScan {
            files = List.copyOf(files);
            trees = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(trees));
            linksOutside = List.copyOf(linksOutside);
        }

        public boolean none() {
            return files.isEmpty() && trees.isEmpty() && linksOutside.isEmpty();
        }
    }

    /** How a file was found. */
    public enum State { ABSENT, READ, TOO_LARGE, NOT_JSON, UNREADABLE }

    /**
     * One file's names: hook events with their counts and the plugins, marketplaces and command-valued keys of a settings
     * file, the server names of {@code .mcp.json}, and every top-level key the analyser does not interpret (review
     * finding 5: those may run other commands, and the dialog says so rather than "nothing runs").
     */
    public record FileScan(String file, State state, Map<String, Integer> hooks, List<String> servers,
                           List<String> plugins, List<String> marketplaces, List<String> commands, List<String> unknown,
                           List<String> permissions) {
        public FileScan {
            // the file's own order, as the person would read it there
            hooks = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(hooks == null ? Map.of() : hooks));
            servers = List.copyOf(servers == null ? List.of() : servers);
            plugins = List.copyOf(plugins == null ? List.of() : plugins);
            marketplaces = List.copyOf(marketplaces == null ? List.of() : marketplaces);
            commands = List.copyOf(commands == null ? List.of() : commands);
            unknown = List.copyOf(unknown == null ? List.of() : unknown);
            permissions = List.copyOf(permissions == null ? List.of() : permissions);
        }

        FileScan(String file, State state) {
            this(file, state, Map.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
        }

        /** Review of PR #147, finding 1: a permission the folder grants itself is never "nothing runs". */
        boolean runsSomething() {
            return !hooks.isEmpty() || !servers.isEmpty() || !plugins.isEmpty() || !marketplaces.isEmpty()
                    || !commands.isEmpty() || !unknown.isEmpty() || !permissions.isEmpty();
        }
    }

    /** Settings keys whose value is a command Claude Code runs (review finding 5). */
    static final List<String> COMMAND_KEYS = List.of("apiKeyHelper", "awsAuthRefresh", "awsCredentialExport",
            "otelHeadersHelper", "statusLine");

    /**
     * Settings keys the analyser understands to run nothing by themselves; anything else is named as "may run".
     * {@code permissions} is NOT one (review of PR #147, finding 1): in the full posture the folder's allow rules,
     * {@code additionalDirectories} and {@code defaultMode} apply, so they are read and named ({@link #PERMISSION_KEYS}).
     */
    static final java.util.Set<String> INERT_SETTINGS = java.util.Set.of("$schema", "model", "outputStyle",
            "includeCoAuthoredBy", "cleanupPeriodDays", "theme", "enableAllProjectMcpServers", "enabledMcpjsonServers",
            "disabledMcpjsonServers", "spinnerTipsEnabled");

    /** Keys of {@code permissions} that only narrow what runs (deny, ask) and so are not named. */
    static final java.util.Set<String> NARROWING_PERMISSIONS = java.util.Set.of("deny", "ask",
            "disableBypassPermissionsMode");

    /** At most this many permission entries per file are named; the rest are counted. */
    static final int MAX_NAMED_PERMISSIONS = 20;

    /** The folder's three settings files, scanned, its instruction files, and the combined digest. */
    public record Scan(List<FileScan> files, InstructionScan instructions, String digest) {
        public Scan {
            files = List.copyOf(files);
        }

        public boolean nothingRuns() {
            for (FileScan f : files) if (f.runsSomething() || f.state() != State.ABSENT && f.state() != State.READ) return false;
            return true;
        }

        /** None of the three files, and no instruction file, exists (the scratch folder's trust, review finding 3). */
        public boolean allAbsent() {
            for (FileScan f : files) if (f.state() != State.ABSENT) return false;
            return instructions == null || instructions.none();
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
        InstructionScan instructions = instructions(dir);
        return new Scan(out, instructions, settingsDigest(dir) + "." + instructions.digest());
    }

    /**
     * Holistic review B7 (2026-10-17): what the trust digest does NOT cover — said in the dialog, never implied. Files a
     * CLAUDE.md pulls in with {@code @path} imports, CLAUDE.md files in folders ABOVE this one, and the contents of the
     * scripts a hook runs are read by Claude Code but not re-checked by the analyser: a change to them asks nothing.
     */
    public static final String NOT_RECHECKED = "Not re-checked by the analyser: files a CLAUDE.md imports with @path, "
            + "CLAUDE.md files in the folders above this one, and the contents of the scripts a hook runs. Claude Code "
            + "reads or runs them, and a change to them does not ask again.";

    /** The trust digest: {@code <settings digest>.<instruction digest>} (review H6). */
    public static String digest(Path dir) {
        return settingsDigest(dir) + "." + instructions(dir).digest();
    }

    /**
     * The digest of the three settings files: each file's name and either {@code absent} or the SHA-256 of all its
     * bytes. A changed, added or removed file changes it.
     */
    public static String settingsDigest(Path dir) {
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

    /**
     * Review H6: the folder's instruction files, digested in a stable order and bounded. A symbolic link is followed
     * only to a regular file inside the folder; otherwise its target's name is digested and, when it points out of the
     * folder, it is named.
     */
    public static InstructionScan instructions(Path dir) {
        java.util.TreeMap<String, byte[]> entries = new java.util.TreeMap<>();
        List<String> present = new ArrayList<>();
        Map<String, Integer> trees = new LinkedHashMap<>();
        List<String> outside = new ArrayList<>();
        boolean[] truncated = {false};
        long[] budget = {MAX_INSTRUCTION_BYTES};
        int[] dirs = {0};
        Path root = realOrNull(dir);
        // review of PR #147, finding 7: when .claude itself leads out of the folder (a symbolic link, or a Windows
        // junction, which isSymbolicLink does not see), nothing under it is walked or read: it is named instead
        Path claude = dir.resolve(CLAUDE_DIR);
        boolean claudeOutside = Files.exists(claude, java.nio.file.LinkOption.NOFOLLOW_LINKS) && leadsOutside(claude, root);
        if (claudeOutside) {
            outside.add(CLAUDE_DIR);
            entries.put(CLAUDE_DIR, outsideEntry(claude));
        }
        for (String name : INSTRUCTION_FILES) {
            if (claudeOutside && name.startsWith(CLAUDE_DIR + "/")) continue;
            Path f = dir.resolve(name);
            if (!Files.exists(f, java.nio.file.LinkOption.NOFOLLOW_LINKS)) continue;
            present.add(name);
            entries.put(name, entry(f, name, root, outside, budget));
        }
        for (String tree : INSTRUCTION_TREES) {
            if (claudeOutside) break;
            Path t = dir.resolve(tree);
            if (!Files.exists(t, java.nio.file.LinkOption.NOFOLLOW_LINKS)) continue;
            if (Files.isSymbolicLink(t) || !Files.isDirectory(t, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                trees.put(tree, 1);
                entries.put(tree, entry(t, tree, root, outside, budget));
                continue;
            }
            if (leadsOutside(t, root)) {
                // a junction or other link the last-component check misses: named, never walked
                outside.add(tree);
                entries.put(tree, outsideEntry(t));
                continue;
            }
            if (truncated[0]) {
                entries.put(tree, "truncated".getBytes(StandardCharsets.UTF_8));
                continue;
            }
            int[] count = {0};
            try {
                Files.walkFileTree(t, java.util.EnumSet.noneOf(java.nio.file.FileVisitOption.class), MAX_DEPTH,
                        new java.nio.file.SimpleFileVisitor<>() {
                            @Override
                            public java.nio.file.FileVisitResult preVisitDirectory(Path d,
                                                                                   java.nio.file.attribute.BasicFileAttributes a) {
                                // review of PR #147, finding 8: folders count against a bound as files do
                                if (++dirs[0] > MAX_INSTRUCTION_DIRS) {
                                    truncated[0] = true;
                                    return java.nio.file.FileVisitResult.TERMINATE;
                                }
                                return java.nio.file.FileVisitResult.CONTINUE;
                            }

                            @Override
                            public java.nio.file.FileVisitResult visitFile(Path file,
                                                                           java.nio.file.attribute.BasicFileAttributes a) {
                                if (entries.size() >= MAX_INSTRUCTION_FILES) {
                                    truncated[0] = true;
                                    return java.nio.file.FileVisitResult.TERMINATE;
                                }
                                String rel = dir.relativize(file).toString().replace('\\', '/');
                                entries.put(rel, entry(file, rel, root, outside, budget));
                                count[0]++;
                                return java.nio.file.FileVisitResult.CONTINUE;
                            }

                            @Override
                            public java.nio.file.FileVisitResult visitFileFailed(Path file, IOException e) {
                                String rel = dir.relativize(file).toString().replace('\\', '/');
                                entries.put(rel, "unreadable".getBytes(StandardCharsets.UTF_8));
                                count[0]++;
                                return java.nio.file.FileVisitResult.CONTINUE;
                            }
                        });
            } catch (IOException e) {
                entries.put(tree, "unreadable".getBytes(StandardCharsets.UTF_8));
            }
            trees.put(tree, count[0]);
        }
        if (budget[0] < 0) truncated[0] = true;
        try {
            MessageDigest all = MessageDigest.getInstance("SHA-256");
            for (var e : entries.entrySet()) {
                all.update(e.getKey().getBytes(StandardCharsets.UTF_8));
                all.update((byte) 0);
                all.update(e.getValue());
                all.update((byte) 0);
            }
            if (truncated[0]) all.update("truncated".getBytes(StandardCharsets.UTF_8));
            return new InstructionScan(present, trees, outside, truncated[0], HexFormat.of().formatHex(all.digest(), 0, 16));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Whether {@code p}, resolved through every link on its way (not only its last component), lies outside
     * {@code root} — or cannot be resolved, which is never taken to be inside.
     */
    static boolean leadsOutside(Path p, Path root) {
        Path real = realOrNull(p);
        return real == null || root == null || !real.startsWith(root);
    }

    /** What an entry that leads out of the folder contributes: its link target's name, never what lies there. */
    private static byte[] outsideEntry(Path p) {
        String target;
        try {
            target = Files.isSymbolicLink(p) ? String.valueOf(Files.readSymbolicLink(p)) : String.valueOf(realOrNull(p));
        } catch (IOException | RuntimeException e) {
            target = "unreadable";
        }
        return ("outside:" + target).getBytes(StandardCharsets.UTF_8);
    }

    /**
     * Review of PR #147, info: the bytes of {@code in}, but never more than {@code max} of them, or null when there are
     * more — a file that grows between its size check and its read is never read unbounded.
     */
    static byte[] readBounded(InputStream in, int max) throws IOException {
        byte[] out = in.readNBytes(max + 1);
        return out.length > max ? null : out;
    }

    /** At most {@code max} bytes of {@code in} into {@code d}, whatever the size check said before. */
    static void digestBounded(InputStream in, MessageDigest d, long max) throws IOException {
        byte[] buf = new byte[8192];
        long left = max;
        for (int n; left > 0 && (n = in.read(buf, 0, (int) Math.min(buf.length, left))) > 0; ) {
            d.update(buf, 0, n);
            left -= n;
        }
    }

    private static Path realOrNull(Path p) {
        try {
            return p.toRealPath();
        } catch (IOException e) {
            return null;
        }
    }

    /** One instruction file's digest bytes: its content (bounded), or for a link not followed, its target's name. */
    private static byte[] entry(Path f, String rel, Path root, List<String> outside, long[] budget) {
        try {
            Path read = f;
            if (Files.isSymbolicLink(f)) {
                String target = String.valueOf(Files.readSymbolicLink(f));
                Path real = realOrNull(f);
                boolean inside = real != null && root != null && real.startsWith(root);
                if (!inside) outside.add(rel);
                if (!inside || !Files.isRegularFile(real)) {
                    return ("link:" + target).getBytes(StandardCharsets.UTF_8);
                }
                read = real;
            }
            if (!Files.isRegularFile(read, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
                return "not-a-file".getBytes(StandardCharsets.UTF_8);
            }
            long size = Files.size(read);
            MessageDigest one = MessageDigest.getInstance("SHA-256");
            one.update(("size:" + size).getBytes(StandardCharsets.UTF_8));
            if (size > MAX_BYTES || budget[0] - size < 0) {
                // past a bound: told apart by its size and modification time, never by its unread content
                one.update((":mtime:" + Files.getLastModifiedTime(read).toMillis()).getBytes(StandardCharsets.UTF_8));
                if (budget[0] - size < 0) budget[0] = -1;
            } else {
                budget[0] -= size;
                try (InputStream in = Files.newInputStream(read)) {
                    digestBounded(in, one, size);
                }
            }
            return one.digest();
        } catch (IOException | NoSuchAlgorithmException | RuntimeException e) {
            return "unreadable".getBytes(StandardCharsets.UTF_8);
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
            byte[] bytes;
            try (InputStream in = Files.newInputStream(f)) {
                bytes = readBounded(in, MAX_BYTES);
            }
            if (bytes == null) return new FileScan(name, State.TOO_LARGE);   // grew past the bound since the check
            text = new String(bytes, StandardCharsets.UTF_8);
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
        List<String> permissions = new ArrayList<>();
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
            } else if (key.equals("permissions")) {
                permissions(v, permissions, unknown);
            } else if (!INERT_SETTINGS.contains(key)) {
                unknown.add(key);
            }
        }
        return new FileScan(name, State.READ, hooks, servers, plugins, marketplaces, commands, unknown, permissions);
    }

    /**
     * Review of PR #147, finding 1: what a settings file's {@code permissions} grants — each {@code allow} rule, each
     * {@code additionalDirectories} entry and {@code defaultMode} — as words for the dialog. {@code deny} and {@code ask}
     * only narrow; any other key is one the analyser does not interpret.
     */
    private static void permissions(Object v, List<String> out, List<String> unknown) {
        if (!(v instanceof Map<?, ?> pm)) {
            unknown.add("permissions");
            return;
        }
        for (var e : pm.entrySet()) {
            String k = String.valueOf(e.getKey());
            Object x = e.getValue();
            switch (k) {
                case "allow" -> {
                    if (x instanceof List<?> l) for (Object r : l) out.add("allow " + r);
                    else unknown.add("permissions.allow");
                }
                case "additionalDirectories" -> {
                    if (x instanceof List<?> l) for (Object r : l) out.add("additional directory " + r);
                    else unknown.add("permissions.additionalDirectories");
                }
                case "defaultMode" -> out.add("default mode " + x);
                default -> {
                    if (!NARROWING_PERMISSIONS.contains(k)) unknown.add("permissions." + k);
                }
            }
        }
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
                .append("settings and its instruction files (CLAUDE.md, commands, skills, agents, rules, output styles). ")
                .append("Your own user-level hooks ")
                .append("and servers run too.\n\n");
        boolean any = false;
        boolean servers = false;
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
                        servers = true;
                        sb.append("MCP servers in this folder: ").append(names(f.servers())).append(" — from ")
                                .append(f.file()).append('\n');
                        if (f.servers().contains(LocalAgentPosture.HOSTED_SERVER)) {
                            // review of PR #147, info: a folder server under the analyser's own bridge's name
                            sb.append("Warning: ").append(f.file()).append(" defines a server named ")
                                    .append(LocalAgentPosture.HOSTED_SERVER).append(", the name of the analyser's own ")
                                    .append("bridge; it is not the analyser's, which binds only to the bridge it started itself\n");
                        }
                    }
                    if (!f.permissions().isEmpty()) {
                        any = true;
                        List<String> shown = f.permissions().size() > MAX_NAMED_PERMISSIONS
                                ? f.permissions().subList(0, MAX_NAMED_PERMISSIONS) : f.permissions();
                        sb.append("Permissions this folder grants itself: ").append(names(shown));
                        if (shown.size() < f.permissions().size()) {
                            sb.append(", and ").append(f.permissions().size() - shown.size()).append(" more");
                        }
                        sb.append(" — from ").append(f.file())
                                .append(" (these apply on top of your own rules, without asking)\n");
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
        if (servers) {
            // review of PR #147, finding 2: what a server runs is not part of the trust
            sb.append("Not re-checked: the scripts or programs these MCP servers run (for example node ./server.js) — a ")
                    .append("change to them does not make the analyser ask again\n");
        }
        InstructionScan ins = scan.instructions();
        if (ins != null && !ins.none()) {
            List<String> parts = new ArrayList<>(ins.files());
            for (var t : ins.trees().entrySet()) {
                parts.add(t.getKey() + " (" + t.getValue() + (t.getValue() == 1 ? " file)" : " files)"));
            }
            sb.append("Instruction files Claude Code reads from this folder: ").append(names(parts)).append('\n');
            if (ins.linksOutside().contains(CLAUDE_DIR)) {
                // review of PR #147, finding 7
                sb.append("This folder's .claude leads to a folder outside it: the Claude Code configuration Start loads ")
                        .append("lives elsewhere, and its instruction files are not read by the analyser\n");
            }
            if (!ins.linksOutside().isEmpty()) {
                sb.append("Links pointing out of this folder, not read by the analyser: ").append(names(ins.linksOutside()))
                        .append('\n');
            }
            if (ins.truncated()) {
                sb.append("Over the limit: there are more instruction files or folders than the analyser reads (over ")
                        .append(MAX_INSTRUCTION_FILES).append(" files, ").append(MAX_INSTRUCTION_DIRS)
                        .append(" folders or 8 MB); the rest are not checked, or checked by size and date only\n");
            }
        }
        // holistic review B7 (2026-10-17): what the trust does NOT cover, said plainly
        sb.append("\n").append(NOT_RECHECKED).append('\n');
        sb.append("\nTrust ").append(dir).append(" for full Claude Code in the analyser? This is asked again if these files ")
                .append("change.\n\nRemembered on this machine only; never in a project, a shared settings file or a bundle.");
        return sb.toString();
    }

    /** §5.2's dialog with the reason it asks again, when this folder was trusted before (review H6). */
    public static String dialogText(Path dir, Scan scan, List<String> records, String realPath) {
        String note = changeNote(records, realPath, scan.digest());
        return note == null ? dialogText(dir, scan) : note + "\n\n" + dialogText(dir, scan);
    }

    /**
     * Why a folder trusted before is asked about again, or null when it was not trusted before (review H6): its
     * instruction files changed, its settings or {@code .mcp.json} changed, both, or it was trusted before instruction
     * files were part of the trust.
     */
    public static String changeNote(List<String> records, String realPath, String digest) {
        String previous = previousDigest(records, realPath);
        if (previous == null || digest == null || previous.equals(digest)) return null;
        int pd = previous.indexOf('.');
        int nd = digest.indexOf('.');
        if (pd < 0 || nd < 0) {
            return "You trusted this folder before the analyser covered its instruction files (CLAUDE.md, .claude/commands, "
                    + ".claude/skills, .claude/agents, .claude/rules, .claude/output-styles, .claude/hooks); it asks again "
                    + "so you see them.";
        }
        boolean settings = !previous.substring(0, pd).equals(digest.substring(0, nd));
        boolean instructions = !previous.substring(pd + 1).equals(digest.substring(nd + 1));
        if (settings && instructions) {
            return "Since you trusted this folder, its settings or .mcp.json AND its instruction files (CLAUDE.md, "
                    + ".claude/commands, .claude/skills, .claude/agents, .claude/rules, .claude/output-styles or "
                    + ".claude/hooks) changed.";
        }
        if (instructions) {
            return "Since you trusted this folder, its instruction files changed (CLAUDE.md, .claude/CLAUDE.md, "
                    + ".claude/commands, .claude/skills, .claude/agents, .claude/rules, .claude/output-styles or "
                    + ".claude/hooks): Claude Code would read or run different instructions.";
        }
        return "Since you trusted this folder, its hooks or MCP servers changed (.claude/settings.json, "
                + ".claude/settings.local.json or .mcp.json).";
    }

    /** The digest a trust record holds for {@code realPath}, or null. */
    public static String previousDigest(List<String> records, String realPath) {
        if (records == null || realPath == null) return null;
        for (String r : records) {
            int bar = r.lastIndexOf('|');
            if (bar >= 0 && r.substring(0, bar).equals(realPath)) return r.substring(bar + 1);
        }
        return null;
    }

    /** The trust record's value: {@code <real path>|<digest>} ({@code assistant.claudeCode.trustedFull.N}). */
    public static String record(String realPath, String digest) {
        return realPath + "|" + digest;
    }

    /** Whether {@code records} confirm {@code realPath} with exactly {@code digest} — never the path alone. */
    /**
     * Authoring walk (2026-10-18, shots 36/37): the ONE full-posture confirmation rule, read by the consent the session
     * decides from AND by the spawn's re-check (holistic review B7) — the analyser's own scratch folder counts as
     * confirmed while it holds none of the files Start would load (review finding 3); any other folder by its record.
     * The two had drifted: the consent exempted the scratch folder, the spawn did not, and Full with no project open
     * was refused "changed after you confirmed it" at every Start, though nothing had ever been confirmed.
     */
    public static boolean confirmedFull(List<String> records, Path dir, Path scratch) {
        if (dir == null) return false;
        Scan scan = scan(dir);
        return dir.equals(scratch) && scan.allAbsent()
                || confirms(records, ClaudeCodeProvider.realPath(dir), scan.digest());
    }

    /** Whether any record names {@code realPath} — a folder trusted once, whatever its content was then. */
    public static boolean namesFolder(List<String> records, String realPath) {
        if (records == null || realPath == null) return false;
        for (String r : records) if (r != null && r.startsWith(realPath + "|")) return true;
        return false;
    }

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
