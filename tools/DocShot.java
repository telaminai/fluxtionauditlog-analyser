package telamin.fluxtion.audit.analyser.analyser.ui;

import javax.swing.SwingUtilities;
import java.awt.Rectangle;
import java.awt.Window;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

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
 */
public final class DocShot {

    private static final BufferedReader HARNESS =
            new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

    private DocShot() {
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
