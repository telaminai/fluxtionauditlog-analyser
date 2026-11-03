package telamin.fluxtion.audit.analyser.bundle;

import com.telamin.fluxtion.runtime.audit.BinaryLogFile;
import com.telamin.fluxtion.runtime.audit.BinaryLogReader;
import telamin.fluxtion.audit.analyser.analyser.parse.EvidenceVerdict;
import telamin.fluxtion.audit.analyser.analyser.parse.RunSegments;
import telamin.fluxtion.audit.analyser.analyser.parse.StreamEndTracker;
import telamin.fluxtion.audit.analyser.analyser.parse.StreamHeader;
import telamin.fluxtion.audit.analyser.analyser.topology.GraphMlParser;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Evidence-set key ({@code docs/specs/spec-evidence-set-key.md} §4.5): the GRAPH KEY and RUN KEY of every log and graph
 * a bundle carries.
 *
 * <ul>
 *   <li>a log member's graph keys are the {@code sourceFingerprint}s its stream headers state (null for a header that
 *       states none) and its run keys are its runs by {@link RunSegments}, read with the stores' own rules: headers and
 *       stream-end markers out of the records ({@link StreamEndTracker}), stream breaks at each marker;</li>
 *   <li>a graph member's graph key is its {@code fluxtion.sourceFingerprint}, with the builder that digested it.</li>
 * </ul>
 * Written into the manifest at capture, and RECOMPUTED from the members' own bytes by {@code --verify}, which judges them
 * with the shared {@link EvidenceVerdict} and reports any disagreement with what the manifest recorded (review F3): the
 * manifest's copy is the sender's statement, never the evidence.
 */
public final class EvidenceKeys {

    private EvidenceKeys() {
    }

    /** What one member states: its manifest entry, and for a log its shape, for a graph its key and builder. */
    public record Member(Map<String, Object> entry, EvidenceVerdict.LogShape shape, String graphKey, String graphBuilder) {
        public boolean isLog() {
            return "log".equals(entry.get("kind"));
        }
    }

    /** The keys of every {@code log/} and {@code graph/} member under {@code payload}, in path order. */
    public static List<Map<String, Object>> of(Path payload) throws IOException {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Member m : members(payload)) out.add(m.entry());
        return out;
    }

    /** As {@link #of}, with each log's shape and each graph's builder, for judging. */
    public static List<Member> members(Path payload) throws IOException {
        TreeMap<String, Path> files = new TreeMap<>();
        for (String dir : List.of("log", "graph")) {
            Path d = payload.resolve(dir);
            if (!Files.isDirectory(d)) continue;
            try (var list = Files.list(d)) {
                for (Path p : (Iterable<Path>) list::iterator) {
                    if (Files.isRegularFile(p)) files.put(dir + "/" + p.getFileName(), p);
                }
            }
        }
        List<Member> out = new ArrayList<>();
        for (var e : files.entrySet()) {
            try (InputStream in = Files.newInputStream(e.getValue())) {
                out.add(member(e.getKey(), in));
            }
        }
        return out;
    }

    /**
     * Recompute every log and graph member's keys from a bundle's own bytes, read from the archive as a stream (nothing is
     * extracted). Call it only on a bundle {@link EvidenceBundle#verify} accepted: then the bytes read are the ones the
     * manifest's digests vouch for.
     */
    public static List<Member> recompute(Path bundle) throws IOException {
        TreeMap<String, Member> out = new TreeMap<>();
        try (InputStream raw = Files.newInputStream(bundle); ZipInputStream zip = new ZipInputStream(raw)) {
            for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                String name = e.getName();
                if (e.isDirectory() || !(name.startsWith("log/") || name.startsWith("graph/"))
                        || name.indexOf('/') != name.lastIndexOf('/')) continue;
                out.put(name, member(name, new NonClosing(zip)));
            }
        }
        return List.copyOf(out.values());
    }

    private static Member member(String name, InputStream in) {
        return name.startsWith("log/") ? ofLog(name, in) : ofGraph(name, in);
    }

    static Member ofGraph(String member, InputStream in) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("member", member);
        m.put("kind", "graph");
        String fingerprint = null;
        String builder = null;
        try {
            var facts = GraphMlParser.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8)).vocabulary().graphFacts();
            fingerprint = blank(facts.get("fluxtion.sourceFingerprint"));
            builder = blank(facts.get("fluxtion.toolchainVersion"));
        } catch (IOException | RuntimeException e) {
            m.put("note", "the graph could not be read for its fingerprint");
        }
        m.put("graphKey", fingerprint);
        m.put("toolchainVersion", builder);
        return new Member(m, null, fingerprint, builder);
    }

    /** A log member's keys, read with the stores' own framing rules, from its bytes. */
    public static Member ofLog(String member, InputStream in) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("member", member);
        m.put("kind", "log");
        EvidenceVerdict.LogShape shape;
        try {
            BufferedInputStream b = new BufferedInputStream(in, 1 << 16);
            b.mark(8);
            byte[] head = b.readNBytes(BinaryLogFile.MAGIC.length);
            b.reset();
            shape = java.util.Arrays.equals(head, BinaryLogFile.MAGIC) ? binary(b) : text(b);
        } catch (IOException | RuntimeException e) {
            m.put("note", "the log could not be read for its stream headers");
            return new Member(m, null, null, null);
        }
        m.put("streamHeaders", shape.headers());
        m.put("graphKeys", shape.fingerprints());
        List<Map<String, Object>> runs = new ArrayList<>();
        for (RunSegments.Segment s : shape.segments()) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("label", s.label());
            r.put("runKey", s.headed() ? s.runKey().toMap() : null);
            r.put("records", s.records());
            runs.add(r);
        }
        m.put("runKeys", runs);
        return new Member(m, shape, null, null);
    }

    /** How much of one document is kept to recognise it: a header or a marker is a few hundred bytes. */
    static final int DOCUMENT_PREFIX = 64 * 1024;

    /**
     * A text log, framed on {@code ---} separator lines as the stores frame it, in BOUNDED memory: only the first
     * {@link #DOCUMENT_PREFIX} bytes of each document are kept, which is all a stream header or a stream-end marker can
     * be, so {@code --verify} of a bundle carrying a log of any size runs in a small heap (EvidenceBundleBoundedMemoryTest).
     * Headers and markers come out of the records with the tracker the stores use; breaks are at each marker.
     */
    private static EvidenceVerdict.LogShape text(InputStream in) throws IOException {
        StreamEndTracker tracker = new StreamEndTracker();
        int[] records = {0};
        java.util.function.Consumer<String> document = t -> {
            int after = StreamHeader.documentAfter(t);
            if (after > 0) {
                // a header the sink ran into the next document: the header, then the rest as a document of its own
                tracker.accept(String.join("\n", java.util.Arrays.asList(t.split("\n", -1)).subList(0, after)));
                if (tracker.accept(String.join("\n", java.util.Arrays.asList(t.split("\n", -1)).subList(after, t.split("\n", -1).length)))) records[0]++;
            } else if (!t.isBlank() && tracker.accept(t)) {
                records[0]++;
            }
        };
        java.io.ByteArrayOutputStream doc = new java.io.ByteArrayOutputStream();
        java.io.ByteArrayOutputStream line = new java.io.ByteArrayOutputStream();
        boolean first = true;
        boolean content = false;
        int b;
        java.io.InputStream buffered = in instanceof BufferedInputStream ? in : new BufferedInputStream(in, 1 << 16);
        while (true) {
            b = buffered.read();
            if (b != -1 && b != '\n') {
                if (line.size() < DOCUMENT_PREFIX) line.write(b);
                continue;
            }
            String l = line.toString(StandardCharsets.UTF_8);
            if (first) {
                while (!l.isEmpty() && telamin.fluxtion.audit.analyser.analyser.parse.AuditText.isBom(l.charAt(0))) l = l.substring(1);
                first = false;
            }
            String trimmed = l.strip();
            if (trimmed.equals("---")) {
                if (content) document.accept(doc.toString(StandardCharsets.UTF_8));
                doc.reset();
                content = false;
            } else {
                if (!trimmed.isEmpty()) content = true;
                if (content && doc.size() < DOCUMENT_PREFIX) {
                    byte[] bytes = (l + "\n").getBytes(StandardCharsets.UTF_8);
                    doc.write(bytes, 0, Math.min(bytes.length, DOCUMENT_PREFIX - doc.size()));
                }
            }
            line.reset();
            if (b == -1) break;
        }
        if (content) document.accept(doc.toString(StandardCharsets.UTF_8));
        return EvidenceVerdict.shape(tracker.streamHeaders(), records[0], tracker.runBoundaries());
    }

    /** A binary log: the runtime's reader, records counted and never decoded. */
    private static EvidenceVerdict.LogShape binary(InputStream in) throws IOException {
        List<StreamHeader> headers = new ArrayList<>();
        int[] records = {0};
        BinaryLogReader.read(in.readAllBytes(), new BinaryLogReader.Visitor() {
            @Override public boolean onRecord(int eventTypeId, String eventType, long eventTime, long logTime,
                                              long endTime, int entryCount) {
                records[0]++;
                return false;
            }

            @Override public void onEntry(int nodeId, String node, int keyId, String key, int tag, long rawBits) {
            }

            @Override public void onStreamHeader(String text) {
                if (StreamHeader.isHeader(text)) headers.add(StreamHeader.parse(text, records[0]));
            }
        });
        return EvidenceVerdict.shape(headers, records[0], List.of());
    }

    private static String blank(String s) {
        return s == null || s.isBlank() ? null : s;
    }

    /** A zip entry's stream, which a reader may close without closing the archive. */
    private static final class NonClosing extends java.io.FilterInputStream {
        NonClosing(InputStream in) {
            super(in);
        }

        @Override public void close() {
        }
    }
}
