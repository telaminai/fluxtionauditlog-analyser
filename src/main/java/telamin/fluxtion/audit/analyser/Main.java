package telamin.fluxtion.audit.analyser;

import telamin.fluxtion.audit.analyser.analyser.config.ConfigStore;
import telamin.fluxtion.audit.analyser.analyser.ui.AppImages;
import telamin.fluxtion.audit.analyser.analyser.ui.ExceptionHandling;
import telamin.fluxtion.audit.analyser.analyser.ui.MainFrame;
import telamin.fluxtion.audit.analyser.analyser.ui.ReleaseNotes;
import telamin.fluxtion.audit.analyser.analyser.ui.SplashScreen;
import telamin.fluxtion.audit.analyser.analyser.ui.ThemeManager;

import java.awt.Taskbar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.nio.file.Path;

/**
 * Launches the Fluxtion Audit Log Analyser (Swing + FlatLaf). Optional arg: a log file to open.
 *
 * <p>{@code --mcp} instead runs the headless MCP stdio bridge (M13.2) — it short-circuits before any UI
 * bootstrap, so no theme, no taskbar icon, no frame.
 */
public class Main {

    /** Launch flag for the MCP stdio bridge — an MCP client runs {@code java -jar analyser.jar --mcp}. */
    static final String MCP_FLAG = "--mcp";

    public static void main(String[] args) {
        // BEFORE anything else: the bridge is headless and must touch no Swing/AWT class, so this has to
        // come ahead of the theme/taskbar/frame bootstrap below (spec-assistant-actions-mcp §9)
        for (String arg : args) {
            if (MCP_FLAG.equals(arg)) {
                telamin.fluxtion.audit.analyser.analyser.mcp.McpBridge.main(args);
                return;
            }
        }
        if (args.length > 0 && isHelpFlag(args[0])) {
            System.out.println(usage());
            return;
        }
        // An unrecognised flag used to fall through and be opened as a *log file*, so running an older
        // build with `--mcp` silently launched the GUI trying to load a file called "--mcp". Fail loudly.
        if (args.length > 0 && looksLikeFlag(args[0])) {
            System.err.println("unknown option: " + args[0] + System.lineSeparator() + System.lineSeparator() + usage());
            System.exit(2);
        }

        ThemeManager.apply(new ConfigStore().load().theme);   // FlatLaf theme before any UI is built
        // custom Dock/taskbar icon (replaces the default Java "Duke")
        try {
            if (Taskbar.isTaskbarSupported()) {
                Taskbar taskbar = Taskbar.getTaskbar();
                if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                    taskbar.setIconImage(AppImages.icon(256));
                }
            }
        } catch (Throwable ignore) {
            // taskbar icon is best-effort
        }

        SwingUtilities.invokeLater(() -> {
            ExceptionHandling.install();
            SplashScreen splash = new SplashScreen();
            splash.showSplash();

            MainFrame frame = new MainFrame();
            frame.setVisible(true);
            String toOpen = args.length > 0 ? args[0] : frame.config().logFile;
            if (toOpen != null && !toOpen.isBlank()) {
                frame.openFile(Path.of(toOpen));
            }

            // keep the splash visible briefly, then dismiss; on a first run (no config file yet)
            // open Settings so the user can configure source roots / LLM before anything else
            Timer t = new Timer(700, e -> {
                splash.close();
                frame.showFirstRunSettingsIfNeeded();
                frame.maybeShowWhatsNew();
            });
            t.setRepeats(false);
            t.start();
        });
    }

    static boolean isHelpFlag(String arg) {
        return "--help".equals(arg) || "-h".equals(arg);
    }

    /**
     * A leading {@code -} means the user meant an option, not a log file. Kept deliberately simple: a
     * real log path starting with a dash is vanishingly rare next to the confusion of having a typo'd
     * flag opened as a file.
     */
    static boolean looksLikeFlag(String arg) {
        return arg.startsWith("-") && arg.length() > 1;
    }

    static String usage() {
        return """
                Fluxtion Audit Log Analyser %s

                Usage:
                  analyser [log-file]   open the desktop app, optionally on a log
                  analyser --mcp        run as an MCP server on stdio, for an MCP client to launch
                                        (needs the app running separately with the REST transport on)
                  analyser --help       show this message
                """.formatted(ReleaseNotes.version());
    }
}
