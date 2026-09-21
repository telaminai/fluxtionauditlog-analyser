package telamin.fluxtion.audit.analyser.analyser.report;

/**
 * What someone concluded about one record: a fault or a confirmation, with optional assessment.
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
 * @param fix         the assessment or suggested fix; {@code null} when nobody has said yet
 * @param kind        fault or confirmation; absent legacy values default to fault
 */
public record Finding(int recordIndex, String note, String fix, String kind) {

    public Finding(int recordIndex, String note, String fix) {
        this(recordIndex, note, fix, "fault");
    }

    public Finding {
        kind = kind == null ? "fault" : kind;
        if (!java.util.Set.of("fault", "confirmation").contains(kind))
            throw new IllegalArgumentException("finding kind must be fault or confirmation");
        note = note == null ? "" : note;
        fix = fix == null || fix.isBlank() ? null : fix;
    }

    public boolean confirmation() { return "confirmation".equals(kind); }
    public String noteLabel() { return confirmation() ? "Observation" : "What is wrong"; }
    public String fixLabel() { return confirmation() ? "Assessment" : "Likely cause / suggested fix"; }
    public String tableText() {
        if (!confirmation()) return hasNote() ? note : null;
        return noteLabel() + ": " + note + (hasFix() ? "\n" + fixLabel() + ": " + fix : "");
    }
    public java.util.Map<String, Object> toMap() {
        var out = new java.util.LinkedHashMap<String,Object>();
        out.put("recordIndex", recordIndex); out.put("kind", kind); out.put("note", note); out.put("fix", fix);
        return out;
    }
    public static Finding fromMap(java.util.Map<?,?> raw) {
        Object row = raw.get("recordIndex");
        if (!(row instanceof Number n) || n.doubleValue() != n.intValue() || n.intValue() < 0)
            throw new IllegalArgumentException("invalid finding recordIndex");
        return new Finding(n.intValue(), (String)raw.get("note"), (String)raw.get("fix"), (String)raw.get("kind"));
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
        return merge(newNote, newFix, null);
    }

    public Finding merge(String newNote, String newFix, String newKind) {
        return new Finding(recordIndex, newNote == null ? note : newNote,
                newFix == null ? fix : newFix, newKind == null ? kind : newKind);
    }
}
