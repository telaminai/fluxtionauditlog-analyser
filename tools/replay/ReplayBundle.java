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
 *   <li>your build is not the bundle's processor: its GraphML (the generator writes {@code <Class>.graphml} beside the
 *   class) differs from the bundle's {@code graph/} member in its nodes or edges. {@code --skip-graph-check} replays
 *   anyway and says so;</li>
 *   <li>the bundle carries no replay records;</li>
 *   <li>a replay record names an event type your processor does not handle. The allow-list is YOUR build's: the event
 *   types its generated {@code handleEvent} methods take. Nothing the bundle names is loaded otherwise.</li>
 * </ul>
 * The audit log is written as the producer's fixtures are: each record framed by {@code ---}, and the runner's own
 * set-up records ({@code EventLogControlEvent}) left out, because they are this run's configuration, not the run's.
 */
public class ReplayBundle {

    /** Every bundle member is read into memory: a guard against a bundle that is not what it says. */
    static final long MAX_MEMBER_BYTES = 512L << 20;

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
        } catch (Refused r) {
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
        return new Args(bundle, processor, out, List.copyOf(cp), skip, level);
    }

    private static String value(String[] argv, int i, String flag) {
        if (i >= argv.length) throw new IllegalArgumentException(flag + " needs a value");
        return argv[i];
    }

    static int replay(Args a, java.io.PrintStream out, java.io.PrintStream err) throws Exception {
        if (Files.exists(a.out())) throw new Refused("will not overwrite " + a.out());
        Map<String, byte[]> members = members(a.bundle());
        String replayName = only(members, "replay/");
        if (replayName == null) throw new Refused("the bundle carries no replay records (no replay/ member)");
        String graphName = only(members, "graph/");

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
                graphLine = "graph: NOT checked (--skip-graph-check): this build is not shown to be the bundle's processor";
            } else {
                if (graphName == null) throw new Refused("the bundle has no graph/ member to check your build against");
                String resource = a.processor().replace('.', '/') + ".graphml";
                byte[] mine;
                try (InputStream in = loader.getResourceAsStream(resource)) {
                    if (in == null) {
                        throw new Refused("your build carries no " + resource + ", so it cannot be shown to be the bundle's "
                                + "processor; generate it with setGenerateDescription(true), or pass --skip-graph-check");
                    }
                    mine = in.readAllBytes();
                }
                String difference = graphDifference(members.get(graphName), mine);
                if (difference != null) throw new Refused("your build's graph is not the bundle's: " + difference);
                graphLine = "graph: your build's nodes and edges are the bundle's (" + graphName + ")";
            }

            Map<String, Class<?>> handled = handledTypes(type);
            List<Object[]> entries = read(new String(members.get(replayName), StandardCharsets.UTF_8), handled);

            StringBuilder log = new StringBuilder();
            DataFlow p = (DataFlow) type.getDeclaredConstructor().newInstance();
            p.init();
            long[] now = {0};
            p.onEvent(ClockStrategy.registerClockEvent(() -> now[0]));      // data-driven: each record's instant
            p.setAuditLogLevel(EventLogControlEvent.LogLevel.valueOf(a.level()));
            p.setAuditLogProcessor(r -> {
                String text = r.toString();
                if (!text.contains("event: EventLogControlEvent")) log.append("---\n").append(text).append('\n');
            });
            for (Object[] e : entries) {
                now[0] = (Long) e[1];
                p.onEvent(e[0]);
            }
            Files.writeString(a.out(), log, StandardCharsets.UTF_8, java.nio.file.StandardOpenOption.CREATE_NEW);
            long records = log.toString().lines().filter(l -> l.equals("---")).count();
            out.println(graphLine);
            out.println("replayed: " + entries.size() + " recorded inputs into " + a.processor() + ", on a data-driven clock");
            out.println("wrote: " + a.out() + " (" + records + " audit records)");
            out.println("next: analyser --replay-compare " + a.bundle() + " " + a.out());
            return 0;
        }
    }

    // ---- the bundle's members, read directly: the analyser verifies; this only needs two of them ---------------

    static Map<String, byte[]> members(Path bundle) throws IOException, Refused {
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(bundle))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (e.isDirectory()) continue;
                ByteArrayOutputStream b = new ByteArrayOutputStream();
                byte[] buf = new byte[64 * 1024];
                long total = 0;
                for (int n; (n = zip.read(buf)) > 0; ) {
                    total += n;
                    if (total > MAX_MEMBER_BYTES) throw new Refused(e.getName() + " is larger than " + (MAX_MEMBER_BYTES >> 20) + " MiB");
                    b.write(buf, 0, n);
                }
                if (out.put(e.getName(), b.toByteArray()) != null) throw new Refused("duplicate member: " + e.getName());
            }
        }
        return out;
    }

    private static String only(Map<String, byte[]> members, String dir) throws Refused {
        List<String> found = members.keySet().stream().filter(n -> n.startsWith(dir)).toList();
        if (found.size() > 1) throw new Refused("the bundle has " + found.size() + " " + dir + " members, not one");
        return found.isEmpty() ? null : found.get(0);
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

    private static final Pattern EVENT = Pattern.compile("^event: !!(\\S+) \\{(.*)}$", Pattern.MULTILINE);
    private static final Pattern TIME = Pattern.compile("^wallClockTime: (-?\\d+)$", Pattern.MULTILINE);

    /** Each entry is {event, Long time}. */
    static List<Object[]> read(String yaml, Map<String, Class<?>> handled) throws Exception {
        List<Object[]> out = new ArrayList<>();
        for (String doc : yaml.split("(?m)^---$")) {
            if (doc.isBlank()) continue;
            Matcher e = EVENT.matcher(doc), t = TIME.matcher(doc);
            if (!e.find() || !t.find()) throw new Refused("not a replay record: " + doc.strip());
            Class<?> type = handled.get(e.group(1));
            if (type == null) throw new Refused("a replay record names " + e.group(1) + ", which your processor does not handle");
            if (!type.isRecord()) throw new Refused(e.group(1) + " is not a record; this runner reads record events");
            out.add(new Object[]{build(type, e.group(2)), Long.parseLong(t.group(1))});
        }
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
        if (t == String.class) return raw.substring(1, raw.length() - 1).replace("\\\"", "\"").replace("\\\\", "\\");
        if (t == double.class || t == Double.class) return Double.parseDouble(raw);
        if (t == float.class || t == Float.class) return Float.parseFloat(raw);
        if (t == int.class || t == Integer.class) return Integer.parseInt(raw);
        if (t == long.class || t == Long.class) return Long.parseLong(raw);
        if (t == short.class || t == Short.class) return Short.parseShort(raw);
        if (t == byte.class || t == Byte.class) return Byte.parseByte(raw);
        if (t == boolean.class || t == Boolean.class) return Boolean.parseBoolean(raw);
        if (t == char.class || t == Character.class) return raw.charAt(0);
        throw new Refused("unsupported component type " + t.getName());
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
