package telamin.fluxtion.audit.analyser;

import telamin.fluxtion.audit.analyser.analyser.config.ConfigStore;
import telamin.fluxtion.audit.analyser.analyser.ui.AppImages;
import telamin.fluxtion.audit.analyser.analyser.ui.ExceptionHandling;
import telamin.fluxtion.audit.analyser.analyser.ui.MainFrame;
import telamin.fluxtion.audit.analyser.analyser.ui.SplashScreen;
import telamin.fluxtion.audit.analyser.analyser.ui.ThemeManager;

import java.awt.Taskbar;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import java.nio.file.Path;

/** Launches the Fluxtion Audit Log Analyser (Swing + FlatLaf). Optional arg: a log file to open. */
public class Main {

    public static void main(String[] args) {
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
}
