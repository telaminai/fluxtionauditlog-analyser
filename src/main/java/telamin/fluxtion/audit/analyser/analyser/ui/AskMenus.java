package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.JMenuItem;
import java.util.List;

/**
 * M74 P2 (spec §5.5): the Ask catalogue's items for the surfaces that are not the frame's own — the topology's node
 * menu, a chart's point menu and the time slider's (new) menu. The frame builds them FROM THE REGISTRY; each surface
 * only hands over the pointers it knows (a node's id and position, a point's time and its note's NUMBER, a range) and
 * adds what comes back. No surface writes a question of its own.
 */
public interface AskMenus {

    /** A topology node; {@code logged} is the analyser's own coverage reading for the current record, or null. */
    List<JMenuItem> forNode(String instanceId, int position, Boolean logged);

    /** A chart point (and, when the click is on one, a note's number — never its words), with the chart's window. */
    List<JMenuItem> forChart(String chartName, String series, long at, Integer noteNumber, Long windowFrom, Long windowTo);

    /** A time range (the time slider's window). */
    List<JMenuItem> forRange(long from, long to);

    AskMenus NONE = new AskMenus() {
        @Override public List<JMenuItem> forNode(String instanceId, int position, Boolean logged) { return List.of(); }
        @Override public List<JMenuItem> forChart(String chartName, String series, long at, Integer noteNumber,
                                                  Long windowFrom, Long windowTo) { return List.of(); }
        @Override public List<JMenuItem> forRange(long from, long to) { return List.of(); }
    };
}
