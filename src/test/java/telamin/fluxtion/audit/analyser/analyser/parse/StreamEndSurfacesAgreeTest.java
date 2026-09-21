package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.spi.SpiLogStore;
import telamin.fluxtion.audit.analyser.analyser.spi.YamlAuditReader;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The same bad file, through every store, on both surfaces — the guard against a sixth instance.
 *
 * <p>One defect was found and fixed five times across three review rounds: a number belonging to one
 * scope presented as another's, or present on one surface and absent from the other. Each fix was local
 * to where the defect was spotted. Round four found two more and said the plain thing: the fix was
 * instance-shaped, and a sixth could arrive by simple omission.
 *
 * <p>The structural repair is {@link StreamEndReport}, one renderer for both surfaces. This is the test
 * that makes the repair checkable rather than merely intended. It opens one file four ways — heap,
 * memory-mapped, through a reader plugin, and as a one-member rolled set — and asserts that the state
 * and the numbers agree, and that neither surface is silent while the other speaks.
 *
 * <p>The SPI row is the one that was failing: it reported a verdict to an agent and nothing to a person,
 * and no test noticed because no test compared them.
 */
class StreamEndSurfacesAgreeTest {

    private static final String REC =
            "eventLogRecord:\n  logTime: %d\n  event: Tick\n  nodeLogs:\n    - book: { mid: 1.0}\n";

    private static String file(int records, int declared) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < records; i++) sb.append("---\n").append(String.format(REC, 1000 + i));
        return sb.append("---\neventLogRecord:\n  streamEnd: normal\n  streamEndRecords: ")
                .append(declared).append("\n---\n").toString();
    }

    /** Every way the analyser can open one file. */
    private static List<LogStore> allStores(Path p) throws IOException {
        return List.of(
                HeapLogStore.fromFile(p),
                new MappedLogStore(p),
                SpiLogStore.open(new YamlAuditReader(), p),
                RolledLogStore.open(List.of(p), 512));
    }

    private static String name(LogStore s) {
        return s.getClass().getSimpleName();
    }

    @Test
    void everyStoreReportsTheSameVerdictToAPersonAndToAnAgent() throws IOException, Exception {
        Path p = Files.createTempFile("agree", ".yaml");
        Files.writeString(p, file(3, 9), StandardCharsets.UTF_8);   // declares 9, holds 3

        for (LogStore s : allStores(p)) {
            try (s) {
                String who = name(s);
                StreamEnd end = s.streamEnd();
                Map<String, Object> facts = StreamEndReport.facts(end, s.size());
                // Everything a person is shown: the reader's findings AND the container's completeness
                // statements. Round five split these so a whole set stops being called "source damage";
                // this test reads the union, because the union is what reaches the tooltip.
                List<String> said = new java.util.ArrayList<>(s.sourceDiagnostics());
                said.addAll(s.completenessDiagnostics());
                assertTrue(s.sourceDiagnostics().stream().noneMatch(d -> d.contains("declares")),
                        who + ": a completeness verdict is not source damage and must not arrive as one");

                assertEquals(3, s.size(), who + " must read every record");

                // A rolled set is never COMPLETE and never MISSING for itself (D-E5); it reports its
                // member's loss, which is the same verdict wearing the member's name.
                assertEquals(StreamEnd.State.MISSING_RECORDS, end.state(), who + " state");
                assertEquals("missing_records", facts.get("state"), who + " context state");

                assertFalse(said.isEmpty(),
                        who + " told an agent records are missing and told a person nothing");
                String sentence = said.stream().filter(d -> d.contains("declares")).findFirst()
                        .orElseThrow(() -> new AssertionError(who + " has no completeness sentence: " + said));
                assertTrue(sentence.contains("9 records") && sentence.contains("3 were read"),
                        who + " sentence must name both numbers: " + sentence);
                assertTrue(sentence.contains("6 records are missing"),
                        who + " gap must be positive and plural-correct: " + sentence);

                // and the numbers an agent reads must be the same ones
                Map<String, Object> scope = facts.containsKey("member")
                        ? asMap(facts.get("member")) : facts;
                assertEquals(9L, scope.get("declaredRecords"), who + " context declaredRecords");
                assertEquals(3L, facts.get("recordsRead"), who + " top-level recordsRead is the whole log");
            }
        }
    }

    /** A whole file says nothing on either surface, everywhere. Silence must also agree. */
    @Test
    void everyStoreIsEquallySilentAboutAWholeFile() throws Exception {
        Path p = Files.createTempFile("agree-ok", ".yaml");
        Files.writeString(p, file(3, 3), StandardCharsets.UTF_8);

        for (LogStore s : allStores(p)) {
            try (s) {
                String who = name(s);
                Map<String, Object> facts = StreamEndReport.facts(s.streamEnd(), s.size());
                boolean rolled = s instanceof RolledLogStore;
                assertEquals(rolled ? "unknown" : "complete", facts.get("state"),
                        who + ": a set is never complete (D-E5); a single file can be");
                assertTrue(s.completenessDiagnostics().stream().noneMatch(d -> d.contains("declares")),
                        who + " must not warn about a whole file: " + s.completenessDiagnostics());
            }
        }
    }

    /**
     * A set's member numbers stay inside the member, including its run — round four's finding 1.
     * {@code recordsRead} at the top is the SET; inside {@code member} it is that FILE's own count.
     */
    @Test
    void aSetsScopesEachStateTheirOwnRecordCount(@TempDir Path dir) throws IOException {
        Path a = dir.resolve("s.log.1");
        Path b = dir.resolve("s.log");
        Files.writeString(a, file(2, 2), StandardCharsets.UTF_8);          // whole
        Files.writeString(b, file(2, 2) + file(3, 8), StandardCharsets.UTF_8);  // run 2 declares 8, holds 3

        try (RolledLogStore set = RolledLogStore.open(List.of(a, b), 512)) {
            assertEquals(7, set.size(), "2 + 5 records across the set");
            Map<String, Object> facts = StreamEndReport.facts(set.streamEnd(), set.size());

            assertEquals(7L, facts.get("recordsRead"), "the SET's count");
            Map<String, Object> member = asMap(facts.get("member"));
            assertEquals("s.log", member.get("file"));
            assertEquals(5L, member.get("recordsRead"),
                    "the MEMBER's own count, which was missing entirely");

            Map<String, Object> run = asMap(member.get("run"));
            assertEquals(2, run.get("ordinal"));
            assertEquals(8L, run.get("declaredRecords"), "the RUN's declaration, labelled as the run's");
            assertEquals(3L, run.get("recordsRead"), "and the run's count, not the member's 5 or the set's 7");
            assertNull(member.get("declaredRecords"),
                    "a run's declaration must not also sit at the member level");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        assertNotNull(o, "expected a nested scope");
        return (Map<String, Object>) o;
    }
}
