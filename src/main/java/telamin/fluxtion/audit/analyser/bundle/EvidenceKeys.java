package telamin.fluxtion.audit.analyser.bundle;

import com.telamin.fluxtion.runtime.audit.BinaryLogFile;
import com.telamin.fluxtion.runtime.audit.BinaryLogReader;
import telamin.fluxtion.audit.analyser.analyser.parse.RunSegments;
import telamin.fluxtion.audit.analyser.analyser.parse.StreamEndMarker;
import telamin.fluxtion.audit.analyser.analyser.parse.StreamHeader;
import telamin.fluxtion.audit.analyser.analyser.topology.GraphMlParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Evidence-set key ({@code docs/specs/spec-evidence-set-key.md} §4.5): the GRAPH KEY and RUN KEY of every log and graph
 * a bundle carries, recorded in its manifest so a recipient can see, before opening anything, which graph shape wrote
 * each log, which run it was, and whether the bundled graph has that shape.
 *
 * <ul>
 *   <li>a log member's graph keys are the {@code sourceFingerprint}s its stream headers state (null for a header that
 *       states none) and its run keys are its runs by {@link RunSegments}; a log with no header has neither, and says
 *       so ({@code streamHeaders: 0});</li>
 *   <li>a graph member's graph key is its {@code fluxtion.sourceFingerprint}.</li>
 * </ul>
 * Read from the members' own bytes as they are packed: nothing here is the sender's opinion, and nothing is inferred
 * when a member states nothing.
 */
public final class EvidenceKeys {

    private EvidenceKeys() {
    }

    /** The keys of every {@code log/} and {@code graph/} member under {@code payload}, by member path, in path order. */
    public static List<Map<String, Object>> of(Path payload) throws IOException {
        TreeMap<String, Path> members = new TreeMap<>();
        for (String dir : List.of("log", "graph")) {
            Path d = payload.resolve(dir);
            if (!Files.isDirectory(d)) continue;
            try (var list = Files.list(d)) {
                for (Path p : (Iterable<Path>) list::iterator) {
                    if (Files.isRegularFile(p)) members.put(dir + "/" + p.getFileName(), p);
                }
            }
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (var e : members.entrySet()) {
            out.add(e.getKey().startsWith("log/") ? ofLog(e.getKey(), e.getValue()) : ofGraph(e.getKey(), e.getValue()));
        }
        return out;
    }

    static Map<String, Object> ofGraph(String member, Path graph) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("member", member);
        m.put("kind", "graph");
        String fingerprint = null;
        try {
            String f = GraphMlParser.parse(graph).vocabulary().graphFacts().get("fluxtion.sourceFingerprint");
            fingerprint = f == null || f.isBlank() ? null : f;
        } catch (RuntimeException e) {
            m.put("note", "the graph could not be read for its fingerprint");
        }
        m.put("graphKey", fingerprint);
        return m;
    }

    static Map<String, Object> ofLog(String member, Path log) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("member", member);
        m.put("kind", "log");
        Headers h;
        try {
            h = isBinary(log) ? binary(log) : text(log);
        } catch (IOException | RuntimeException e) {
            m.put("note", "the log could not be read for its stream headers");
            return m;
        }
        m.put("streamHeaders", h.headers.size());
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        boolean unstated = false;
        for (StreamHeader s : h.headers) {
            if (s.valid() && s.sourceFingerprint() != null) keys.add(s.sourceFingerprint());
            else unstated = true;
        }
        List<String> graphKeys = new ArrayList<>(keys);
        if (unstated) graphKeys.add(null);
        m.put("graphKeys", graphKeys);
        List<Map<String, Object>> runs = new ArrayList<>();
        for (RunSegments.Segment s : RunSegments.of(h.headers, h.records)) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("label", s.label());
            r.put("runKey", s.headed() ? s.runKey().toMap() : null);
            r.put("records", s.records());
            runs.add(r);
        }
        m.put("runKeys", runs);
        return m;
    }

    private record Headers(List<StreamHeader> headers, int records) { }

    private static boolean isBinary(Path p) throws IOException {
        byte[] head = new byte[BinaryLogFile.MAGIC.length];
        try (InputStream in = Files.newInputStream(p)) {
            if (in.readNBytes(head, 0, head.length) < head.length) return false;
        }
        return java.util.Arrays.equals(head, BinaryLogFile.MAGIC);
    }

    /** A text log, framed as the stores frame it: headers out, markers out, every other document a record. */
    private static Headers text(Path log) throws IOException {
        List<StreamHeader> headers = new ArrayList<>();
        int[] records = {0};
        telamin.fluxtion.audit.analyser.analyser.parse.ByteRecordFramer.frame(log, (offset, length, text) -> {
            if (StreamHeader.isHeader(text)) headers.add(StreamHeader.parse(text, records[0]));
            else if (StreamEndMarker.of(text).isEmpty()) records[0]++;
        });
        return new Headers(headers, records[0]);
    }

    /** A binary log: the runtime's reader, records counted and never decoded. */
    private static Headers binary(Path log) throws IOException {
        List<StreamHeader> headers = new ArrayList<>();
        int[] records = {0};
        BinaryLogReader.read(log, new BinaryLogReader.Visitor() {
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
        return new Headers(headers, records[0]);
    }
}
