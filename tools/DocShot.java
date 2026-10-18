package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.awt.Window;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * The documentation capture protocol between a Java capture program and {@code tools/capture-docs.py}.
 *
 * <p>The program sets a surface up, then calls {@link #window} (or {@link #withPopups}) and WAITS: it prints one line,
 * {@code SHOT <asset> <x>,<y>,<w>,<h>} or {@code SHOTALL <asset> <x>,<y>,<w>,<h>}, and blocks until the harness answers
 * {@code ok} on stdin. The harness photographs that window by its window id ({@code screencapture -l}) — for SHOTALL,
 * the window and every popup of this process, composited — so only the analyser's own pixels can reach the image.
 *
 * <p>Why not {@code java.awt.Robot}: without the screen-recording grant, Robot returns the DESKTOP behind the window,
 * silently, and the earlier dialog captures wrote the wallpaper (and once the lock-screen clock) into four assets that
 * the harness reported as captured (2026-10-17). A per-window capture either shows the window or fails visibly.
 *
 * <p><b>A real home, never a symlink</b> (M77-4, rule 1). The analyser resolves {@code user.home} to its real path, so a
 * home that is, or runs through, a symbolic link puts the link's TARGET — the account's scratch area, its name — into
 * dialogs and paths on screen (found by a walk, 2026-10-18). {@link #requireRealHome} refuses one; every shot calls it,
 * so no image is taken under such a home whatever the capture program forgot.
 */
public final class DocShot {

    private static final BufferedReader HARNESS =
            new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

    private DocShot() {
    }

    /**
     * Refuse a {@code user.home} that is, or runs through, a symbolic link (or does not exist): its real path is what the
     * analyser would show. Returns the home when it is its own real path.
     */
    public static Path requireRealHome(Path home) {
        if (home == null) throw new IllegalStateException("no user.home: refusing to capture");
        Path given = home.toAbsolutePath().normalize();
        Path real;
        try {
            real = given.toRealPath();
        } catch (IOException e) {
            throw new IllegalStateException("user.home " + given + " does not exist: refusing to capture", e);
        }
        if (!real.equals(given)) {
            throw new IllegalStateException("user.home " + given + " is a symbolic link or runs through one (it is "
                    + real + "): the analyser would show the real path, so refusing to capture — use a real, neutral "
                    + "directory");
        }
        return given;
    }

    /** {@link #requireRealHome(Path)} for this process's {@code user.home}. */
    public static Path requireRealHome() {
        String home = System.getProperty("user.home");
        return requireRealHome(home == null ? null : Path.of(home));
    }

    /** Photograph this window alone. */
    public static void window(Window w, String asset) throws Exception {
        ask("SHOT", w, asset);
    }

    /** Photograph this window with the popups now open on it (a context menu, a combo list). */
    public static void withPopups(Window w, String asset) throws Exception {
        ask("SHOTALL", w, asset);
    }

    private static void ask(String verb, Window w, String asset) throws Exception {
        requireRealHome();                               // every shot: no image under a symlinked home
        SwingUtilities.invokeAndWait(() -> {
            if (!verb.equals("SHOTALL")) w.toFront();   // raising the frame would close an open popup
        });
        Thread.sleep(700);                               // a deferred layout or repaint lands first
        Rectangle[] b = new Rectangle[1];
        SwingUtilities.invokeAndWait(() -> b[0] = w.getBounds());
        System.out.println(verb + " " + asset + " " + b[0].x + "," + b[0].y + "," + b[0].width + "," + b[0].height);
        System.out.flush();
        String answer = HARNESS.readLine();
        if (!"ok".equals(answer)) throw new IllegalStateException("the harness did not take " + asset + ": " + answer);
    }

    /** A note for the harness's log (never a capture). */
    public static void say(String text) {
        System.out.println("NOTE " + text);
        System.out.flush();
    }
}
