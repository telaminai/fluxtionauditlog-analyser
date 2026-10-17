package telamin.fluxtion.audit.analyser.analyser.assistant;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.MissingResourceException;
import java.util.Optional;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * M74 P2 (spec-claude-code-provider.md §5.5, §5.8; OD-74.14): the ONE registry of every question the analyser can draft
 * for an assistant, and of the start page's entries. Pure data and pure functions, no Swing: the menus, the toolbar, the
 * detail panel, the diff dialog, the topology, the charts, the time slider and the start page build their items from
 * here, and the tests iterate it — so a new question is one {@link Entry} and its lines in
 * {@code AskCatalogue.properties}.
 *
 * <p><b>A question only drafts.</b> It has no verb of its own. Its draft names WHERE to look — record indexes, the log's
 * name and generation, node ids, a chart's name, a time — and never what is there: no value, payload, note text,
 * finding text or model-written words, because those can carry text written to steer an AI. Every pointer passes its
 * sanitiser in {@link AskPointers}. A draft that names records is BOUND to the log's identity and generation; the session
 * refuses to send it if the log has moved (§8.2).
 */
public final class AskCatalogue {

    private AskCatalogue() {
    }

    /** Where an entry appears. */
    public enum Surface {
        /** the records table's right-click menu */
        RECORDS_MENU,
        /** its copy on the Audit log menu bar */
        AUDIT_LOG_MENU,
        /** the toolbar's single Ask button: Q1 with one row, Q2 with more */
        TOOLBAR,
        /** the detail panel's button: Q1 or Q2, as the toolbar */
        DETAIL_BUTTON,
        /** the record diff dialog's button, from the dialog's own context */
        DIFF_DIALOG,
        /** the topology's right-click on a node */
        TOPOLOGY_NODE,
        /** a chart's right-click on a point or a note */
        CHART_POINT,
        /** the time slider's right-click (new in P2) */
        TIME_SLIDER,
        /** the start page's prompt list */
        START_PAGE
    }

    public enum Kind {
        /** asks about something on screen: a pop-up, then a draft */
        QUESTION,
        /** a start-page entry that runs the analyser's own flow first (the chooser, the demo, the catalogue) */
        FLOW,
        /** a start-page entry whose text is placed in the box */
        PROMPT
    }

    /** What a template may interpolate (§5.5's table), each through one sanitiser. */
    public enum Pointer {
        RECORD_INDEX, LOG_NAME, LOG_GENERATION, NODE_ID, KEY_NAME, GRAPH_NAME, CHART_NAME, SERIES_EXPR, TIME, COUNT,
        BUNDLE_NAME, VERDICT, PROJECT_NAME, WALK, TEMPLATE_NAME, DOCS_URL, FREE_TEXT
    }

    /** A no-AI fallback: a BUTTON running an existing start-page action (review c7f2 F7), never a menu path. */
    public enum Fallback {
        ASSISTANT_SETTINGS, CONNECT, COPY_PROMPT, NEW_PROJECT, BUNDLE_GUIDE, TEMPLATE, USER_GUIDE
    }

    /**
     * One registered question or start-page entry.
     *
     * @param id        {@code Q1}…{@code Q6}, {@code S1}…{@code S6}, or a test's own
     * @param kind      question, flow or prompt
     * @param surfaces  where it appears
     * @param pointers  the pointer kinds its template may interpolate
     * @param fallbacks with no AI configured, the buttons the start page offers (§5.8's last column)
     */
    public record Entry(String id, Kind kind, Set<Surface> surfaces, Set<Pointer> pointers, List<Fallback> fallbacks) {
        public Entry {
            surfaces = Set.copyOf(surfaces);
            pointers = pointers.isEmpty() ? Set.of() : Set.copyOf(pointers);
            fallbacks = List.copyOf(fallbacks == null ? List.of() : fallbacks);
        }

        public boolean startPage() {
            return surfaces.contains(Surface.START_PAGE);
        }

        public String labelKey() {
            return id + ".label";
        }

        public String templateKey() {
            return id + ".template";
        }
    }

    /** A menu item as a surface shows it: the entry, its label in the mode in force, and why it is disabled (or null). */
    public record Item(Entry entry, String label, String disabledReason) {
        public boolean enabled() {
            return disabledReason == null;
        }
    }

    /**
     * A composed draft: its words, which question it is, and — when it names records of a log — the log's identity and
     * generation it is bound to ({@code logIdentity} null for an unbound draft).
     */
    public record Draft(String questionId, String text, String logIdentity, long generation) {
        public boolean bound() {
            return logIdentity != null;
        }

        @Override
        public String toString() {          // never the words
            return "Draft[" + questionId + (bound() ? " bound to generation " + generation : " unbound") + "]";
        }
    }

    // ---- the registry ------------------------------------------------------------------------------------------------

    private static final Set<Surface> RECORDS = EnumSet.of(Surface.RECORDS_MENU, Surface.AUDIT_LOG_MENU,
            Surface.TOOLBAR, Surface.DETAIL_BUTTON);

    private static final List<Entry> BUILT_IN = List.of(
            new Entry("Q1", Kind.QUESTION, RECORDS,
                    EnumSet.of(Pointer.RECORD_INDEX, Pointer.LOG_NAME, Pointer.LOG_GENERATION, Pointer.NODE_ID), List.of()),
            new Entry("Q2", Kind.QUESTION, union(RECORDS, Surface.DIFF_DIALOG),
                    EnumSet.of(Pointer.RECORD_INDEX, Pointer.LOG_NAME, Pointer.LOG_GENERATION, Pointer.KEY_NAME), List.of()),
            new Entry("Q3", Kind.QUESTION, EnumSet.of(Surface.TOPOLOGY_NODE),
                    EnumSet.of(Pointer.GRAPH_NAME, Pointer.NODE_ID, Pointer.RECORD_INDEX, Pointer.LOG_NAME,
                            Pointer.LOG_GENERATION), List.of()),
            new Entry("Q4", Kind.QUESTION, EnumSet.of(Surface.CHART_POINT),
                    EnumSet.of(Pointer.CHART_NAME, Pointer.SERIES_EXPR, Pointer.TIME, Pointer.RECORD_INDEX,
                            Pointer.LOG_GENERATION, Pointer.COUNT), List.of()),
            new Entry("Q5", Kind.QUESTION, EnumSet.of(Surface.RECORDS_MENU, Surface.AUDIT_LOG_MENU),
                    EnumSet.of(Pointer.RECORD_INDEX, Pointer.LOG_NAME, Pointer.LOG_GENERATION), List.of()),
            new Entry("Q6", Kind.QUESTION, EnumSet.of(Surface.TIME_SLIDER, Surface.CHART_POINT),
                    EnumSet.of(Pointer.LOG_NAME, Pointer.LOG_GENERATION, Pointer.TIME, Pointer.RECORD_INDEX,
                            Pointer.COUNT), List.of()),
            new Entry("S1", Kind.FLOW, EnumSet.of(Surface.START_PAGE), EnumSet.noneOf(Pointer.class),
                    List.of(Fallback.ASSISTANT_SETTINGS, Fallback.CONNECT, Fallback.COPY_PROMPT)),
            new Entry("S2", Kind.FLOW, EnumSet.of(Surface.START_PAGE), EnumSet.noneOf(Pointer.class),
                    List.of(Fallback.NEW_PROJECT)),
            new Entry("S3", Kind.FLOW, EnumSet.of(Surface.START_PAGE),
                    EnumSet.of(Pointer.BUNDLE_NAME, Pointer.VERDICT), List.of(Fallback.BUNDLE_GUIDE)),
            new Entry("S4", Kind.FLOW, EnumSet.of(Surface.START_PAGE),
                    EnumSet.of(Pointer.TEMPLATE_NAME, Pointer.FREE_TEXT), List.of(Fallback.TEMPLATE)),
            new Entry("S5", Kind.FLOW, EnumSet.of(Surface.START_PAGE),
                    EnumSet.of(Pointer.LOG_NAME, Pointer.LOG_GENERATION), List.of(Fallback.COPY_PROMPT)),
            new Entry("S6", Kind.PROMPT, EnumSet.of(Surface.START_PAGE), EnumSet.of(Pointer.DOCS_URL),
                    List.of(Fallback.USER_GUIDE)));

    /** Test seam: an entry registered here appears on its surfaces with no other change (§10 "the registry is the one source"). */
    private static final List<Entry> EXTRA = new CopyOnWriteArrayList<>();

    private static Set<Surface> union(Set<Surface> a, Surface b) {
        EnumSet<Surface> s = EnumSet.copyOf(a);
        s.add(b);
        return s;
    }

    /** Every registered entry, built-in first. */
    public static List<Entry> entries() {
        List<Entry> all = new ArrayList<>(BUILT_IN);
        all.addAll(EXTRA);
        return all;
    }

    /** The built-in start-page entries, S1–S6, in order. */
    public static List<Entry> startPageBuiltIns() {
        return BUILT_IN.stream().filter(Entry::startPage).toList();
    }

    public static Optional<Entry> byId(String id) {
        if (id == null) return Optional.empty();
        return entries().stream().filter(e -> e.id().equals(id)).findFirst();
    }

    /** The entries that appear on {@code surface}, in registry order. */
    public static List<Entry> on(Surface surface) {
        return entries().stream().filter(e -> e.surfaces().contains(surface)).toList();
    }

    /** Register a test-only entry; its keys must be in the bundle or {@link #text} falls back to the key. */
    static void registerForTest(Entry e) {
        EXTRA.add(e);
    }

    static void clearTestEntries() {
        EXTRA.clear();
    }

    // ---- the bundle (the application's first ResourceBundle) ----------------------------------------------------------

    private static final String BUNDLE = "telamin.fluxtion.audit.analyser.analyser.assistant.AskCatalogue";

    /** The bundle's text for {@code key}, for {@code Locale.getDefault()}; a missing key reads as itself. */
    public static String text(String key) {
        try {
            return ResourceBundle.getBundle(BUNDLE, Locale.getDefault()).getString(key);
        } catch (MissingResourceException e) {
            return key;
        }
    }

    /** Whether the bundle has {@code key} — for the test that every registered entry's keys exist. */
    public static boolean hasText(String key) {
        try {
            ResourceBundle.getBundle(BUNDLE, Locale.getDefault()).getString(key);
            return true;
        } catch (MissingResourceException e) {
            return false;
        }
    }

    /** {@code text(key)} with its {@code {name}} placeholders replaced from {@code values}; others stay literal. */
    public static String text(String key, Map<String, String> values) {
        return fill(text(key), values);
    }

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z]+)\\}");

    static String fill(String template, Map<String, String> values) {
        Matcher m = PLACEHOLDER.matcher(template);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            String v = values.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? m.group(0) : v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    // ---- labels and preconditions -------------------------------------------------------------------------------------

    /** The question's own label ({@code What happened here?}). */
    public static String question(Entry e) {
        return text(e.labelKey());
    }

    /** The item label in the mode in force: {@code Ask Claude: What happened here?} / {@code Ask the assistant: …}. */
    public static String label(Entry e, AskContext.Mode mode) {
        String q = hasText(e.id() + ".menu") ? text(e.id() + ".menu") : question(e);
        return text("mode." + mode.name(), Map.of("question", q));
    }

    /** Why {@code e} cannot be asked on {@code surface} now, or null when it can (the precondition, §10). */
    public static String disabledReason(Entry e, Surface surface, AskContext c) {
        if (e.kind() != Kind.QUESTION) return null;
        if (c.mode() == AskContext.Mode.IN_APP && !c.inProcessActions()) return text("disabled.inProcess");
        if (!c.logOpen()) return text("disabled.noLog");
        int n = c.selected().size();
        return switch (e.id()) {
            case "Q1" -> n == 1 ? null : text("disabled.oneRow");
            case "Q2" -> n < 2 ? text("disabled.twoRows") : n > AskPointers.MAX_COMPARED ? text("disabled.tooMany") : null;
            case "Q3" -> c.nodeId() == null ? text("disabled.noNode") : c.current() == null ? text("disabled.current") : null;
            case "Q4" -> c.chartName() == null || c.pointTime() == null || c.pointRecord() == null
                    ? text("disabled.noPoint") : null;
            case "Q5" -> n != 1 ? text("disabled.oneRow") : !c.flagged().contains(c.selected().get(0))
                    ? text("disabled.flagged") : null;
            case "Q6" -> c.rangeCount() == null || c.rangeCount() < 1 || c.rangeFrom() == null || c.rangeTo() == null
                    ? text("disabled.range") : null;
            default -> null;            // a test entry: always askable
        };
    }

    /** The items of {@code surface}, each with its label and enabled state. */
    public static List<Item> items(Surface surface, AskContext c) {
        List<Item> out = new ArrayList<>();
        for (Entry e : on(surface)) {
            if (surface == Surface.CHART_POINT && e.id().equals("Q6") && c.rangeCount() == null) continue;
            out.add(new Item(e, label(e, c.mode()), disabledReason(e, surface, c)));
        }
        return out;
    }

    /**
     * The single-action entrances (the toolbar, the detail panel): Q1 with one row, Q2 with two or more, null with none —
     * which leaves the button disabled with {@code surface.<S>.none} (§5.5).
     */
    public static Item singleAction(Surface surface, AskContext c) {
        int n = c.selected().size();
        if (n == 0) return null;
        Entry e = byId(n == 1 ? "Q1" : "Q2").orElseThrow();
        return new Item(e, label(e, c.mode()), disabledReason(e, surface, c));
    }

    // ---- composing a question -----------------------------------------------------------------------------------------

    /** The draft for question {@code e} over {@code c}; refused (IllegalStateException) when its precondition fails. */
    public static Draft compose(Entry e, Surface surface, AskContext c) {
        String why = disabledReason(e, surface, c);
        if (why != null) throw new IllegalStateException(e.id() + ": " + why);
        Map<String, String> v = new LinkedHashMap<>();
        v.put("log", AskPointers.name(c.logName()));
        v.put("gen", AskPointers.integer(c.generation()));
        String text = switch (e.id()) {
            case "Q1" -> {
                int index = c.selected().get(0);
                int last = Math.max(0, c.recordCount() - 1);
                int from = Math.max(0, index - 5);
                int to = Math.min(last, index + 5);
                v.put("index", AskPointers.integer(index));
                v.put("before", AskPointers.integer(index - from));
                v.put("after", AskPointers.integer(Math.max(0, to - index)));
                v.put("from", AskPointers.integer(from));
                v.put("to", AskPointers.integer(Math.max(from, to)));
                v.put("nodeIds", nodeIds(c.cycleNodeIds()));
                yield text(e.templateKey(), v);
            }
            case "Q2" -> {
                v.put("indexes", indexes(c.selected()));
                List<String> keys = c.diffKeys();
                v.put("diffKeys", keys.isEmpty() ? "" : text("Q2.diffKeys", Map.of("keys", keyNames(keys))) + " ");
                yield text(e.templateKey(), v);
            }
            case "Q3" -> {
                v.put("graph", c.graphName() == null ? text("Q3.noGraph") : AskPointers.name(c.graphName()));
                v.put("nodeId", AskPointers.identifier(c.nodeId(), "#" + Math.max(1, c.nodePosition()) + " of the graph"));
                boolean logged = Boolean.TRUE.equals(c.nodeLogged());
                v.put("logged", text(logged ? "Q3.logged" : "Q3.notLogged"));
                v.put("ask", text(logged ? "Q3.ask.logged" : "Q3.ask.notLogged"));
                v.put("index", AskPointers.integer(c.current()));
                yield text(e.templateKey(), v);
            }
            case "Q4" -> {
                v.put("chart", AskPointers.name(c.chartName()));
                v.put("expr", AskPointers.expression(c.seriesExpr()));
                v.put("time", AskPointers.time(c.pointTime()));
                v.put("index", AskPointers.integer(c.pointRecord()));
                v.put("note", c.noteNumber() == null ? ""
                        : text("Q4.note", Map.of("noteNumber", AskPointers.integer(c.noteNumber()))));
                yield text(e.templateKey(), v);
            }
            case "Q5" -> {
                v.put("index", AskPointers.integer(c.selected().get(0)));
                yield text(e.templateKey(), v);
            }
            case "Q6" -> {
                v.put("fromTime", AskPointers.time(c.rangeFrom()));
                v.put("toTime", AskPointers.time(c.rangeTo()));
                v.put("first", AskPointers.integer(c.rangeFirst() == null ? 0 : c.rangeFirst()));
                v.put("last", AskPointers.integer(c.rangeLast() == null ? 0 : c.rangeLast()));
                v.put("count", AskPointers.integer(c.rangeCount()));
                yield text(e.templateKey(), v);
            }
            default -> text(e.templateKey(), v);
        };
        return new Draft(e.id(), text, c.logIdentity(), c.generation());
    }

    private static String nodeIds(List<String> ids) {
        if (ids.isEmpty()) return text("Q1.noNodes");
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (String id : ids) {
            if (n == AskPointers.MAX_NODE_IDS) {
                sb.append(" and ").append(ids.size() - n).append(" more");
                break;
            }
            if (n > 0) sb.append(", ");
            sb.append(AskPointers.identifier(id, "#" + (n + 1) + " of this cycle's nodes"));
            n++;
        }
        return sb.toString();
    }

    private static String indexes(List<Integer> rows) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            if (i > 0) sb.append(i == rows.size() - 1 ? " and " : ", ");
            sb.append('#').append(AskPointers.integer(rows.get(i)));
        }
        return sb.toString();
    }

    private static String keyNames(List<String> keys) {
        StringBuilder sb = new StringBuilder();
        int n = 0;
        for (String k : keys) {
            if (n == AskPointers.MAX_DIFF_KEYS) {
                sb.append(" and ").append(keys.size() - n).append(" more");
                break;
            }
            if (n > 0) sb.append(", ");
            sb.append(AskPointers.identifier(k, "key " + (n + 1)));
            n++;
        }
        return sb.toString();
    }

    // ---- composing a start-page draft (after its flow, §5.8 F9) ------------------------------------------------------

    /**
     * What a start-page flow found, for the draft composed after it: the log's name, identity and generation (S5 and a
     * configured {@code {log}}), the verified bundle's file name (S3), the template names as fetched (S4; null when the
     * catalogue could not be read), the project's folder name, and the person's own words in the box.
     */
    public record StartFacts(String logName, String logIdentity, long generation, String bundleName, boolean verified,
                             List<String> templateNames, String projectName, String freeText) {
        public StartFacts {
            freeText = freeText == null ? "" : freeText.strip();
        }

        public static StartFacts none(String freeText) {
            return new StartFacts(null, null, 0, null, false, null, null, freeText);
        }
    }

    /**
     * The draft for start-page entry {@code id} (a built-in) after its flow, or null when the entry composes none (S1 and
     * S2 play or open the analyser's own flows). The person's text is appended after a blank line (S4 puts it in the
     * sentence).
     */
    public static Draft composeStart(String id, StartFacts f) {
        Map<String, String> v = new LinkedHashMap<>();
        String text;
        boolean bound = false;
        switch (id) {
            case "S3" -> {
                v.put("bundle", AskPointers.name(f.bundleName()));
                AskPointers.verdict(f.verified());
                text = text("S3.template", v);
            }
            case "S4" -> {
                String names = AskPointers.templateNames(f.templateNames());
                v.put("templates", names == null ? text("S4.unreachable") : names);
                v.put("text", f.freeText().isEmpty() ? text("S4.describe") : f.freeText());
                return new Draft(id, text("S4.template", v), null, 0);
            }
            case "S5" -> {
                v.put("log", AskPointers.name(f.logName()));
                v.put("gen", AskPointers.integer(f.generation()));
                text = text("S5.template", v);
                bound = f.logIdentity() != null;
            }
            case "S6" -> {
                v.put("docsUrl", AskPointers.DOCS_URL);
                text = text("S6.template", v);
            }
            // P2b (§5.6): S2 toward Claude Code, after the set-up folder was chosen — no pointer, no record text
            case "S2" -> text = text("S2.template", v);
            default -> {
                return null;
            }
        }
        return new Draft(id, withFreeText(text, f.freeText()), bound ? f.logIdentity() : null, bound ? f.generation() : 0);
    }

    /**
     * The start page's Copy prompt (r8 F14): the composed draft ONLY — the entry's words from pointers, then the box. No
     * record context, no glossary, never a token: there is nothing else for this to read. S1 composes none, so the
     * box's words (or, with an empty box, S6's question) are copied.
     */
    public static String copyText(String entryId, StartFacts f) {
        Draft d = entryId == null ? null : composeStart(entryId, f);
        if (d != null) return d.text();
        if (!f.freeText().isBlank()) return f.freeText();
        return composeStart("S6", f).text();
    }

    /** Free text alone (no entry chosen): the person's own question, unbound. */
    public static Draft composeFreeText(StartFacts f) {
        return new Draft("FREE_TEXT", f.freeText(), null, 0);
    }

    /**
     * Final review #12 (owner decision, 2026-10-17): what the MODEL is told about a message's binding — the log generation
     * its composed drafts were checked against at Send — appended to the text sent, never to the person's draft. Empty
     * for 0 (no draft names a log).
     */
    public static String modelBinding(long generation) {
        if (generation <= 0) return "";
        return "\n\n" + text("binding.model", Map.of("gen", AskPointers.integer(generation)));
    }

    /** The journey handoff's draft in the Claude Code mode (§5.5 Journeys), bound to the walk's log. */
    public static Draft composeJourney(String walkName, int walkPosition, AskContext c) {
        Map<String, String> v = new LinkedHashMap<>();
        v.put("walk", AskPointers.walk(walkName, walkPosition));
        v.put("log", AskPointers.name(c.logName()));
        v.put("gen", AskPointers.integer(c.generation()));
        // the person's own question follows the colon: the draft ends with a space for it
        return new Draft("JOURNEY", text("JOURNEY.template", v) + " ", c.logIdentity(), c.generation());
    }

    private static String withFreeText(String draft, String freeText) {
        return freeText == null || freeText.isBlank() ? draft : draft + "\n\n" + freeText;
    }
}
