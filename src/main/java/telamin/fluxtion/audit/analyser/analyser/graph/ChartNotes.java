package telamin.fluxtion.audit.analyser.analyser.graph;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a reader needs written on a chart to understand it later: a block of explanation, and notes
 * pinned to moments in time.
 *
 * <p>A plot answers "what happened"; it never answers "and here is why that matters". Today that second
 * half lives in whatever message the plot was pasted into, and is lost the moment the image is. Holding
 * it <b>with the graph</b> is the difference between an artefact that survives the conversation and one
 * that does not — which matters most when the person reading it later is the same person, six weeks on.
 *
 * <p>Pure and immutable: no Swing, no colours, no layout. The chart decides how to draw these; this
 * decides what they mean. Notes are kept in time order because that is the order a reader consumes them,
 * whatever order they were added in.
 */
public record ChartNotes(String explanation, List<Note> notes) {

    /**
     * A note pinned to a moment.
     *
     * @param atMillis the time the note is about — the anchor
     * @param text     what to say
     * @param series   the series label it refers to, or {@code null} for the chart as a whole
     */
    public record Note(long atMillis, String text, String series) {

        public boolean isForSeries(String label) {
            return series != null && series.equals(label);
        }
    }

    public static final ChartNotes EMPTY = new ChartNotes("", List.of());

    public ChartNotes {
        explanation = explanation == null ? "" : explanation;
        List<Note> copy = notes == null ? List.of() : new ArrayList<>(notes);
        copy.removeIf(n -> n == null || n.text() == null || n.text().isBlank());
        copy.sort((a, b) -> Long.compare(a.atMillis(), b.atMillis()));
        notes = List.copyOf(copy);
    }

    public boolean isEmpty() {
        return explanation.isBlank() && notes.isEmpty();
    }

    public ChartNotes withExplanation(String text) {
        return new ChartNotes(text, notes);
    }

    /** Add a note, keeping time order. */
    public ChartNotes plus(Note note) {
        List<Note> next = new ArrayList<>(notes);
        next.add(note);
        return new ChartNotes(explanation, next);
    }

    /** Drop every note, keeping the explanation — "clear the pins, keep the write-up". */
    public ChartNotes withoutNotes() {
        return new ChartNotes(explanation, List.of());
    }

    /** Notes falling inside a window, for a chart that is showing only part of the log. */
    public List<Note> between(long from, long to) {
        List<Note> out = new ArrayList<>();
        for (Note note : notes) {
            if (note.atMillis() >= from && note.atMillis() <= to) {
                out.add(note);
            }
        }
        return out;
    }

    /**
     * Notes grouped by the pixel column they land on, so a chart can stack the ones that collide instead
     * of drawing them over each other. Grouping is the caller's business but the arithmetic is not, and
     * getting it wrong produces an unreadable pile at one x.
     */
    public Map<Integer, List<Note>> byColumn(long from, long to, int width) {
        Map<Integer, List<Note>> out = new LinkedHashMap<>();
        if (width <= 0 || to <= from) {
            return out;
        }
        for (Note note : between(from, to)) {
            int column = (int) ((note.atMillis() - from) * (width - 1) / (to - from));
            out.computeIfAbsent(column, c -> new ArrayList<>()).add(note);
        }
        return out;
    }
}
