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
    private String key = "", state = "idle", message = "", identityCheck = "";
    private SessionResumeStore.Snapshot candidate, withheld;
    private List<SessionResumeStore.Check> checks = List.of();
    private Plan plan;
    public SessionRecovery(OperationGate gate) { this.gate = gate; }
    @Override public void setLogger(EventLogger logger) { auditLog = logger; }

    public record Plan(SessionResumeStore.Snapshot snapshot, List<SessionResumeStore.Identity> available,
                       List<String> omitted) {
        public Plan { available = List.copyOf(available); omitted = List.copyOf(omitted); }
    }

    @OnEventHandler public boolean activate(ResumeEvents.Activated e) {
        generation++; candidate = null; withheld = null; identityCheck = ""; plan = null; checks = List.of(); key = "";
        state = "checking"; message = "Looking for this project's last available session; nothing opened";
        auditLog.info("recovery", "offer requested").info("generation", generation);
        return true;
    }
    @OnEventHandler public boolean offer(ResumeEvents.OfferLoaded e) {
        if (e.generation() != generation || !"checking".equals(state)) return stale();
        key = e.key() == null ? "" : e.key();
        candidate = e.snapshot();
        boolean project = !SessionResumeStore.NO_PROJECT.equals(key);
        if (candidate != null && !candidate.key().equals(key)) {
            candidate = null; state = "unavailable"; message = "Recovery belongs to another project";
        } else if (candidate != null && project
                && (e.profileIdentity() == null || candidate.profileIdentity() == null)) {
            // A missing identity never implies a match (edit-loop spec §E): the session predates profile
            // identity, or this profile has no creation nonce yet. Who captured it is unknown — withhold.
            state = "unavailable";
            message = "A session saved at this path on " + candidate.capturedAt() + " cannot be tied to this "
                    + "profile (it was saved before profile identity, or the profile has no identity yet); "
                    + "it is not offered";
            identityCheck = "unknown";
            withheld = candidate; candidate = null;
        } else if (candidate != null && project
                && !e.profileIdentity().equals(candidate.profileIdentity())) {
            // Same path, different profile: the project was deleted and recreated here. An unchanged input
            // hash says nothing about who captured it — withhold.
            state = "unavailable";
            message = "A session saved at this path on " + candidate.capturedAt() + " was captured by a different "
                    + "profile (the project was replaced or recreated at this path); it is not offered";
            identityCheck = "different profile at this path";
            withheld = candidate; candidate = null;
        } else if (e.error() != null) {
            candidate = null; state = "unavailable"; message = e.error();
        } else {
            state = candidate == null ? "none" : "offered";
            message = candidate == null ? "No saved session for this project"
                    : "Restore the session captured " + candidate.capturedAt() + " is available; nothing opened";
            if (candidate != null) identityCheck = project ? "same profile" : "no project";
        }
        auditLog.info("recovery", state).info("generation", generation);
        return true;
    }
    @OnEventHandler public boolean request(ResumeEvents.Requested e) {
        if (e.generation() != generation) {
            message = "That session offer was superseded; inspect the current offer"; return true;
        }
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
        boolean applying = "restoring".equals(state) && e.operationId() != Long.MIN_VALUE;
        if (e.generation() != generation || (!applying && !"verifying".equals(state))) return stale();
        if (applying && e.operationId() != gate.expectedOpId()) return stale();
        if (!applying && (gate.expectedOpId() != operationAtRequest || gate.inFlightWhat() != null)) {
            state = "offered"; message = "A newer open superseded this restore; request again when ready"; return true;
        }
        if (e.error() != null) { state = "unavailable"; message = e.error(); return true; }
        checks = List.copyOf(e.checks());
        if (!checks.stream().map(SessionResumeStore.Check::input).toList().equals(candidate.inputs())) {
            state = "unavailable"; message = "Recovery verification did not cover the offered inputs"; return true;
        }
        // Rechecks can only narrow the accepted plan; a later read cannot resurrect a refused input.
        var previouslyAvailable = applying ? plan.available() : candidate.inputs();
        boolean logsComplete = checks.stream().filter(c -> c.input().role().equals("log"))
                .allMatch(c -> c.unchanged() && previouslyAvailable.contains(c.input()));
        List<SessionResumeStore.Identity> available = new ArrayList<>();
        List<String> omitted = new ArrayList<>();
        for (var check : checks) {
            if (check.unchanged() && previouslyAvailable.contains(check.input()) && (!check.input().role().equals("log") || logsComplete)) available.add(check.input());
            else omitted.add(check.input().role() + ": " + check.input().path() + " — "
                    + (check.unchanged() ? "another member of this log set changed or is unavailable" : check.status()));
        }
        plan = new Plan(candidate, available, omitted);
        state = available.isEmpty() && !applying ? "unavailable" : "restoring";
        message = available.isEmpty() ? "No unchanged inputs can be restored" : "Opening verified inputs; completion is pending";
        auditLog.info("recovery", state).info("omitted", omitted.size());
        return true;
    }
    @OnEventHandler public boolean finished(ResumeEvents.Finished e) {
        if (e.generation() != generation || !"restoring".equals(state)) return stale();
        state = e.outcome().superseded() ? "offered" : "finished";
        message = e.outcome().message(); plan = null;
        auditLog.info("recovery", state).info("outcome", message);
        return true;
    }
    private static List<Map<String,String>> inputOrigin(SessionResumeStore.Snapshot snapshot) {
        return snapshot.inputs().stream().map(i -> Map.of("role", i.role(), "path", i.path(),
                "identity", i.sha256() == null ? "unverified" : "sha256")).toList();
    }
    private boolean stale() { auditLog.info("recovery", "stale completion ignored"); return false; }
    public long generation() { return generation; }
    public SessionResumeStore.Snapshot candidate() { return candidate; }
    public Plan plan() { return plan; }
    public List<SessionResumeStore.Check> checks() { return checks; }
    public boolean verifying() { return "verifying".equals(state); }
    public Map<String,Object> echo() {
        Map<String,Object> out = new LinkedHashMap<>();
        out.put("state", state); out.put("message", message); out.put("generation", generation);
        out.put("available", "offered".equals(state));
        if (candidate != null) {
            out.put("capturedAt", candidate.capturedAt());
            out.put("inputs", inputOrigin(candidate));
        }
        if (withheld != null) {
            // §E: a withheld session still discloses when it was captured and what it would have opened
            out.put("capturedAt", withheld.capturedAt());
            out.put("inputs", inputOrigin(withheld));
        }
        if (!identityCheck.isEmpty()) out.put("capturedBy", identityCheck);
        if (!checks.isEmpty()) out.put("checks", checks.stream().map(c -> Map.of("role", c.input().role(), "path", c.input().path(), "status", c.status())).toList());
        out.put("identityScope", "file bytes at session capture; not proof of application build or execution identity");
        return out;
    }
}
