package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import telamin.fluxtion.audit.analyser.analyser.llm.ReadService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The composite store (M30.2): one gap-free global recordIndex, per-file backends, FILE-LOCAL byte
 * offsets with the file id carried in the merged index (D-R2), and the corrected D-R6 — the heap
 * threshold applies to the SET TOTAL.
 */
class RolledLogStoreTest {

    @TempDir
    Path dir;

    private static String records(String event, long... logTimes) {
        StringBuilder sb = new StringBuilder("---\n");
        for (long t : logTimes) {
            sb.append("#00:00:00.000 [t] INFO L\neventLogRecord:\n  logTime: ").append(t)
                    .append("\n  event: ").append(event).append("\n---\n");
        }
        return sb.toString();
    }

    private List<Path> threeFiles() throws IOException {
        Path a = dir.resolve("m.log.1");
        Path b = dir.resolve("m.log.2");
        Path c = dir.resolve("m.log");
        Files.writeString(a, records("A", 100, 110));
        Files.writeString(b, records("B", 200, 210, 220));
        Files.writeString(c, records("C", 300));
        return List.of(a, b, c);
    }

    @Test
    void oneLogicalLog_globalIndexIsGapFreeAndFileAware() throws IOException {
        RolledLogStore store = RolledLogStore.open(threeFiles(), 512);
        assertEquals(6, store.size());
        var idx = store.index();
        assertEquals(3, idx.fileCount());
        assertEquals(List.of("m.log.1", "m.log.2", "m.log"), idx.files());
        assertEquals(0, idx.fileId(0));
        assertEquals(1, idx.fileId(2));
        assertEquals(2, idx.fileId(5));
        assertEquals(100L, idx.logTime(0));
        assertEquals(300L, idx.logTime(5));
        assertEquals(100L, store.minLogTime());
        assertEquals(300L, store.maxLogTime());
        // record()/rawText() route to the right member with member-local rows
        assertTrue(store.rawText(2).contains("event: B"), "global row 2 is m.log.2's first record");
        assertEquals("C", store.record(5).event());
        // dimension interning survived the column-copy merge
        assertEquals(6, idx.dimensionCounts().values().stream().mapToInt(Integer::intValue).sum());
    }

    @Test
    void offsetsStayFileLocal_soTheSameOffsetExistsInSeveralFiles() throws IOException {
        RolledLogStore store = RolledLogStore.open(threeFiles(), 512);
        var idx = store.index();
        // each member starts at its own "---\n": the first record of EVERY file has the same offset
        assertEquals(idx.offset(0), idx.offset(2), "file-local offsets — a real offset into a real file");
        assertEquals(idx.offset(0), idx.offset(5));
    }

    @Test
    void bareByteOffsetReadsAreRefusedWithTheFileList_fileDiscriminatorResolves() throws IOException {
        RolledLogStore store = RolledLogStore.open(threeFiles(), 512);
        var snap = store.index().snapshot();

        var e = assertThrows(IllegalArgumentException.class,
                () -> ReadService.read(snap, Map.of("byteOffset", 4L), store::rawText));
        assertTrue(e.getMessage().contains("rolled set"), e.getMessage());
        assertTrue(e.getMessage().contains("m.log.2"), "the refusal lists the files: " + e.getMessage());

        Map<String, Object> out = ReadService.read(snap,
                Map.of("byteOffset", 4L, "file", "m.log.2", "count", 1), store::rawText);
        assertEquals(2, out.get("anchor"), "offset 4 IN m.log.2 is its first record = global row 2");
        @SuppressWarnings("unchecked")
        var rows = (List<Map<String, Object>>) out.get("records");
        assertEquals("m.log.2", rows.get(0).get("file"), "every row names its member file");
    }

    @Test
    void theHeapThresholdAppliesToTheSetTotal() throws IOException {
        // three files of ~200 bytes each; a threshold of 0 MB puts the TOTAL over → all mapped (D-R6)
        RolledLogStore mapped = RolledLogStore.open(threeFiles(), 0);
        assertEquals(6, mapped.size(), "mapped members behave identically");
        assertTrue(mapped.rawText(0).contains("event: A"));
        mapped.close();
    }

    private static String marker(long records) {
        return "eventLogRecord:\n  streamEnd: normal\n  streamEndRecords: " + records + "\n---\n";
    }

    /**
     * D-E3 for a set, and the blocker re-review found.
     *
     * <p>A marker vouches for the FILE THAT CARRIES IT. Nothing in a rolled set records how many files
     * there should be, so a set of individually-whole files with one file missing between two of them is
     * indistinguishable from a set with nothing missing. The first version of this override returned
     * COMPLETE when every member was COMPLETE, and re-review measured what that costs: members of 10 and
     * 5 records with the file between them absent reported <b>complete, 15 records</b>. An agent reading
     * that concludes a node never ran. <b>A set is never COMPLETE.</b>
     */
    @Test
    void aSetIsNeverCompleteHoweverWholeItsMembersAre() throws IOException {
        Path a = dir.resolve("s.log.1");
        Path b = dir.resolve("s.log");
        Files.writeString(a, records("A", 100, 110) + marker(2));
        Files.writeString(b, records("B", 200) + marker(1));
        try (RolledLogStore whole = RolledLogStore.open(List.of(a, b), 512)) {
            assertEquals(3, whole.size(), "no marker is a record in any member");
            assertEquals(StreamEnd.State.UNKNOWN, whole.streamEnd().state(),
                    "every member is whole, and that is not evidence about the SET");
            assertFalse(whole.streamEnd().isKnownComplete());
            assertEquals(1, whole.sourceDiagnostics().size(),
                    () -> "say what the members established AND what they did not: "
                            + whole.sourceDiagnostics());
            assertTrue(whole.sourceDiagnostics().get(0).contains("unknown"),
                    () -> whole.sourceDiagnostics().get(0));
        }
    }

    /**
     * The measured failure, reconstructed: the middle file of a rolled set was never copied. Each
     * surviving member is whole and says so, and the hour between them is simply gone.
     */
    @Test
    void aMissingMemberIsIndistinguishableFromNoneMissingAndTheSetSaysSo() throws IOException {
        Path r1 = dir.resolve("g.log.2");
        Path r3 = dir.resolve("g.log");
        Files.writeString(r1, records("A", 100, 110) + marker(2));
        Files.writeString(r3, records("C", 300, 310, 320) + marker(3));
        // g.log.1, the middle hour, is absent — nothing in the set can notice
        try (RolledLogStore gapped = RolledLogStore.open(List.of(r1, r3), 512)) {
            assertEquals(5, gapped.size());
            assertEquals(StreamEnd.State.UNKNOWN, gapped.streamEnd().state(),
                    "this reported COMPLETE with 15 records over a set that had lost an hour");
            assertFalse(gapped.streamEnd().isKnownComplete());
        }
    }

    /**
     * Re-review B2, the third occurrence of one defect class: a member's numbers printed beside the
     * SET's count, on the machine surface. {@code context} showed {@code recordsRead: 25} (the set) next
     * to {@code declaredRecords: 6} (one file), with no file named, under a missing-records state — so
     * an agent read "declared 6, read 25", which looks like MORE than declared.
     */
    @Test
    void aSetsVerdictCarriesTheMemberItCameFromSoTheTwoScopesCannotBePrintedAsOne() throws IOException {
        Path g1 = dir.resolve("g.log.1");
        Path g2 = dir.resolve("g.log");
        Files.writeString(g1, records("A", 100, 110) + marker(2));
        Files.writeString(g2, records("B", 200, 210, 220) + marker(6));   // declares 6, holds 3
        try (RolledLogStore set = RolledLogStore.open(List.of(g1, g2), 512)) {
            assertEquals(5, set.size());
            StreamEnd end = set.streamEnd();
            assertEquals(StreamEnd.State.MISSING_RECORDS, end.state());
            assertEquals("g.log", end.member(), "the numbers belong to a file, and it must be named");
            assertEquals(6, end.declaredRecords(), "the member's declaration");
            assertEquals(3, end.emittedRecords(), "the member's records, not the set's 5");
        }
    }

    @Test
    void aMemberThatLostRecordsStillMakesTheSetSaySoAndNamesTheFile() throws IOException {
        Path a = dir.resolve("s.log.1");
        Path b = dir.resolve("s.log");
        Files.writeString(a, records("A", 100, 110) + marker(2));
        Files.writeString(b, records("B", 200) + marker(4));      // this member lost three
        try (RolledLogStore lossy = RolledLogStore.open(List.of(a, b), 512)) {
            assertEquals(StreamEnd.State.MISSING_RECORDS, lossy.streamEnd().state(),
                    "loss inside a member IS something a member can establish about itself");
            assertEquals(1, lossy.sourceDiagnostics().size());
            assertTrue(lossy.sourceDiagnostics().get(0).startsWith("s.log "),
                    () -> "the diagnostic must name WHICH member: " + lossy.sourceDiagnostics());
        }
    }

    @Test
    void oneSilentMemberMakesTheSetUnknownHoweverCompleteTheOthersAre() throws IOException {
        Path a = dir.resolve("q.log.1");
        Path b = dir.resolve("q.log");
        Files.writeString(a, records("A", 100, 110) + marker(2));
        Files.writeString(b, records("B", 200));                  // no marker: says nothing
        try (RolledLogStore set = RolledLogStore.open(List.of(a, b), 512)) {
            assertEquals(StreamEnd.State.UNKNOWN, set.streamEnd().state(),
                    "a gap could sit inside the silent member and nothing would say so");
            assertFalse(set.streamEnd().isKnownComplete());
        }
    }

    @Test
    void recordIndexAnchorsNeedNoFileAndKeepWorking() throws IOException {
        RolledLogStore store = RolledLogStore.open(threeFiles(), 512);
        Map<String, Object> out = ReadService.read(store.index().snapshot(),
                Map.of("recordIndex", 3, "count", 1), store::rawText);
        assertEquals(3, out.get("anchor"), "recordIndex is global and gap-free — the primary anchor");
    }
}
