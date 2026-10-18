package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import telamin.fluxtion.audit.analyser.analyser.filter.RecordsViewCounts;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** The words every Records surface says, from the session's counts — headless, so a wording change is cheap to see. */
class RecordsViewWordsTest {

    private static RecordsViewCounts counts(int shown, int byTypes, int byText, String text, boolean typesFiltered, int selected) {
        return new RecordsViewCounts(21, shown, byTypes, byText, 0, 0, typesFiltered, selected, 7, !text.isEmpty(), 1L, false, false,
                List.of(new RecordsViewCounts.TypeCount("Tick", false, 7, 3, true)));
    }

    @Test
    void noLogSaysNothingCounted() {
        assertEquals(" ", RecordsViewWords.showing(null));
        assertEquals("Records", RecordsViewWords.title(null));
        assertNull(RecordsViewWords.typesBadge(null));
        assertTrue(RecordsViewWords.emptySentence(null, null).startsWith("No log loaded"));
    }

    @Test
    void oneTypeSelectedHidesEighteen() {
        RecordsViewCounts rv = counts(3, 18, 0, "", true, 1);
        assertEquals("showing 3 of 21", RecordsViewWords.showing(rv));
        assertEquals("Records: 3 of 21 shown · 18 hidden", RecordsViewWords.title(rv));
        assertEquals("18 hidden by event types", RecordsViewWords.hiddenSentence(rv, ""));
        assertEquals("1 of 7", RecordsViewWords.typesBadge(rv));
        assertEquals("Event types, 1 of 7 selected, list hidden", RecordsViewWords.typesToggleName(rv, false));
        assertEquals("▸ Event types", RecordsViewWords.typesToggle(false));
        assertEquals("▾ Event types", RecordsViewWords.typesToggle(true));
    }

    @Test
    void everythingHiddenNamesEachFilter() {
        RecordsViewCounts rv = counts(0, 21, 9, "hedging", true, 0);
        assertEquals("No records: event types exclude all 21 · search \"hedging\" excludes 9",
                RecordsViewWords.emptySentence(rv, "hedging"));
        assertEquals("No records: event types exclude all 21 · search text excludes 9",
                RecordsViewWords.emptySentence(rv, null), "an unresolved reference names no words");
        assertEquals(List.of(RecordsViewWords.Kind.TYPES, RecordsViewWords.Kind.TEXT),
                RecordsViewWords.reasons(rv, "hedging").stream().map(RecordsViewWords.Reason::kind).toList());
    }

    @Test
    void aFilterThatHidesNothingIsNotNamed() {
        RecordsViewCounts rv = counts(21, 0, 0, "Tick", false, 7);
        assertEquals("Records: all 21 shown", RecordsViewWords.title(rv));
        assertTrue(RecordsViewWords.reasons(rv, "Tick").isEmpty());
        assertNull(RecordsViewWords.typesBadge(rv));
    }

    @Test
    void aLongSearchIsShortenedInTheSentence() {
        String text = "x".repeat(80);
        String line = RecordsViewWords.hiddenSentence(counts(12, 0, 9, text, false, 7), text);
        assertTrue(line.length() < 80, line);
        assertTrue(line.endsWith("…\""), line);
    }

    @Test
    void aRowCountsUnderTheOtherFilters() {
        assertEquals("(7)", RecordsViewWords.typeCount(new RecordsViewCounts.TypeCount("a", false, 7, 7, true)));
        assertEquals("(3 / 7)", RecordsViewWords.typeCount(new RecordsViewCounts.TypeCount("a", false, 7, 3, true)));
    }
}
