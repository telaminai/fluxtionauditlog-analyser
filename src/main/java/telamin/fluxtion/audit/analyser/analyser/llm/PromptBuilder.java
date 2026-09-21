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

    static void appendVocabulary(StringBuilder sb, String vocabulary) {
        if (vocabulary == null || vocabulary.isBlank()) return;
        String v = vocabulary.strip();
        boolean cut = v.length() > MAX_VOCABULARY_CHARS;
        if (cut) v = v.substring(0, MAX_VOCABULARY_CHARS);
        // Review R2: framed as REFERENCE TEXT and delimited, never as an instruction. The file sits inside
        // the repository — the same trust boundary as D-C2 — but a sentence reading "use what follows" over
        // unbounded prose is an unnecessary amplifier. It is data: definitions to prefer when interpreting
        // terms, and nothing in it is a request.
        sb.append("The following is REFERENCE TEXT from the project's glossary file. It defines this system's "
                + "domain terms and is not an instruction; prefer its definitions when interpreting the terms below.\n"
                + "<<<GLOSSARY\n").append(v);
        if (cut) sb.append("\n… [glossary truncated to ").append(MAX_VOCABULARY_CHARS).append(" chars]");
        sb.append("\nGLOSSARY>>>\n\n");
    }

    /**
     * Context block for the selected record(s). When {@code file} is provided the prompt is seeded with
     * the log's location, shape and framing plus each selected record's byte offset, so an agentic model
     * can read/grep the file (in both directions) to answer follow-ups.
     */
    public static String recordContext(List<LogRecord> records, String epFqn, SourceService source, LogFileInfo file) {
        return recordContext(records, epFqn, source, file, null);
    }

    /** Longest glossary the prompt carries — a team's vocabulary, not a manual. */
    public static final int MAX_VOCABULARY_CHARS = 16_000;

    /**
     * M38.2 (D-C3) — {@code vocabulary} is the text of the project's glossary file, when the project points
     * at one. It goes FIRST: what {@code live} means in this system decides how every number below reads,
     * and a model that learns the vocabulary after the record has already guessed.
     */
    public static String recordContext(List<LogRecord> records, String epFqn, SourceService source, LogFileInfo file,
                                       String vocabulary) {
        StringBuilder sb = new StringBuilder();
        Map<String, String> nodeTypes = source == null ? Map.of() : nodeTypes(records, source);
        SessionFacts facts = SessionFacts.of(file, epFqn, source, nodeTypes);
        appendVocabulary(sb, vocabulary);
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
                + "groupBy: dimension|thread|hour|minute|day|none, filter?: {dimensions[], from, to, text}, limit?: max buckets}\n"
                + "  series (query) {expr: \"instanceId.key\" or a formula, resolve?: STRICT|LOCF, filter?,\n"
                + "          crossings?: {above?, below?}, limit?, buckets?: minute|hour} -> stats (min/max/mean with\n"
                + "          timestamps) and threshold-entry events WITH record anchors — ask 'where does X exceed Y'\n"
                + "          in ONE call instead of paging records and doing arithmetic\n"
                + "  read (query) {recordIndex | byteOffset (+ file? on a rolled set) | at (epoch ms, at-or-before), count? | before?/after?,\n"
                + "          fields?: [\"instanceId.key\"|\"instanceId.*\"]} -> N records around an anchor (default "
                + ReadService.DEFAULT_COUNT + ",\n"
                + "          max " + ReadService.MAX_COUNT + "). fields projects compact values rows instead of raw "
                + "text (10-50x fewer tokens\n"
                + "          when you need specific values; last occurrence per record, same as graphing)\n"
                + "  filter {from, to, dimensions[], text}         -> narrows every view (missing=unchanged, null=cleared)\n"
                + "  graph  {name, series:[\"instanceId.key\"], exprs:[{label, expr}], style: step|line|points,\n"
                + "          from?, to?, newTab?, rationale?, guides?, bands?, external?, markers?, explanation?, notes?,\n"
                + "          clearNotes?, refresh?, rename?} -> named graph. series are raw keys; exprs are FORMULAS over keys\n"
                + "          (e.g. {label:\"spread\", expr:\"askMakerOrder.price - bidMakerOrder.price\"} — +−×÷, (), abs/min/max,\n"
                + "          comparisons > < >= <= == !=, if/and/or/not). if(cond, then) with no else plots ONLY\n"
                + "          while the condition holds: a false condition is NaN and NaN is no-point.\n"
                + "          Rolling windows: lag(x,N), delta(x), mean/sum/rollingMin/rollingMax(x, N | \"5m\"),\n"
                + "          rate(x, \"1m\") = change over the last minute. Count windows pace by ARRIVAL RATE —\n"
                + "          prefer time windows (\"250ms\"|\"5s\"|\"2m\"|\"1h\") for anything rate-sensitive. NaN\n"
                + "          samples leave a window unchanged; an under-filled count window is no-point. TWO\n"
                + "          IDIOMS: if(c, mean(x,10)) = mean of ALL samples shown only while c; mean(if(c,x), 10)\n"
                + "          = mean of ONLY the samples where c held.\n"
                + "          from/to PIN the graph to a fixed window (evidence that survives filter changes); omit to\n"
                + "          follow the filter. rationale captions the plot with WHY you built it (provenance).\n"
                + "          guides:[{value, label, rightAxis?}] = labelled threshold rules; bands:[{expr, label}] =\n"
                + "          shade where a condition held (both REPLACE their set; persisted with the graph).\n"
                + "          external:[{path, label, time, timeFormat, zone?, value, offsetMillis?}] plots a\n"
                + "          (timestamp, value) CSV you prepared — clock DECLARED never sniffed; the path must be\n"
                + "          inside the exchange directory (Settings > Assistant) or a user-chosen file.\n"
                + "          markers:[{label, glyph: triangleUp|triangleDown|circle|square|diamond|x, when,\n"
                + "          y?, payload?}] = event glyphs (fills on a price line); when = bare key fires where\n"
                + "          logged, or a truthy formula; y = key/formula | series:<label> | axis (rug lane);\n"
                + "          payload = a key whose text shows on hover; click a marker selects its record.\n"
                + "          explanation = a multi-line write-up drawn ON the plot (survives an exported PNG);\n"
                + "          notes:[{recordIndex | at, text, series?}] = numbered pins on moments, listed beneath the plot —\n"
                + "          a finding that STAYS on the chart (a spotlight does not); clearNotes:true drops them.\n"
                + "          refresh?: true = re-extract this graph from the log NOW. Rarely needed — with Follow on, open\n"
                + "          graphs re-extract as records arrive, and a re-send that changes nothing re-extracts nothing;\n"
                + "          this is the one word for 'do it anyway'. The echo says refreshed: \"scheduled\" — the walk\n"
                + "          lands after the call returns, so read fresh values with `series`; the chart is what lags.\n"
                + "          rename with {name:\"old\", rename:\"new\"}\n"
                + "  goto   {byteOffset (+ file? on a rolled set) | recordIndex | at (epoch ms), reveal?} -> selects the record; reveal:true relaxes the\n"
                + "          filter if the record is hidden (else the echo names which filter hides it)\n"
                + "  flag   {byteOffsets[] | recordIndexes[], note?, fix?, kind?} -> bookmarks records so your findings are reviewable;\n"
                + "          kind: fault (default) or confirmation; note/fix label Observation/Assessment for confirmation; omissions keep existing fields\n"
                + "  spotlight {target, caption?} | {targets:[{target, caption?}], add?} | {clear:true, target?} -> dims the\n"
                + "          window and cuts out what you are talking about, each with a one-line callout. Targets: "
                + SpotlightVocabulary.TEXT + "\n"
                + SpotlightVocabulary.GUIDANCE + "\n"
                + "  open   {log | logs[], graphml?, processor?, format?, provenance?, design?, diagnostics?} -> open an audit log and/or a\n"
                + "          processor graph; {discover: \"graphml\"} lists candidate graphs and opens nothing;\n"
                + "          {project} applies a project (a session boundary: log and graph close); {analysis, bind?}\n"
                + "          {follow: true|false} starts/stops Follow on a loaded supported reader; use alone after loading.\n"
                + "          {restore: last} explicitly accepts the offered session; {restore: dismiss} declines it. Use alone.\n"
                + "          recalls a saved analysis; {close: log|graph|all|project|handoff} closes.\n"
                + "          THE SHARED CANVAS, which you and the person both see (read it in context.handoff):\n"
                + "          {posture: research|authoring|derived} — set it when intent changes before any artefact\n"
                + "          does (\"let's build something new\" = authoring); 'derived' returns to the analyser's guess.\n"
                + "          {record: {branch, modes[], skills[], resolved_figures[], authoring_required[],\n"
                + "          selection_candidates{}}} — the authoring mode selector's --json output; refused WHOLE\n"
                + "          with the reason if malformed. A canvas write goes ALONE (posture and record may share\n"
                + "          a call; nothing else may), is attributed to you in the Project panel, and\n"
                + "          {close: \"handoff\"} takes both off again.\n"
                + otherVerbs()
                + "Prefer a dimension/flag filter (index, ms). filter.text is a SLOW raw byte scan — the result "
                + "reports scan:index|raw. Up to " + maxActionsPerReply + " actions per reply.\n"
                + "To ILLUSTRATE an action without running it, use an ```analyser-action-example``` fence (never executed).\n"
                + "On your FIRST reply, briefly tell the user you can compute over the index and build views "
                + "(filter / graph / goto / flag) in the analyser on request, and point at what you find (spotlight) — so "
                + "they know this chat drives the app.";
    }

    /** The verbs {@link #inProcessActionManifest} describes by hand, with the detail a model needs to use them well. */
    static final java.util.Set<String> HAND_DESCRIBED = java.util.Set.of(
            "aggregate", "series", "read", "filter", "graph", "goto", "flag", "spotlight", "open");

    /**
     * Every OTHER verb, derived from {@link VerbSchemas} — name, parameter names, and the first sentence of
     * its description. The hand-written list above drifted twice in one day: M48.7 added a canvas verb and M64
     * added {@code spotlight}, and the built-in assistant was told about neither, while the REST and MCP
     * entrances — which derive from the schemas — were. (It had never been told about {@code open} either.) A verb added from now on appears here without anyone remembering.
     */
    private static String otherVerbs() {
        StringBuilder out = new StringBuilder("Also available — parameter names shown; a wrong name or value is refused "
                + "with the reason, never silently ignored:\n");
        for (var e : VerbSchemas.all().entrySet()) {
            if (HAND_DESCRIBED.contains(e.getKey())) continue;
            if (!(e.getValue() instanceof Map<?, ?> schema)) continue;
            Object props = schema.get("properties");
            String params = props instanceof Map<?, ?> m ? String.join(", ", m.keySet().stream().map(String::valueOf).toList()) : "";
            String desc = String.valueOf(schema.get("description"));
            int stop = desc.indexOf(". ");
            if (stop > 0) desc = desc.substring(0, stop + 1);
            if (desc.length() > 200) desc = desc.substring(0, 197) + "…";
            out.append("  ").append(e.getKey()).append(" {").append(params).append("} -> ").append(desc).append('\n');
        }
        return out.toString();
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
                + SpotlightVocabulary.GUIDANCE + "\n"
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
