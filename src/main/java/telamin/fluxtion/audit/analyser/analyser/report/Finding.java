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
 * @param kind        fault, confirmation or note; {@code null} means fault (what every finding was before categories).
 *                    A SAVED finding with no kind is read by {@link #fromMap}: as fault when it has text, as note when
 *                    it is empty (a bare bookmark, which {@link #toMap} saves without a kind)
 */
public record Finding(int recordIndex, String note, String fix, String kind) {

    /** The categories a finding may have. {@code note} is neutral: neither a fault nor a confirmation. */
    public static final java.util.Set<String> KINDS = java.util.Set.of("fault", "confirmation", "note");

    /**
     * The category of a NEW finding written by the {@code flag} verb without a {@code kind}. Neutral on purpose: an
     * agent that flags the record proving a correct refusal must not have it saved as a fault (it was, on first use).
     * A saved finding with no kind is read as {@code fault} only when it has text (what every finding was before
     * categories); an empty one is a bare bookmark and reads as {@code note}. Accepted inconsistency: a bare flag saved
     * by 1.40.3 or earlier carries an explicit {@code kind: "fault"} and so restores as an empty fault, while a bare flag
     * made now reports {@code note} — the saved kind is honoured rather than guessed at.
     */
    public static final String FLAG_DEFAULT_KIND = "note";

    public Finding(int recordIndex, String note, String fix) {
        this(recordIndex, note, fix, "fault");
    }

    public Finding {
        kind = kind == null ? "fault" : kind;
        if (!KINDS.contains(kind))
            throw new IllegalArgumentException("finding kind must be fault, confirmation or note");
        note = note == null ? "" : note;
        fix = fix == null || fix.isBlank() ? null : fix;
    }

    /**
     * What the {@code flag} verb stores on a record: a new finding takes {@code kind}, or {@link #FLAG_DEFAULT_KIND}
     * when none was given; an existing one is refined by {@link #merge(String, String, String)}, which keeps its kind.
     */
    public static Finding flagged(Finding existing, int recordIndex, String note, String fix, String kind) {
        return existing == null
                ? new Finding(recordIndex, note, fix, kind == null ? FLAG_DEFAULT_KIND : kind)
                : existing.merge(note, fix, kind);
    }

    public boolean confirmation() { return "confirmation".equals(kind); }
    public boolean fault() { return "fault".equals(kind); }
    public String noteLabel() { return confirmation() ? "Observation" : fault() ? "What is wrong" : "Note"; }
    public String fixLabel() { return fault() ? "Likely cause / suggested fix" : "Assessment"; }
    /** The records table's note text; {@code null} for an empty finding (a bare bookmark), whatever its kind. */
    public String tableText() {
        if (isEmpty()) return null;
        if (fault()) return hasNote() ? note : null;
        String noteText = hasNote() ? noteLabel() + ": " + note : null;
        String fixText = hasFix() ? fixLabel() + ": " + fix : null;
        return noteText == null ? fixText : fixText == null ? noteText : noteText + "\n" + fixText;
    }

    /**
     * The persisted shape (session recovery). An empty {@code note} finding — a bare bookmark — is saved WITHOUT a
     * {@code kind}, exactly as builds before the {@code note} kind can read it (they reject {@code "note"}, and one
     * rejected entry withholds every finding); {@link #fromMap} reads it back as a {@code note}.
     */
    public java.util.Map<String, Object> toMap() {
        var out = new java.util.LinkedHashMap<String,Object>();
        out.put("recordIndex", recordIndex);
        if (!(isEmpty() && FLAG_DEFAULT_KIND.equals(kind))) out.put("kind", kind);
        out.put("note", note); out.put("fix", fix);
        return out;
    }
    public static Finding fromMap(java.util.Map<?,?> raw) {
        Object row = raw.get("recordIndex");
        if (!(row instanceof Number n) || n.doubleValue() != n.intValue() || n.intValue() < 0)
            throw new IllegalArgumentException("invalid finding recordIndex");
        String note = (String) raw.get("note"), fix = (String) raw.get("fix"), kind = (String) raw.get("kind");
        // no kind and no text: a bare bookmark (see toMap); no kind WITH text: a finding from before categories, a fault
        boolean bare = (note == null || note.isBlank()) && (fix == null || fix.isBlank());
        if (kind == null && bare) kind = FLAG_DEFAULT_KIND;
        return new Finding(n.intValue(), note, fix, kind);
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
