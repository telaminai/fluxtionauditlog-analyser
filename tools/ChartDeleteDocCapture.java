package telamin.fluxtion.audit.analyser.analyser.ui;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import java.awt.Window;
import java.util.concurrent.TimeUnit;

/**
 * Generated documentation capture of the Graph tab's delete confirmation ({@code chart-delete-confirmation.png}).
 *
 * <p>The words are the product's own, {@link GraphTabs#deleteQuestion}, shown through the same
 * {@code JOptionPane.showConfirmDialog} call {@code GraphTabs.askWhetherToDelete} makes (title, options and icon), so
 * the image changes when the question does. The one difference is the parent: the dialog is centred on the screen
 * instead of the Graph tab, which the image does not show. Run by {@code tools/capture-docs.py --chart-delete} under
 * the isolated capture home; the harness photographs the dialog by window id ({@link DocShot}).
 */
public final class ChartDeleteDocCapture {
    private ChartDeleteDocCapture() { }

    static final String TITLE = "Delete chart";

    public static void main(String[] args) throws Exception {
        FlatLightLaf.setup();
        SwingUtilities.invokeLater(() -> JOptionPane.showConfirmDialog(null,
                UiText.inert(GraphTabs.deleteQuestion("Delete me")),
                TITLE, JOptionPane.OK_CANCEL_OPTION, JOptionPane.WARNING_MESSAGE));
        Window dialog = awaitWindow(TITLE);
        DocShot.window(dialog, "chart-delete-confirmation.png");
        SwingUtilities.invokeAndWait(dialog::dispose);
        System.exit(0);
    }

    private static Window awaitWindow(String title) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
        while (System.nanoTime() < end) {
            for (Window window : Window.getWindows()) {
                if (window.isDisplayable() && window.isShowing()
                        && window instanceof javax.swing.JDialog d && title.equals(d.getTitle())) {
                    return window;
                }
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("the " + title + " dialog never appeared");
    }
}
