package telamin.fluxtion.audit.analyser.analyser.walk;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;

/**
 * M69 — the digests a walk's targets are written against, and the verdict that compares them
 * ({@code spec-spotlight-walks.md} §3.5). Pure.
 *
 * <p><b>What each digest binds, and nothing more</b> (review R4):
 * <ul>
 *   <li>a <b>record digest</b> is SHA-256 over the UTF-8 bytes of the exact {@code LogStore.rawText(i)} string —
 *       untrimmed, unframed — and binds that one record's text under one store representation. It says nothing about
 *       the original file's bytes, a chart's population, or the run as a whole;</li>
 *   <li>a <b>run basis</b> is the loaded files' SHA-256 digests as the read identity recorded them. Any unknown
 *       file makes the whole basis unknown;</li>
 *   <li>graph and definition digests are computed elsewhere, and compared here the same way.</li>
 * </ul>
 * Unknown is never equal: a verdict over an unknown basis is {@link State#UNRESOLVED}.
 */
public final class WalkIdentity {

    private WalkIdentity() {
    }

    /** A target's state against what is loaded now (§3.5). */
    public enum State {
        /** Its basis matches. */
        CURRENT,
        /** Its basis is known and differs: shown as not current, never re-drawn as it was. */
        HISTORICAL,
        /** Its basis, or the target itself, cannot be established. */
        UNRESOLVED
    }

    public static String sha256(String text) {
        return sha256(text == null ? new byte[0] : text.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256(byte[] bytes) {
        try {
            return "sha256:" + HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is required by every Java runtime", e);
        }
    }

    /** The record digest: over the exact raw text, or "" when there is none. */
    public static String recordDigest(String rawText) {
        return rawText == null ? "" : sha256(rawText);
    }

    /**
     * A run basis from the read identity's per-file digests. An empty list, or any null or blank digest (a file that
     * changed while it was read), makes the basis unknown: the empty list.
     */
    /**
     * Review PR57 R1: whether record text may be read to certify a record, given the SESSION's published file-identity
     * verdict. {@code UNVERIFIED} and {@code REPLACEMENT} mean the file behind the log changed after it was read, so no
     * record can be certified current against it. The session node decides with this for playback; authoring applies
     * the same rule to the verdict the session published.
     */
    public static boolean recordsTrusted(String sessionVerdict) {
        return !"UNVERIFIED".equals(sessionVerdict) && !"REPLACEMENT".equals(sessionVerdict);
    }

    /**
     * Review PR57 R1: the run a chart step describes — the opening file digests PLUS the session's current record
     * count. The digests are taken when the log opens and do not see a Follow append, so without the count a chart saved
     * before an append would read as CURRENT against the grown log. Unknown digests make the whole basis unknown.
     */
    public static List<String> runBasisOf(List<String> fileDigests, long recordCount) {
        List<String> files = runBasis(fileDigests);
        if (files.isEmpty()) return List.of();
        List<String> basis = new java.util.ArrayList<>(files);
        basis.add("records:" + recordCount);
        return List.copyOf(basis);
    }

    public static List<String> runBasis(List<String> fileDigests) {
        if (fileDigests == null || fileDigests.isEmpty()) return List.of();
        for (String d : fileDigests) if (d == null || d.isBlank()) return List.of();
        return List.copyOf(fileDigests);
    }

    /** Compare a saved digest with the current one: unknown on either side is UNRESOLVED, never CURRENT. */
    public static State compare(String saved, String now) {
        if (saved == null || saved.isBlank() || now == null || now.isBlank()) return State.UNRESOLVED;
        return saved.equals(now) ? State.CURRENT : State.HISTORICAL;
    }

    /** Compare two run bases: either unknown is UNRESOLVED. */
    public static State compareRuns(List<String> saved, List<String> now) {
        if (saved == null || saved.isEmpty() || now == null || now.isEmpty()) return State.UNRESOLVED;
        return saved.equals(now) ? State.CURRENT : State.HISTORICAL;
    }

    /** The worse of two states, for a step's summary: never better than its worst required target. */
    public static State worse(State a, State b) {
        if (a == State.UNRESOLVED || b == State.UNRESOLVED) return State.UNRESOLVED;
        if (a == State.HISTORICAL || b == State.HISTORICAL) return State.HISTORICAL;
        return State.CURRENT;
    }
}
