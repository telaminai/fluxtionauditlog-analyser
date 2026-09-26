package telamin.fluxtion.audit.analyser.analyser.parse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static telamin.fluxtion.audit.analyser.analyser.parse.FollowIdentity.Verdict.*;

/** M68.5 (D-E6, acceptance 7): every case resolves to append, replacement, unchanged or unverified — no hole. */
class FollowIdentityTest {

    private static final Object KEY = "inode-1";
    private static final String OLD = "---\nr1\n---\nr2\n---\n";

    private static FollowIdentity.Verdict v(Object keyNow, String textNow) {
        return FollowIdentity.classify(KEY, OLD, keyNow, textNow, false).verdict();
    }

    @Test
    @DisplayName("an ordinary append is APPEND, and may be indexed")
    void ordinaryAppend() {
        var id = FollowIdentity.classify(KEY, OLD, KEY, OLD + "r3\n---\n", false);
        assertEquals(APPEND, id.verdict());
        assertTrue(id.mayIndexGrowth());
    }

    @Test
    @DisplayName("the case that produced nothing: a same-length rewrite is REPLACEMENT")
    void sameLengthRewrite() {
        String rewritten = OLD.replace("r1", "R1");
        assertEquals(OLD.length(), rewritten.length());
        assertEquals(REPLACEMENT, v(KEY, rewritten));
    }

    @Test
    @DisplayName("truncation is REPLACEMENT")
    void truncation() {
        assertEquals(REPLACEMENT, v(KEY, "---\nr1\n---\n"));
    }

    @Test
    @DisplayName("round 4's case: a middle-record rewrite combined with a genuine append is REPLACEMENT, not APPEND")
    void middleRewritePlusAppend() {
        var id = FollowIdentity.classify(KEY, OLD, KEY, OLD.replace("r1", "X1") + "r3\n---\n", false);
        assertEquals(REPLACEMENT, id.verdict());
        assertFalse(id.mayIndexGrowth(), "already-indexed rows would describe bytes that no longer exist");
    }

    @Test
    @DisplayName("an in-place rewrite with its metadata restored is still caught — the bytes are compared, not the metadata")
    void inPlaceRewriteMetadataRestored() {
        assertEquals(REPLACEMENT, v(KEY, OLD.replace("r2", "rX")));
    }

    @Test
    @DisplayName("a changed file key is REPLACEMENT even when the bytes still match")
    void keyChanged() {
        assertEquals(REPLACEMENT, v("inode-2", OLD + "r3\n---\n"));
    }

    @Test
    @DisplayName("same key, same length, touched: every byte compared, so UNCHANGED — the stated departure from D-E6")
    void touchedButIdentical() {
        var id = FollowIdentity.classify(KEY, OLD, KEY, OLD, false);
        assertEquals(UNCHANGED, id.verdict());
        assertTrue(id.reason().contains("every byte compared"), id.reason());
    }

    @Test
    @DisplayName("an unavailable file key is UNVERIFIED, and two absent keys are never an equal identity")
    void unavailableKey() {
        var grown = FollowIdentity.classify(null, OLD, null, OLD + "r3\n---\n", false);
        assertEquals(UNVERIFIED, grown.verdict());
        assertTrue(grown.mayIndexGrowth(), "the bytes are verified, so the appended rows may be indexed — labelled");
        assertEquals(UNVERIFIED, FollowIdentity.classify(null, OLD, null, OLD, false).verdict(),
                "never reported as proven unchanged");
    }

    @Test
    @DisplayName("a missing file, and a change during the read, are UNVERIFIED and index nothing")
    void missingOrMoving() {
        var missing = FollowIdentity.classify(KEY, OLD, null, null, false);
        assertEquals(UNVERIFIED, missing.verdict());
        assertFalse(missing.mayIndexGrowth());
        var moving = FollowIdentity.classify(KEY, OLD, KEY, OLD + "r3\n---\n", true);
        assertEquals(UNVERIFIED, moving.verdict());
        assertFalse(moving.mayIndexGrowth(), "re-checked at the next poll, not indexed on a moving read");
    }
}
