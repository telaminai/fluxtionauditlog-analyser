package telamin.fluxtion.audit.analyser.bundle;

import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Drives a recipient's DEMO build directly, through {@code src/test/resources/replay/live/LiveRecordingDriver.java},
 * compiled against it: a producer's live run, not the runner's replay (PR #70 review 3 and 6).
 */
final class LiveRecording {
    static final Path DRIVER = Path.of("src/test/resources/replay/live/LiveRecordingDriver.java");

    private LiveRecording() { }

    /** {what the replay writer recorded, the audit log} for {@code replay}'s inputs, and optionally an external breach. */
    static String[] run(Path tmp, Path build, String replay, boolean externalBreach) throws Exception {
        Path classes = Files.createDirectories(tmp.resolve("live-driver"));
        String cp = build + java.io.File.pathSeparator + System.getProperty("java.class.path");
        var javac = javax.tools.ToolProvider.getSystemJavaCompiler();
        var diag = new java.io.ByteArrayOutputStream();
        int rc = javac.run(null, diag, diag, List.of("-proc:none", "-nowarn", "-d", classes.toString(), "-cp", cp,
                DRIVER.toString()).toArray(String[]::new));
        if (rc != 0) throw new AssertionError("the driver compiles: " + diag);
        try (URLClassLoader l = new URLClassLoader(new URL[]{classes.toUri().toURL(), build.toUri().toURL()},
                LiveRecording.class.getClassLoader())) {
            Method m = l.loadClass("LiveRecordingDriver").getMethod("run", String.class, boolean.class);
            return (String[]) m.invoke(null, replay, externalBreach);
        }
    }
}
