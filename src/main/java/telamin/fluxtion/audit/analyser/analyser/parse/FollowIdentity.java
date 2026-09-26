package telamin.fluxtion.audit.analyser.analyser.parse;

import java.util.Objects;

/**
 * M68.5 (spec-evidence-integrity D-E6): is the file under Follow still the file that was read? Decided on every poll,
 * BEFORE anything is indexed, from facts a heap store has in hand: the file key at load and now, every byte read so
 * far, and every byte now.
 *
 * <p>Identity, not path, decides. The old poll compared LENGTHS only, so a same-length rewrite was "no growth" and
 * announced nothing, and a rewrite in the middle combined with a genuine append was indexed as an append, leaving
 * already-indexed rows describing bytes that no longer exist.
 *
 * <p>The table has no hole. Anything not established as unchanged, append or replacement is UNVERIFIED, and
 * unverified is never reported as proven unchanged.
 *
 * <p><b>Where this departs from D-E6's wording, deliberately.</b> D-E6 calls "same key, same length, changed
 * modification time, matching prefix" unverified, because a prefix SAMPLE cannot prove the bytes identical. The heap
 * store compares EVERY byte, so identical bytes are proven identical content. A touched file is UNCHANGED here, and
 * its reason says the comparison was complete.
 */
public record FollowIdentity(Verdict verdict, String reason) {

    public enum Verdict {
        /** Every byte compared, and identical. */
        UNCHANGED,
        /** Same file, grown, and every byte already read is unchanged. */
        APPEND,
        /** A different file, or the content already read has changed: announced before anything is served. */
        REPLACEMENT,
        /** Not established either way. Never read as "unchanged". */
        UNVERIFIED
    }

    /**
     * @param keyAtLoad        the file key when the open content was read, or null if the filesystem has none
     * @param textRead         every character read so far
     * @param keyNow           the file key now, or null if unavailable
     * @param textNow          every character now, or null if the file could not be read
     * @param changedDuringRead metadata moved between the start and the end of this read
     */
    public static FollowIdentity classify(Object keyAtLoad, String textRead, Object keyNow, String textNow,
                                          boolean changedDuringRead) {
        if (textNow == null) {
            return new FollowIdentity(Verdict.UNVERIFIED, "the file is missing or could not be read");
        }
        if (changedDuringRead) {
            return new FollowIdentity(Verdict.UNVERIFIED, "the file changed while it was being read; checked again at the next poll");
        }
        if (keyAtLoad != null && keyNow != null && !Objects.equals(keyAtLoad, keyNow)) {
            return new FollowIdentity(Verdict.REPLACEMENT, "a different file now has this path (its file identity changed)");
        }
        if (textNow.length() < textRead.length()) {
            return new FollowIdentity(Verdict.REPLACEMENT, "the file is shorter than what was already read");
        }
        if (!textNow.startsWith(textRead)) {
            return new FollowIdentity(Verdict.REPLACEMENT, "content already read has changed on disk");
        }
        if (keyAtLoad == null || keyNow == null) {
            // Absent keys are never compared as an equal identity (D-E6): the bytes are verified, the file is not.
            return new FollowIdentity(Verdict.UNVERIFIED, textNow.length() > textRead.length()
                    ? "the bytes already read are unchanged and new records were appended, but this filesystem gives no "
                      + "file identity, so a different file with the same leading content cannot be ruled out"
                    : "the bytes already read are unchanged, but this filesystem gives no file identity");
        }
        if (textNow.length() == textRead.length()) {
            return new FollowIdentity(Verdict.UNCHANGED, "every byte compared, identical");
        }
        return new FollowIdentity(Verdict.APPEND, "the same file, grown; every byte already read is unchanged");
    }

    /** Whether rows may be indexed from {@code textNow}: only when the bytes already read are verified unchanged. */
    public boolean mayIndexGrowth() {
        return verdict == Verdict.APPEND
                || (verdict == Verdict.UNVERIFIED && reason.startsWith("the bytes already read are unchanged"));
    }
}
