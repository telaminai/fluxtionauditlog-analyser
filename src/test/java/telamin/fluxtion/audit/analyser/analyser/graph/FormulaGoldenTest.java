package telamin.fluxtion.audit.analyser.analyser.graph;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.HeapLogStore;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.DynamicTest.dynamicTest;

/**
 * Golden-fixture suite for the formula engine — the {@code Expr → Series} scan path
 * ({@link SeriesExtractor#extractExpr}).
 *
 * <p><b>Why this exists.</b> The formula engine is now a small language — arithmetic, comparisons,
 * conditionals, rolling and time windows, and their composition — and its failure mode is the dangerous
 * one for a forensic tool: a plausible but wrong number, silently plotted. {@code rate()} shipped
 * <em>systematically biased low</em> (commit c3094ea) exactly on this path. Hand-written spot checks catch
 * the cases someone thought to write; a growing corpus of hand-derived expected series guards the whole
 * semantic surface against regressions and interaction bugs.
 *
 * <p><b>The one rule that makes this worth doing.</b> Every fixture's expected points are
 * <b>hand-derived from the intended semantics, with the derivation recorded in the {@code why:} line —
 * never a snapshot of current engine output.</b> Snapshotting would pin whatever bug is live as the
 * "expected" answer and give false confidence. A failing fixture is therefore a real signal: either the
 * engine regressed, or the stated semantics were wrong — and the second is resolved by understanding and
 * correcting the {@code why}, not by pasting the observed output.
 *
 * <p><b>Growing the corpus needs no code.</b> Drop a {@code .golden} file in
 * {@code src/test/resources/formula-golden/}; it becomes its own dynamic test. Format:
 * <pre>
 *   name:  &lt;id&gt;
 *   why:   &lt;one line deriving the expected points&gt;
 *   expr:  &lt;formula&gt;
 *   keys:  nodeA.price, nodeB.price
 *   resolve: LOCF | STRICT          (default LOCF)
 *   acrossAllTime: false            (default false)
 *   --- LOG ---
 *   &lt;raw audit-log YAML, parsed by the real HeapLogStore&gt;
 *   --- EXPECT ---
 *   &lt;logTime&gt; =&gt; &lt;value&gt;
 *   ...
 * </pre>
 */
class FormulaGoldenTest {

    private static final Path DIR = Path.of("src/test/resources/formula-golden");
    private static final double EPS = 1e-9;

    @TestFactory
    Stream<DynamicTest> goldenFixtures() throws IOException {
        assertTrue(Files.isDirectory(DIR), "fixture dir missing: " + DIR.toAbsolutePath());
        List<Path> files;
        try (Stream<Path> s = Files.list(DIR)) {
            files = s.filter(p -> p.toString().endsWith(".golden")).sorted().toList();
        }
        assertFalse(files.isEmpty(), "no .golden fixtures found in " + DIR);
        return files.stream().map(p -> dynamicTest(p.getFileName().toString(), () -> run(p)));
    }

    private void run(Path file) throws IOException {
        Fixture f = Fixture.parse(Files.readString(file));
        HeapLogStore store = new HeapLogStore(f.log);

        // G1 — a fixture that expects nothing must not pass because nothing WORKED. An unparsed log
        // section or an instanceId that appears nowhere also yields an empty series, and without these
        // two guards such a fixture goes green while testing nothing.
        assertFalse(store.size() == 0, "fixture '" + f.name + "': the LOG section parsed to zero records");
        assertFalse(f.expectX.isEmpty() && !f.expectEmpty,
                "fixture '" + f.name + "': empty EXPECT must be declared with 'expectEmpty: true', so "
                        + "\"this formula legitimately plots nothing\" is stated rather than indistinguishable "
                        + "from a broken fixture");

        Series s = SeriesExtractor.extractExpr(store, new FilterState(),
                Expr.parse(f.expr, f.keys()), f.expr, f.acrossAllTime, f.resolve);

        String ctx = "\n  fixture: " + f.name + "\n  why:     " + f.why + "\n  expr:    " + f.expr
                + "\n  got:     " + dump(s) + "\n  want:    " + want(f) + "\n";
        assertEquals(f.expectX.size(), s.size(), ctx + "→ wrong number of points");
        for (int i = 0; i < s.size(); i++) {
            assertEquals(f.expectX.get(i).longValue(), s.x(i), ctx + "→ x[" + i + "] (time) differs");
            assertEquals(f.expectY.get(i), s.y(i), EPS, ctx + "→ y[" + i + "] (value at t=" + s.x(i) + ") differs");
        }

        assertScanAgrees(store, f, s, ctx);
    }

    /**
     * G2 — the same formula must mean the same thing on the chart and in the {@code series} verb.
     *
     * <p>M28's acceptance #1 is "one vocabulary, two surfaces", but the two surfaces do not share one
     * code path: {@link SeriesScan} carries its own STRICT and LOCF arms, structurally parallel to
     * {@link SeriesExtractor}'s. Duplicated logic that is supposed to agree, with nothing asserting that
     * it does — so a carry rule corrected in one arm and not the other would be invisible, and would
     * surface as the chart and the verb quoting different numbers for one expression. Both plausible.
     *
     * <p>Every fixture therefore checks both paths. {@code scan} reports statistics rather than points,
     * which is enough: point count, min, max and mean cannot all agree by accident across a divergence.
     */
    private void assertScanAgrees(HeapLogStore store, Fixture f, Series expected, String ctx) {
        Map<String, Object> scan = SeriesScan.scan(store, Map.of(
                "expr", f.expr,
                "resolve", f.resolve.name()));

        int points = ((Number) scan.get("points")).intValue();
        assertEquals(expected.size(), points,
                ctx + "→ SeriesScan disagrees with SeriesExtractor on the POINT COUNT (G2)");
        if (expected.size() == 0) return;

        @SuppressWarnings("unchecked")
        Map<String, Object> stats = (Map<String, Object>) scan.get("stats");
        assertNotNull(stats, ctx + "→ scan reported points but no stats");

        double min = Double.POSITIVE_INFINITY, max = Double.NEGATIVE_INFINITY, sum = 0;
        for (int i = 0; i < expected.size(); i++) {
            min = Math.min(min, expected.y(i));
            max = Math.max(max, expected.y(i));
            sum += expected.y(i);
        }
        assertEquals(min, ((Number) stats.get("min")).doubleValue(), EPS, ctx + "→ scan/extract MIN differ (G2)");
        assertEquals(max, ((Number) stats.get("max")).doubleValue(), EPS, ctx + "→ scan/extract MAX differ (G2)");
        assertEquals(sum / expected.size(), ((Number) stats.get("mean")).doubleValue(), EPS,
                ctx + "→ scan/extract MEAN differ (G2)");
        assertEquals(expected.y(0), ((Number) stats.get("first")).doubleValue(), EPS,
                ctx + "→ scan/extract FIRST differ (G2)");
        assertEquals(expected.y(expected.size() - 1), ((Number) stats.get("last")).doubleValue(), EPS,
                ctx + "→ scan/extract LAST differ (G2)");
    }

    private static String dump(Series s) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < s.size(); i++) {
            b.append(i == 0 ? "" : ", ").append(s.x(i)).append("=>").append(s.y(i));
        }
        return b.append("]").toString();
    }

    private static String want(Fixture f) {
        StringBuilder b = new StringBuilder("[");
        for (int i = 0; i < f.expectX.size(); i++) {
            b.append(i == 0 ? "" : ", ").append(f.expectX.get(i)).append("=>").append(f.expectY.get(i));
        }
        return b.append("]").toString();
    }

    /** A parsed fixture. Line-oriented on purpose, so a failure points straight at a file. */
    private record Fixture(String name, String why, String expr, String keysRaw,
                           SeriesExtractor.Resolve resolve, boolean acrossAllTime, boolean expectEmpty,
                           String log, List<Long> expectX, List<Double> expectY) {

        Set<GraphKey> keys() {
            Set<GraphKey> ks = new LinkedHashSet<>();
            for (String k : keysRaw.split(",")) {
                String t = k.trim();
                if (t.isEmpty()) continue;
                int dot = t.lastIndexOf('.');
                assertTrue(dot > 0, "key '" + t + "' must be instanceId.key");
                ks.add(new GraphKey(t.substring(0, dot), t.substring(dot + 1)));
            }
            return ks;
        }

        static Fixture parse(String text) {
            String[] logSplit = text.split("(?m)^--- LOG ---\\s*$", 2);
            assertEquals(2, logSplit.length, "fixture missing a '--- LOG ---' section");
            String[] expSplit = logSplit[1].split("(?m)^--- EXPECT ---\\s*$", 2);
            assertEquals(2, expSplit.length, "fixture missing a '--- EXPECT ---' section");

            Map<String, String> meta = new HashMap<>();
            for (String line : logSplit[0].split("\n")) {
                String l = line.trim();
                if (l.isEmpty() || l.startsWith("#")) continue;
                int c = l.indexOf(':');
                assertTrue(c > 0, "metadata line is not 'key: value': " + l);
                meta.put(l.substring(0, c).trim(), l.substring(c + 1).trim());
            }
            List<Long> xs = new ArrayList<>();
            List<Double> ys = new ArrayList<>();
            for (String line : expSplit[1].split("\n")) {
                String l = line.trim();
                if (l.isEmpty() || l.startsWith("#")) continue;
                String[] kv = l.split("=>", 2);
                assertEquals(2, kv.length, "expect line is not '<time> => <value>': " + l);
                xs.add(Long.parseLong(kv[0].trim()));
                ys.add(Double.parseDouble(kv[1].trim()));
            }
            return new Fixture(
                    req(meta, "name"), req(meta, "why"), req(meta, "expr"),
                    meta.getOrDefault("keys", ""),
                    SeriesExtractor.Resolve.valueOf(meta.getOrDefault("resolve", "LOCF")),
                    Boolean.parseBoolean(meta.getOrDefault("acrossAllTime", "false")),
                    Boolean.parseBoolean(meta.getOrDefault("expectEmpty", "false")),
                    expSplit[0], xs, ys);
        }

        private static String req(Map<String, String> m, String k) {
            String v = m.get(k);
            assertNotNull(v, "fixture missing required metadata '" + k + "'");
            return v;
        }
    }
}
