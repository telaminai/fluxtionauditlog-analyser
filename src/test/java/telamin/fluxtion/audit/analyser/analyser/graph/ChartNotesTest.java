package telamin.fluxtion.audit.analyser.analyser.graph;

import telamin.fluxtion.audit.analyser.analyser.graph.ChartNotes.Note;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Notes and explanations held with a chart. Pure, so the awkward parts — ordering, windowing and the
 * column arithmetic that stops notes piling up on one pixel — are testable without drawing anything.
 */
class ChartNotesTest {

    private static Note at(long t, String text) {
        return new Note(t, text, null);
    }

    @Test
    void notesAreKeptInTimeOrderHoweverTheyWereAdded() {
        ChartNotes notes = ChartNotes.EMPTY
                .plus(at(300, "third"))
                .plus(at(100, "first"))
                .plus(at(200, "second"));
        assertEquals(List.of("first", "second", "third"), notes.notes().stream().map(Note::text).toList(),
                "a reader consumes them along the axis, not in the order someone typed them");
    }

    @Test
    void anEmptyOrBlankNoteIsDroppedRatherThanDrawnAsAnEmptyMarker() {
        ChartNotes notes = new ChartNotes("", List.of(at(1, "real"), at(2, "  "), at(3, null)));
        assertEquals(1, notes.notes().size());
        assertEquals("real", notes.notes().get(0).text());
    }

    @Test
    void emptyMeansNothingToDraw() {
        assertTrue(ChartNotes.EMPTY.isEmpty());
        assertFalse(ChartNotes.EMPTY.withExplanation("why this matters").isEmpty());
        assertFalse(ChartNotes.EMPTY.plus(at(1, "here")).isEmpty());
    }

    @Test
    void windowingKeepsOnlyWhatIsOnScreen() {
        ChartNotes notes = ChartNotes.EMPTY.plus(at(50, "before")).plus(at(150, "inside"))
                .plus(at(250, "after"));
        assertEquals(List.of("inside"), notes.between(100, 200).stream().map(Note::text).toList());
        assertEquals(3, notes.between(0, 1000).size(), "boundaries are inclusive");
    }

    @Test
    void collidingNotesShareAColumnSoTheChartCanStackThem() {
        // two notes 1ms apart on a 100px chart spanning 10s land on the same pixel
        ChartNotes notes = ChartNotes.EMPTY.plus(at(5_000, "a")).plus(at(5_001, "b"))
                .plus(at(9_000, "far"));
        var columns = notes.byColumn(0, 10_000, 100);
        assertEquals(2, columns.size(), "two distinct columns, not three");
        assertEquals(2, columns.values().stream().filter(l -> l.size() == 2).count() * 2,
                "the pair shares one column");
    }

    @Test
    void aDegenerateWindowYieldsNoColumnsRatherThanDividingByZero() {
        ChartNotes notes = ChartNotes.EMPTY.plus(at(1, "x"));
        assertTrue(notes.byColumn(0, 0, 100).isEmpty());
        assertTrue(notes.byColumn(0, 10, 0).isEmpty());
    }

    @Test
    void aNoteCanNameTheSeriesItIsAbout() {
        Note note = new Note(1, "shelf empties here", "stockLedger.onHand");
        assertTrue(note.isForSeries("stockLedger.onHand"));
        assertFalse(note.isForSeries("revenueLedger.gross"));
        assertFalse(at(1, "chart-wide").isForSeries("anything"), "no series means the chart as a whole");
    }

    @Test
    void clearingPinsKeepsTheWriteUp() {
        ChartNotes notes = ChartNotes.EMPTY.withExplanation("the point of this chart").plus(at(1, "x"));
        ChartNotes cleared = notes.withoutNotes();
        assertEquals("the point of this chart", cleared.explanation());
        assertTrue(cleared.notes().isEmpty());
    }
}
