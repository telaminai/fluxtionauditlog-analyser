package telamin.fluxtion.audit.analyser;

import telamin.fluxtion.audit.analyser.analyser.config.ConfigStore;
import telamin.fluxtion.audit.analyser.analyser.ui.AppImages;
import telamin.fluxtion.audit.analyser.analyser.ui.ExceptionHandling;
import telamin.fluxtion.audit.analyser.analyser.ui.MainFrame;
import telamin.fluxtion.audit.analyser.analyser.core.ReleaseNotes;
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

    /**
     * Start with the REST transport ON and no first-run dialog (M19.7). A process asked for this
     * launch — an agent on a fresh machine — and a process cannot answer Settings. Read by
     * {@code MainFrame} as the system property {@value #REST_PROPERTY}.
     */
    static final String REST_FLAG = "--rest";
    public static final String REST_PROPERTY = "analyser.rest";

    /** Evidence bundle v1 (spec-evidence-bundle-packaging.md r2 §3.3): headless, before any UI, like {@code --mcp}. */
    static final java.util.Set<String> BUNDLE_FLAGS = java.util.Set.of("--verify", "--unpack");
    /** Removed in the convergence: said so, rather than launching the app with the flag taken for a log path. */
    static final java.util.Set<String> RETIRED_BUNDLE_FLAGS = java.util.Set.of("--pack", "--bundle-profile");

    public static void main(String[] args) {
        if (args.length > 0 && RETIRED_BUNDLE_FLAGS.contains(args[0])) {
            System.exit(bundle(args, System.out, System.err));
        }
        if (args.length > 0 && BUNDLE_FLAGS.contains(args[0])) {
            System.exit(bundle(args, System.out, System.err));
        }
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
        // M19.7/M19.9: decide without touching Swing or process state, then apply the launch mode. The
        // pure parser has a headless regression test for strip-rest / reject-unknown / retain-log.
        DesktopArgs parsed = parseDesktopArgs(args);
        if (parsed.rest()) System.setProperty(REST_PROPERTY, "true");
        final String[] fileArgs = parsed.remaining().toArray(new String[0]);
        // An unrecognised flag used to fall through and be opened as a *log file*, so running an older
        // build with `--mcp` silently launched the GUI trying to load a file called "--mcp". Fail loudly.
        if (fileArgs.length > 0 && looksLikeFlag(fileArgs[0])) {
            System.err.println("unknown option: " + fileArgs[0] + System.lineSeparator() + System.lineSeparator() + usage());
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
            // A command-line path is explicit. Remembered global paths carry no project association.
            if (fileArgs.length > 0 && !fileArgs[0].isBlank()) {
                frame.openFile(Path.of(fileArgs[0]), telamin.fluxtion.audit.analyser.analyser.ui.OpenRequest.atStartup(false));
            }
            frame.offerSessionRecovery();

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

    /**
     * {@code --verify <bundle.fexp>}, {@code --unpack <bundle.fexp> [--into <dir>]}: the RECIPIENT's half. A bundle is
     * written by the running analyser ({@code report {bundle}}), because every refusal is a fact only the live session
     * holds; so there is no {@code --pack} (convergence, 2026-09-28).
     * Returns the exit code: 0 ok, 1 refused, 2 usage. Everything it prints states the limits, and nothing it prints
     * says or implies that the sender is authenticated (D-3).
     */
    static int bundle(String[] args, java.io.PrintStream out, java.io.PrintStream err) {
        if (RETIRED_BUNDLE_FLAGS.contains(args[0])) {
            err.println(args[0] + " was removed: the running analyser writes evidence bundles, because only the live "
                    + "session can refuse an incoherent capture. Ask it with report {bundle: {path}} on the action socket.");
            return 2;
        }
        try {
            switch (args[0]) {
                case "--verify" -> {
                    if (args.length != 2) { err.println("usage: --verify <bundle.fexp>"); return 2; }
                    var v = telamin.fluxtion.audit.analyser.bundle.EvidenceBundle.verify(Path.of(args[1]));
                    return report(v, out, err);
                }
                default -> {
                    Path into = Path.of(System.getProperty("user.home"), ".fluxtion-analyser", "bundles");
                    if (args.length == 4 && "--into".equals(args[2])) into = Path.of(args[3]);
                    else if (args.length != 2) { err.println("usage: --unpack <bundle.fexp> [--into <dir>]"); return 2; }
                    var u = telamin.fluxtion.audit.analyser.bundle.EvidenceBundle.unpack(Path.of(args[1]), into);
                    int code = report(u.verification(), out, err);
                    if (code == 0) out.println("working copy: " + u.workingCopy() + "  (the received bundle is unchanged)");
                    return code;
                }
            }
        } catch (java.io.IOException e) {
            err.println("REFUSED: " + e.getMessage());
            return 1;
        }
    }

    private static int report(telamin.fluxtion.audit.analyser.bundle.EvidenceBundle.Verification v,
                              java.io.PrintStream out, java.io.PrintStream err) {
        if (v.identity() != null) out.println("identity: " + v.identity());
        if (!v.ok()) {
            err.println("REFUSED: " + v.refusal());
            return 1;
        }
        out.println("verified: " + v.members().size() + " members, each matching the manifest's sha256 and size");
        if (v.excerpt() != null) out.println("excerpt: the log is records " + whole(v.excerpt().get("firstRecord")) + ".."
                + whole(v.excerpt().get("lastRecord")) + " of " + whole(v.excerpt().get("sourceRecords")) + ", not the whole log"
                + (Boolean.TRUE.equals(v.excerpt().get("readSoFar"))
                        ? " (it was still growing when captured: these are the records read so far)" : ""));
        if (v.replay() != null) out.println("replay: " + v.replay().get("member") + ", the run's "
                + whole(v.replay().get("records")) + " recorded inputs"
                + (v.replay().get("serviceCalls") instanceof Number n && n.longValue() > 0
                        ? "; the log holds " + n.longValue() + " exported-service call(s) the replay does not carry, so a"
                          + " replay diverges from the first cycle that depends on one"
                        : ""));
        limits(v, out);
        return 0;
    }

    /** A manifest count, as the integer it is: JSON numbers parse as doubles, and "4.0" is not a record index. */
    private static String whole(Object n) {
        return n instanceof Number x ? Long.toString(x.longValue()) : String.valueOf(n);
    }

    private static void limits(telamin.fluxtion.audit.analyser.bundle.EvidenceBundle.Verification v, java.io.PrintStream out) {
        for (String l : telamin.fluxtion.audit.analyser.bundle.EvidenceBundle.limits(v)) out.println("limit: " + l);
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

    /** The desktop-only launch decision. MCP/help short-circuit before this in {@link #main}. */
    record DesktopArgs(boolean rest, java.util.List<String> remaining) {
        DesktopArgs {
            remaining = java.util.List.copyOf(remaining);
        }
    }

    static DesktopArgs parseDesktopArgs(String[] args) {
        boolean rest = false;
        java.util.List<String> remaining = new java.util.ArrayList<>();
        for (String arg : args == null ? new String[0] : args) {
            if (REST_FLAG.equals(arg)) rest = true;
            else remaining.add(arg);
        }
        return new DesktopArgs(rest, remaining);
    }

    static String usage() {
        return """
                Fluxtion Audit Log Analyser %s

                Usage:
                  analyser [log-file]   open the desktop app, optionally on a log
                  analyser --mcp        run as an MCP server on stdio, for an MCP client to launch
                                        (needs the app running separately with the REST transport on)
                  analyser --rest [log] open the desktop app with the REST transport ON and no first-run
                                        dialog — for an agent starting the analyser on a fresh machine.
                                        The setting persists (Settings ▸ Assistant) and stdout says so.
                  analyser --verify <bundle.fexp>
                                        check every member against the manifest; prints the bundle's
                                        identity. It does NOT authenticate the sender (unsigned)
                  analyser --unpack <bundle.fexp> [--into <dir>]
                                        verify, then extract into a fresh working copy; nothing is
                                        extracted if verification fails
                  analyser --help       show this message
                """.formatted(ReleaseNotes.version());
    }
}
