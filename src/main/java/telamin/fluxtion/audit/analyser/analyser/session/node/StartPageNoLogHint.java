package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.PushReference;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

/**
 * Robot re-walk #3 (2026-10-18): a start-page question about evidence, asked while NO log is open, would get a general
 * answer with no warning. This node HOLDS such a question before anything else sees it — before the profile cards open
 * for it and before it is placed for an assistant — and the start page says so in one line with three actions:
 * <i>No log is open — the answer will be general. [Open the sample] [Open a log…] [Ask anyway]</i>. Nothing is placed and
 * nothing is sent until the person acts (rule 9: the hold, and when it ends, are decided here).
 *
 * <ul>
 *   <li><b>Ask anyway</b> continues the question as it was asked ({@link SessionEffects.ContinueStartPagePromptEffect}).</li>
 *   <li><b>Open the sample</b> and <b>Open a log…</b> run the person's own open ({@link SessionEffects.RunStartPageFlowEffect}
 *       with {@link #SAMPLE_FLOW} or {@link #LOG_FLOW}); when a log has opened the question continues, now about it. A
 *       chooser closed without a choice opens nothing: the performer reports it ({@link SessionEvents.StartPageFlowDone},
 *       not {@code DONE}) and the line returns to offering — still shown, its actions usable again.</li>
 *   <li>A new press replaces a held question; one that does not read as evidence, or asked with a log open, is never held.</li>
 * </ul>
 *
 * <p><b>Order.</b> Downstream of {@code openLog}; upstream of {@code assistantProfile} and {@code startPagePrompt} (a
 * JavaBean parent of both), which read {@link #holds} first and do nothing with a held press.
 */
public class StartPageNoLogHint implements EventLogSource {

    /** The person's own open of the sample project (no tour), then the question continues. */
    public static final String SAMPLE_FLOW = "NOLOG.sample";
    /** The person's own open-log chooser, then the question continues. */
    public static final String LOG_FLOW = "NOLOG.log";
    /** The start page's line while a question is held. */
    public static final String HINT = "No log is open — the answer will be general.";

    private final OpenLog openLog;
    @PushReference
    private final EffectQueue effects;

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    // node-local state (not final: a final field is constructor-mapped by the generator)
    private long held;
    private String heldEntry = "NONE";
    private long heldText;
    /** The person chose to open the sample or a log: the question continues once a log has opened. */
    private boolean opening;
    private long revision;

    public StartPageNoLogHint(OpenLog openLog, EffectQueue effects) {
        this.openLog = openLog;
        this.effects = effects;
    }

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    @OnEventHandler
    public boolean onSent(SessionEvents.StartPagePromptSent e) {
        if (e.aboutEvidence() && !e.noLogAcknowledged() && !openLog.isOpen()) {
            held = e.request();
            heldEntry = e.entryId() == null ? "NONE" : e.entryId();
            heldText = e.textRef();
            opening = false;
            auditLog.info("startPageNoLog", "HELD").info("request", e.request());
            return changed();
        }
        if (held == 0) return false;
        auditLog.info("startPageNoLog", "REPLACED").info("request", held);
        clear();
        return changed();
    }

    @OnEventHandler
    public boolean onAnswered(SessionEvents.StartPageNoLogAnswered e) {
        if (held == 0 || e.request() != held) {
            auditLog.info("staleFact", "StartPageNoLogAnswered").info("request", e.request()).info("held", held);
            return false;
        }
        auditLog.info("startPageNoLog", String.valueOf(e.choice())).info("request", held);
        switch (e.choice()) {
            case ASK_ANYWAY -> {
                proceed();
                return changed();
            }
            case OPEN_SAMPLE, OPEN_LOG -> {
                opening = true;
                effects.request(new SessionEffects.RunStartPageFlowEffect(0L, held,
                        e.choice() == SessionEvents.NoLogChoice.OPEN_SAMPLE ? SAMPLE_FLOW : LOG_FLOW));
                return changed();
            }
            default -> {
                return false;
            }
        }
    }

    /**
     * The person's open ended without a log (robot walk: a cancelled chooser left Open the sample and Open a log…
     * disabled, a dead end): the held question stays, and the line offers its actions again. A {@code DONE} is not
     * this node's — {@link #onLogOpened} continues the question; any other press's flow is not held here.
     */
    @OnEventHandler
    public boolean onFlowDone(SessionEvents.StartPageFlowDone e) {
        if (held == 0 || e.request() != held || !opening || e.outcome() == SessionEvents.FlowOutcome.DONE) return false;
        auditLog.info("startPageNoLog", "OFFER_AGAIN").info("request", held).info("outcome", e.outcome().name());
        opening = false;
        return changed();
    }

    /** A log opened: if the person chose to open one for the held question, the question continues about it. */
    @OnEventHandler
    public boolean onLogOpened(SessionEvents.LogOpened e) {
        if (held == 0 || !opening || !openLog.isOpen()) return false;
        auditLog.info("startPageNoLog", "LOG_OPENED").info("request", held);
        proceed();
        return changed();
    }

    private void proceed() {
        effects.request(new SessionEffects.ContinueStartPagePromptEffect(0L, held, heldEntry, heldText));
        clear();
    }

    private void clear() {
        held = 0;
        heldEntry = "NONE";
        heldText = 0;
        opening = false;
    }

    private boolean changed() {
        revision++;
        return true;
    }

    // ---- what children read -----------------------------------------------------------------------------------------

    /** Whether press {@code request} is held here (read by assistantProfile and startPagePrompt, downstream). */
    public boolean holds(long request) {
        return request != 0 && request == held;
    }

    /** The held press, or 0. */
    public long held() {
        return held;
    }

    /** The person chose to open a log for the held question and it has not opened yet. */
    public boolean opening() {
        return opening;
    }

    /** Bumped on every change, so startPagePrompt republishes. */
    public long revision() {
        return revision;
    }
}
