package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * M77-4 (CLAUDE.md rule 1, T20): the documentation captures refuse a {@code user.home} that is, or runs through, a
 * symbolic link. The analyser resolves the home to its real path, so a walk under a symlinked home showed the account's
 * scratch path (its name in it) in dialogs, where the sweep cannot read it (2026-10-18). {@code tools/DocShot.java} is
 * the one protocol every Java capture program takes its images through; it is compiled here from the tools directory,
 * as the harness compiles it, and its guard is called on a real directory, a link to it, and a path through a linked
 * parent. The Python harness's own guard is {@code tools/test_capture_home.py}.
 *
 * <p>Witness: make {@code requireRealHome} return the given path without comparing it with its real path — the link
 * case no longer throws.
 */
class CaptureHomeGuardTest {

    static Method guard(Path out) throws Exception {
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        assertNotNull(javac, "a JDK compiler");
        int rc = javac.run(null, null, null, "-d", out.toString(), Path.of("tools/DocShot.java").toString());
        assertEquals(0, rc, "tools/DocShot.java compiles as the harness compiles it");
        URLClassLoader loader = new URLClassLoader(new URL[]{out.toUri().toURL()}, ClassLoader.getPlatformClassLoader());
        Class<?> shot = Class.forName("telamin.fluxtion.audit.analyser.analyser.ui.DocShot", false, loader);
        return shot.getMethod("requireRealHome", Path.class);
    }

    static Throwable refusal(Method m, Path home) throws Exception {
        try {
            m.invoke(null, home);
            return null;
        } catch (InvocationTargetException e) {
            return e.getCause();
        }
    }

    @Test
    @DisplayName("★ a capture refuses a symlinked user.home, and one that runs through a link; a real home passes")
    void aSymlinkedHomeIsRefused(@TempDir Path tmp) throws Exception {
        Method m = guard(Files.createDirectories(tmp.resolve("classes")));
        Path base = tmp.toRealPath();                        // macOS: /var → /private/var, so start from the real one
        Path real = Files.createDirectories(base.resolve("real-home"));
        assertNull(refusal(m, real), "a real, neutral directory is accepted");
        assertEquals(real, m.invoke(null, real));

        Path link = Files.createSymbolicLink(base.resolve("linked-home"), real);
        Throwable t = refusal(m, link);
        assertInstanceOf(IllegalStateException.class, t, "★ the home itself is a link");
        assertTrue(t.getMessage().contains("symbolic link"), t.getMessage());

        Path linkedParent = Files.createSymbolicLink(base.resolve("linked-parent"), base);
        Path through = linkedParent.resolve("real-home");
        assertTrue(Files.isDirectory(through), "control: the path exists");
        assertInstanceOf(IllegalStateException.class, refusal(m, through), "★ a home through a linked parent");

        assertInstanceOf(IllegalStateException.class, refusal(m, base.resolve("missing")), "a home that is not there");
    }
}
