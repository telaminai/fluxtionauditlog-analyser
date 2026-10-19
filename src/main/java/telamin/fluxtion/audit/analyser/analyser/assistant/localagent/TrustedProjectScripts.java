package telamin.fluxtion.audit.analyser.analyser.assistant.localagent;

import telamin.fluxtion.audit.analyser.analyser.llm.Json;
import telamin.fluxtion.audit.analyser.analyser.session.LocalAgentPosture;
import telamin.fluxtion.audit.analyser.analyser.template.TemplateArchive;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * M81 B: machine-only, content-bound identities of the project's declared build scripts. Reads are bounded and
 * never execute a script. This observes identities; only the permission node decides whether a call is allowed.
 */
public final class TrustedProjectScripts {
    private TrustedProjectScripts() { }
    public static final int MAX_BYTES = FullFolderTrust.MAX_BYTES;
    private static final int MAX_RECORD = 64 * 1024;
    public static final String CHANGED = "Since you trusted this folder's build scripts, their content changed, or the "
            + "set of scripts changed (one was added or removed); it asks again so you see them.";

    public static boolean supported() {
        return !System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).startsWith("windows");
    }

    /** A single checkbox, but separate path-and-content bindings for every named program. */
    public record Entry(String real, String digest) { }
    public record Scan(String realRoot, Map<String, Entry> entries, String digest) {
        public Scan { entries = Collections.unmodifiableMap(new LinkedHashMap<>(entries)); }
        public List<String> scripts() { return List.copyOf(entries.keySet()); }
    }

    public static List<String> candidates(Path root) { return scan(root).scripts(); }

    public static Scan scan(Path root) {
        Map<String, Entry> entries = new LinkedHashMap<>();
        if (!supported() || root == null) return new Scan("", entries, "");
        try {
            Path real = root.toRealPath();
            var tokens = new java.util.TreeSet<String>();
            for (String command : TemplateArchive.commandsFor(root)) tokens.add(command.split("\\s+", 2)[0]);
            for (String wrapper : List.of("./mvnw", "./mvnw.cmd")) {
                if (Files.exists(root.resolve(wrapper.substring(2)))) tokens.add(wrapper);
            }
            for (String token : tokens) {
                Optional<Path> script = LocalAgentPosture.buildScriptInvocation(token, real);
                if (script.isEmpty()) continue;
                String digest = fileDigest(script.get());
                if (digest != null) entries.put(token, new Entry(script.get().toString(), digest));
            }
            return new Scan(real.toString(), entries, digestEntries(entries));
        } catch (IOException | RuntimeException e) {
            return new Scan("", Map.of(), "");
        }
    }

    public static String digest(Path root, List<String> scripts) {
        Scan scan = scan(root);
        return scan.scripts().equals(scripts) ? scan.digest() : "";
    }

    private static String digestEntries(Map<String, Entry> entries) {
        return sha(Json.write(values(entries)).getBytes(java.nio.charset.StandardCharsets.UTF_8));
    }

    private static Map<String, Object> values(Map<String, Entry> entries) {
        Map<String, Object> values = new LinkedHashMap<>();
        entries.forEach((token, entry) -> values.put(token, List.of(entry.real(), entry.digest())));
        return values;
    }

    /** The exact scan shown by the dialog, never a later scan silently substituted at acceptance. */
    public static String record(Scan scan) {
        if (scan.realRoot().isEmpty() || scan.entries().isEmpty()) return "";
        return Json.write(Map.of("v", 1, "root", scan.realRoot(), "scripts", values(scan.entries())));
    }

    public static String record(String realPath, List<String> scripts, String digest) {
        Scan scan = scan(Path.of(realPath));
        return scan.scripts().equals(scripts) && scan.digest().equals(digest) ? record(scan) : "";
    }

    private static Scan read(String record) {
        if (record == null || record.length() > MAX_RECORD) return null;
        try {
            Object raw = Json.parse(record);
            if (!(raw instanceof Map<?, ?> m) || !(m.get("v") instanceof Number version) || version.doubleValue() != 1
                    || !(m.get("root") instanceof String root)
                    || !(m.get("scripts") instanceof Map<?, ?> scripts) || scripts.size() > 32) return null;
            Map<String, Entry> entries = new java.util.TreeMap<>();
            for (var item : scripts.entrySet()) {
                if (!(item.getKey() instanceof String token) || !token.matches("\\./[A-Za-z0-9_.-]+")
                        || !(item.getValue() instanceof List<?> pair) || pair.size() != 2
                        || !(pair.get(0) instanceof String real) || !(pair.get(1) instanceof String digest)
                        || !digest.matches("[0-9a-f]{64}")) return null;
                entries.put(token, new Entry(real, digest));
            }
            return new Scan(root, entries, digestEntries(entries));
        } catch (RuntimeException | StackOverflowError unreadable) { return null; }
    }

    public static boolean confirms(List<String> records, String realPath, List<String> scripts, String digest) {
        for (String record : records == null ? List.<String>of() : records) {
            Scan saved = read(record);
            if (saved != null && saved.realRoot().equals(realPath) && saved.scripts().equals(scripts)
                    && saved.digest().equals(digest)) return true;
        }
        return false;
    }

    public static String changeNote(List<String> records, String realPath, List<String> scripts, String digest) {
        if (confirms(records, realPath, scripts, digest)) return "";
        for (String record : records == null ? List.<String>of() : records) {
            Scan saved = read(record);
            if (saved != null && saved.realRoot().equals(realPath)) return CHANGED;
        }
        return "";
    }

    /** Removes only this real project's records. An unticked answer is a revocation, not an implicit grant. */
    public static List<String> recorded(List<String> records, String realRoot, String record) {
        List<String> out = new ArrayList<>();
        for (String old : records == null ? List.<String>of() : records) {
            Scan scan = read(old);
            if (scan == null || !scan.realRoot().equals(realRoot)) out.add(old);
        }
        if (record != null && !record.isBlank()) out.add(record);
        return List.copyOf(out);
    }

    /**
     * Fresh observation for ONE call: declared token, resolved real path, complete bounded content, launch grant AND
     * still-present machine grant. A changed or new script cannot borrow an unchanged sibling's permission.
     */
    public static boolean permits(List<String> captured, List<String> live, Path root, String command) {
        if (!supported() || root == null) return false;
        try {
            Path real = root.toRealPath();
            Optional<Path> invocation = LocalAgentPosture.buildScriptInvocation(command, real);
            if (invocation.isEmpty()) return false;
            String token = command.strip().split("[ \\t]+", 2)[0];
            Scan now = scan(root);
            Entry entry = now.entries().get(token);
            if (entry == null || !invocation.get().toString().equals(entry.real())) return false;
            return contains(captured, real.toString(), token, entry) && contains(live, real.toString(), token, entry);
        } catch (IOException | RuntimeException unreadable) { return false; }
    }

    private static boolean contains(List<String> records, String root, String token, Entry entry) {
        for (String record : records == null ? List.<String>of() : records) {
            Scan scan = read(record);
            if (scan != null && scan.realRoot().equals(root) && entry.equals(scan.entries().get(token))) return true;
        }
        return false;
    }

    private static String fileDigest(Path script) {
        try (var input = Files.newInputStream(script, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes = input.readNBytes(MAX_BYTES + 1);
            if (bytes.length > MAX_BYTES || !Files.isRegularFile(script)) return null;
            return sha(bytes);
        } catch (IOException | RuntimeException e) { return null; }
    }

    private static String sha(byte[] bytes) {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }
}
