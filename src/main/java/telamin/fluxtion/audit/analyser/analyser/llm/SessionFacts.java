package telamin.fluxtion.audit.analyser.analyser.llm;

import telamin.fluxtion.audit.analyser.analyser.source.SourceService;

import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What is loaded and where the code is — assembled once, rendered two ways.
 *
 * <p>Two callers need the same facts in different forms. {@link PromptBuilder} renders them as prose for
 * a model that receives one block of text and cannot call back. The {@code context} verb serialises them
 * as JSON for an agent that can. Assembling them twice is how the two drift: a field added for the prompt
 * silently never reaches the verb, and the agent and the pasted prompt start describing different
 * sessions. This class exists so there is one assembly and two renderings, not two assemblies.
 *
 * <p>It holds <b>facts about the session</b>, not about a selection — the log, the processor, the source
 * roots, and the node-type map. Anything per-record (which records are selected, their byte anchors) stays
 * with the caller that knows what a selection means to it.
 */
public record SessionFacts(LogFileInfo file, String eventProcessorFqn, List<Path> sourceRoots,
                           Map<String, String> nodeTypes, Map<String, Path> nodeTypeFiles) {

    private static final DateTimeFormatter UTC_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS").withZone(ZoneOffset.UTC);

    /**
     * Gather what is known. {@code nodeTypes} is supplied rather than derived, because working it out
     * needs records and the two callers scope that differently.
     */
    public static SessionFacts of(LogFileInfo file, String eventProcessorFqn, SourceService source,
                                  Map<String, String> nodeTypes) {
        List<Path> roots = source == null ? List.of() : source.resolver().roots();
        Map<String, String> types = nodeTypes == null ? Map.of() : nodeTypes;
        Map<String, Path> files = new LinkedHashMap<>();
        if (source != null) {
            types.forEach((id, fqn) -> source.resolver().find(fqn).ifPresent(p -> files.put(id, p)));
        }
        return new SessionFacts(file, eventProcessorFqn, List.copyOf(roots), types, files);
    }

    // ---- JSON rendering (the `context` verb) -------------------------------------------------------

    /** The log's identity and shape, or an empty map when nothing is open. */
    public Map<String, Object> logAsMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        if (file == null) return out;
        // absolute: the caller is very likely another process with a different working directory, and a
        // relative path it cannot resolve is worse than no path at all
        if (file.localPath() != null) {
            out.put("path", Path.of(file.localPath()).toAbsolutePath().normalize().toString());
        }
        out.put("openedFrom", file.displayLocation());
        out.put("records", file.recordCount());
        out.put("sizeBytes", file.sizeBytes());
        if (file.minTime() != null) out.put("from", file.minTime());
        if (file.maxTime() != null) out.put("to", file.maxTime());
        return out;
    }

    /** Where the code is: roots, the processor, and the node-type map with resolved files. */
    public Map<String, Object> sourceAsMap() {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("roots", sourceRoots.stream().map(Path::toString).toList());
        if (eventProcessorFqn != null) out.put("eventProcessor", eventProcessorFqn);
        if (!nodeTypes.isEmpty()) {
            Map<String, Object> types = new LinkedHashMap<>();
            nodeTypes.forEach((id, fqn) -> {
                Map<String, Object> one = new LinkedHashMap<>();
                one.put("class", fqn);
                Path at = nodeTypeFiles.get(id);
                if (at != null) one.put("file", at.toAbsolutePath().normalize().toString());
                types.put(id, one);
            });
            out.put("nodeTypes", types);
        }
        return out;
    }

    // ---- prose rendering (the pasted prompt) -------------------------------------------------------

    /**
     * The log's location, shape and framing — everything an agentic reader needs to grep the file itself.
     * Inert text for a non-agentic target. Appends nothing when there is no readable local file.
     */
    public void appendLogFraming(StringBuilder sb) {
        if (file == null || !file.hasLocalFile()) return;
        sb.append("Full audit log: ").append(file.localPath());
        sb.append(" (").append(humanSize(file.sizeBytes()))
                .append(", ").append(String.format("%,d", file.recordCount())).append(" records");
        if (file.minTime() != null && file.maxTime() != null) {
            sb.append(", ").append(utc(file.minTime())).append(" → ").append(utc(file.maxTime())).append(" UTC");
        }
        sb.append(").\n");
        if (file.isRemote()) {
            sb.append("Opened from: ").append(file.displayLocation())
                    .append(" (fetched to the local path above).\n");
        }
        sb.append("Framing: records are separated by lines of exactly `---`; each record starts with a "
                + "`#HH:MM:SS.mmm [thread] LEVEL logger` header, then an `eventLogRecord:` block whose "
                + "`nodeLogs:` list holds `- instanceId: {key: value, …}` entries. All times are UTC.\n");
        sb.append("You may read or grep this file to answer follow-up questions — navigate in BOTH "
                + "directions from the byte offsets below (read-behind for what led up to a record, "
                + "read-ahead for what followed). There are many market-data records between events, so "
                + "prefer targeted grep / sed around an offset over sequential scans of the whole file.\n");
    }

    /** Source roots and the instanceId → declared type map, as prose. */
    public void appendSourceFacts(StringBuilder sb) {
        if (!sourceRoots.isEmpty()) {
            sb.append("\n\nSource roots (open files below these to explore related classes / object hierarchy):\n");
            for (Path root : sourceRoots) sb.append("  ").append(root).append('\n');
        }
        if (!nodeTypes.isEmpty()) {
            sb.append("\nNode types (nodeLogs instanceId → declared field type @ source file):\n");
            nodeTypes.forEach((id, fqn) -> {
                sb.append("  ").append(id).append(" -> ").append(fqn);
                Path at = nodeTypeFiles.get(id);
                if (at != null) sb.append(" @ ").append(at);
                sb.append('\n');
            });
        }
    }

    public static String utc(long epochMillis) {
        return UTC_FMT.format(Instant.ofEpochMilli(epochMillis));
    }

    public static String humanSize(long bytes) {
        if (bytes < 0) return "size unknown";
        if (bytes < 1024) return bytes + " B";
        String[] units = {"KB", "MB", "GB", "TB"};
        double v = bytes / 1024.0;
        int u = 0;
        while (v >= 1024 && u < units.length - 1) {
            v /= 1024;
            u++;
        }
        return String.format("%.1f %s", v, units[u]);
    }
}
