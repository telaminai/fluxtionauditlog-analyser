package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.PushReference;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.assistant.ExampleSource;
import telamin.fluxtion.audit.analyser.analyser.session.ExampleDownloadState;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

/**
 * M74 P2b (owner request 2026-10-17, with the owner's addition of the same day): a prompt pack's <b>example</b> entry,
 * downloaded and unpacked — every step decided here (rule 9); fetching and unpacking are effects that answer with facts.
 *
 * <ol>
 *   <li>The entry's own button ({@link SessionEvents.ExampleDownloadRequested}). Nothing was fetched on the drop or on
 *       choosing. The source is checked again here: https, an allowed host, a pinned 40-character commit.</li>
 *   <li>The confirmation, showing the exact URL, the publisher (third-party unless Telamin's), the commit and the target
 *       folder the person chooses. No → nothing is fetched.</li>
 *   <li>The fetch. With an integrity digest in the entry, the archive's SHA-256 must equal it BEFORE anything is
 *       unpacked; a mismatch is refused with a named reason and the bytes discarded — never silently accepted.</li>
 *   <li>The unpack, through the template archive's containment checks, with no executable bits. Nothing is built, run
 *       or installed.</li>
 *   <li>The M73 project offer for the unpacked folder: the person confirms. A download never implies Claude Code folder
 *       trust: that is Start's own question, asked there.</li>
 * </ol>
 */
public class ExampleDownload implements EventLogSource {

    @PushReference
    private final EffectQueue effects;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    private String phase = "IDLE";
    private long request;
    private ExampleSource source;
    private String target;
    /** R6: the chosen folder and the unpacked root by reference (the frame resolves them); facts carry no path. */
    private long targetRef;
    private long rootRef;
    private long bytesRef;
    private String note = "";
    /** Owner follow-up (2026-10-18): the files the unpack made runnable (relative paths), for the line; empty: none. */
    private java.util.List<String> runnable = java.util.List.of();
    private String root;
    private ExampleDownloadState published = ExampleDownloadState.IDLE;
    private boolean dirty = true;

    public ExampleDownload(EffectQueue effects) {
        this.effects = effects;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    @OnEventHandler
    public boolean onRequested(SessionEvents.ExampleDownloadRequested e) {
        if (busy()) {
            auditLog.info("exampleRefused", "BUSY").info("request", e.request());
            note = "a download is already in progress — wait for it, or cancel its confirmation";
            return changed();
        }
        request = e.request();
        root = null;
        rootRef = 0;
        targetRef = 0;
        bytesRef = 0;
        target = null;
        ExampleSource s = e.source();
        ExampleSource.Parsed again = s == null ? null : ExampleSource.parse(s.url(), s.sha256());
        if (again == null || again.refusal() != null) {
            source = null;
            phase = "REFUSED";
            note = "This example was not downloaded: " + (again == null ? "it names no archive"
                    : again.refusal().words) + ".";
            auditLog.info("exampleRefused", again == null ? "NO_SOURCE" : again.refusal().name());
            return changed();
        }
        source = again.source();
        phase = "CONFIRMING";
        runnable = java.util.List.of();
        note = "";
        auditLog.info("example", "CONFIRMING").info("thirdParty", source.thirdParty())
                .info("digest", source.sha256() != null);
        effects.request(new SessionEffects.ConfirmExampleDownloadEffect(0L, request, source, source.thirdParty()));
        return changed();
    }

    @OnEventHandler
    public boolean onConfirmed(SessionEvents.ExampleDownloadConfirmed e) {
        if (stale(e.request(), "CONFIRMING", "ExampleDownloadConfirmed")) return false;
        if (!e.yes()) {
            phase = "CANCELLED";
            note = "Nothing was downloaded.";
            auditLog.info("example", "CANCELLED");
            return changed();
        }
        if (e.targetRef() == 0 || e.targetProblem() != null) {
            phase = "REFUSED";
            note = "Nothing was downloaded: " + targetWords(e.targetProblem()) + ".";
            auditLog.info("exampleRefused", String.valueOf(e.targetProblem()));
            return changed();
        }
        target = e.targetShown();
        targetRef = e.targetRef();
        phase = "FETCHING";
        note = "Downloading " + source.publisher() + "…";
        auditLog.info("example", "FETCHING");
        effects.request(new SessionEffects.FetchExampleEffect(0L, request, source.url(), ExampleSource.MAX_ARCHIVE_BYTES));
        effects.request(new SessionEffects.StartExampleClockEffect(0L, request, phase, FETCH_DEADLINE_MS));
        return changed();
    }

    @OnEventHandler
    public boolean onFetched(SessionEvents.ExampleFetched e) {
        if (stale(e.request(), "FETCHING", "ExampleFetched")) {
            // review S7: a fetch that lands after its deadline is not used, and its bytes are not kept either
            if (e.ok() && e.bytesRef() != 0) effects.request(new SessionEffects.DiscardExampleBytesEffect(0L, e.request(), e.bytesRef()));
            return false;
        }
        if (!e.ok()) {
            phase = "FAILED";
            note = "The example could not be downloaded: " + safe(e.failure()) + ".";
            auditLog.info("example", "FAILED").info("at", "fetch");
            return changed();
        }
        bytesRef = e.bytesRef();
        if (source.sha256() != null && !source.sha256().equalsIgnoreCase(String.valueOf(e.sha256()))) {
            phase = "REFUSED";
            note = "Not unpacked: the archive's SHA-256 is " + safe(e.sha256()) + ", but the pack pins "
                    + source.sha256() + " — the archive is not the one the pack names.";
            auditLog.info("exampleRefused", "DIGEST_MISMATCH");
            effects.request(new SessionEffects.DiscardExampleBytesEffect(0L, request, bytesRef));
            bytesRef = 0;
            return changed();
        }
        phase = "UNPACKING";
        note = "Unpacking into " + target + "…";
        auditLog.info("example", "UNPACKING").info("verified", source.sha256() != null);
        effects.request(new SessionEffects.UnpackExampleEffect(0L, request, bytesRef, targetRef));
        effects.request(new SessionEffects.StartExampleClockEffect(0L, request, phase, UNPACK_DEADLINE_MS));
        return changed();
    }

    @OnEventHandler
    public boolean onUnpacked(SessionEvents.ExampleUnpacked e) {
        if (stale(e.request(), "UNPACKING", "ExampleUnpacked")) return false;
        effects.request(new SessionEffects.DiscardExampleBytesEffect(0L, request, bytesRef));
        bytesRef = 0;
        if (!e.ok()) {
            phase = "FAILED";
            note = "The example was not unpacked: " + safe(e.failure()) + ".";
            auditLog.info("example", "FAILED").info("at", "unpack");
            return changed();
        }
        phase = "DONE";
        runnable = e.runnable().stream().map(ExampleDownload::safe).toList();
        root = e.rootShown();
        rootRef = e.rootRef();
        note = "Unpacked " + source.publisher() + " — nothing was built or run. Confirm the project to open it.";
        auditLog.info("example", "DONE");
        effects.request(new SessionEffects.OfferExampleProjectEffect(0L, request, rootRef));
        return changed();
    }

    /**
     * Review S7 (P2, 2026-10-17): a fetch or an unpack that has not answered by its deadline is FAILED, so the entry is
     * never left busy for ever (a stalled body, a performer that died). Its late answer then arrives stale and is ignored.
     */
    @OnEventHandler
    public boolean onDeadline(SessionEvents.ExampleDeadlinePassed e) {
        if (stale(e.request(), String.valueOf(e.phase()), "ExampleDeadlinePassed")) return false;
        boolean fetching = "FETCHING".equals(phase);
        phase = "FAILED";
        note = fetching ? "The example could not be downloaded: it did not arrive in time (nothing was unpacked)."
                : "The example was not unpacked: unpacking did not finish in time.";
        auditLog.info("example", "FAILED").info("at", fetching ? "fetchDeadline" : "unpackDeadline");
        if (bytesRef != 0) {
            effects.request(new SessionEffects.DiscardExampleBytesEffect(0L, request, bytesRef));
            bytesRef = 0;
        }
        return changed();
    }

    /** The deadlines (review S7): above the transport's own body deadline, so the transport normally answers first. */
    public static final long FETCH_DEADLINE_MS = 6 * 60_000L;
    public static final long UNPACK_DEADLINE_MS = 2 * 60_000L;

    @OnEventHandler(propagate = false)
    public boolean onEffectStarted(SessionEvents.ExampleEffectStarted e) {
        auditLog.info("exampleEffect", e.what()).info("request", e.request());
        return false;
    }

    private boolean stale(long req, String expected, String fact) {
        if (req == request && expected.equals(phase)) return false;
        auditLog.info("staleFact", fact).info("request", req).info("phase", phase);
        return true;
    }

    private boolean busy() {
        return "CONFIRMING".equals(phase) || "FETCHING".equals(phase) || "UNPACKING".equals(phase);
    }

    static String targetWords(String problem) {
        if (problem == null) return "no folder was chosen";
        return switch (problem) {
            case "EXISTS_NOT_EMPTY" -> "the chosen folder is not empty — choose a new or empty folder";
            case "NOT_A_DIRECTORY" -> "the chosen location is not a folder";
            case "NO_PARENT" -> "the chosen folder's parent does not exist";
            case "NOT_ABSOLUTE" -> "the chosen folder is not a full path — type the whole path, or use Choose…";
            case "CONFIRM_FAILED" -> "the confirmation could not be shown";
            default -> "the chosen folder cannot be used (" + problem + ")";
        };
    }

    /** At most this many file names in the runnable line; the rest are counted, and all are in its tooltip. */
    public static final int RUNNABLE_SHOWN = 5;

    /**
     * Owner follow-up (2026-10-18): the one line that says which of the example's files were made runnable — "Runnable
     * after unpacking: mvnw, regen.sh" — the first few and "and N more" past {@link #RUNNABLE_SHOWN}; "" for none.
     */
    public static String runnableLine(java.util.List<String> files) {
        if (files == null || files.isEmpty()) return "";
        int shown = files.size() > RUNNABLE_SHOWN ? RUNNABLE_SHOWN - 1 : files.size();
        String line = "Runnable after unpacking: " + String.join(", ", files.subList(0, shown));
        return shown < files.size() ? line + " and " + (files.size() - shown) + " more" : line;
    }

    private static String safe(String s) {
        if (s == null) return "unknown";
        StringBuilder b = new StringBuilder();
        s.codePoints().limit(300).forEach(cp -> b.appendCodePoint(Character.isISOControl(cp) ? ' ' : cp));
        return b.toString();
    }

    private boolean changed() {
        dirty = true;
        return true;
    }

    public ExampleDownloadState state() {
        if (dirty) {
            boolean done = "DONE".equals(phase);
            published = new ExampleDownloadState(phase, request, source == null ? "" : source.publisher(),
                    source != null && source.thirdParty(), note, root, done ? runnableLine(runnable) : "",
                    done ? String.join("\n", runnable) : "");
            dirty = false;
        }
        return published;
    }
}
