package telamin.fluxtion.audit.analyser.analyser.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * M74 UX (P3 leftover d): the M74 smoke harnesses under {@code docs/specs/evidence/m74-*-smoke/harness/} are evidence a
 * reviewer re-runs, and they drive the analyser's own classes. The P1b harness stopped compiling when warm start was cut
 * (its {@code claudeCodeWarmStart} setting and {@code WarmStart} class went away), and the P2b one when P3's rule R6 took
 * the words out of the snapshot; nothing noticed. This compiles every one against the current analyser, so the next cut
 * fails here. The P2b and P3 harnesses use the P1b harness's helpers, so they are compiled with it.
 *
 * <p>Witness: put back {@code config().claudeCodeWarmStart = true;} in {@code M74P1bSmoke.java} — the P1b group fails.
 */
class M74HarnessesCompileTest {

    static final Path EVIDENCE = Path.of("docs/specs/evidence");

    @Test
    @DisplayName("★ every M74 smoke harness compiles against the current analyser")
    void everyHarnessCompiles(@TempDir Path out) throws Exception {
        List<Path> p1b = javaIn(EVIDENCE.resolve("m74-p1b-smoke/harness"));
        assertTrue(!p1b.isEmpty(), "the P1b harness is where this test expects it");
        List<List<Path>> groups = new ArrayList<>();
        groups.add(javaIn(EVIDENCE.resolve("m74-p1a-smoke/harness")));
        groups.add(p1b);
        for (String dependent : List.of("m74-p2b-smoke", "m74-p3-smoke")) {
            List<Path> g = new ArrayList<>(p1b);
            g.addAll(javaIn(EVIDENCE.resolve(dependent).resolve("harness")));
            groups.add(g);
        }
        int n = 0;
        try (Stream<Path> dirs = Files.list(EVIDENCE)) {
            for (Path d : dirs.filter(p -> p.getFileName().toString().matches("m74-.*-smoke")).toList()) {
                if (Files.isDirectory(d.resolve("harness"))) n++;
            }
        }
        assertEquals(4, n, "a new M74 harness folder is added to a group here");
        int i = 0;
        for (List<Path> group : groups) {
            if (group.isEmpty()) continue;
            Path into = Files.createDirectories(out.resolve("g" + i++));
            JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
            List<String> args = new ArrayList<>(List.of("-proc:none", "-nowarn", "-Xlint:none", "-d", into.toString(),
                    "-cp", System.getProperty("java.class.path")));
            for (Path p : group) args.add(p.toString());
            ByteArrayOutputStream diag = new ByteArrayOutputStream();
            int rc = javac.run(null, diag, diag, args.toArray(String[]::new));
            assertEquals(0, rc, group.get(group.size() - 1).getParent() + " compiles: "
                    + diag.toString(StandardCharsets.UTF_8));
        }
    }

    private static List<Path> javaIn(Path dir) throws Exception {
        if (!Files.isDirectory(dir)) return List.of();
        try (Stream<Path> s = Files.list(dir)) {
            return s.filter(p -> p.toString().endsWith(".java")).sorted().toList();
        }
    }
}
