package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.filter.RecordsViewCounts;

import java.util.ArrayList;
import java.util.List;

/**
 * The words every Records surface says about a {@link RecordsViewCounts} — composed here, once, so the "showing" label,
 * the Records title, the hidden line, the empty table and the Event types toggle cannot drift apart. Pure: it renders
 * what the session measured and counts nothing itself.
 */
final class RecordsViewWords {

    /** The four filters that can hide a record, in the order they are named. */
    enum Kind { TYPES, TEXT, TIME, FLAGGED }

    /**
     * What one active filter hides, as the hidden line and the empty table each say it, with the words of its undo.
     *
     * @param hidden     "18 hidden by event types" — the Records hidden line
     * @param empty      "event types exclude all 21" — the empty table
     * @param hiddenUndo the hidden line's button
     * @param emptyUndo  the empty table's button
     * @param undoName   what the button does, for a screen reader and the tooltip
     */
    record Reason(Kind kind, int count, String hidden, String empty, String hiddenUndo, String emptyUndo, String undoName) { }

    /** A search longer than this is shortened in the sentences (the Search box carries the whole of it). */
    static final int TEXT_LIMIT = 40;

    private RecordsViewWords() { }

    /** The Time range header's label: "showing 3 of 21", or a blank with no log (M35.1: no log, no count). */
    static String showing(RecordsViewCounts rv) {
        return rv == null ? " " : "showing " + rv.shown() + " of " + rv.total();
    }

    /** The Records section's title: "Records: 3 of 21 shown · 18 hidden", "Records: all 21 shown", or "Records". */
    static String title(RecordsViewCounts rv) {
        if (rv == null) return "Records";
        if (!rv.anyHidden()) return "Records: all " + rv.total() + " shown";
        return "Records: " + rv.shown() + " of " + rv.total() + " shown · " + (rv.total() - rv.shown()) + " hidden";
    }

    /**
     * Each active filter that hides at least one record, in {@link Kind} order. {@code text} is the search text the
     * counts name by reference, resolved by the frame (null when it cannot be: the sentence then names no words).
     */
    static List<Reason> reasons(RecordsViewCounts rv, String text) {
        List<Reason> out = new ArrayList<>();
        if (rv == null) return out;
        if (rv.typesFiltered() && rv.hiddenByTypes() > 0) {
            int n = rv.hiddenByTypes();
            out.add(new Reason(Kind.TYPES, n, n + " hidden by event types",
                    "event types exclude " + (n == rv.total() ? "all " : "") + n, "Show all", "Select all",
                    "Select all event types"));
        }
        if (rv.searching() && rv.hiddenByText() > 0) {
            int n = rv.hiddenByText();
            String quoted = quoted(text);
            out.add(new Reason(Kind.TEXT, n, n + " hidden by search " + quoted,
                    "search " + quoted + " excludes " + (n == rv.total() ? "all " : "") + n, "×", "Clear",
                    "Clear the search"));
        }
        if (rv.timeBounded() && rv.hiddenByTime() > 0) {
            int n = rv.hiddenByTime();
            out.add(new Reason(Kind.TIME, n, n + " hidden by the time range",
                    "the time range excludes " + (n == rv.total() ? "all " : "") + n, "Reset", "Reset",
                    "Reset the time range to the whole log"));
        }
        if (rv.flaggedOnly() && rv.hiddenByFlags() > 0) {
            int n = rv.hiddenByFlags();
            out.add(new Reason(Kind.FLAGGED, n, n + " hidden by flagged only",
                    "flagged only excludes " + (n == rv.total() ? "all " : "") + n, "Show all", "Turn off",
                    "Turn off flagged only"));
        }
        return out;
    }

    /** The hidden line as one sentence, for a screen reader: "18 hidden by event types · 9 hidden by search "x"". */
    static String hiddenSentence(RecordsViewCounts rv, String text) {
        return String.join(" · ", reasons(rv, text).stream().map(Reason::hidden).toList());
    }

    /** The empty table as one sentence: "No records: event types exclude all 21 · search "x" excludes 9". */
    static String emptySentence(RecordsViewCounts rv, String text) {
        if (rv == null) return "No log loaded — Audit log ▸ Open log…, drag a file in, or Audit log ▸ Open log from S3";
        if (rv.total() == 0) return "No records: this log has none";
        List<Reason> reasons = reasons(rv, text);
        return "No records: " + (reasons.isEmpty() ? "the filter hides every record"
                : String.join(" · ", reasons.stream().map(Reason::empty).toList()));
    }

    /** The Event types toggle's badge, "1 of 7", or null when every type is selected. */
    static String typesBadge(RecordsViewCounts rv) {
        return rv == null || !rv.typesFiltered() ? null : rv.typesSelected() + " of " + rv.typesTotal();
    }

    /** The toggle's own text: the arrow says whether the list shows, the name says what it is. */
    static String typesToggle(boolean listShown) {
        return (listShown ? "▾" : "▸") + " Event types";
    }

    /** The toggle as a screen reader and its tooltip say it. */
    static String typesToggleName(RecordsViewCounts rv, boolean listShown) {
        String badge = typesBadge(rv);
        return "Event types" + (badge == null ? ", all selected" : ", " + badge + " selected")
                + (listShown ? ", list shown" : ", list hidden");
    }

    /** One checklist row's count: "(7)" when the other filters keep all of its records, "(3 / 7)" when they do not. */
    static String typeCount(RecordsViewCounts.TypeCount t) {
        return t.shown() == t.total() ? "(" + t.total() + ")" : "(" + t.shown() + " / " + t.total() + ")";
    }

    private static String quoted(String text) {
        if (text == null || text.isEmpty()) return "text";
        String t = text.length() > TEXT_LIMIT ? text.substring(0, TEXT_LIMIT - 1) + "…" : text;
        return "\"" + t + "\"";
    }
}
