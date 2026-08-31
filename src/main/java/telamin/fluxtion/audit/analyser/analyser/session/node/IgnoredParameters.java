package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.EventLogSource;
import com.telamin.fluxtion.runtime.audit.EventLogger;
import com.telamin.fluxtion.runtime.audit.NullEventLogger;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * <b>Which act does a combined request mean, and what did it therefore not honour?</b>
 *
 * <p>{@code open} is a compatibility surface: one verb carries every lifecycle act, because the
 * surface does not grow a new verb for a concept an existing one already names. That makes
 * combinations expressible which are not coherent — {@code open {project, log}} has no reading,
 * because a project switch is a session boundary (M35.5) and whatever the log arrived into would be
 * swept away by the switch.
 *
 * <p>The rule was right and it lived in three near-identical blocks of
 * {@code ActionExecutor.doOpen}, each with its own hand-written sentence. Three copies of a
 * precedence order is three places for it to drift, and the sentences are the part a caller actually
 * reads. Both are stated once here.
 *
 * <p><b>Why naming them matters more than it looks.</b> A parameter silently dropped reads to the
 * caller exactly like one that was honoured. On the agent path there is nobody to notice the log they
 * asked for never opened — so the echo has to say it, and this is where it is decided.
 */
public class IgnoredParameters implements EventLogSource {

    /**
     * Largest act first. A request naming several is read as the largest, because that is the one
     * whose side effects would invalidate the others.
     */
    private static final List<String> PRECEDENCE =
            List.of("project", "analysis", "close", "discover", "logs", "log", "graphml", "processor");

    /**
     * Parameters that MODIFY another rather than competing with it. {@code bind} supplies an
     * analysis's parameters; it is not a rival act and must never be reported as ignored when the
     * analysis it belongs to is the thing being run.
     */
    private static final Map<String, List<String>> SUBORDINATES = Map.of(
            "analysis", List.of("bind"),
            "log", List.of("format", "provenance"),
            "logs", List.of("provenance"));

    /** Why each act wins, in the caller's terms rather than the implementation's. */
    private static final Map<String, String> BECAUSE = Map.of(
            "project", "'project' was also given; a project switch is a session boundary, so open the "
                    + "log and graph in a second call, inside the new project",
            "analysis", "'analysis' was also given; its steps decide what opens",
            "close", "'close' was also given, and closing is what the request means");

    /** What a request meant, and what it therefore left alone. */
    public record Decision(String honoured, List<String> ignored, String why) {

        public Decision {
            ignored = ignored == null ? List.of() : List.copyOf(ignored);
        }

        public boolean anythingIgnored() {
            return !ignored.isEmpty();
        }
    }

    private EventLogger auditLog = NullEventLogger.INSTANCE;
    private Decision decision = new Decision(null, List.of(), null);

    @Override
    public void setLogger(EventLogger log) {
        this.auditLog = log;
    }

    @OnEventHandler
    public boolean onOpenRequestReceived(SessionEvents.OpenRequestReceived event) {
        decision = decide(event.supplied());
        if (decision.anythingIgnored()) {
            auditLog.info("honoured", decision.honoured())
                    .info("ignored", String.join(",", decision.ignored()))
                    .info("reason", decision.why());
        } else {
            auditLog.info("honoured", String.valueOf(decision.honoured()))
                    .info("ignored", "none");
        }
        return true;
    }

    static Decision decide(java.util.Set<String> supplied) {
        if (supplied == null || supplied.isEmpty()) {
            return new Decision(null, List.of(), null);
        }
        String honoured = null;
        for (String candidate : PRECEDENCE) {
            if (supplied.contains(candidate)) {
                honoured = candidate;
                break;
            }
        }
        if (honoured == null) {
            return new Decision(null, List.of(), null);
        }
        List<String> subordinate = SUBORDINATES.getOrDefault(honoured, List.of());
        List<String> ignored = new ArrayList<>();
        for (String name : PRECEDENCE) {
            if (!name.equals(honoured) && supplied.contains(name) && !subordinate.contains(name)) {
                ignored.add(name);
            }
        }
        for (String extra : new java.util.TreeSet<>(supplied)) {
            if (!PRECEDENCE.contains(extra) && !extra.equals(honoured) && !subordinate.contains(extra)) {
                ignored.add(extra);
            }
        }
        return new Decision(honoured, ignored, ignored.isEmpty() ? null : BECAUSE.get(honoured));
    }

    /**
     * Decide for a request without dispatching one — the read a surface needs when it is about to
     * perform the act itself and only wants the echo. The dispatched path
     * ({@link #onOpenRequestReceived}) is what puts the decision in the audit record.
     */
    public Decision apply(java.util.Set<String> supplied) {
        return decide(supplied);
    }

    public Decision decision() {
        return decision;
    }

    /** The precedence, exposed so a surface can DOCUMENT it rather than restate it. */
    public static List<String> precedence() {
        return PRECEDENCE;
    }
}
