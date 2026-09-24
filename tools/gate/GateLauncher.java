import org.junit.platform.engine.DiscoverySelector;
import org.junit.platform.engine.TestExecutionResult;
import org.junit.platform.engine.support.descriptor.ClassSource;
import org.junit.platform.engine.support.descriptor.MethodSource;
import org.junit.platform.launcher.Launcher;
import org.junit.platform.launcher.TestExecutionListener;
import org.junit.platform.launcher.TestIdentifier;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.platform.engine.discovery.DiscoverySelectors.selectClass;
import static org.junit.platform.engine.discovery.DiscoverySelectors.selectMethod;

/**
 * The fast mutation engine's test runner: run the named tests in THIS (fresh) JVM and write one JSON line per
 * result, classified the way Surefire classifies them, so the harness can apply the same named-assertion check
 * it applies to Surefire XML.
 *
 * <p>Usage: {@code java GateLauncher <output.jsonl> <fqcn#method | fqcn>...}
 *
 * <p>Classification, matching Surefire: a throwable that is an {@link AssertionError} (which includes
 * opentest4j's AssertionFailedError) is a {@code failure}; any other throwable is an {@code error}; an aborted
 * test (a failed assumption) or a disabled one is {@code skipped}.
 *
 * <p>A CONTAINER's result is reported too, with a null method: a class whose {@code @AfterAll} or
 * {@code @BeforeAll} throws, or an engine that fails discovery. Surefire counts those as errors and fails the
 * run; dropping them let a broken class pass here while Maven rejected it (PR #18 review, finding 1).
 */
public final class GateLauncher {

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args[0]);
        List<String> lines = new ArrayList<>();
        LauncherDiscoveryRequestBuilder request = LauncherDiscoveryRequestBuilder.request();
        for (int i = 1; i < args.length; i++) {
            try {
                request.selectors(selector(args[i]));
            } catch (Exception unresolvable) {
                // a method that cannot be found or is ambiguous is an error in the run, never a quiet zero
                String[] parts = args[i].split("#", 2);
                lines.add(row(parts[0], parts.length > 1 ? parts[1] : null, "error",
                        "selector " + args[i] + ": " + unresolvable));
            }
        }
        Launcher launcher = LauncherFactory.create();
        launcher.execute(request.build(), new TestExecutionListener() {
            @Override
            public void executionSkipped(TestIdentifier id, String reason) {
                lines.add(line(id, "skipped", reason));
            }

            @Override
            public void executionFinished(TestIdentifier id, TestExecutionResult result) {
                Throwable t = result.getThrowable().orElse(null);
                String kind = switch (result.getStatus()) {
                    case SUCCESSFUL -> "passed";
                    case ABORTED -> "skipped";
                    case FAILED -> t instanceof AssertionError ? "failure" : "error";
                };
                // a container that simply finished is not a result; one that failed or aborted is
                if (!id.isTest() && kind.equals("passed")) return;
                lines.add(line(id, kind, t == null ? null : t.getClass().getName() + ": " + t.getMessage()));
            }
        });
        Files.write(out, lines, StandardCharsets.UTF_8);
        // Surefire ends its forked JVM with System.exit too. Frame tests leave AWT and Swing threads (the EDT,
        // timers) that are not daemons, and without this the JVM finished every test and then never exited.
        System.exit(0);
    }

    /**
     * "pkg.Class" selects the class. "pkg.Class#method" selects that method by NAME, whatever its parameters:
     * JUnit's string form means a no-argument method, so a parameter-injected test such as
     * {@code parameter(@TempDir Path)} was never discovered (PR #18 review, finding 7). Overloads are refused
     * rather than guessed.
     */
    private static DiscoverySelector selector(String arg) throws Exception {
        if (!arg.contains("#")) return selectClass(arg);
        String[] parts = arg.split("#", 2);
        if (parts[1].contains("(")) return selectMethod(arg);
        Class<?> type = Class.forName(parts[0], false, Thread.currentThread().getContextClassLoader());
        List<Method> matches = new ArrayList<>();
        for (Class<?> c = type; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(parts[1]) && !m.isSynthetic()) matches.add(m);
            }
            if (!matches.isEmpty()) break;          // the nearest declaration wins, as it would in Java
        }
        if (matches.size() != 1) {
            throw new IllegalArgumentException(matches.isEmpty() ? "no such method"
                    : matches.size() + " overloads; name the parameter types");
        }
        return selectMethod(type, matches.get(0));
    }

    private static String line(TestIdentifier id, String kind, String message) {
        Object source = id.getSource().orElse(null);
        if (source instanceof MethodSource m && id.isTest()) return row(m.getClassName(), m.getMethodName(), kind, message);
        if (source instanceof ClassSource c) return row(c.getClassName(), null, kind, message);
        return row("", null, kind, id.getDisplayName() + (message == null ? "" : ": " + message));
    }

    private static String row(String cls, String method, String kind, String message) {
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
