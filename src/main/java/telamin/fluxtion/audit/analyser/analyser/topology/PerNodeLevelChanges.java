package telamin.fluxtion.audit.analyser.analyser.topology;

import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * MA-8 — the audit levels a log states about itself, and which records each one governed.
 *
 * <p><b>The problem this exists for.</b> A node set to {@code WARN} still RUNS, but its {@code info}
 * lines are suppressed, so it carries no node entries and coverage lists it as uncovered with no
 * explanation. Measured: a file read {@code complete}, 6 of 6, no findings, with zero entries for a node
 * that ran three times — while the control record naming {@code sourceId=riskCheck, level=WARN} sat in
 * the log. That is "this node never ran" being wrong with the answer already in the file.
 *
 * <p><b>Annotate, never excuse (MA-8.2).</b> An annotated node stays in {@code uncovered} and in the
 * ratio. Excusing it would hide a node that genuinely never ran whenever the qualifying record is
 * wrong — and a control-LOOKING record can be content a payload produced.
 *
 * <p><b>Read regardless of the current filter (MA-8.3).</b> A level change is configuration state, not
 * an event you happen to be looking at, so every record is scanned for changes. The filter decides only
 * which records the annotation must be ABOUT.
 *
 * <h2>The runtime's rule, read rather than inferred</h2>
 *
 * <p>fluxtion-runtime 1.0.16 {@code EventLogManager.calculationLogConfig} does this with a level change:
 *
 * <pre>
 * if (level != null &amp;&amp; (logRecord.groupingId == null || logRecord.groupingId.equals(change.groupId))) {
 *     node2Logger.computeIfPresent(change.sourceId, setLevel);            // that node
 *     if (change.sourceId == null) every node's logger.setLevel(level);   // EVERY node
 * }
 * </pre>
 *
 * <p>So {@code groupId} is <b>not a group of nodes</b>. It gates the whole change against the PROCESSOR's
 * grouping, which every runtime record writes as its {@code groupingId:} field; and a change naming no
 * {@code sourceId} sets every node. The first version of this class read {@code groupId} as node
 * membership and annotated each node "this may or may not cover" — an inference that shipped, found while
 * answering the independent review's F4 by reading the runtime instead of this class's own comments.
 * Assumption, stated: a record that carries no {@code groupingId} field is read as ungrouped, which is the
 * runtime's default and the only reading available to a producer with no grouping concept.
 *
 * <h2>Order, not time</h2>
 *
 * <p>A change is dispatched through the same processor as every other event, so its RECORD's position is
 * where it took effect. Intervals are therefore {@code (change row, next applicable change row)} in record
 * order, and an annotation applies when a record in view lies inside one. The earlier version used
 * {@code logTime}, and the independent review found every consequence (F4, F5): a global restore never
 * closed a per-node interval; a record at exactly the restore's instant was still inside the WARN window;
 * an empty filter selection widened to all time and received every annotation; and an untimed control
 * after the records in view was assigned to the start of the log. None of those can happen by position.
 *
 * <h2>Within ONE processor's records — and what the log cannot establish</h2>
 *
 * <p>Record order is a clock only inside one processor's stream. The re-review (RR-3) showed a change from
 * a processor grouped {@code alpha} explaining a {@code beta} record, and a {@code beta} restore closing
 * {@code alpha}'s window. So every record is given the context its OWN {@code groupingId:} line declares,
 * and a change explains — and is closed by — only records of the same declared context. Two limits are
 * stated rather than hidden: records that SHARE a grouping (two ungrouped processors, say) are read as one
 * stream, which nothing in a record establishes; and a record with NO {@code groupingId:} line does not
 * declare "ungrouped", so whether a change applied there is qualified, not assumed.
 *
 * <p><b>What "in the records sharing its grouping" relies on</b> (owner decision, 2026-09-26, eighth re-review).
 * The owner states, as a fact about the producer, that it writes <b>one processor per grouping</b>. Every note's
 * scope phrase — "in the records sharing its grouping", "in the records that, like it, state no grouping" — reads
 * the records of one grouping as one processor's stream on that statement, not on anything a record shows. The
 * note itself does not repeat the limit; this Javadoc and the spec (MA-8) are where it is written down.
 *
 * <h2>How far a change reaches past a stream-end marker</h2>
 *
 * <p>A stream-end marker begins a later run, and the log does not say whether a level survived into it. A change's
 * annotation therefore covers at most ONE marker (owner decision, 2026-09-26, eighth re-review, option b): it stops
 * at the SECOND stream-end marker after the change. Records past that marker get no level annotation and stay
 * uncovered, so a note never has to carry survival of more than one marker.
 */
public final class PerNodeLevelChanges {

    /** The control event the runtime dispatches when an audit level is set. */
    static final String CONTROL_EVENT = "EventLogControlEvent";

    private final List<Change> changes;
    private final List<Unreadable> unreadable;
    private final Grouping[] rowContext;
    private final List<Integer> runBoundaries;

    private PerNodeLevelChanges(List<Change> changes, List<Unreadable> unreadable, Grouping[] rowContext,
                                List<Integer> runBoundaries) {
        this.changes = changes;
        this.unreadable = unreadable;
        this.rowContext = rowContext;
        this.runBoundaries = runBoundaries;
    }

    /**
     * A control record this reader could not read — no {@code eventToString}, or one that is not the pinned
     * rendering (sixth re-review R6-1). RR-2 skips such a record rather than guess what it set, which is right; but
     * the window logic was never told, so "Nothing later … changes it" ran on past a record that may have restored
     * INFO. It now closes the window, the conservative direction, and the sentence says why.
     */
    record Unreadable(int row, Long logTime, Grouping context, boolean nullRecord) {
        /**
         * Eighth re-review R8-4. A row whose {@code record()} is null was read only from its raw text: its time is
         * whatever that text's header says, or unknown — never "(untimed)", which is a claim about the record.
         */
        String at() {
            return "record " + (row + 1) + (logTime != null ? " (logTime " + logTime + ")"
                    : nullRecord ? " (its time was not read)" : " (untimed)");
        }
    }

    /**
     * The processor grouping a record DECLARES about itself.
     *
     * @param declared false when the record has no {@code groupingId:} line at all — which is not a
     *                 statement that it is ungrouped (re-review RR-3)
     * @param value    the grouping, or null when declared as {@code null}
     */
    public record Grouping(boolean declared, String value) {
        static final Grouping ABSENT = new Grouping(false, null);
    }

    /** Whether a change applied to the processor that logged it. */
    public enum Applies { YES, NO, NOT_ESTABLISHED }

    /**
     * One level change a log states about itself.
     *
     * @param sourceId the node it names — EXACTLY as rendered, commas, braces and spaces included — or null
     *                 when it names none and so sets every node
     * @param groupId  the processor grouping it was addressed to, exactly as rendered, or null
     * @param level    the level set
     * @param row      the control record's row
     * @param logTime  the control record's time, or null when untimed
     * @param context  the grouping the control record itself declares — its processor's
     */
    public record Change(String sourceId, String groupId, String level, int row, Long logTime, Grouping context) {
        /** The runtime's gate: an ungrouped processor takes every change, a grouped one only its own. */
        public Applies applies() {
            if (!context.declared()) return Applies.NOT_ESTABLISHED;
            if (context.value() == null) return Applies.YES;
            return context.value().equals(groupId) ? Applies.YES : Applies.NO;
        }

        boolean affects(String nodeId) {
            // Exact: the runtime looks a sourceId up in a map, so " riskMonitor " and "riskMonitor, DEMO"
            // address no node called riskMonitor, and "" addresses a node called "" (re-review RR-2).
            return applies() != Applies.NO && (sourceId == null || sourceId.equals(nodeId));
        }
    }

    public static PerNodeLevelChanges of(LogStore store) {
        return of(store, store == null ? 0 : store.size());
    }

    /**
     * The level changes in the first {@code bound} records — the rows a caller captured (integration with M44.4's R1:
     * coverage fixes its scan bound when it captures its inputs, so the annotations it attaches must read the same
     * rows, not records Follow appended since). Also fixes a latent overrun: the array was sized once while the loop
     * re-read {@code store.size()}, so an append during the scan could index past it.
     */
    public static PerNodeLevelChanges of(LogStore store, int bound) {
        List<Change> out = new ArrayList<>();
        List<Unreadable> unread = new ArrayList<>();
        if (store == null) return new PerNodeLevelChanges(out, unread, new Grouping[0], List.of());
        int rows = Math.max(0, Math.min(bound, store.size()));
        Grouping[] context = new Grouping[rows];
        for (int row = 0; row < rows; row++) {          // deliberately unfiltered — MA-8.3
            context[row] = groupingOf(store.rawText(row));
            var record = store.record(row);
            if (record == null) {
                // Seventh re-review O7-1: no store in this repository returns null, but a plugin's might. A row whose
                // raw text names the control event is then a control record this reader could not read, not nothing.
                String raw = store.rawText(row);
                if (isControlEvent(rawEvent(raw))) unread.add(new Unreadable(row, rawLogTime(raw), context[row], true));
                continue;
            }
            // Exact match, not contains: `FakeEventLogControlEventX` is not a control event. The
            // fully-qualified name is accepted by comparing the simple name after the last dot.
            if (!isControlEvent(record.event())) continue;
            String text = record.eventToString();
            Rendering r = text == null ? null : parse(text);
            if (r == null) {
                unread.add(new Unreadable(row, record.logTime(), context[row], false));
                continue;
            }
            out.add(new Change(r.sourceId(), r.groupId(), r.level(), row, record.logTime(), context[row]));
        }
        final int scope = rows;
        List<Integer> boundaries = store.runBoundaries().stream().filter(b -> b <= scope).toList();
        return new PerNodeLevelChanges(List.copyOf(out), List.copyOf(unread), context, boundaries);
    }

    /**
     * The {@code event:} header field's value, read from the raw text, or null (seventh re-review O7-1; eighth R8-4).
     *
     * <p>Only the header is read — {@code eventLogRecord}, {@code logTime}, {@code groupingId}, {@code event} — and the
     * scan stops at the first payload field, as {@link #groupingOf} stops at {@code event:}. A node value that reads
     * {@code event: EventLogControlEvent} is therefore never taken for the record's event.
     */
    static String rawEvent(String rawText) {
        String v = headerField(rawText, "event:");
        return v == null || v.isEmpty() ? null : v;
    }

    /** The {@code logTime:} header field, or null when the header has none or it is not a number (R8-4). */
    static Long rawLogTime(String rawText) {
        String v = headerField(rawText, "logTime:");
        if (v == null) return null;
        try {
            return Long.parseLong(v);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * The fields a payload can reach: {@code eventToString} is the event's own {@code toString()}, written unescaped,
     * and {@code nodeLogs} holds node values. Nothing at or after the first of them is read as the record's header.
     */
    static final java.util.Set<String> PAYLOAD_FIELDS = java.util.Set.of("eventToString", "nodeLogs");

    /**
     * One header field of the raw text (eighth re-review R8-4; ninth R9-2). The record's own fields are the lines at
     * the {@code eventLogRecord:} mapping's first indentation; nested lines are never read. Every such field the format
     * permits is accepted in any order — {@code eventTime}, {@code logTime}, {@code groupingId}, {@code event},
     * {@code thread}, {@code endTime}, and any other, which Format §2 says is ignored, never rejected — until the
     * first payload field, where the scan stops. R8-4's allow-list took {@code eventTime}, which the published
     * example writes before {@code logTime}, for payload, and so lost the record's event.
     */
    private static String headerField(String rawText, String name) {
        if (rawText == null) return null;
        int indent = -1;
        for (String line : rawText.split("\n", -1)) {
            String t = line.strip();
            if (t.isEmpty() || t.equals("---") || t.startsWith("#") || t.equals("eventLogRecord:")) continue;
            int at = line.length() - line.stripLeading().length();
            if (indent < 0) indent = at;
            if (at > indent) continue;                   // nested: part of a field's value, never a field
            if (at < indent) return null;                // outside the record mapping
            int colon = t.indexOf(':');
            if (colon <= 0) return null;                 // not a field line
            String key = t.substring(0, colon);
            if (PAYLOAD_FIELDS.contains(key)) return null;   // the first payload field: nothing after it is the header
            if (key.equals(name.substring(0, name.length() - 1))) return t.substring(colon + 1).strip();
        }
        return null;
    }

    /**
     * The grouping a record declares: its {@code groupingId:} line, read only BEFORE its {@code event:} line.
     *
     * <p>The runtime writes {@code groupingId} before {@code event} and everything a payload can reach —
     * {@code eventToString}, node values — comes after it, so a payload line cannot declare a grouping.
     * A producer that writes the fields in another order simply reads as undeclared, the safe direction.
     */
    static Grouping groupingOf(String rawText) {
        if (rawText == null) return Grouping.ABSENT;
        for (String line : rawText.split("\n", -1)) {
            String t = line.strip();
            if (t.startsWith("event:")) break;
            if (t.startsWith("groupingId:")) {
                String v = t.substring("groupingId:".length()).strip();
                return new Grouping(true, v.isEmpty() || v.equals("null") ? null : v);
            }
        }
        return Grouping.ABSENT;
    }

    /**
     * Is this the runtime's control event, by NAME rather than by resemblance?
     *
     * <p>{@code contains} accepted {@code FakeEventLogControlEventX}; an exact match alone missed a
     * fully-qualified name. So: the simple name, after the last dot, must equal it exactly.
     */
    static boolean isControlEvent(String event) {
        // One predicate, shared with ProducerDiagnostics.onlyControlEvents (phase 1 round 4, F4).
        return telamin.fluxtion.audit.analyser.analyser.parse.ProducerDiagnostics.isControlEvent(event);
    }

    /** The runtime's {@code EventLogConfig} rendering, field by field, values exactly as written. */
    record Rendering(String level, String logRecordProcessor, String sourceId, String groupId) {
    }

    private static final String OPEN = "EventLogConfig{level=";
    private static final String[] SEPARATORS = {", logRecordProcessor=", ", sourceId=", ", groupId="};
    private static final List<String> LEVELS = List.of("NONE", "ERROR", "WARN", "INFO", "DEBUG", "TRACE");

    /**
     * Parse the pinned rendering by its FIXED separators, or return null.
     *
     * <p>fluxtion-runtime 1.0.16 writes {@code EventLogConfig{level=L, logRecordProcessor=P, sourceId=S,
     * groupId=G}} and escapes nothing. So a value may itself contain a comma, a brace or spaces. The
     * previous parser stopped each value at the first comma or brace and trimmed it, which the re-review
     * measured (RR-2) against the real runtime: {@code riskMonitor, DEMO}, {@code riskMonitor}DEMO} and
     * {@code " riskMonitor "} left riskMonitor at INFO and were read as setting it to WARN; {@code
     * groupId="alpha, DEMO"} was ignored by the runtime and read as {@code alpha}; an EMPTY sourceId was
     * read as every node.
     *
     * <p>Now each separator must occur exactly once, in order, and every value is taken whole between
     * them: the text a value can hold is whatever does not complete a separator. A value that DOES contain
     * one — a listener whose {@code toString} writes {@code ", sourceId="} — makes the rendering ambiguous,
     * and it is refused. The level must be one of the runtime's six names.
     *
     * <p><b>The one thing no parser of this text can do:</b> the runtime renders a Java {@code null} and the
     * four-character string {@code "null"} identically. {@code null} is read as absent — the overwhelmingly
     * common meaning, and the only one the global case can have — and the sentence says so.
     */
    static Rendering parse(String text) {
        if (!text.startsWith(OPEN) || !text.endsWith("}")) return null;
        int[] at = new int[SEPARATORS.length];
        int from = OPEN.length();
        for (int i = 0; i < SEPARATORS.length; i++) {
            int first = text.indexOf(SEPARATORS[i]);
            if (first < 0 || text.indexOf(SEPARATORS[i], first + 1) >= 0 || first < from) return null;
            at[i] = first;
            from = first + SEPARATORS[i].length();
        }
        String level = text.substring(OPEN.length(), at[0]);
        if (!LEVELS.contains(level)) return null;
        String processor = text.substring(at[0] + SEPARATORS[0].length(), at[1]);
        String source = text.substring(at[1] + SEPARATORS[1].length(), at[2]);
        String group = text.substring(at[2] + SEPARATORS[2].length(), text.length() - 1);
        return new Rendering(level, nullLiteral(processor), nullLiteral(source), nullLiteral(group));
    }

    private static String nullLiteral(String v) {
        return v.equals("null") ? null : v;
    }

    /** Is this the pinned rendering? */
    static boolean recognised(String text) {
        return parse(text) != null;
    }

    /**
     * One field of the pinned rendering, exactly as written; null when it reads {@code null} or when the
     * text is not the pinned rendering at all. Kept for the tests that pin the format.
     */
    static String field(String text, String name) {
        Rendering r = parse(text);
        if (r == null) return null;
        return switch (name) {
            case "level" -> r.level();
            case "logRecordProcessor" -> r.logRecordProcessor();
            case "sourceId" -> r.sourceId();
            case "groupId" -> r.groupId();
            default -> null;
        };
    }

    /** True when the log states any level change that applied, or may have applied, to its processor. */
    public boolean any() {
        return changes.stream().anyMatch(c -> c.applies() != Applies.NO);
    }

    /**
     * The annotation for one uncovered node over the records in view, or {@code null} when no quiet level
     * the log states governed any of them.
     *
     * @param inView the rows in view, ascending. EMPTY means nothing is in view, and nothing is annotated:
     *               an empty selection is not an unbounded one (independent review, F5).
     */
    public String annotationFor(String nodeId, int[] inView) {
        if (inView == null || inView.length == 0) return null;
        for (Change c : changes) {
            if (!c.affects(nodeId) || !isQuiet(c.level())) continue;
            // The next change to this node IN THE SAME PROCESSOR CONTEXT ends the window (RR-3): another
            // processor's restore does not touch this processor's loggers.
            Change next = null;
            for (Change later : changes) {
                if (later.row() > c.row() && later.context().equals(c.context()) && later.affects(nodeId)) {
                    next = later;
                    break;
                }
            }
            // R6-1: an unreadable control record in the same context, before that change, ends it instead.
            Unreadable blocked = null;
            for (Unreadable u : unreadable) {
                if (u.row() > c.row() && u.context().equals(c.context()) && (next == null || u.row() < next.row())) {
                    blocked = u;
                    break;
                }
            }
            if (blocked != null) next = null;
            int until = blocked != null ? blocked.row() : next == null ? Integer.MAX_VALUE : next.row();
            // Eighth re-review R8-1/R8-2, owner decision (option b): the annotation stops at the SECOND stream-end
            // marker after the change, so a record past it is never explained and at most one marker is crossed.
            Integer m1 = boundaryBetween(c.row(), Integer.MAX_VALUE);
            // Ninth re-review R9-1: the SECOND MARKER, by occurrence. Adjacent markers share a record position (a
            // run may hold no records), so "the next boundary past m1's position" skipped them.
            Integer m2 = m1 == null ? null : markerAfter(c.row(), 2);
            int stop = m2 == null ? until : Math.min(until, m2);
            int first = -1, last = -1;
            for (int r : inView) {
                if (r <= c.row()) continue;
                if (r >= stop) break;
                if (!c.context().equals(context(r))) continue;       // another processor's record
                if (first < 0) first = r;
                last = r;
            }
            if (first < 0) continue;                                  // governed none of the records in view
            // R6-2: a stream-end marker between the change and what ends it means the level is not known to have
            // held until then, so the closing clause must not say it did.
            Integer crossing = until == Integer.MAX_VALUE ? null : boundaryBetween(c.row(), until);
            // Seventh re-review R7-2/R7-3: the conclusion is bounded by the change, the window's end and the grouping.
            // The window's end, for the definite part, is the closer or the first marker after the change, whichever
            // comes first; for the part after that marker, the closer or the NEXT marker.
            Integer closer = until == Integer.MAX_VALUE ? null : until;
            String end1 = closer != null && (m1 == null || closer < m1) ? "record " + (closer + 1)
                    : m1 != null ? "the stream-end marker preceding record " + (m1 + 1) : null;
            String end2 = m1 == null ? null : closer != null && closer > m1 && (m2 == null || closer < m2)
                    ? "record " + (closer + 1) : m2 != null ? "the stream-end marker preceding record " + (m2 + 1) : null;
            return sentence(nodeId, c, next, blocked, crossing, first, boundaryBetween(c.row(), last), end1, end2);
        }
        return null;
    }

    private Grouping context(int row) {
        return row >= 0 && row < rowContext.length ? rowContext[row] : Grouping.ABSENT;
    }

    /**
     * The {@code n}th stream-end marker after row {@code from}, counting OCCURRENCES (ninth re-review R9-1): two
     * adjacent markers are two boundaries at one position, and the second of them is the second marker.
     */
    private Integer markerAfter(int from, int n) {
        int seen = 0;
        for (int b : runBoundaries) if (b > from && ++seen == n) return b;
        return null;
    }

    /** The FIRST run boundary in {@code (from, to]}: a record at or after it belongs to a later run. */
    private Integer boundaryBetween(int from, int to) {
        for (int b : runBoundaries) if (b > from && b <= to) return b;
        return null;
    }

    /**
     * The annotation as one sentence whose every conclusion carries ALL the premises the log does not
     * establish.
     *
     * <p><b>Why the premises are collected rather than written inline</b> (second re-review S2, S3). Each
     * earlier version added its caveat as a clause, and the clauses drifted apart: "…whether this change
     * applied is not established; if it did, … if it did, riskMonitor's lines are not in this log" had two
     * "if it did"s with different antecedents — applied, then survived — and the second read as though
     * survival alone were enough. The {@code "null"} case asserted the no-node reading first and disclosed
     * the ambiguity in a parenthesis, then concluded from the asserted reading; in the re-review's own probe
     * that reading was false. So the sentence now STATES what the record says, lists what is not established,
     * and every conclusion is conditioned on the whole list at once.
     */
    private static String sentence(String nodeId, Change c, Change next, Unreadable blocked, Integer crossing,
                                   int firstInView, Integer boundary, String end1, String end2) {
        boolean crosses = crossing != null;
        java.util.List<String> premises = new ArrayList<>();
        StringBuilder s = new StringBuilder();
        // Fifth re-review R5-2: while applying is open, the opening RECORDS a change; it never says it "sets" one.
        boolean applied = c.applies() == Applies.YES;
        if (c.sourceId() == null) {
            // S3: lead with the ambiguity. The runtime renders a Java null and the string "null" identically.
            s.append("at ").append(at(c)).append(" this log records a change to ").append(c.level())
                    .append(" that names no node — which would set every node's audit level — or a node literally ")
                    .append("called \"null\"; the log renders both identically");
            // Third re-review O-A: for a node literally named "null", both readings set THIS node, so the
            // premise is not open and an "otherwise" would be false.
            if ("null".equals(nodeId)) {
                s.append(" — either way it ").append(applied ? "sets" : "addresses").append(" this node, which is named \"null\"");
            }
            else premises.add("it named no node");
        } else {
            s.append(applied ? "this log sets " : "this log records a change setting ").append(nodeId)
                    .append("'s audit level to ").append(c.level()).append(" at ").append(at(c));
        }
        boolean declared = c.context().declared();
        switch (c.applies()) {
            case YES -> {
                if (c.groupId() != null) {
                    // Third re-review O-D: a sentence of its own. As a parenthesis it landed straight after "the log
                    // renders both identically" in the "null" case, and read as being about the rendering.
                    s.append(". It was addressed to processor grouping '").append(c.groupId()).append("', ")
                            .append(c.context().value() == null
                                    ? "which applies because the control record declares no grouping"
                                    : "which is the grouping the control record itself declares");
                }
            }
            case NOT_ESTABLISHED -> {
                s.append(". The control record states no processor grouping, so whether this change")
                        .append(c.groupId() == null ? "" : " — addressed to processor grouping '" + c.groupId() + "' —")
                        .append(" applied here is not established");
                premises.add("it applied here");
            }
            case NO -> throw new IllegalStateException("an inapplicable change never reaches a sentence");
        }
        // Never "this processor's records": RR-3 reads records that share a grouping as one stream, and nothing
        // in a record establishes that they came from one processor (S2). Checked by
        // ControlAddressAndScopeTest.noBranchOfTheSentencePresumesAProcessor over a matrix that reaches every branch
        // of this sentence and of closing() (fifth re-review R5-1: the declared-null YES note and the three open
        // closings were missing), with "processor" allowed only in "processor grouping".
        String scope = declared ? "the records sharing its grouping" : "the records that, like it, state no grouping";
        boolean closed = next != null || blocked != null;
        // Found while reading the fifth re-review's sentences: after a closing clause, "if it applied here" could read
        // as the CLOSING change — O-1's ambiguity, in the condition. Name the change the condition is about.
        String subject = !closed ? "it" : "the change at " + at(c);
        if (!premises.isEmpty()) premises.set(0, premises.get(0).replaceFirst("^it ", subject + " "));
        // Eighth re-review R8-3: "it holds" carries the conclusion's WHOLE condition — naming as well as applying.
        String condition = premises.isEmpty() ? null : all(premises);
        s.append(!closed ? ". Nothing later in " + scope + " changes it"
                : ". " + (blocked != null ? unreadableClosing(nodeId, condition, blocked, declared, crosses)
                : closing(nodeId, c, next, declared, crosses, condition)));
        String lines = nodeId + "'s lines below " + c.level() + " are not in this log";
        // Read in the sixth round's own output: after "whether that record changed … is not established" or after a
        // change past a marker, ", so …" made the conclusion read as following from the clause before it. It follows
        // from the change that opened the window, so it stands as its own sentence.
        boolean ownSentence = blocked != null || crosses;
        // Seventh re-review R7-1–R7-3: EVERY conclusion is bounded — from the change, to where the window ends, within
        // the change's grouping — on every branch, with a premise or without. Round 6 bounded only the premise-free
        // branch, and from the beginning of the log, so it took in records before the change and other groupings'.
        String in = ", in " + scope + ", " + lines;
        String span = "after record " + (c.row() + 1) + (end1 == null ? "" : " and before " + end1) + in;
        if (boundary == null) {
            s.append(premises.isEmpty() ? (ownSentence ? ". " + capitalise(span) : ", so " + span)
                    : ". If " + all(premises) + ", then " + span + "; otherwise this change explains nothing here");
        } else {
            String marker = "a stream-end marker before record " + (boundary + 1);
            java.util.List<String> later = new ArrayList<>(premises);
            later.add((premises.isEmpty() ? subject : "it") + " survived the marker");
            if (firstInView < boundary) {
                // RR-4: definite only within the run the change was made in; conditional after the marker. The bound
                // names the marker (end1), so "That marker" below has its antecedent (third re-review O-C, O5-3).
                s.append(premises.isEmpty() ? (ownSentence ? ". " + capitalise(span) : ", so " + span)
                        : ". If " + all(premises) + ", then " + span);
                // Eighth re-review R8-1: the later-run clause is bounded like every other conclusion — after that
                // marker, before the closer or the second marker (end2), in the change's grouping.
                s.append(". That marker begins a later run, and the log does not say ")
                        .append("whether the level survived into it: for the records in view after that marker")
                        .append(end2 == null ? "" : " and before " + end2).append(", in ").append(scope)
                        .append(", those lines are absent only if ").append(all(later));
            } else {
                // Ninth re-review R9-3: the view may hold records this annotation does not concern — the change's own
                // record, another grouping's, one past the second marker — so the lead speaks for the ones it does.
                s.append(". Every record in view that this annotation concerns is in a LATER run — ").append(marker)
                        .append(" begins it — and the log does not say whether the level survived into it. If ")
                        .append(all(later)).append(", then after that marker")
                        .append(end2 == null ? "" : " and before " + end2).append(in)
                        .append("; otherwise this change explains nothing here");
            }
        }
        return s.append(". It is still counted as uncovered, because a level change is not proof the node ran")
                .toString();
    }

    /**
     * The clause for the change that closes the window (third re-review R1). S3 disclosed the {@code "null"}
     * ambiguity in the opening and missed this clause, which still said "sets it to INFO for every node" — the
     * no-node reading asserted as fact. The window still ends at such a change: that only withholds annotations
     * after it, the conservative direction. What the sentence must not do is say why as if it were known.
     */
    private static String closing(String nodeId, Change c, Change next, boolean declared, boolean crosses,
                                  String condition) {
        // Fourth re-review R-B: did the CLOSING change apply? With a declared context, yes: it shares c's context
        // and affects the node, so it is YES. With an absent context it is as open as c was — and it is not
        // implied by c having applied only when their groupIds differ: c addressed to 'alpha' applied under a null
        // or 'alpha' grouping, and under 'alpha' a change addressed elsewhere (or to no grouping) did not. Derived
        // from the runtime's rule (grouping null or equal), not taken on trust; see P9.
        // Seventh re-review R7-4 (owner decision 2026-09-26): that rule does NOT extend across a stream-end marker. A
        // later run is not shown to be the same processor, so with no grouping declared a closer past a marker is open.
        boolean closeOpen = !c.context().declared()
                && (crosses || c.groupId() != null && !c.groupId().equals(next.groupId()));
        String addressed = next.groupId() == null ? "no processor grouping" : "processor grouping '" + next.groupId() + "'";
        // Fifth re-review O5-1: while that is open, the level is known to hold AT LEAST until then, not to end there.
        // R7-5: while the change's own applying is open, "It holds" is said only under that condition, naming it.
        String holds = holdsLead(condition) + (closeOpen ? " at least until " : " until ");
        // O5-2: one "if it applied", not two.
        String onlyIf = ", and only if it applied here: it was addressed to " + addressed
                + ", and whether it applied is not established either";
        if (crosses) {
            // Sixth re-review R6-2: across a stream-end marker the level is not known to have held until the next
            // change, so name where the next change is without saying the level lasted until it.
            String lead = next(nodeId, declared) + at(next) + ", which";
            if (next.sourceId() != null) {
                return closeOpen
                        ? lead + " records a change to " + next.level() + " addressed to " + addressed
                        + "; whether that applied here is not established either"
                        : lead + " sets it to " + next.level();
            }
            if ("null".equals(nodeId)) {
                return lead + " records a change to " + next.level() + " that names no node or this node, which is "
                        + "named \"null\" — either way it " + (closeOpen ? "would change this node" + onlyIf : "changes this node");
            }
            return lead + " records a change to " + next.level() + " that names no node — or a node literally called "
                    + "\"null\"; the log renders both identically, and only the first would change it"
                    + (closeOpen ? onlyIf : "");
        }
        if (next.sourceId() != null) {
            return closeOpen
                    ? holds + at(next) + ", which records a change to " + next.level() + " addressed to " + addressed
                    + "; whether that applied here is not established either"
                    : c.applies() == Applies.YES ? holds + at(next) + " sets it to " + next.level()
                    // R7-5 with R-B: definite only as far as the change it closes applied — so no ungrouped note
                    // says the closer "sets it", which R5-2's check now forbids everywhere.
                    : holds + at(next) + ", whose change to " + next.level() + " applied wherever this one did";
        }
        if ("null".equals(nodeId)) {
            // Sixth re-review O6-3: "it would end it there" had two referents.
            return holds + at(next) + " records a change to " + next.level() + " that names no node or "
                    + "this node, which is named \"null\" — either way it " + (closeOpen ? "would end the window there"
                    + onlyIf : "ends here");
        }
        return holds + at(next) + ", which records a change to " + next.level() + " that names no node — "
                + "or a node literally called \"null\"; the log renders both identically, and only the first would "
                + "end it there" + (closeOpen ? onlyIf : "");
    }

    /**
     * R7-5, eighth re-review R8-3: "It holds", or — while anything the conclusion rests on is not established — that,
     * under the conclusion's own condition: naming as well as applying.
     */
    private static String holdsLead(String condition) {
        return condition == null ? "It holds" : "If " + condition + ", it holds";
    }

    /** R6-2's lead: where the next change is, among the records the window is read over. */
    private static String next(String nodeId, boolean declared) {
        return "The next change to " + nodeId + "'s audit level "
                + (declared ? "in the same grouping" : "among the records that likewise state no grouping") + " is at ";
    }

    /**
     * R6-1's clause: the window ends at a control record this reader could not read. Whether it changed the level
     * is exactly what cannot be said, so the level holds AT LEAST until it — or, past a stream-end marker (R6-2),
     * the record is only named.
     */
    private static String unreadableClosing(String nodeId, String condition, Unreadable u, boolean declared,
                                            boolean crosses) {
        String what = "whether that record changed " + nodeId + "'s audit level is not established";
        String where = declared ? "in the same grouping" : "among the records that likewise state no grouping";
        // Seventh re-review R7-6: "A later", not "The next" — a readable control record for another node may come first.
        // Eighth re-review R8-4: a row read only from its raw text is "a record … whose text names the control event".
        if (u.nullRecord()) {
            return crosses
                    ? "A later record " + where + ", " + u.at() + ", whose text names the control event, could not be read "
                    + "by this reader; " + what
                    : holdsLead(condition) + " at least until " + u.at() + ", a record this reader could not read, whose "
                    + "text names the control event; " + what;
        }
        return crosses
                ? "A later control record " + where + ", " + u.at() + ", could not be read by this reader; " + what
                : holdsLead(condition) + " at least until " + u.at() + ", a control record this reader could "
                + "not read; " + what;
    }

    /** "a", "a and b", "a, b and c" — one condition, every premise in it. */
    private static String all(java.util.List<String> premises) {
        if (premises.size() == 1) return premises.get(0);
        return String.join(", ", premises.subList(0, premises.size() - 1)) + " and " + premises.get(premises.size() - 1);
    }

    private static String capitalise(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /** "record 3 (logTime 1000)", or "(untimed)" — never a stand-in number. */
    private static String at(Change c) {
        return at(c.row(), c.logTime());
    }

    private static String at(int row, Long logTime) {
        return "record " + (row + 1) + (logTime == null ? " (untimed)" : " (logTime " + logTime + ")");
    }

    /** Levels at or above WARN suppress the {@code info} lines coverage is looking for. */
    private static boolean isQuiet(String level) {
        if (level == null) return false;
        return switch (level.toUpperCase(Locale.ROOT)) {
            // The runtime's quiet levels. FATAL and OFF were listed here once; the runtime has neither.
            case "WARN", "ERROR", "NONE" -> true;
            default -> false;
        };
    }

    /**
     * Every change the log states, applied or not. NOT yet wired to the {@code context} echo or the
     * report — that is D-MA0c / MA-8's report path, still open.
     */
    public List<Change> all() {
        return changes;
    }
}
