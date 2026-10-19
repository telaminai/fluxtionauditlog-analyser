package telamin.fluxtion.audit.analyser.analyser.session;

/**
 * M74 P2b (owner request 2026-10-17): a prompt-pack example's download, as the {@code exampleDownload} node decided it.
 * The start page RENDERS this — the phase, the publisher line and the node's note — and decides nothing.
 *
 * @param phase     {@code IDLE}, {@code CONFIRMING}, {@code FETCHING}, {@code UNPACKING}, {@code DONE}, {@code CANCELLED},
 *                  {@code REFUSED} or {@code FAILED}
 * @param request   the press this is about
 * @param publisher "host / owner / repo @ short commit", or ""
 * @param thirdParty the publisher is not the Telamin GitHub organisation
 * @param note      the node's words for the phase, or ""
 * @param root      the unpacked project root, once DONE, or null
 * @param runnable  owner follow-up (2026-10-18): the node's one line naming the files the unpack made runnable, or ""
 * @param runnableAll every such file, one per line, for the line's tooltip, or ""
 */
public record ExampleDownloadState(String phase, long request, String publisher, boolean thirdParty, String note,
                                   String root, String runnable, String runnableAll) {

    public ExampleDownloadState {
        phase = phase == null ? "IDLE" : phase;
        publisher = publisher == null ? "" : publisher;
        note = note == null ? "" : note;
        runnable = runnable == null ? "" : runnable;
        runnableAll = runnableAll == null ? "" : runnableAll;
    }

    public ExampleDownloadState(String phase, long request, String publisher, boolean thirdParty, String note,
                                String root) {
        this(phase, request, publisher, thirdParty, note, root, "", "");
    }

    public static final ExampleDownloadState IDLE = new ExampleDownloadState("IDLE", 0, "", false, "", null);

    /** A download is between the person's Yes and its end: another press is refused. */
    public boolean busy() {
        return "CONFIRMING".equals(phase) || "FETCHING".equals(phase) || "UNPACKING".equals(phase);
    }
}
