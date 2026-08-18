package telamin.fluxtion.audit.analyser.analyser.graph;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The M29.1 loader — the full D-F1/D-F4 test set, before any UI exists (the spec's own slice order).
 * The two decisions under test: the clock domain is DECLARED, never inferred (D-F1), and diagnostics
 * are bounded and sanitised — they may name the shape of a failure, never echo a cell verbatim (D-F4).
 */
class ExternalCsvLoaderTest {

    @TempDir
    Path dir;

    private Path csv(String name, String content) throws IOException {
        Path f = dir.resolve(name);
        Files.writeString(f, content);
        return f;
    }

    private static ExternalCsvLoader.Spec spec(String timeFormat, String zone) {
        return new ExternalCsvLoader.Spec("venue mid", "ts", timeFormat, zone, "mid", 0);
    }

    @Test
    void epochMillisHappyPath() throws IOException {
        Path f = csv("a.csv", "ts,mid\n1000,17.1\n2000,17.2\n");
        var r = ExternalCsvLoader.load(f, spec("epochMillis", null));
        assertEquals(2, r.rowsLoaded());
        assertEquals(0, r.rowsSkipped());
        assertEquals(1000L, r.fromMillis());
        assertEquals(2000L, r.toMillis());
        assertEquals("venue mid", r.series().label());
        assertEquals(17.1, r.series().y(0), 1e-9);
    }

    @Test
    void epochSecondsScaleAndOffsetApply() throws IOException {
        Path f = csv("a.csv", "ts,mid\n10,1.5\n");
        var r = ExternalCsvLoader.load(f,
                new ExternalCsvLoader.Spec("s", "ts", "epochSeconds", null, "mid", 250));
        assertEquals(10_250L, r.series().x(0), "seconds scaled to millis, declared offset applied");
    }

    @Test
    void iso8601WithOffsetNeedsNoZone_withoutOffsetNeedsTheDeclaredZone() throws IOException {
        Path withOffset = csv("o.csv", "ts,mid\n2026-08-17T10:00:00+01:00,5\n");
        var r = ExternalCsvLoader.load(withOffset, spec("iso8601", null));
        assertEquals(0, r.rowsSkipped(), "the text carries its own offset");

        Path noOffset = csv("n.csv", "ts,mid\n2026-08-17T10:00:00,5\n");
        var declared = ExternalCsvLoader.load(noOffset, spec("iso8601", "UTC"));
        assertEquals(0, declared.rowsSkipped());

        var undeclared = ExternalCsvLoader.load(noOffset, spec("iso8601", null));
        assertEquals(1, undeclared.rowsSkipped(),
                "no offset in the text and no declared zone → the row is REPORTED, never guessed (D-F1)");
    }

    @Test
    void explicitPatternWorksWithDeclaredZone() throws IOException {
        Path f = csv("p.csv", "ts,mid\n17/08/2026 10:00:00,5\n");
        var r = ExternalCsvLoader.load(f,
                new ExternalCsvLoader.Spec("s", "ts", "dd/MM/uuuu HH:mm:ss", "Europe/London", "mid", 0));
        assertEquals(1, r.rowsLoaded());
        assertEquals(0, r.rowsSkipped());
    }

    @Test
    void nothingIsSniffed() throws IOException {
        Path f = csv("a.csv", "ts,mid\n1000,17.1\n");
        var e = assertThrows(IllegalArgumentException.class,
                () -> ExternalCsvLoader.load(f, spec(null, null)));
        assertTrue(e.getMessage().contains("declared, never inferred"), e.getMessage());
        assertThrows(IllegalArgumentException.class,
                () -> ExternalCsvLoader.load(f, spec("iso8601", "Narnia/Wardrobe")),
                "a typo'd zone fails the LOAD, not every row");
    }

    @Test
    void blankOrTextValueIsAGapPointNotASkip() throws IOException {
        Path f = csv("a.csv", "ts,mid\n1000,\n2000,N/A\n3000,5\n");
        var r = ExternalCsvLoader.load(f, spec("epochMillis", null));
        assertEquals(3, r.rowsLoaded(), "value problems are gaps, not skips");
        assertTrue(Double.isNaN(r.series().y(0)));
        assertTrue(Double.isNaN(r.series().y(1)));
        assertEquals(5.0, r.series().y(2), 1e-9);
    }

    @Test
    void unparseableTimesAreCountedWithOriginalLineNumbers_andExcerptsAreSanitised() throws IOException {
        String secret = "ssh-rsa AAAAB3NzaC1yc2EAAAADAQABAAABgQDf-very-long-secret-key-material";
        Path f = csv("a.csv", "ts,mid\n1000,1\n" + secret + ",2\n3000,3\n");
        var r = ExternalCsvLoader.load(f, spec("epochMillis", null));
        assertEquals(2, r.rowsLoaded());
        assertEquals(1, r.rowsSkipped());
        String diag = r.diagnostics().get(0);
        assertTrue(diag.startsWith("line 3:"), "ORIGINAL line number (header = line 1): " + diag);
        assertFalse(diag.contains(secret), "the cell must never be echoed verbatim (D-F4)");
        assertTrue(diag.length() < 120, "bounded: " + diag);
    }

    @Test
    void diagnosticsAreBounded_overflowIsCountedNotListed() throws IOException {
        StringBuilder sb = new StringBuilder("ts,mid\n");
        for (int i = 0; i < ExternalCsvLoader.MAX_DIAGNOSTICS + 5; i++) sb.append("bad").append(i).append(",1\n");
        var r = ExternalCsvLoader.load(csv("a.csv", sb.toString()), spec("epochMillis", null));
        assertEquals(ExternalCsvLoader.MAX_DIAGNOSTICS + 5, r.rowsSkipped());
        assertEquals(ExternalCsvLoader.MAX_DIAGNOSTICS + 1, r.diagnostics().size());
        assertTrue(r.diagnostics().get(ExternalCsvLoader.MAX_DIAGNOSTICS).contains("5 more"));
    }

    @Test
    void outOfOrderRowsAreSortedAndTheReorderIsReported() throws IOException {
        Path f = csv("a.csv", "ts,mid\n3000,3\n1000,1\n2000,2\n");
        var r = ExternalCsvLoader.load(f, spec("epochMillis", null));
        assertEquals(2, r.rowsReordered(), "two rows arrived earlier than their predecessor");
        assertEquals(1000L, r.series().x(0));
        assertEquals(2000L, r.series().x(1));
        assertEquals(3000L, r.series().x(2));
    }

    @Test
    void duplicateTimestampsAreBothKeptInFileOrder() throws IOException {
        Path f = csv("a.csv", "ts,mid\n1000,1\n1000,2\n");
        var r = ExternalCsvLoader.load(f, spec("epochMillis", null));
        assertEquals(2, r.rowsLoaded());
        assertEquals(1.0, r.series().y(0), 1e-9, "stable sort keeps file order for equal times");
        assertEquals(2.0, r.series().y(1), 1e-9);
    }

    @Test
    void theRowCapRefusesLoudlyDuringTheStreamingPass() throws IOException {
        Path f = csv("a.csv", "ts,mid\n1,1\n2,2\n3,3\n4,4\n");
        var e = assertThrows(IllegalArgumentException.class,
                () -> ExternalCsvLoader.load(f, spec("epochMillis", null), 3));
        assertTrue(e.getMessage().contains("3-row bound"), e.getMessage());
        assertTrue(e.getMessage().contains("refuses"), "refused, never silently truncated: " + e.getMessage());
    }

    @Test
    void missingColumnsAreNamedAgainstTheActualHeader() throws IOException {
        Path f = csv("a.csv", "time,price\n1000,1\n");
        var e = assertThrows(IllegalArgumentException.class,
                () -> ExternalCsvLoader.load(f, spec("epochMillis", null)));
        assertTrue(e.getMessage().contains("'ts'"), e.getMessage());
        assertTrue(e.getMessage().contains("time, price") || e.getMessage().contains("[time, price]"),
                "the header is offered so the caller can fix the column name: " + e.getMessage());
    }

    @Test
    void quotedCellsWithCommasParse() throws IOException {
        Path f = csv("a.csv", "ts,mid,note\n1000,\"1,5\",x\n");
        // the VALUE "1,5" is quoted-with-comma — not numeric in Java, so a gap, but the row parses
        var r = ExternalCsvLoader.load(f, spec("epochMillis", null));
        assertEquals(1, r.rowsLoaded());
        assertTrue(Double.isNaN(r.series().y(0)));
    }
}
