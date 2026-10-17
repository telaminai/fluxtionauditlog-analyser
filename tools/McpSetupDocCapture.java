import com.formdev.flatlaf.FlatLightLaf;
import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.ui.McpSetupDialog;

import javax.swing.JButton;
import javax.swing.SwingUtilities;
import java.awt.Component;
import java.awt.Container;
import java.awt.Window;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/**
 * Captures M42's Swing-only disclosures without macOS Accessibility permission.
 *
 * <p>Each image is taken by the harness, by window id ({@code DocShot}): Robot photographed the desktop where the
 * screen-recording grant was missing. The Python docs harness supplies an isolated {@code user.home}, a neutral fake JBang launcher and
 * a neutral fake Claude executable before starting this class. It never accepts a registration: the
 * second image is the confirmation the person sees before a CLI would be invoked.
 */
public final class McpSetupDocCapture {

    private McpSetupDocCapture() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) throw new IllegalArgumentException("need the documentation asset directory");
        Path assets = Path.of(args[0]);
        Files.createDirectories(assets);
        FlatLightLaf.setup();

        AppConfig config = new AppConfig();
        config.assistantActionsRest = true;
        show(config, McpSetupDialog.Target.GENERIC);
        Window generic = awaitWindow("Connect an AI client");
        telamin.fluxtion.audit.analyser.analyser.ui.DocShot.window(generic, "mcp-generic-setup.png");
        dispose(generic);

        show(config, McpSetupDialog.Target.CLAUDE_CODE);
        Window setup = awaitWindow("Connect an AI client");
        JButton register = awaitButton(setup, "Register with Claude Code…");
        SwingUtilities.invokeLater(register::doClick);
        Window confirmation = awaitWindow("Confirm Claude Code registration");
        telamin.fluxtion.audit.analyser.analyser.ui.DocShot.window(confirmation, "mcp-claude-code-confirm.png");
        dispose(confirmation);
        dispose(setup);
    }

    private static void show(AppConfig config, McpSetupDialog.Target target) throws Exception {
        SwingUtilities.invokeAndWait(() -> McpSetupDialog.show(null, config, () -> { }, target, false));
    }

    private static Window awaitWindow(String title) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < end) {
            for (Window window : Window.getWindows()) {
                if (window.isDisplayable() && window.isShowing() && title.equals(window.getName())) return window;
                if (window.isDisplayable() && window.isShowing() && window instanceof javax.swing.JDialog dialog
                        && title.equals(dialog.getTitle())) return window;
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("did not show window: " + title);
    }

    private static JButton awaitButton(Window window, String text) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < end) {
            JButton found = findButton(window, text);
            if (found != null && found.isShowing() && found.isEnabled()) return found;
            Thread.sleep(50);
        }
        throw new IllegalStateException("did not show button: " + text);
    }

    private static JButton findButton(Component component, String text) {
        if (component instanceof JButton button && text.equals(button.getText())) return button;
        if (component instanceof Container container) {
            for (Component child : container.getComponents()) {
                JButton found = findButton(child, text);
                if (found != null) return found;
            }
        }
        return null;
    }

    private static void dispose(Window window) throws Exception {
        SwingUtilities.invokeAndWait(window::dispose);
    }
}
