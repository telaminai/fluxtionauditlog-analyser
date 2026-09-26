package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Review O1, headless half: the clipped status line is readable in full as a tooltip, one part per line. */
class TopologyStatusTooltipTest {

    @Test
    void theTooltipCarriesEveryPartOnItsOwnLine() {
        String line = "kept on a partial match (3/4 ids declared)   ·   20 records   ·   a <b> & c";
        String tip = TopologyPanel.statusTooltip(line);
        assertTrue(tip.startsWith("<html>kept on a partial match (3/4 ids declared)<br>20 records<br>"), tip);
        assertTrue(tip.contains("a &lt;b&gt; &amp; c"), "text is escaped, not interpreted as markup: " + tip);
    }
}
