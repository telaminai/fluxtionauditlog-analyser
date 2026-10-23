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
    public static String step(int step, int count) { return "Step " + step + " of " + count + " · " + SKIP; }
    public static String failed(String reason) { return "The bundled diamond could not be opened: " + reason; }
}
