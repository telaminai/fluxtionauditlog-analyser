package telamin.fluxtion.audit.analyser.analyser.topology;

/**
 * The sentences a surface uses when a log, a record or a saved focus names nodes the loaded graph does not
 * declare (M68.1 re-review R3).
 *
 * <p>One rule for all of them: <b>state the disagreement and the artefacts compared, and stop.</b> Matching node
 * names establish nothing about which build either artefact came from, so none of these says "different build",
 * "version mismatch" or which artefact is right. That conclusion survived on four surfaces after M68.1 first
 * removed it from three — the finding export, the step-through status and two focus-recall messages — which is
 * why they now share one class and one test, instead of each carrying its own string.
 */
public final class MismatchWording {

    private MismatchWording() {
    }

    /** A single-record finding export with a topology loaded but none of the record's nodes in it. */
    public static String findingHasNoDeclaredNode() {
        return "none of this record's node ids are declared in the loaded topology, so no cycle view was drawn — "
                + "the record and the graph disagree about which nodes exist";
    }

    /** The step-through status suffix when a stepped record writes ids the graph does not declare. */
    public static String stepUnknownSuffix(long unknown) {
        return "  ·  " + unknown + " not declared in this topology";
    }

    /** Recalling a saved focus none of whose nodes the graph declares. */
    public static String focusNoneDeclared(String focus, int size) {
        return "focus '" + focus + "': none of its " + size + " nodes are declared in this topology — the saved "
                + "focus and this graph disagree about which nodes exist";
    }

    /** Recalling a saved focus some of whose nodes the graph does not declare. */
    public static String focusPartlyDeclared(int missing, int size) {
        return missing + " of " + size + " nodes are not declared in this topology — the saved focus names nodes "
                + "this graph lacks";
    }
}
