package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.swing.SwingUtilities;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent review, M68.5 "the table is not suspended" (set 13, A). The request verbs are refused or labelled after
 * the file behind a log changed; the table on screen went on painting as if nothing had. It now carries the session's
 * file-identity verdict as a banner — rendered from the snapshot, so the table holds no verdict of its own.
 */
class LogTablePanelIdentityBannerTest {

    @Test
    @DisplayName("A: a changed or replaced file is stated on the table; an unchanged, verified or reopened one is not")
    void theBannerStatesOnlyAChange() {
        // witness: identityBannerText returning null
        assertNotNull(LogTablePanel.identityBannerText("UNVERIFIED", "reads are suspended until the log is reopened"));
        assertTrue(LogTablePanel.identityBannerText("REPLACEMENT", "a different file now has this path")
                .contains("a different file now has this path"));
        assertNull(LogTablePanel.identityBannerText(null, null), "nothing observed: nothing said");
        assertNull(LogTablePanel.identityBannerText("VERIFIED", "the same file"));
        assertNull(LogTablePanel.identityBannerText("REOPENED", "reopened because …"), "the reopened log is current");
    }

    @Test
    @DisplayName("A: the banner is shown and cleared on the panel itself")
    void theBannerIsOnThePanel() throws Exception {
        var shown = new AtomicReference<String>();
        var cleared = new AtomicReference<String>("not cleared");
        SwingUtilities.invokeAndWait(() -> {
            var panel = new LogTablePanel();
            assertNull(panel.identityNote(), "hidden until something is observed");
            panel.setIdentityNote("⚠ the file changed");
            shown.set(panel.identityNote());
            panel.setIdentityNote(null);
            cleared.set(panel.identityNote());
        });
        assertEquals("⚠ the file changed", shown.get());
        assertNull(cleared.get());
    }

    @Test
    @DisplayName("A: the frame renders the banner from the session snapshot, where it renders everything else")
    void theFrameRendersItFromTheSnapshot() throws Exception {
        String frame = Files.readString(Path.of("src/main/java/telamin/fluxtion/audit/analyser/analyser/ui/MainFrame.java"));
        int at = frame.indexOf("private void onSessionSnapshot(");
        String body = frame.substring(at, frame.indexOf("\n    }\n", at));
        assertTrue(body.contains("tablePanel.setIdentityNote(LogTablePanel.identityBannerText(next.logIdentity(), next.logIdentityReason()))"),
                "the table's banner comes from the snapshot: " + body);
    }
}
