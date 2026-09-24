import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.LauncherDiscoveryRequest;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;

/**
 * The fast mutation engine's test runner: run the named test methods in THIS (fresh) JVM and write one JSON
 * line per test, classified the way Surefire classifies them, so the harness can apply the same
 * named-assertion check it applies to Surefire XML.
 *
 * <p>Usage: {@code java GateLauncher <output.jsonl> <fqcn#method | fqcn>...}
 *
 * <p>Classification, matching Surefire: a throwable that is an {@link AssertionError} (which includes
 * opentest4j's AssertionFailedError) is a {@code failure}; any other throwable is an {@code error}; an aborted
 * test (a failed assumption) or a disabled one is {@code skipped}.
 */
public final class GateLauncher {

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        LauncherDiscoveryRequestBuilder request = LauncherDiscoveryRequestBuilder.request();
        // "pkg.Class#method" selects one method; a bare "pkg.Class" selects the whole class (the baseline)
        for (int i = 1; i < args.length; i++) {
            request.selectors(args[i].contains("#") ? selectMethod(args[i]) : selectClass(args[i]));
        }
        LauncherDiscoveryRequest built = request.build();
        List<String> lines = new ArrayList<>();
        Launcher launcher = LauncherFactory.create();
        launcher.execute(built, new TestExecutionListener() {
            @Override
            public void executionSkipped(TestIdentifier id, String reason) {
                if (id.isTest()) lines.add(line(id, "skipped", reason));
            }

            @Override
            public void executionFinished(TestIdentifier id, TestExecutionResult result) {
                if (!id.isTest()) return;
                Throwable t = result.getThrowable().orElse(null);
                String kind = switch (result.getStatus()) {
                    case SUCCESSFUL -> "passed";
                    case ABORTED -> "skipped";
                    case FAILED -> t instanceof AssertionError ? "failure" : "error";
                };
                lines.add(line(id, kind, t == null ? null : t.getClass().getName() + ": " + t.getMessage()));
            }
        });
        Files.write(out, lines, StandardCharsets.UTF_8);
        // Surefire ends its forked JVM with System.exit too. Frame tests leave AWT and Swing threads (the EDT,
        // timers) that are not daemons, and without this the JVM finished every test and then never exited.
        System.exit(0);
    }

    private static String line(TestIdentifier id, String kind, String message) {
        String cls = "", method = id.getDisplayName();
        if (id.getSource().orElse(null) instanceof MethodSource m) {
            cls = m.getClassName();
            method = m.getMethodName();
        }
        return "{\"class\":" + json(cls) + ",\"method\":" + json(method) + ",\"kind\":" + json(kind)
                + ",\"message\":" + json(message) + "}";
    }

    private static String json(String s) {
        if (s == null) return "null";
        StringBuilder b = new StringBuilder("\"");
        for (char c : s.toCharArray()) {
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c < 0x20) b.append(String.format("\\u%04x", (int) c));
                    else b.append(c);
                }
            }
        }
        return b.append('"').toString();
    }
}
