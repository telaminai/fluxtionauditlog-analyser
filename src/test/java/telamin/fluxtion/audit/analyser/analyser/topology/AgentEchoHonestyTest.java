package telamin.fluxtion.audit.analyser.analyser.topology;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M46 A3 and A5 — two agent-facing echoes that stated one fact under another's name.
 *
 * <p>Both were reported by agents reading the echo, not the screen: a person sees the status bar's
 * "20 nodes" and a populated records table, so neither defect was visible from the UI.
 */
class AgentEchoHonestyTest {

    // ---- A3: `nodes` held the AUTHORED count, beside a pairing verdict, where it read as graph size --

    @Test
    void theDemoGraphsSizeAndItsAuthoredCountAreDifferentFacts_whichIsWhyTheEchoNamesBoth() throws Exception {
        ProcessorTopology graph = GraphMlParser.parse(
                Files.readString(Path.of("src/test/resources/topology/demo-quote-processor.graphml")));

        int graphNodes = graph.nodes().size();
        int authoredNodes = Scaffolding.authoredNodes(graph).size();

        assertEquals(20, graphNodes, "what the status bar counts, and what `graphNodes` now echoes");
        assertEquals(10, authoredNodes, "what the old `nodes` key held, now `authoredNodes`");
        assertNotEquals(graphNodes, authoredNodes,
                "if these were ever equal one key would do; they are not, so one key misleads");
    }

    // ---- A5: an unbound cursor said "no records" while ten were open ---------------------------------

    @Test
    void anUnboundCursorWithRecordsOpenSaysNoRecordIsSELECTED_andHowToSelectOne() {
        String label = StepCursor.unboundLabel(10);

        assertTrue(label.startsWith("no record selected"), label);
        assertTrue(label.contains("10 open"), label);
        assertTrue(label.contains("goto"), "an agent told only what is missing cannot act on it: " + label);
    }

    @Test
    void withNothingOpen_noRecords_isStillTheTruth() {
        assertEquals("no records", StepCursor.unboundLabel(0));
        assertEquals("no records", StepCursor.unboundLabel(-1));
    }

    @Test
    void theUnboundLabelIsOnlyForAnEmptyCursor_aBoundOneKeepsItsOwnWording() {
        // the echo chooses by cursor.isEmpty(); an empty cursor's own label stays "no records" so the
        // status line for a genuinely empty source is unchanged
        assertEquals("no records", StepCursor.over(java.util.List.of()).positionLabel());
    }
}
