package telamin.fluxtion.audit.analyser.analyser.firstminute;

/** M79 copy in one place, shared by the stage, card, start-page failure and capture assertions. */
public final class FirstMinuteLines {
    private FirstMinuteLines() { }
    public static final String TITLE = "The diamond — bundled evidence";
    public static final String CALLOUT = "Click any record";
    public static final String TOPOLOGY = "Topology", RECORDS = "Records";
    public static final String LEARN = "Learn", INVESTIGATE = "Investigate", BUILD = "Build";
    public static final String GUIDED_TOUR = "Guided tour", OPEN_LOG = "Open audit log";
    public static final String TEMPLATE = "Start from a template", SAMPLE = "Sample project", CLOSE = "Close";
    public static final String SKIP = "Esc skips";
    /** M79-1b, the start page under 40 words (§5.4). */
    public static final String START_TITLE = "See what your code did.";
    public static final String THE_DIAMOND = "The diamond", OPEN_DIRECTLY = "Or open directly", ASK = "Ask…";
    /** Said before Learn ▸ The diamond replaces what is open (OD-79.29). */
    public static final String REPLACES = "The diamond opens in this window in place of what is open now. Your project and "
            + "its selected processor are not changed, and nothing is added to your recent files. Reopen your log from "
            + "Recent afterwards.";
    /** The shipped diamond's files, read back and matched to the manifest's pinned digests (E6). */
    public static final String VERIFIED = "Bundled with this run · verified by digest";
    public static String step(int step, int count) { return "Step " + step + " of " + count + " · " + SKIP; }
    /** r8 (owner, 2026-10-27): the strip names whose work the step shows — the shipped step's own caption. */
    public static String step(int step, int count, String author) {
        return author == null || author.isBlank() ? step(step, count)
                : "Step " + step + " of " + count + " · " + author + " · " + SKIP;
    }
    public static String failed(String reason) { return "The bundled diamond could not be opened: " + reason; }
}
