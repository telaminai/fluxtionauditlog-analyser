package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.swing.SwingUtilities;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FirstMinuteTopologyOwnershipTest {
    @Test void everySuccessfulLoadReportsItsOwnReceiptAndOrdinaryLoadsClearIt(@TempDir Path tmp) throws Exception {
        Path root = DiamondAssets.install(tmp.resolve("DEMO"));
        var receipts = new ArrayList<Long>();
        SwingUtilities.invokeAndWait(() -> {
            var panel = new TopologyPanel();
            panel.onGraphChanged(() -> receipts.add(panel.firstMinuteTicket()));
            panel.load(root.resolve("DiamondSpringProcessor.graphml"), 17);
            panel.load(root.resolve("DiamondSpringProcessor.graphml"));
            panel.clearGraph();
        });
        assertEquals(List.of(17L, 0L, 0L), receipts,
                "the graph fact observes this load's receipt, never the previous operation or pathname");
    }
}
