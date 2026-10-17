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
        return changed();
    }

    @OnEventHandler
    public boolean onFetched(SessionEvents.ExampleFetched e) {
        if (stale(e.request(), "FETCHING", "ExampleFetched")) return false;
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
        root = e.rootShown();
        rootRef = e.rootRef();
        note = "Unpacked " + source.publisher() + " — nothing was built or run. Confirm the project to open it.";
        auditLog.info("example", "DONE");
        effects.request(new SessionEffects.OfferExampleProjectEffect(0L, request, rootRef));
        return changed();
    }

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
            default -> "the chosen folder cannot be used (" + problem + ")";
        };
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
            published = new ExampleDownloadState(phase, request, source == null ? "" : source.publisher(),
                    source != null && source.thirdParty(), note, root);
            dirty = false;
        }
        return published;
    }
}
