package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Review of M64, F1 — {@code graph:series:<label>} names a series EXACTLY. The first version fell back to a
 * prefix match, so a label that is no series at all was lit (and echoed as lit), and of two series sharing a
 * prefix the first one drawn was chosen: a pointer at a guessed target.
 */
class SpotlightSeriesLabelTest {

    private static final List<String> LEGEND = List.of(
            "quotePublisher.spread", "quotePublisher.spreadCap", "venue mid" + GraphPanel.EXTERNAL_SUFFIX, "spread");

    @Test
    void anExactLabelIsFound() {
        assertEquals(0, GraphPanel.legendIndexOf(LEGEND, "quotePublisher.spread"));
        assertEquals(1, GraphPanel.legendIndexOf(LEGEND, "quotePublisher.spreadCap"));
        assertEquals(3, GraphPanel.legendIndexOf(LEGEND, " spread "), "surrounding space is not part of a name");
    }

    @Test
    void aPrefixIsNotAName_itIsRefused_notBoundToTheFirstSeriesThatStartsWithIt() {
        assertEquals(-1, GraphPanel.legendIndexOf(LEGEND, "quote"), "the reviewer's reproduction");
        assertEquals(-1, GraphPanel.legendIndexOf(LEGEND, "quotePublisher"));
        assertEquals(-1, GraphPanel.legendIndexOf(LEGEND, "quotePublisher.spre"),
                "two series share this prefix — choosing either would be a guess");
    }

    @Test
    void anExternalSeriesIsNamedByItsOwnLabel_theLegendsSuffixIsTheOnlyToleratedDifference() {
        assertEquals(2, GraphPanel.legendIndexOf(LEGEND, "venue mid"));
        assertEquals(2, GraphPanel.legendIndexOf(LEGEND, "venue mid" + GraphPanel.EXTERNAL_SUFFIX));
        assertEquals(-1, GraphPanel.legendIndexOf(LEGEND, "venue"));
        assertEquals(-1, GraphPanel.legendIndexOf(LEGEND, "venue mid (external)"), "not the suffix the legend writes");
    }

    @Test
    void whenAnAuditSeriesAndAnExternalOneShareALabel_theBareLabelIsTheAuditSeries() {
        List<String> both = List.of("mid" + GraphPanel.EXTERNAL_SUFFIX, "mid");
        assertEquals(1, GraphPanel.legendIndexOf(both, "mid"), "exact wins over suffixed, whatever the order");
        assertEquals(0, GraphPanel.legendIndexOf(both, "mid" + GraphPanel.EXTERNAL_SUFFIX));
    }

    @Test
    void nothingIsNotASeries() {
        assertEquals(-1, GraphPanel.legendIndexOf(LEGEND, null));
        assertEquals(-1, GraphPanel.legendIndexOf(LEGEND, "  "));
        assertEquals(-1, GraphPanel.legendIndexOf(List.of(), "spread"));
    }
}
