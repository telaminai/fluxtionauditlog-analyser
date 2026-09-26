package telamin.fluxtion.audit.analyser.analyser.parse;

import java.util.Objects;

/**
 * M68.5 (spec-evidence-integrity D-E6): has the file behind an open log changed, observed at the next REQUEST that
 * reads it — the boundary for a log that is not being followed, and the only one the mapped store has.
 *
 * <p>Two outcomes matter, and they are not the same:
 * <ul>
 *   <li><b>superseded, bytes retained</b> — the path now names a different file, or none. What the analyser shows is
 *       still the file that was opened: the heap store holds its text, and the mapped store's open channel still
 *       reads the opened file. It may be read, labelled superseded.</li>
 *   <li><b>reads suspended</b> — the opened file itself changed in place. The mapped store reads row bytes through
 *       its channel on demand, so its index would now describe bytes that are no longer there. Until a reopen,
 *       record-reading requests are refused with this reason.</li>
 * </ul>
 *
 * <p>{@code null} from {@link #classify} means NO CHANGE WAS OBSERVED, from metadata only, and is never reported as
 * proven unchanged (D-E6). <b>The limit, stated:</b> an in-place rewrite that restores size, modification time and
 * key cannot be seen this way. Seeing it means re-reading every byte, the cost D-E6 names, which the heap store's
 * Follow pays ({@link FollowIdentity}) and a request-time check does not.
 */
public record ReadThroughIdentity(FollowIdentity.Verdict verdict, String reason, boolean bytesRetained) {

    /** The file as it was read at open, and as it is now. {@code key} is null where the filesystem gives none. */
    public record Meta(boolean exists, Object key, long size, long modifiedMillis) {
        public static final Meta MISSING = new Meta(false, null, -1, -1);
    }

    /**
     * @param inMemory whether the store holds the opened content itself (the heap store), so nothing a change on
     *                 disk does can alter what it serves
     */
    public static ReadThroughIdentity classify(Meta atOpen, Meta now, boolean inMemory) {
        if (!now.exists()) {
            return new ReadThroughIdentity(FollowIdentity.Verdict.REPLACEMENT,
                    "the file is no longer at this path; what is shown is the file as it was opened", true);
        }
        if (atOpen.key() != null && now.key() != null && !Objects.equals(atOpen.key(), now.key())) {
            return new ReadThroughIdentity(FollowIdentity.Verdict.REPLACEMENT,
                    "a different file now has this path; what is shown is the file as it was opened", true);
        }
        boolean moved = atOpen.size() != now.size() || atOpen.modifiedMillis() != now.modifiedMillis();
        if (!moved) return null;
        if (inMemory) {
            return new ReadThroughIdentity(FollowIdentity.Verdict.UNVERIFIED,
                    "the file has changed on disk since it was read; what is shown is the content as it was read", true);
        }
        if (atOpen.key() == null || now.key() == null) {
            return new ReadThroughIdentity(FollowIdentity.Verdict.UNVERIFIED,
                    "the file has changed and this filesystem gives no file identity, so whether the opened file "
                    + "was rewritten cannot be told; reads are suspended until the log is reopened", false);
        }
        if (now.size() < atOpen.size()) {
            return new ReadThroughIdentity(FollowIdentity.Verdict.REPLACEMENT,
                    "the opened file was rewritten in place and is now shorter; reads are suspended until the log is reopened",
                    false);
        }
        return new ReadThroughIdentity(FollowIdentity.Verdict.UNVERIFIED,
                "the opened file changed in place, so the records already indexed may no longer match it; reads are "
                + "suspended until the log is reopened", false);
    }

    public boolean suspendsReads() {
        return !bytesRetained;
    }

    /** Read the metadata now; a failure to read it is reported as missing, never as unchanged. */
    public static Meta metaOf(java.nio.file.Path path) {
        try {
            var a = java.nio.file.Files.readAttributes(path, java.nio.file.attribute.BasicFileAttributes.class);
            return new Meta(true, a.fileKey(), a.size(), a.lastModifiedTime().toMillis());
        } catch (java.io.IOException | SecurityException e) {
            return Meta.MISSING;
        }
    }
}
