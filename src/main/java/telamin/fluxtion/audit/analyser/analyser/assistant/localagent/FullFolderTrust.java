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

    /** One file's names: hook events with their counts (settings files), server names ({@code .mcp.json}). */
    public record FileScan(String file, State state, Map<String, Integer> hooks, List<String> servers) {
        public FileScan {
            // the file's own order, as the person would read it there
            hooks = java.util.Collections.unmodifiableMap(new LinkedHashMap<>(hooks == null ? Map.of() : hooks));
            servers = List.copyOf(servers == null ? List.of() : servers);
        }
    }

    /** The folder's three files, scanned, and their digest. */
    public record Scan(List<FileScan> files, String digest) {
        public Scan {
            files = List.copyOf(files);
        }

        public boolean nothingRuns() {
            for (FileScan f : files) if (!f.hooks().isEmpty() || !f.servers().isEmpty()) return false;
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
                if (!Files.isRegularFile(f)) {
                    all.update("absent".getBytes(StandardCharsets.UTF_8));
                } else {
                    MessageDigest one = MessageDigest.getInstance("SHA-256");
                    try (InputStream in = Files.newInputStream(f)) {
                        byte[] buf = new byte[8192];
                        for (int n; (n = in.read(buf)) > 0; ) one.update(buf, 0, n);
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
        if (!Files.exists(f)) return new FileScan(name, State.ABSENT, Map.of(), List.of());
        String text;
        try {
            if (!Files.isRegularFile(f)) return new FileScan(name, State.UNREADABLE, Map.of(), List.of());
            if (Files.size(f) > MAX_BYTES) return new FileScan(name, State.TOO_LARGE, Map.of(), List.of());
            text = Files.readString(f, StandardCharsets.UTF_8);
        } catch (IOException | RuntimeException e) {
            return new FileScan(name, State.UNREADABLE, Map.of(), List.of());
        }
        Object root;
        try {
            root = Json.parse(text);
        } catch (RuntimeException e) {
            return new FileScan(name, State.NOT_JSON, Map.of(), List.of());
        }
        if (!(root instanceof Map<?, ?> m)) return new FileScan(name, State.NOT_JSON, Map.of(), List.of());
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
        return new FileScan(name, State.READ, hooks, servers);
    }

    /**
     * A name read from the folder, as plain text for the dialog: control and format characters made visible, at most 80
     * characters. It is shown by a plain-text component, never one that renders HTML.
     */
    public static String plain(String name) {
        StringBuilder sb = new StringBuilder();
        name.codePoints().limit(80).forEach(cp -> {
            if (Character.isISOControl(cp) || Character.getType(cp) == Character.FORMAT) {
                sb.append(String.format("\\u%04X", cp));
            } else {
                sb.appendCodePoint(cp);
            }
        });
        if (name.codePointCount(0, name.length()) > 80) sb.append('…');
        return sb.toString();
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
                        List<String> parts = new ArrayList<>();
                        for (String s : f.servers()) parts.add(plain(s));
                        sb.append("MCP servers in this folder: ").append(String.join(", ", parts)).append(" — from ")
                                .append(f.file()).append('\n');
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
