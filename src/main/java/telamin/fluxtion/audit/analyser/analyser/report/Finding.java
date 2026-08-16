package telamin.fluxtion.audit.analyser.analyser.report;

/**
 * What someone concluded about one record: what is wrong, and — optionally — where to look to fix it.
 *
 * <p>Anchored to a record index rather than a time or a node, because a record <em>is</em> a cycle: the
 * event, everything that ran because of it, and the values each node logged. Anchoring anywhere else
 * loses the thing that makes the finding provable.
 *
 * <p>There is deliberately <b>one</b> place a finding is written — the flag on a record. It is then shown
 * in three: the table's note column, the callout on the topology, and the exported report. Two write
 * sites for the same sentence is how the two halves silently drift apart, and a diagnosis that disagrees
 * with itself is worse than none.
 *
 * @param recordIndex the cycle this is about
 * @param note        the explanation — what is wrong and why it matters
 * @param fix         the suggested fix or likely problem area; {@code null} when nobody has said yet
 */
public record Finding(int recordIndex, String note, String fix) {

    public Finding {
        note = note == null ? "" : note;
        fix = fix == null || fix.isBlank() ? null : fix;
    }

    public boolean hasNote() {
        return !note.isBlank();
    }

    public boolean hasFix() {
        return fix != null;
    }

    public boolean isEmpty() {
        return !hasNote() && !hasFix();
    }

    /**
     * Merge in what a caller supplied, keeping what it left out.
     *
     * <p>A flag that carries only a fix must not erase the note that explains what the fix is for, and
     * vice versa — a caller adding one field is refining the finding, not replacing it.
     */
    public Finding merge(String newNote, String newFix) {
        return new Finding(recordIndex,
                newNote == null ? note : newNote,
                newFix == null ? fix : newFix);
    }
}
