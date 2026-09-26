package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static telamin.fluxtion.audit.analyser.analyser.parse.ReadThroughIdentity.Meta;

/** M68.5 (D-E6): a change observed at the next request — superseded-but-retained, or reads suspended. */
class ReadThroughIdentityTest {

    private static final Meta OPEN = new Meta(true, "ino-1", 100, 1000);

    @Test
    @DisplayName("no change observed is null — never an 'unchanged' verdict")
    void nothingObserved() {
        assertNull(ReadThroughIdentity.classify(OPEN, OPEN, false));
    }

    @Test
    @DisplayName("a different file at the path: superseded, and the opened bytes are still read")
    void replacedAtPath() {
        var id = ReadThroughIdentity.classify(OPEN, new Meta(true, "ino-2", 100, 1000), false);
        assertEquals(FollowIdentity.Verdict.REPLACEMENT, id.verdict());
        assertFalse(id.suspendsReads(), "the mapped store's open channel still reads the opened file");
    }

    @Test
    @DisplayName("the file gone from the path: superseded, retained")
    void gone() {
        var id = ReadThroughIdentity.classify(OPEN, Meta.MISSING, false);
        assertEquals(FollowIdentity.Verdict.REPLACEMENT, id.verdict());
        assertFalse(id.suspendsReads());
    }

    @Test
    @DisplayName("the opened file changed in place under a read-through store: reads are suspended")
    void inPlaceChangeSuspends() {
        var grew = ReadThroughIdentity.classify(OPEN, new Meta(true, "ino-1", 120, 2000), false);
        assertTrue(grew.suspendsReads(), "the index would describe bytes that are no longer there");
        assertEquals(FollowIdentity.Verdict.UNVERIFIED, grew.verdict());
        var shrank = ReadThroughIdentity.classify(OPEN, new Meta(true, "ino-1", 80, 2000), false);
        assertEquals(FollowIdentity.Verdict.REPLACEMENT, shrank.verdict());
        assertTrue(shrank.suspendsReads());
        var touched = ReadThroughIdentity.classify(OPEN, new Meta(true, "ino-1", 100, 2000), false);
        assertTrue(touched.suspendsReads(), "same length, new time: an in-place rewrite looks exactly like this");
    }

    @Test
    @DisplayName("a store that holds its content in memory is labelled, never suspended")
    void inMemoryIsRetained() {
        var id = ReadThroughIdentity.classify(OPEN, new Meta(true, "ino-1", 120, 2000), true);
        assertNotNull(id);
        assertFalse(id.suspendsReads());
    }

    @Test
    @DisplayName("no file identity on either side: a change cannot be attributed, so a read-through store suspends")
    void noKeys() {
        var id = ReadThroughIdentity.classify(new Meta(true, null, 100, 1000), new Meta(true, null, 120, 2000), false);
        assertTrue(id.suspendsReads());
        assertNull(ReadThroughIdentity.classify(new Meta(true, null, 100, 1000), new Meta(true, null, 100, 1000), false),
                "and absent keys with nothing moved is still only 'no change observed'");
    }
}
