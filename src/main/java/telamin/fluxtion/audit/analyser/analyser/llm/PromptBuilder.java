package telamin.fluxtion.audit.analyser.analyser.llm;

import telamin.fluxtion.audit.analyser.analyser.model.LogRecord;
import telamin.fluxtion.audit.analyser.analyser.model.NodeLog;
import telamin.fluxtion.audit.analyser.analyser.source.SourceService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Assembles the LLM prompt (spec §10): a bundled system explainer + a context block of the selected
 * record(s), the EventProcessor FQN, and source snippets for the classes the record references
 * (node types + the callback's declaring type). Everything is budgeted so the context stays bounded.
 * The same builder feeds both the API path and the no-key "copy prompt" path (parity).
 */
public final class PromptBuilder {

    private static final int MAX_CONTEXT_CHARS = 60_000;
    private static final int MAX_SNIPPET_CHARS = 4_000;
    private static final int MAX_SNIPPETS = 6;

    private PromptBuilder() {
    }

    public static String systemPrompt() {
        try (InputStream in = PromptBuilder.class.getResourceAsStream("/llm/system-prompt.md")) {
            if (in == null) return "You interpret Fluxtion event-audit logs.";
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return "You interpret Fluxtion event-audit logs.";
        }
    }

    /** Context block for the selected record(s): the records, the EP, and relevant source snippets. */
    public static String recordContext(List<LogRecord> records, String epFqn, SourceService source) {
        return recordContext(records, epFqn, source, null);
    }

    /**
     * Context block for the selected record(s). When {@code file} is provided the prompt is seeded with
     * the log's location, shape and framing plus each selected record's byte offset, so an agentic model
     * can read/grep the file (in both directions) to answer follow-ups.
     */
    public static String recordContext(List<LogRecord> records, String epFqn, SourceService source, LogFileInfo file) {
        StringBuilder sb = new StringBuilder();
        Map<String, String> nodeTypes = source == null ? Map.of() : nodeTypes(records, source);
        SessionFacts facts = SessionFacts.of(file, epFqn, source, nodeTypes);
        facts.appendLogFraming(sb);
        appendRecordAnchors(sb, file, records);
        if (epFqn != null) sb.append("EventProcessor: ").append(epFqn).append("\n\n");

        // The last record is the one to explain; earlier selected records are prior context.
        LogRecord primary = records.get(records.size() - 1);
        if (records.size() == 1) {
            sb.append("Record to explain:\n").append(primary.rawText());
        } else {
            sb.append("Record to explain (the final/resulting cycle):\n").append(primary.rawText());
            sb.append("\n\nPreceding selected records for context (oldest first):");
            for (int i = 0; i < records.size() - 1; i++) {
                sb.append("\n---\n").append(records.get(i).rawText());
            }
        }

        if (source != null) {
            facts.appendSourceFacts(sb);
            appendSnippets(sb, epFqn, records, source, nodeTypes);
        }

        if (sb.length() > MAX_CONTEXT_CHARS) {
            sb.setLength(MAX_CONTEXT_CHARS);
            sb.append("\n… [context truncated to ").append(MAX_CONTEXT_CHARS).append(" chars]");
        }
        return sb.toString();
    }

    /**
     * A byte anchor per selected record, so an agentic reader can seek to one and read outward. Stays
     * here rather than in {@link SessionFacts}: it is a fact about <em>this selection</em>, not about the
     * session, and the {@code context} verb reports the same thing as structured offsets instead.
     */
    private static void appendRecordAnchors(StringBuilder sb, LogFileInfo file, List<LogRecord> records) {
        if (file == null || !file.hasLocalFile()) return;
        sb.append("Selected record anchors (byte offset into the file above):\n");
        for (LogRecord r : records) {
            sb.append("  - byte ").append(String.format("%,d", r.fileOffset()))
                    .append(" (length ").append(r.byteLength()).append(')');
            if (r.logTime() != null) sb.append(", logTime ").append(utc(r.logTime())).append(" UTC");
            sb.append('\n');
        }
        sb.append('\n');
    }

    private static String utc(long epochMillis) {
        return SessionFacts.utc(epochMillis);
    }


    /** Ordered instanceId → declared-type FQN for the nodes in the records (resolved via the EP). */
    /**
     * {@code instanceId → declared field type} for the nodes these records mention. Public because the
     * {@code context} verb needs the same map: it is the one piece of the shared assembly that depends on
     * records, so it is computed by the caller and handed to {@link SessionFacts}.
     */
    public static Map<String, String> nodeTypes(List<LogRecord> records, SourceService source) {
        Map<String, String> types = new LinkedHashMap<>();
        for (LogRecord r : records) {
            for (NodeLog nl : r.nodeLogs()) {
                if (types.containsKey(nl.instanceId())) continue;
                String fqn = source.fqnForInstance(nl.instanceId());
                if (fqn != null) types.put(nl.instanceId(), fqn);
            }
        }
        return types;
    }

    private static void appendSnippets(StringBuilder sb, String epFqn, List<LogRecord> records,
                                       SourceService source, Map<String, String> nodeTypes) {
        Set<String> fqns = new LinkedHashSet<>();
        if (epFqn != null) fqns.add(epFqn);                 // the EventProcessor itself (declares the fields)
        for (LogRecord r : records) {
            if (r.declaringType() != null) fqns.add(r.declaringType());
        }
        fqns.addAll(nodeTypes.values());                     // the node classes
        int shown = 0;
        for (String fqn : fqns) {
            if (shown >= MAX_SNIPPETS) break;
            var src = source.sourceForFqn(fqn);
            if (src.isEmpty()) continue;
            String code = src.get();
            if (code.length() > MAX_SNIPPET_CHARS) {
                code = code.substring(0, MAX_SNIPPET_CHARS) + "\n… [truncated — open the file above for the rest]";
            }
            Optional<Path> path = source.resolver().find(fqn);
            sb.append("\n\n--- source: ").append(fqn);
            path.ifPresent(p -> sb.append(" @ ").append(p));
            sb.append(" ---\n").append(code);
            shown++;
        }
    }

    /**
     * The in-process action manifest (spec §7): tells the model it may emit {@code analyser-action} fenced
     * blocks that the app runs, feeding results back so it can reason on real numbers. No URL/token (this
     * is the in-process path). Seeded only when the assistant-actions feature is enabled.
     */
    public static String inProcessActionManifest(int maxActionsPerReply) {
        return "You can run ANALYSER ACTIONS while you answer — to compute over the index and to build "
                + "curation in the UI. Emit a fenced block:\n"
                + "```analyser-action\n"
                + "{ \"action\": \"aggregate\", \"params\": { \"metric\": \"count\", \"groupBy\": \"hour\","
                + " \"filter\": { \"dimensions\": [\"onMultilevelMarketData\"] } } }\n"
                + "```\n"
                + "The app runs it; QUERY results are fed back so you can reason on real numbers, then you answer.\n"
                + "Verbs:\n"
                + "  aggregate (query) {metric: count|rate_per_min|nan_count|breach_count, "
                + "groupBy: dimension|thread|hour|minute|day|none, filter?: {dimensions[], from, to, text}}\n"
                + "  read (query) {recordIndex | byteOffset, count? | before?/after?} -> the raw text of N records\n"
                + "          around an anchor (default " + ReadService.DEFAULT_COUNT + ", max " + ReadService.MAX_COUNT
                + "), so you can seek the log via this socket without file access\n"
                + "  filter {from, to, dimensions[], text}         -> narrows every view (missing=unchanged, null=cleared)\n"
                + "  graph  {name, series:[\"instanceId.key\"], exprs:[{label, expr}], style: step|line|points,\n"
                + "          from?, to?, newTab?, rationale?} -> named graph. series are raw keys; exprs are FORMULAS over keys\n"
                + "          (e.g. {label:\"spread\", expr:\"askMakerOrder.price - bidMakerOrder.price\"} — +−×÷, (), abs/min/max).\n"
                + "          from/to PIN the graph to a fixed window (evidence that survives filter changes); omit to\n"
                + "          follow the filter. rationale captions the plot with WHY you built it (provenance).\n"
                + "          rename with {name:\"old\", rename:\"new\"}\n"
                + "  goto   {byteOffset | recordIndex, reveal?}    -> selects the record; reveal:true relaxes the\n"
                + "          filter if the record is hidden (else the echo names which filter hides it)\n"
                + "  flag   {byteOffsets[] | recordIndexes[], note?} -> bookmarks records so your findings are reviewable\n"
                + "Prefer a dimension/flag filter (index, ms). filter.text is a SLOW raw byte scan — the result "
                + "reports scan:index|raw. Up to " + maxActionsPerReply + " actions per reply.\n"
                + "To ILLUSTRATE an action without running it, use an ```analyser-action-example``` fence (never executed).\n"
                + "On your FIRST reply, briefly tell the user you can compute over the index and build views "
                + "(filter / graph / goto / flag) in the analyser on request — so they know this chat drives the app.";
    }

    /**
     * The REST action manifest (spec §5.2): the loopback endpoint + token an external agentic client can
     * POST to. Seeded into the copy-prompt path when the REST transport is enabled, so a session that can
     * make HTTP calls can drive the app directly.
     */
    public static String restActionManifest(String url, String token, int maxActionsPerReply) {
        return "ANALYSER ACTIONS over REST (this session can drive the app): POST JSON to " + url + "/action\n"
                + "with header  X-Analyser-Token: " + token + "\n"
                // derived, not listed: this sentence named five verbs while thirteen shipped, and it is
                // the only verb list a copy-prompt session ever sees
                + "GET " + url + "/manifest for the verbs + caps. Verbs: "
                + String.join(", ", VerbSchemas.all().keySet()) + ".\n"
                + "aggregate {metric, groupBy, filter?} returns typed counts/rates over the index (scan:index|raw); "
                + "prefer a dimension/flag filter. Up to " + maxActionsPerReply + " actions per reply. Loopback only; "
                + "do NOT send an Origin header.\n"
                + "Tell the user up front that you can drive this analyser (compute + build filter/graph/goto/flag views), "
                + "so it's clear this session is interactive with the app.";
    }

    /** The full single-shot prompt for the no-key copy path (system + context + question). */
    public static String fullPrompt(String context, String question) {
        return systemPrompt()
                + "\n\n===== CONTEXT =====\n" + context
                + "\n\n===== QUESTION =====\n" + question;
    }
}
