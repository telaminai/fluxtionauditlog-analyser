package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.MalformedInputException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Independent review, F2: a byte the decoder holds back is still a byte that EXISTS.
 *
 * <p>Round 4 taught Follow to wait for the rest of a character a poll had cut, instead of failing. It
 * measured growth in DECODED characters, so a poll that added only the first byte of a character decoded
 * to the same text and returned "no growth" — keeping the opening identity and, after a marker, keeping
 * <b>COMPLETE</b> over bytes it could see and had not read. A fresh read of the same bytes said UNKNOWN.
 *
 * <p>Each case starts from a genuinely complete file, so a COMPLETE after the cut can only come from
 * the store having hidden the tail.
 */
class FollowPendingBytesTest {

    private static final String RECORD =
            "eventLogRecord:\n  logTime: 1\n  event: Tick\n  nodeLogs:\n    - a: { v: 1}\n---\n";
    private static final String MARKER = "eventLogRecord:\n  streamEnd: normal\n  streamEndRecords: 1\n---\n";
    private static final String COMPLETE_FILE = RECORD + MARKER;

    /** A two-, three- (the BOM a `cat` of a marked file leaves mid-file, and a euro) and four-byte character. */
    private static final String[] CHARACTERS = {"é", "﻿", "€", "𝄞"};

    private static HeapLogStore followFromComplete(Path p) throws Exception {
        Files.writeString(p, COMPLETE_FILE, StandardCharsets.UTF_8);
        HeapLogStore s = HeapLogStore.fromFile(p).forFollow();
        assertEquals(StreamEnd.State.COMPLETE, s.streamEnd().state(), "precondition: the file starts whole");
        assertFalse(s.readIdentities().isEmpty(), "precondition: the opening identity is held");
        return s;
    }

    @Test
    void aCharacterCutAfterEveryPrefixByteWithholdsTheVerdictAndThenRecovers(@TempDir Path dir) throws Exception {
        int cases = 0;
        for (String ch : CHARACTERS) {
            byte[] c = ch.getBytes(StandardCharsets.UTF_8);
            for (int cut = 1; cut < c.length; cut++) {
                String label = ch.codePointAt(0) + " cut after byte " + cut + " of " + c.length;
                Path p = dir.resolve("f" + (cases++) + ".yaml");
                HeapLogStore s = followFromComplete(p);

                Files.write(p, Arrays.copyOfRange(c, 0, cut), StandardOpenOption.APPEND);
                assertEquals(0, s.appendFrom(p), label + ": nothing is decodable yet, so nothing is indexed");
                assertNotEquals(StreamEnd.State.COMPLETE, s.streamEnd().state(),
                        "F2 (" + label + "): bytes exist past the last marker; the file cannot vouch for itself");
                assertEquals(StreamEnd.State.UNKNOWN, s.streamEnd().state(), label + ": the §1a answer is unknown");
                assertEquals(1, s.trailingRecordsPending(), label + ": and it is said to be pending");
                assertTrue(s.readIdentities().isEmpty(),
                        "F2 (" + label + "): the file's bytes changed, so the opening identity must not survive");

                // Finish the character, give it a document, and end the run with a marker for it. The
                // character's line opens the new document (a lone BOM line is blank and skipped), so
                // exactly one record arrives either way, and the new marker covers it.
                Files.write(p, Arrays.copyOfRange(c, cut, c.length), StandardOpenOption.APPEND);
                Files.write(p, ("\n" + RECORD + MARKER).getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
                assertEquals(1, s.appendFrom(p), label + ": the completed document arrives");
                assertEquals(StreamEnd.State.COMPLETE, s.streamEnd().state(),
                        label + ": finished and marked, it is whole again");
                assertEquals(0, s.trailingRecordsPending(), label + ": nothing is pending once it is finished");
            }
        }
        assertEquals(1 + 2 + 2 + 3, cases, "every prefix of every character was cut");
    }

    /** Bytes that can never become a character must fail loudly, not wait for ever. */
    @Test
    void bytesThatCanNeverBeginACharacterFailRatherThanWait(@TempDir Path dir) throws Exception {
        int[][] never = {
                {0xC0}, {0xC1}, {0xF5}, {0xFF},   // never a lead byte
                {0xE0, 0x80},                     // an overlong three-byte form
                {0xED, 0xA0},                     // a UTF-16 surrogate
                {0xF0, 0x80},                     // an overlong four-byte form
                {0xF4, 0x90},                     // beyond U+10FFFF
        };
        int n = 0;
        for (int[] bad : never) {
            Path p = dir.resolve("bad" + (n++) + ".yaml");
            HeapLogStore s = followFromComplete(p);
            byte[] b = new byte[bad.length];
            for (int i = 0; i < bad.length; i++) b[i] = (byte) bad[i];
            Files.write(p, b, StandardOpenOption.APPEND);
            assertThrows(MalformedInputException.class, () -> s.appendFrom(p),
                    "F2: " + Arrays.toString(bad) + " can never complete, so waiting for it is a silent lie");

            // Re-review RR-1: refusing the bytes is right; keeping the old verdict over them is not.
            String label = Arrays.toString(bad);
            assertEquals(StreamEnd.State.UNKNOWN, s.streamEnd().state(),
                    "RR-1 (" + label + "): the store refused bytes it saw, so it cannot still vouch for the file");
            assertTrue(s.readIdentities().isEmpty(),
                    "RR-1 (" + label + "): the file's bytes changed, so the opening identity must not survive");
            assertEquals(0, s.trailingRecordsPending(),
                    label + ": bytes that can never be a character are not presented as one on its way");
            assertEquals(1, s.size(), label + ": the record read before them still stands");
            // Second re-review O1: undecodable bytes are SOURCE DAMAGE, listed first — not a completeness gap.
            assertTrue(s.sourceDiagnostics().size() == 1 && s.sourceDiagnostics().get(0).contains("not valid UTF-8"),
                    label + ": the reader could not read part of the source, and says so: " + s.sourceDiagnostics());
            assertEquals("SOURCE_DAMAGE", ProducerDiagnostics.of(s.index(), s::rawText, s.sourceDiagnostics())
                    .findings().get(0).kind().name(), label + ": and it is the FIRST thing a reader is told");
            assertEquals(0, s.appendFrom(p), label + ": a re-poll of the same bytes changes nothing ...");
            assertEquals(StreamEnd.State.UNKNOWN, s.streamEnd().state(), label + ": ... and does not restore the claim");
        }
    }

    /** The unchanged case must stay cheap and must not disturb a whole file. */
    @Test
    void noNewBytesIsNoGrowth(@TempDir Path dir) throws Exception {
        Path p = dir.resolve("same.yaml");
        HeapLogStore s = followFromComplete(p);
        assertEquals(0, s.appendFrom(p));
        assertEquals(StreamEnd.State.COMPLETE, s.streamEnd().state());
        assertFalse(s.readIdentities().isEmpty(), "no bytes changed, so the identity still describes the file");
    }

    /**
     * Second re-review S1: a failed live read was never cleared. A writer that then REPLACED the file with a
     * longer, valid, marked log was read as an append — the store said COMPLETE while its own diagnostics
     * still said "unknown until it is reopened". After a failure, a successful decode can only mean the bytes
     * changed under the store, so it must ask to be reloaded.
     */
    @Test
    void aSuccessfulReadAfterAFailedOneReloadsRatherThanAppends(@TempDir Path dir) throws Exception {
        Path p = dir.resolve("replaced.yaml");
        HeapLogStore s = followFromComplete(p);
        Files.write(p, new byte[]{(byte) 0xC0}, StandardOpenOption.APPEND);
        assertThrows(MalformedInputException.class, () -> s.appendFrom(p), "precondition: C0 is refused");
        assertEquals(StreamEnd.State.UNKNOWN, s.streamEnd().state(), "precondition: and the verdict is retired");

        String three = RECORD + RECORD + RECORD
                + "eventLogRecord:\n  streamEnd: normal\n  streamEndRecords: 3\n---\n";
        assertTrue(three.getBytes(StandardCharsets.UTF_8).length > COMPLETE_FILE.length() + 1,
                "precondition: the replacement is LONGER, so it cannot be mistaken for a truncation");
        Files.writeString(p, three, StandardCharsets.UTF_8);

        assertEquals(-1, s.appendFrom(p),
                "S1: after a failed read a clean decode means the file was replaced — reload, do not append");
        assertFalse(s.streamEnd().state() == StreamEnd.State.COMPLETE && !s.sourceDiagnostics().isEmpty(),
                "S1: never COMPLETE beside 'unknown until it is reopened'");
        assertEquals(1, s.size(), "and nothing from the replacement was indexed as though appended");
    }

    /**
     * Second re-review S4: RR-1's reset of the pending-byte count had no witness, because every invalid case
     * appended to a WHOLE file, where the count was already zero. Here a valid partial character is pending
     * first, and then an impossible byte arrives.
     */
    @Test
    void aRefusedByteAfterAPendingCharacterLeavesNothingPending(@TempDir Path dir) throws Exception {
        Path p = dir.resolve("pending-then-bad.yaml");
        HeapLogStore s = followFromComplete(p);
        Files.write(p, new byte[]{(byte) 0xE2, (byte) 0x82}, StandardOpenOption.APPEND);   // two of the euro's three
        assertEquals(0, s.appendFrom(p));
        assertEquals(1, s.trailingRecordsPending(), "precondition: a valid partial character is pending");
        Files.write(p, new byte[]{(byte) 0xC0}, StandardOpenOption.APPEND);                // cannot continue it
        assertThrows(MalformedInputException.class, () -> s.appendFrom(p));
        assertEquals(0, s.trailingRecordsPending(),
                "S4: the pending character was abandoned by the refusal and must not still be presented as on its way");
    }
}
