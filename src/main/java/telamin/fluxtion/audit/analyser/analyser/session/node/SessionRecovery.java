package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.*;
import telamin.fluxtion.audit.analyser.analyser.session.resume.*;
import java.util.*;

/** Explicit restore decisions. No filesystem, Swing, application execution or implicit acceptance. */
public class SessionRecovery implements EventLogSource {
    private final OperationGate gate;
    private EventLogger auditLog = NullEventLogger.INSTANCE;
    private long generation;
    private long operationAtRequest;
    private String key = "", state = "idle", message = "";
    private SessionResumeStore.Snapshot candidate;
    private List<SessionResumeStore.Check> checks = List.of();
    private Plan plan;
    public SessionRecovery(OperationGate gate) { this.gate = gate; }
    @Override public void setLogger(EventLogger logger) { auditLog = logger; }

    public record Plan(SessionResumeStore.Snapshot snapshot, List<SessionResumeStore.Identity> available,
                       List<String> omitted) {
        public Plan { available = List.copyOf(available); omitted = List.copyOf(omitted); }
    }

    @OnEventHandler public boolean activate(ResumeEvents.Activated e) {
        generation++; candidate = null; plan = null; checks = List.of(); key = "";
        state = "checking"; message = "Looking for this project's last available session; nothing opened";
        auditLog.info("recovery", "offer requested").info("generation", generation);
        return true;
    }
    @OnEventHandler public boolean offer(ResumeEvents.OfferLoaded e) {
        if (e.generation() != generation || !"checking".equals(state)) return stale();
        key = e.key() == null ? "" : e.key();
        candidate = e.snapshot();
        if (candidate != null && !candidate.key().equals(key)) {
            candidate = null; state = "unavailable"; message = "Recovery belongs to another project";
        } else if (e.error() != null) {
            candidate = null; state = "unavailable"; message = e.error();
        } else {
            state = candidate == null ? "none" : "offered";
            message = candidate == null ? "No saved session for this project" : "Restore last session is available; nothing opened";
        }
        auditLog.info("recovery", state).info("generation", generation);
        return true;
    }
    @OnEventHandler public boolean request(ResumeEvents.Requested e) {
        if (!"offered".equals(state) || candidate == null) {
            message = "No unaccepted session offer is available"; return true;
        }
        if (!e.accept()) {
            state = "dismissed"; message = "Session offer dismissed; nothing opened";
            auditLog.info("recovery", state); return true;
        }
        // The operation gate's in-flight profile/log transition has priority over a new restore.
        if (gate.inFlightWhat() != null) { message = "Wait for the current open or project transition before restoring"; return true; }
        plan = null;
        operationAtRequest = gate.expectedOpId();
        state = "verifying"; message = "Checking the offered files before restoring";
        auditLog.info("recovery", state).info("generation", generation);
        return true;
    }
    @OnEventHandler public boolean checked(ResumeEvents.Checked e) {
        if (e.generation() != generation || !"verifying".equals(state)) return stale();
        if (gate.expectedOpId() != operationAtRequest || gate.inFlightWhat() != null) {
            state = "offered"; message = "A newer open superseded this restore; request again when ready"; return true;
        }
        if (e.error() != null) { state = "unavailable"; message = e.error(); return true; }
        checks = List.copyOf(e.checks());
        if (!checks.stream().map(SessionResumeStore.Check::input).toList().equals(candidate.inputs())) {
            state = "unavailable"; message = "Recovery verification did not cover the offered inputs"; return true;
        }
        boolean logsComplete = checks.stream().filter(c -> c.input().role().equals("log")).allMatch(SessionResumeStore.Check::unchanged);
        List<SessionResumeStore.Identity> available = new ArrayList<>();
        List<String> omitted = new ArrayList<>();
        for (var check : checks) {
            if (check.unchanged() && (!check.input().role().equals("log") || logsComplete)) available.add(check.input());
            else omitted.add(check.input().role() + ": " + check.input().path() + " — "
                    + (check.unchanged() ? "another member of this log set changed or is unavailable" : check.status()));
        }
        plan = new Plan(candidate, available, omitted);
        state = available.isEmpty() ? "unavailable" : "restoring";
        message = available.isEmpty() ? "No unchanged inputs can be restored" : "Opening verified inputs; completion is pending";
        auditLog.info("recovery", state).info("omitted", omitted.size());
        return true;
    }
    @OnEventHandler public boolean finished(ResumeEvents.Finished e) {
        if (e.generation() != generation || !"restoring".equals(state)) return stale();
        state = "finished"; message = e.outcome(); plan = null;
        auditLog.info("recovery", state).info("outcome", message);
        return true;
    }
    private boolean stale() { auditLog.info("recovery", "stale completion ignored"); return false; }
    public long generation() { return generation; }
    public SessionResumeStore.Snapshot candidate() { return candidate; }
    public Plan plan() { return plan; }
    public boolean verifying() { return "verifying".equals(state); }
    public Map<String,Object> echo() {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("state", state); out.put("message", message);
        out.put("available", "offered".equals(state));
        if (candidate != null) {
            out.put("capturedAt", candidate.capturedAt());
            out.put("inputs", candidate.inputs().stream().map(i -> Map.of("role", i.role(), "path", i.path(), "identity", i.sha256() == null ? "unverified" : "sha256")).toList());
        }
        if (!checks.isEmpty()) out.put("checks", checks.stream().map(c -> Map.of("role", c.input().role(), "path", c.input().path(), "status", c.status())).toList());
        out.put("identityScope", "file bytes at session capture; not proof of application build or execution identity");
        return out;
    }
}
