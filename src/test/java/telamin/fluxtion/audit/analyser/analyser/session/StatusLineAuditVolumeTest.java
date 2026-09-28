package telamin.fluxtion.audit.analyser.analyser.session;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.parse.ProducerDiagnostics;
import telamin.fluxtion.audit.analyser.analyser.parse.TimeOrderReport;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * View-model spike (2026-09-27), prediction 5: what drawing the status line through the session costs the audit.
 * 1,000 Follow polls — 600 that append one record, 400 idle — on the real processor, into the real sink with a capacity
 * large enough to keep everything, so the count is of what was WRITTEN, not what survived retention.
 */
class StatusLineAuditVolumeTest {

    private static int bytes(List<String> records) {
        return records.stream().mapToInt(r -> r.getBytes(StandardCharsets.UTF_8).length).sum();
    }

    @Test
    @DisplayName("1,000 polls: one render per appending poll, none per idle one, and what they cost")
    void measure() {
        FakeSessionAdapter a = new FakeSessionAdapter();
        SessionAuditSink sink = new SessionAuditSink(1_000_000);
        SessionDriver d = new SessionDriver(a, sink);
        SessionFixtures.openLog(d, a, "/logs/f.yaml", "DEMO", Set.of("a"), 1, 25, "TRACE");
        long g = d.snapshot().logGeneration();
        int total = 25;
        long last = 5_000;
        d.post(new SessionEvents.LogShapeObserved(g, total, 1_000L, last, false, 0, 0));
        d.post(new SessionEvents.ProducerFindingsObserved(g, ProducerDiagnostics.clean()));
        d.post(new SessionEvents.TimeOrderObserved(g, TimeOrderReport.clean()));
        d.post(new SessionEvents.FollowToggled(g, true));
        int rendersBefore = a.statusLines.size();
        long recordsBefore = sink.total();

        int appending = 0;
        java.util.Map<String, int[]> byKind = new java.util.TreeMap<>();   // event -> {records, bytes}
        java.util.List<String> entries = new java.util.ArrayList<>();
        java.util.Map<String, Integer> appendedLines = new java.util.TreeMap<>();
        int[] appendedRecords = {0};
        int[] idleLines = {0};
        for (int poll = 0; poll < 1_000; poll++) {
            long before = sink.total();
            boolean appends = poll % 5 < 3;
            if (appends) {
                appending++;
                total++;
                last += 100;
                // the frame's order on an appending poll: the append, the content signature, then the scan it asked for
                d.post(new SessionEvents.LogAppended(g, Set.of("a"), 1, total, "TRACE"));
                d.post(new SessionEvents.LogContentObserved(g, total, 0, "UNKNOWN", 0, null));
                d.post(new SessionEvents.LogShapeObserved(g, total, 1_000L, last, false, 0, 0));
                d.post(new SessionEvents.ProducerFindingsObserved(g, ProducerDiagnostics.clean()));
                d.post(new SessionEvents.TimeOrderObserved(g, TimeOrderReport.clean()));
            } else {
                d.post(new SessionEvents.LogContentObserved(g, total, 0, "UNKNOWN", 0, null));
            }
            // this poll's records are the newest (sink.total() - before) — fewer than any ring holds, so none evicted
            List<String> held = sink.records();
            for (String r : held.subList(held.size() - (int) (sink.total() - before), held.size())) {
                String kind = r.lines().filter(l -> l.strip().startsWith("event: ")).findFirst().orElse("?").strip()
                        .substring("event: ".length());
                int[] c = byKind.computeIfAbsent(kind, k -> new int[2]);
                c[0]++;
                c[1] += r.getBytes(StandardCharsets.UTF_8).length;
                r.lines().filter(l -> l.contains("- statusLineView:") && l.contains("render: statusLine"))
                        .forEach(entries::add);
                // spike round 3: which nodes write a line in an appending poll's LogAppended record, and how many say
                // nothing but that they ran (only thread and method)
                if ("LogAppended".equals(kind)) {
                    appendedRecords[0]++;
                    r.lines().map(String::strip).filter(l -> l.startsWith("- ") && l.contains(": {")).forEach(l -> {
                        String node = l.substring(2, l.indexOf(':'));
                        appendedLines.merge(node, 1, Integer::sum);
                        String body = l.substring(l.indexOf('{') + 1, l.lastIndexOf('}')).strip();
                        if (body.split(", ").length <= 2 && body.startsWith("thread:")) idleLines[0]++;
                    });
                }
            }
        }

        int renders = a.statusLines.size() - rendersBefore;
        long written = sink.total() - recordsBefore;
        System.out.printf("VOLUME polls=1000 appending=%d renders=%d records_written=%d%n", appending, renders, written);
        byKind.forEach((k, c) -> System.out.printf("VOLUME kind=%s records=%d bytes=%d mean=%.0f%n", k, c[0], c[1],
                c[1] / (double) c[0]));
        int allBytes = byKind.values().stream().mapToInt(c -> c[1]).sum();
        int newBytes = java.util.stream.Stream.of("LogShapeObserved", "ViewRendered")
                .mapToInt(k -> byKind.getOrDefault(k, new int[2])[1]).sum();
        int newRecords = java.util.stream.Stream.of("LogShapeObserved", "ViewRendered")
                .mapToInt(k -> byKind.getOrDefault(k, new int[2])[0]).sum();
        System.out.printf("VOLUME render_entries=%d entry_bytes_mean=%.1f%n", entries.size(),
                bytes(entries) / (double) Math.max(1, entries.size()));
        System.out.printf("VOLUME new_kinds records=%d bytes=%d share_of_bytes=%.1f%% share_of_records=%.1f%%%n",
                newRecords, newBytes, 100.0 * newBytes / allBytes, 100.0 * newRecords / written);
        int lineTotal = appendedLines.values().stream().mapToInt(Integer::intValue).sum();
        System.out.printf("VOLUME appended_records=%d node_lines_per_record=%.2f idle_lines_per_record=%.2f%n",
                appendedRecords[0], lineTotal / (double) Math.max(1, appendedRecords[0]),
                idleLines[0] / (double) Math.max(1, appendedRecords[0]));
        appendedLines.forEach((n, c) -> System.out.printf("VOLUME appended_node=%s lines=%d%n", n, c));
        System.out.printf("VOLUME sample_entry=%s%n", entries.isEmpty() ? "" : entries.get(entries.size() - 1).strip());

        assertEquals(appending, renders, "one render per appending poll");
        assertEquals(appending, entries.size(), "and one audit entry each, riding in the settling cycle's record");
        assertEquals(appending, byKind.get("ViewRendered")[0], "each render answered");
        assertEquals(0, sink.droppedTransitions(), "the new kinds are re-scopes: they never evict a transition");
    }
}
