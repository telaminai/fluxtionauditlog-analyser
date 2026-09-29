///usr/bin/env jbang "$0" "$@" ; exit $?
//REPOS mavencentral,repsy-fluxtion-public=https://repo.repsy.io/mvn/fluxtion/fluxtion-public
//DEPS com.telamin.fluxtion:fluxtion-runtime:1.0.16
//JAVA 21

import com.telamin.fluxtion.runtime.DataFlow;
import com.telamin.fluxtion.runtime.audit.EventLogControlEvent;
import com.telamin.fluxtion.runtime.time.ClockStrategy;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.RecordComponent;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * The recipient's replay (spec-evidence-bundle-replay §5): feed an evidence bundle's replay records into YOUR build of
 * the processor, on a data-driven clock, and write the audit log it produces. Then ask the analyser whether that log is
 * the bundle's: {@code analyser --replay-compare <bundle.fexp> <replayed-audit.yaml>}.
 *
 * <pre>
 * jbang tools/replay/ReplayBundle.java --bundle run.fexp --processor com.acme.demo.generated.DemoQuoteRecordedProcessor \
 *       --cp path/to/your/classes --out replayed-audit.yaml
 * </pre>
 *
 * It runs YOUR code, which is why it is not part of the analyser; the analyser only compares what it writes. It
 * decides nothing about the verdict. It refuses, by name, exit 1, when:
 * <ul>
 *   <li>your build's graph does not match the bundle's: its GraphML (the generator writes {@code <Class>.graphml}
 *   beside the class) differs from the bundle's {@code graph/} member in its node ids or edges. A match is graph
 *   compatibility, not the same code. {@code --skip-graph-check} replays anyway and says so;</li>
 *   <li>the bundle is not what its manifest says: the manifest is read as JSON and held to the bundle schema, and
 *   every member to its digest and size, each within the runner's limits, before your build runs;</li>
 *   <li>a replay record anywhere in the member is malformed: the whole member is read before any is replayed;</li>
 *   <li>the bundle carries no replay records;</li>
 *   <li>a replay record names an event type your processor does not handle. The allow-list is YOUR build's: the event
 *   types its generated {@code handleEvent} methods take. Nothing the bundle names is loaded otherwise.</li>
 * </ul>
 * The audit log is written as the producer's fixtures are: each record framed by {@code ---}. What the runner's own
 * set-up (the clock, the level, the sink) emits, before the first recorded input, is left out: a build generated with
 * tracing on writes a record for it. Every record after that is written, whatever it says. The file appears only when
 * the replay completes.
 */
public class ReplayBundle {

    public static void main(String[] args) throws Exception {
        System.exit(run(args, System.out, System.err));
    }

    record Args(Path bundle, String processor, Path out, List<Path> cp, boolean skipGraphCheck, String level) { }

    static int run(String[] argv, java.io.PrintStream out, java.io.PrintStream err) {
        Args a;
        try {
            a = parse(argv);
        } catch (IllegalArgumentException e) {
            err.println(e.getMessage());
            err.println("usage: ReplayBundle --bundle <b.fexp> --processor <fqcn> --out <replayed-audit.yaml> "
                    + "[--cp <path>[" + File.pathSeparator + "<path>…]] [--skip-graph-check] [--level INFO]");
            return 2;
        }
        try {
            return replay(a, out, err);
        } catch (Refused | Oversize r) {
            err.println("REFUSED: " + r.getMessage());
            return 1;
        } catch (Exception e) {
            err.println("REFUSED: " + e);
            return 1;
        }
    }

    static final class Refused extends Exception {
        Refused(String why) { super(why); }
    }

    /** A member longer than it may be, found while it streams: refused as plainly as a {@link Refused}. */
    static final class Oversize extends IOException {
        Oversize(String why) { super(why); }
    }

    static Args parse(String[] argv) {
        Path bundle = null, out = null;
        String processor = null, level = "INFO";
        List<Path> cp = new ArrayList<>();
        boolean skip = false;
        for (int i = 0; i < argv.length; i++) {
            switch (argv[i]) {
                case "--bundle" -> bundle = Path.of(value(argv, ++i, "--bundle"));
                case "--processor" -> processor = value(argv, ++i, "--processor");
                case "--out" -> out = Path.of(value(argv, ++i, "--out"));
                case "--cp" -> {
                    for (String p : value(argv, ++i, "--cp").split(Pattern.quote(File.pathSeparator))) {
                        if (!p.isBlank()) cp.add(Path.of(p));
                    }
                }
                case "--level" -> level = value(argv, ++i, "--level");
                case "--skip-graph-check" -> skip = true;
                default -> throw new IllegalArgumentException("unknown argument: " + argv[i]);
            }
        }
        if (bundle == null || processor == null || out == null) {
            throw new IllegalArgumentException("--bundle, --processor and --out are required");
        }
        try {
            EventLogControlEvent.LogLevel.valueOf(level);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("--level is one of " + java.util.Arrays.toString(EventLogControlEvent.LogLevel.values()));
        }
        return new Args(bundle, processor, out, List.copyOf(cp), skip, level);
    }

    private static String value(String[] argv, int i, String flag) {
        if (i >= argv.length) throw new IllegalArgumentException(flag + " needs a value");
        return argv[i];
    }

    static int replay(Args a, java.io.PrintStream out, java.io.PrintStream err) throws Exception {
        if (Files.exists(a.out())) throw new Refused("will not overwrite " + a.out());
        Path work = Files.createTempDirectory("replay-bundle");
        try {
            return replay(a, out, work);
        } finally {
            try (var walk = Files.walk(work)) {
                for (Path p : walk.sorted(java.util.Comparator.reverseOrder()).toList()) Files.deleteIfExists(p);
            }
        }
    }

    private static int replay(Args a, java.io.PrintStream out, Path work) throws Exception {
        Members taken = members(a.bundle(), work);
        if (taken.replay() == null) throw new Refused("the bundle carries no replay records (no replay/ member)");

        URL[] urls = new URL[a.cp().size()];
        for (int i = 0; i < urls.length; i++) urls[i] = a.cp().get(i).toUri().toURL();
        try (URLClassLoader loader = new URLClassLoader(urls, ReplayBundle.class.getClassLoader())) {
            Class<?> type;
            try {
                type = loader.loadClass(a.processor());
            } catch (ClassNotFoundException e) {
                throw new Refused(a.processor() + " is not on the classpath given with --cp");
            }
            if (!DataFlow.class.isAssignableFrom(type)) throw new Refused(a.processor() + " is not a Fluxtion processor");

            String graphLine;
            if (a.skipGraphCheck()) {
                graphLine = "graph: NOT checked (--skip-graph-check): this build's graph is not compared with the bundle's";
            } else {
                if (taken.graphName() == null) throw new Refused("the bundle has no graph/ member to check your build against");
                String resource = a.processor().replace('.', '/') + ".graphml";
                byte[] mine;
                try (InputStream in = loader.getResourceAsStream(resource)) {
                    if (in == null) {
                        throw new Refused("your build carries no " + resource + ", so its graph cannot be compared with "
                                + "the bundle's; generate it with setGenerateDescription(true), or pass --skip-graph-check");
                    }
                    mine = readBounded(in, resource, MAX_GRAPH_BYTES);
                }
                String difference = graphDifference(taken.graph(), mine);
                if (difference != null) throw new Refused("your build's graph is not the bundle's: " + difference);
                // PR #70 review: the same node ids and edges, which is graph compatibility, not the same code
                graphLine = "graph: your build's node ids and edges match the bundle's (" + taken.graphName()
                        + "); this does not show it is the same code";
            }

            // the WHOLE replay member is read and every record built before your processor runs (PR #70 review 4):
            // a malformed record anywhere refuses the replay, with nothing run and nothing written
            Map<String, Class<?>> handled = handledTypes(type);
            int inputs = forEachRecord(taken.replay(), handled, e -> { });
            if (inputs == 0) throw new Refused("the replay member holds no records");
            if (taken.declaredRecords() != null && taken.declaredRecords() != inputs) {
                throw new Refused("the replay member holds " + inputs + " records, and the manifest says "
                        + taken.declaredRecords());
            }

            // written beside --out and moved there only when the replay completes: a refusal leaves no file
            Path part = a.out().resolveSibling(a.out().getFileName() + ".part-" + ProcessHandle.current().pid());
            long[] records = {0};
            try (var log = Files.newBufferedWriter(part, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW)) {
                DataFlow p = (DataFlow) type.getDeclaredConstructor().newInstance();
                p.init();
                long[] now = {0};
                boolean[] replaying = {false};
                p.onEvent(ClockStrategy.registerClockEvent(() -> now[0]));      // data-driven: each record's instant
                p.setAuditLogLevel(EventLogControlEvent.LogLevel.valueOf(a.level()));
                // what the runner's set-up emits is left out by WHEN it is written, before the first replay input, never
                // by what a record says (PR #70 review 3: a text filter deleted a business record that printed the
                // phrase). A build generated with tracing on writes a record for the set-up's own control event into
                // this sink (re-review C1); one with tracing off writes none. Every record after it is written
                p.setAuditLogProcessor(r -> {
                    if (!replaying[0]) return;
                    try {
                        log.write("---\n");
                        log.write(r.toString());
                        log.write('\n');
                        records[0]++;
                    } catch (IOException x) {
                        throw new java.io.UncheckedIOException(x);
                    }
                });
                replaying[0] = true;
                forEachRecord(taken.replay(), handled, e -> {
                    now[0] = (Long) e[1];
                    p.onEvent(e[0]);
                });
            } catch (Exception | Error x) {
                Files.deleteIfExists(part);
                throw x;
            }
            Files.move(part, a.out());
            out.println(graphLine);
            out.println("members: every member matches the manifest (for the bundle's identity: analyser --verify)");
            if (taken.levelChanges() > 0) {
                out.println("warning: the bundled log changes its audit level " + taken.levelChanges() + " time(s); this "
                        + "replay runs at one level (--level " + a.level() + "), so records after a change may differ "
                        + "for that reason alone");
            }
            out.println("replayed: " + inputs + " recorded inputs into " + a.processor() + ", on a data-driven clock");
            out.println("wrote: " + a.out() + " (" + records[0] + " audit records)");
            out.println("next: analyser --replay-compare " + a.bundle() + " " + a.out());
            return 0;
        }
    }

    // ---- the bundle's members, each held to the manifest -----------------------------------------------------------

    /** The largest member the runner reads; members are streamed, and only the graph is held. */
    static final long MAX_MEMBER_BYTES = Long.getLong("replayBundle.maxMemberBytes", 512L << 20);
    /** The largest bundle the runner reads: every member's declared size, together. */
    static final long MAX_BUNDLE_BYTES = Long.getLong("replayBundle.maxBundleBytes", 4L << 30);
    /** The graph is held and parsed, so it has its own, smaller bound. */
    static final long MAX_GRAPH_BYTES = Long.getLong("replayBundle.maxGraphBytes", 32L << 20);
    /** The longest line the runner reads: a replay record is one line per field, and a log line is only scanned. */
    static final int MAX_LINE_CHARS = Integer.getInteger("replayBundle.maxLineChars", 1 << 20);
    static final int MANIFEST_MAX_BYTES = 4 << 20;

    /**
     * What the runner takes from a bundle: the replay member, spooled to {@code work}; the graph, held; and what the
     * log says about audit levels.
     */
    record Members(Path replay, Integer declaredRecords, String graphName, byte[] graph, int levelChanges) { }

    /** One member as the manifest lists it. */
    record Listed(String path, String sha256, long bytes) { }

    /** The manifest, read as JSON and held to the bundle schema the analyser reads (PR #70 review 7). */
    record Manifest(int format, Map<String, Listed> members, String replayMember, Integer replayRecords) { }

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    /** A JSON number that is whole, as a long, or null: {@code 3375} and {@code 3375.0} are the same number. */
    static Long whole(Object v) {
        if (v instanceof Long l) return l;
        if (v instanceof Double d && d == Math.rint(d) && Math.abs(d) <= (1L << 53)) return (long) (double) d;
        return null;
    }

    /**
     * The manifest's schema, checked before any member is read (PR #70 review 2 and 7): format 1 or 2; each member a
     * path, a sha256 and a non-negative size, listed once; a format-2 bundle's replay names its one {@code replay/}
     * member, and no bundle lists more than one {@code graph/}. Every size is within the runner's limits, and all of
     * them together within the bundle limit, so what the runner will read is bounded before it reads it.
     */
    @SuppressWarnings("unchecked")
    static Manifest manifest(byte[] json) throws Refused {
        Object parsed = Json.parse(new String(json, StandardCharsets.UTF_8));
        if (!(parsed instanceof Map<?, ?> m)) throw new Refused("manifest.json is not a JSON object");
        Object format = m.get("format");
        Long f = whole(format);
        if (f == null || (f != 1 && f != 2)) {
            throw new Refused("manifest.json: unsupported format " + format + " (this runner reads formats 1 and 2)");
        }
        if (!(m.get("members") instanceof List<?> list)) throw new Refused("manifest.json has no members list");
        Map<String, Listed> members = new LinkedHashMap<>();
        long total = 0;
        int replays = 0, graphs = 0;
        for (Object o : list) {
            if (!(o instanceof Map<?, ?> x) || !(x.get("path") instanceof String path) || !(x.get("sha256") instanceof String sha)
                    || whole(x.get("bytes")) == null || whole(x.get("bytes")) < 0) {
                throw new Refused("manifest.json: a member needs path, sha256 and a non-negative whole bytes");
            }
            long bytes = whole(x.get("bytes"));
            if (!SHA256.matcher(sha).matches()) throw new Refused("manifest.json: " + path + " has no sha256 digest");
            if (path.isEmpty() || path.startsWith("/") || path.contains("\\") || path.equals("manifest.json")
                    || java.util.Arrays.asList(path.split("/")).contains("..")) {
                throw new Refused("manifest.json lists a member that cannot be a bundle path: " + path);
            }
            if (members.put(path, new Listed(path, sha, bytes)) != null) throw new Refused("manifest.json lists " + path + " twice");
            if (bytes > MAX_MEMBER_BYTES) throw new Refused(path + " is larger than the runner's limit of " + MAX_MEMBER_BYTES + " bytes");
            if (path.startsWith("graph/") && bytes > MAX_GRAPH_BYTES) {
                throw new Refused(path + " is larger than the runner's graph limit of " + MAX_GRAPH_BYTES + " bytes");
            }
            total += bytes;
            if (total > MAX_BUNDLE_BYTES) throw new Refused("the bundle's members are larger than the runner's limit of "
                    + MAX_BUNDLE_BYTES + " bytes together");
            if (path.startsWith("replay/")) replays++;
            if (path.startsWith("graph/")) graphs++;
        }
        if (graphs > 1) throw new Refused("the bundle lists " + graphs + " graph/ members, not one");
        String replayMember = null;
        Integer replayRecords = null;
        Object r = m.get("replay");
        if (f == 1) {
            if (r != null || replays != 0) throw new Refused("a format 1 manifest states no replay, and lists no replay/ member");
        } else {
            if (!(r instanceof Map<?, ?> rm) || !(rm.get("member") instanceof String member)) {
                throw new Refused("a format 2 manifest needs a replay with its member");
            }
            if (replays != 1) throw new Refused("the bundle lists " + replays + " replay/ members, not one");
            if (!member.startsWith("replay/") || !members.containsKey(member)) throw new Refused("the replay member " + member + " is not listed");
            Object n = rm.get("records");
            if (n != null) {
                Long c = whole(n);
                if (c == null || c < 0 || c > Integer.MAX_VALUE) throw new Refused("manifest.json: replay records is not a count");
                replayRecords = (int) (long) c;
            }
            replayMember = member;
        }
        return new Manifest(f.intValue(), members, replayMember, replayRecords);
    }

    /**
     * Reads the manifest (the first entry) and its schema, then every member, each held to the manifest's sha256 and
     * size as it streams past (review S3/S4; PR #70 review 2): a member the manifest does not list, or lists and the
     * bundle lacks, is refused. Only the graph is held in memory; the replay is spooled to {@code work}, and the log
     * is scanned, a bounded line at a time, for audit-level changes. {@code analyser --verify} states the bundle's
     * identity; this is what the runner needs to trust what it reads.
     */
    static Members members(Path bundle, Path work) throws IOException, Refused {
        Manifest manifest = null;
        Path replay = null;
        String graphName = null;
        byte[] graph = null;
        int levelChanges = 0;
        java.util.Set<String> seen = new java.util.HashSet<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(bundle))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                String name = e.getName();
                if (manifest == null) {
                    if (!name.equals("manifest.json")) throw new Refused("no manifest.json first: this is not an evidence bundle");
                    manifest = manifest(readBounded(zip, name, MANIFEST_MAX_BYTES));
                    continue;
                }
                if (!seen.add(name)) throw new Refused("duplicate member: " + name);
                Listed want = manifest.members().get(name);
                if (want == null) throw new Refused(name + " is not listed in the manifest");
                var bounded = new BoundedStream(zip, name, want.bytes(), "the manifest's " + want.bytes() + " bytes");
                var digest = new java.security.DigestInputStream(bounded, sha256());
                if (name.equals(manifest.replayMember())) {
                    replay = work.resolve("replay.yaml");
                    Files.copy(digest, replay);
                } else if (name.startsWith("graph/")) {
                    graph = digest.readAllBytes();
                    graphName = name;
                } else if (name.startsWith("log/")) {
                    var r = new java.io.BufferedReader(new java.io.InputStreamReader(digest, StandardCharsets.UTF_8));
                    for (String l; (l = line(r, true)) != null; ) if (l.strip().equals("event: EventLogControlEvent")) levelChanges++;
                } else {
                    digest.transferTo(java.io.OutputStream.nullOutputStream());
                }
                String sha = java.util.HexFormat.of().formatHex(digest.getMessageDigest().digest());
                if (!sha.equals(want.sha256()) || bounded.total() != want.bytes()) {
                    throw new Refused(name + " does not match the manifest: the bundle was changed; run analyser --verify");
                }
            }
        }
        if (manifest == null) throw new Refused("no manifest.json: this is not an evidence bundle");
        for (String listed : manifest.members().keySet()) {
            if (!seen.contains(listed)) throw new Refused("missing member: " + listed);
        }
        return new Members(replay, manifest.replayRecords(), graphName, graph, levelChanges);
    }

    /**
     * A member's bytes, read no further than {@code max}: past it, the read fails naming the member and the bound.
     * Reading past a member's declared size is a changed bundle, and is refused as that.
     */
    static final class BoundedStream extends java.io.FilterInputStream {
        private final String name;
        private final long max;
        private final String bound;
        private long total;

        BoundedStream(InputStream in, String name, long max, String bound) {
            super(in);
            this.name = name;
            this.max = max;
            this.bound = bound;
        }

        long total() {
            return total;
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b >= 0) count(1);
            return b;
        }

        @Override
        public int read(byte[] buf, int off, int len) throws IOException {
            int n = super.read(buf, off, len);
            if (n > 0) count(n);
            return n;
        }

        private void count(int n) throws IOException {
            total += n;
            if (total > max) throw new Oversize(name + " does not match the manifest: it is larger than " + bound);
        }

        @Override
        public void close() {
            // the zip stream's entries are read in turn; closing one member must not close the archive
        }
    }

    private static byte[] readBounded(InputStream in, String name, long max) throws IOException, Refused {
        ByteArrayOutputStream b = new ByteArrayOutputStream();
        byte[] buf = new byte[64 * 1024];
        long total = 0;
        for (int n; (n = in.read(buf)) > 0; ) {
            total += n;
            if (total > max) throw new Refused(name + " is larger than the runner's limit of " + max + " bytes");
            b.write(buf, 0, n);
        }
        return b.toByteArray();
    }

    private static java.security.MessageDigest sha256() {
        try {
            return java.security.MessageDigest.getInstance("SHA-256");
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * One line, without its {@code \n} or a {@code \r} before it, or null at the end. A line longer than
     * {@link #MAX_LINE_CHARS} is refused, or with {@code scanOnly} cut short and the rest skipped: a scanned log
     * line is only compared, so a long one is not needed whole, and nothing unbounded is ever held.
     */
    static String line(java.io.Reader r, boolean scanOnly) throws IOException {
        StringBuilder b = new StringBuilder();
        int c;
        boolean any = false;
        while ((c = r.read()) >= 0) {
            any = true;
            if (c == '\n') break;
            if (b.length() < MAX_LINE_CHARS) b.append((char) c);
            else if (!scanOnly) throw new IOException("a line longer than the runner's limit of " + MAX_LINE_CHARS + " characters");
        }
        if (!any) return null;
        int n = b.length();
        if (n > 0 && b.charAt(n - 1) == '\r') b.setLength(n - 1);
        return b.toString();
    }

    // ---- JSON, as RFC 8259 has it: the manifest is read as JSON, never matched as text (PR #70 review 7) ----------

    /**
     * A strict JSON reader for the manifest: objects (a key twice is refused), arrays, strings, numbers, true, false
     * and null, with nothing after the value. A whole number is a {@link Long}, any other a {@link Double}. The
     * manifest is bounded before it is read, and nesting is bounded here, so a hostile manifest cannot exhaust the
     * stack.
     */
    static final class Json {
        private final String s;
        private int i;
        private int depth;

        private Json(String s) {
            this.s = s;
        }

        static Object parse(String text) throws Refused {
            Json j = new Json(text.startsWith("﻿") ? text.substring(1) : text);
            j.space();
            Object v = j.value();
            j.space();
            if (j.i != j.s.length()) throw j.bad("content after the JSON value");
            return v;
        }

        private Refused bad(String why) {
            return new Refused("manifest.json is not valid JSON at character " + i + ": " + why);
        }

        private void space() {
            while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == '\t' || s.charAt(i) == '\n' || s.charAt(i) == '\r')) i++;
        }

        private Object value() throws Refused {
            if (i >= s.length()) throw bad("a value is missing");
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> word("true", Boolean.TRUE);
                case 'f' -> word("false", Boolean.FALSE);
                case 'n' -> word("null", null);
                default -> number();
            };
        }

        private Object word(String w, Object v) throws Refused {
            if (!s.startsWith(w, i)) throw bad("not a value");
            i += w.length();
            return v;
        }

        private Map<String, Object> object() throws Refused {
            if (++depth > 32) throw bad("nested too deeply");
            Map<String, Object> out = new LinkedHashMap<>();
            i++;
            space();
            if (i < s.length() && s.charAt(i) == '}') {
                i++;
                depth--;
                return out;
            }
            while (true) {
                space();
                if (i >= s.length() || s.charAt(i) != '"') throw bad("a key must be a string");
                String k = string();
                space();
                if (i >= s.length() || s.charAt(i) != ':') throw bad("':' expected");
                i++;
                space();
                Object v = value();
                if (out.containsKey(k)) throw bad("the key " + k + " twice");
                out.put(k, v);
                space();
                if (i < s.length() && s.charAt(i) == ',') {
                    i++;
                    continue;
                }
                if (i < s.length() && s.charAt(i) == '}') {
                    i++;
                    depth--;
                    return out;
                }
                throw bad("',' or '}' expected");
            }
        }

        private List<Object> array() throws Refused {
            if (++depth > 32) throw bad("nested too deeply");
            List<Object> out = new ArrayList<>();
            i++;
            space();
            if (i < s.length() && s.charAt(i) == ']') {
                i++;
                depth--;
                return out;
            }
            while (true) {
                space();
                out.add(value());
                space();
                if (i < s.length() && s.charAt(i) == ',') {
                    i++;
                    continue;
                }
                if (i < s.length() && s.charAt(i) == ']') {
                    i++;
                    depth--;
                    return out;
                }
                throw bad("',' or ']' expected");
            }
        }

        private String string() throws Refused {
            StringBuilder b = new StringBuilder();
            i++;
            while (true) {
                if (i >= s.length()) throw bad("a string is not closed");
                char c = s.charAt(i++);
                if (c == '"') return b.toString();
                if (c < 0x20) throw bad("a control character in a string");
                if (c != '\\') {
                    b.append(c);
                    continue;
                }
                if (i >= s.length()) throw bad("a dangling escape");
                char e = s.charAt(i++);
                switch (e) {
                    case '"', '\\', '/' -> b.append(e);
                    case 'b' -> b.append('\b');
                    case 'f' -> b.append('\f');
                    case 'n' -> b.append('\n');
                    case 'r' -> b.append('\r');
                    case 't' -> b.append('\t');
                    case 'u' -> {
                        if (i + 4 > s.length()) throw bad("a short \\u escape");
                        try {
                            b.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        } catch (NumberFormatException x) {
                            throw bad("a bad \\u escape");
                        }
                        i += 4;
                    }
                    default -> throw bad("an unknown escape \\" + e);
                }
            }
        }

        private static final Pattern NUMBER = Pattern.compile("-?(0|[1-9][0-9]*)(\\.[0-9]+)?([eE][+-]?[0-9]+)?");

        private Object number() throws Refused {
            Matcher m = NUMBER.matcher(s).region(i, s.length());
            if (!m.lookingAt()) throw bad("not a value");
            String t = m.group();
            i = m.end();
            if (m.group(2) == null && m.group(3) == null) {
                try {
                    return Long.parseLong(t);
                } catch (NumberFormatException e) {
                    throw bad("a whole number too large: " + t);
                }
            }
            return Double.parseDouble(t);
        }
    }

    // ---- is this the same processor? (spec §5.2): nodes and edges, not bytes -----------------------------------

    /** How {@code mine} differs from {@code theirs}, in words, or null when they have the same nodes and edges. */
    static String graphDifference(byte[] theirs, byte[] mine) throws Exception {
        Graph t = graph(theirs), m = graph(mine);
        List<String> out = new ArrayList<>();
        TreeSet<String> added = new TreeSet<>(m.nodes()), removed = new TreeSet<>(t.nodes());
        added.removeAll(t.nodes());
        removed.removeAll(m.nodes());
        if (!added.isEmpty()) out.add("node(s) " + added + " added");
        if (!removed.isEmpty()) out.add("node(s) " + removed + " missing");
        TreeSet<String> edgesAdded = new TreeSet<>(m.edges()), edgesRemoved = new TreeSet<>(t.edges());
        edgesAdded.removeAll(t.edges());
        edgesRemoved.removeAll(m.edges());
        if (!edgesAdded.isEmpty()) out.add("edge(s) " + edgesAdded + " added");
        if (!edgesRemoved.isEmpty()) out.add("edge(s) " + edgesRemoved + " missing");
        return out.isEmpty() ? null : String.join("; ", out);
    }

    record Graph(TreeSet<String> nodes, TreeSet<String> edges) { }

    static Graph graph(byte[] graphml) throws Exception {
        DocumentBuilderFactory f = DocumentBuilderFactory.newInstance();
        // untrusted XML: no DOCTYPE, no external entities
        f.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        f.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        f.setExpandEntityReferences(false);
        Document d = f.newDocumentBuilder().parse(new ByteArrayInputStream(graphml));
        TreeSet<String> nodes = new TreeSet<>(), edges = new TreeSet<>();
        NodeList ns = d.getElementsByTagName("node");
        for (int i = 0; i < ns.getLength(); i++) nodes.add(((Element) ns.item(i)).getAttribute("id"));
        NodeList es = d.getElementsByTagName("edge");
        for (int i = 0; i < es.getLength(); i++) {
            Element e = (Element) es.item(i);
            edges.add(e.getAttribute("source") + "->" + e.getAttribute("target"));
        }
        return new Graph(nodes, edges);
    }

    // ---- the allow-list is YOUR build's handled event types ----------------------------------------------------

    static Map<String, Class<?>> handledTypes(Class<?> processor) {
        Map<String, Class<?>> out = new HashMap<>();
        for (Method m : processor.getMethods()) {
            if (m.getName().equals("handleEvent") && m.getParameterCount() == 1) {
                Class<?> t = m.getParameterTypes()[0];
                out.put(t.getName(), t);
            }
        }
        return out;
    }

    // ---- the replay records: ReplayRecord YAML, events as records, each resolved from the allow-list ------------

    private static final Pattern EVENT = Pattern.compile("^event: !!([\\w.$]+) \\{(.*)}$");
    private static final Pattern TIME = Pattern.compile("^wallClockTime: (-?\\d+)$");

    /** What is done with each record read: the grammar's raw fields, or a built event and its time. */
    interface Sink<T> {
        void accept(T t) throws Exception;
    }

    /**
     * The replay-record grammar, every line of it (PR #70 review, finding 2 and 4): blank lines, then for each record
     * exactly {@code ---}, the {@code ReplayRecord} header, one {@code event:} line and one {@code wallClockTime:}
     * line, then blank lines until the next {@code ---} or the end. A preamble, a stray or repeated field, trailing
     * content or a record cut short is refused, naming the line; nothing is searched for and nothing is skipped. It
     * reads a line at a time, so a replay of any size is read in bounded memory. Each record is {class name,
     * component body, time}. Returns how many were read.
     */
    static int records(java.io.Reader in, Sink<String[]> sink) throws Exception {
        int n = 0, at = 0, state = 0;          // state: 0 between records, 1 after ---, 2 after the header, 3 after the event
        String event = null, body = null, line;
        int start = 0;
        while ((line = next(in, at + 1)) != null) {
            at++;
            if (at == 1 && line.startsWith("﻿")) line = line.substring(1);
            switch (state) {
                case 0 -> {
                    if (line.isBlank()) continue;
                    if (!line.equals("---")) throw new Refused("line " + at + " is not part of a replay record: " + line.strip());
                    start = at;
                    state = 1;
                }
                case 1 -> {
                    if (!line.equals(HEADER)) throw new Refused("line " + at + " is not the ReplayRecord header: " + line.strip());
                    state = 2;
                }
                case 2 -> {
                    Matcher e = EVENT.matcher(line);
                    if (!e.matches()) throw new Refused("line " + at + " is not a replay record's event line: " + line.strip());
                    event = e.group(1);
                    body = e.group(2);
                    state = 3;
                }
                default -> {
                    Matcher t = TIME.matcher(line);
                    if (!t.matches()) throw new Refused("line " + at + " is not a replay record's wallClockTime line: " + line.strip());
                    sink.accept(new String[]{event, body, t.group(1)});
                    n++;
                    state = 0;
                }
            }
        }
        if (state != 0) throw new Refused("the replay record at line " + start + " is cut off");
        return n;
    }

    private static String next(java.io.Reader in, int at) throws Refused {
        try {
            return line(in, false);
        } catch (IOException e) {
            throw new Refused("the replay cannot be read at line " + at + ": " + e.getMessage());
        }
    }

    /** The records of a whole replay document, as {class name, component body, time}. */
    static List<String[]> records(String yaml) throws Exception {
        List<String[]> out = new ArrayList<>();
        records(new java.io.StringReader(yaml), out::add);
        return out;
    }

    static final String HEADER = "!!com.telamin.fluxtion.runtime.event.ReplayRecord";

    /**
     * Each record, built against the allow-list and handed to {@code sink} as {event, Long time}. Called once to check
     * the whole member before your processor runs, and again to replay it: nothing is held between.
     */
    static int forEachRecord(Path replay, Map<String, Class<?>> handled, Sink<Object[]> sink) throws Exception {
        var decoder = StandardCharsets.UTF_8.newDecoder();        // malformed UTF-8 is refused, never replaced
        try (var in = new java.io.BufferedReader(new java.io.InputStreamReader(Files.newInputStream(replay), decoder))) {
            return forEachRecord(in, handled, sink);
        }
    }

    static int forEachRecord(java.io.Reader in, Map<String, Class<?>> handled, Sink<Object[]> sink) throws Exception {
        return records(in, r -> sink.accept(event(r, handled)));
    }

    private static Object[] event(String[] r, Map<String, Class<?>> handled) throws Exception {
        Class<?> type = handled.get(r[0]);
        if (type == null) throw new Refused("a replay record names " + r[0] + ", which your processor does not handle");
        if (!type.isRecord()) throw new Refused(r[0] + " is not a record; this runner reads record events");
        long time;
        try {
            time = Long.parseLong(r[2]);
        } catch (NumberFormatException e) {
            throw new Refused("not a time: " + r[2]);
        }
        return new Object[]{build(type, r[1]), time};
    }

    static List<Object[]> read(String yaml, Map<String, Class<?>> handled) throws Exception {
        List<Object[]> out = new ArrayList<>();
        forEachRecord(new java.io.StringReader(yaml), handled, out::add);
        if (out.isEmpty()) throw new Refused("the replay member holds no records");
        return out;
    }

    static Object build(Class<?> type, String body) throws Exception {
        RecordComponent[] parts = type.getRecordComponents();
        List<String> values = split(body);
        if (values.size() != parts.length) throw new Refused(type.getName() + " has " + parts.length + " components: " + body);
        Class<?>[] types = new Class<?>[parts.length];
        Object[] args = new Object[parts.length];
        for (int i = 0; i < parts.length; i++) {
            String kv = values.get(i);
            int colon = kv.indexOf(':');
            if (colon < 0) throw new Refused(type.getName() + ": not a component: " + kv.strip());
            String name = kv.substring(0, colon).strip();
            if (!name.equals(parts[i].getName())) throw new Refused(type.getName() + ": expected " + parts[i].getName() + ", got " + name);
            types[i] = parts[i].getType();
            args[i] = value(types[i], kv.substring(colon + 1).strip());
        }
        Constructor<?> c = type.getDeclaredConstructor(types);
        c.setAccessible(true);
        return c.newInstance(args);
    }

    static Object value(Class<?> t, String raw) throws Refused {
        if (raw.equals("null")) {
            if (t.isPrimitive()) throw new Refused("null for a primitive " + t.getName());
            return null;
        }
        if (t == String.class || t == char.class || t == Character.class) {
            String v = unquote(raw);
            if (t == String.class) return v;
            if (v.length() != 1) throw new Refused("not one character: " + raw);
            return v.charAt(0);
        }
        try {
            if (t == double.class || t == Double.class) return Double.parseDouble(raw);
            if (t == float.class || t == Float.class) return Float.parseFloat(raw);
            if (t == int.class || t == Integer.class) return Integer.parseInt(raw);
            if (t == long.class || t == Long.class) return Long.parseLong(raw);
            if (t == short.class || t == Short.class) return Short.parseShort(raw);
            if (t == byte.class || t == Byte.class) return Byte.parseByte(raw);
        } catch (NumberFormatException e) {
            throw new Refused("not a " + t.getSimpleName() + ": " + raw);
        }
        if (t == boolean.class || t == Boolean.class) {
            if (!raw.equals("true") && !raw.equals("false")) throw new Refused("not a boolean: " + raw);
            return Boolean.parseBoolean(raw);
        }
        throw new Refused("unsupported component type " + t.getName());
    }

    /** One quoted token, unescaped left to right, as the replay writer escapes it. An unquoted string is refused. */
    static String unquote(String raw) throws Refused {
        if (raw.length() < 2 || raw.charAt(0) != '"' || raw.charAt(raw.length() - 1) != '"') {
            throw new Refused("a string must be quoted: " + raw);
        }
        StringBuilder out = new StringBuilder();
        for (int i = 1; i < raw.length() - 1; i++) {
            char ch = raw.charAt(i);
            if (ch != '\\') {
                out.append(ch);
                continue;
            }
            if (++i >= raw.length() - 1) throw new Refused("a dangling escape: " + raw);
            char e = raw.charAt(i);
            switch (e) {
                case '\\' -> out.append('\\');
                case '"' -> out.append('"');
                case 'n' -> out.append('\n');
                case 'r' -> out.append('\r');
                case 't' -> out.append('\t');
                case 'u' -> {
                    if (i + 4 >= raw.length() - 1) throw new Refused("a short \\u escape: " + raw);
                    try {
                        out.append((char) Integer.parseInt(raw.substring(i + 1, i + 5), 16));
                    } catch (NumberFormatException x) {
                        throw new Refused("a bad \\u escape: " + raw);
                    }
                    i += 4;
                }
                default -> throw new Refused("an unknown escape \\" + e + ": " + raw);
            }
        }
        return out.toString();
    }

    static List<String> split(String body) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (quoted && ch == '\\') {
                cur.append(ch).append(body.charAt(++i));
                continue;
            }
            if (ch == '"') quoted = !quoted;
            if (ch == ',' && !quoted) {
                out.add(cur.toString());
                cur.setLength(0);
                continue;
            }
            cur.append(ch);
        }
        if (!cur.isEmpty()) out.add(cur.toString());
        return out;
    }
}
