package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.*;
import telamin.fluxtion.audit.analyser.analyser.design.*;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import java.util.*;

/** Authoritative design/result lifecycle; replayed through the same generated processor as log/project state. */
public class DesignSession implements EventLogSource, DesignWorkspace.State {
    private final OperationGate gate;
    private EventLogger auditLog = NullEventLogger.INSTANCE;
    private DesignDocument document;
    private String path, error = "", resultError = "";
    private ProducerResult result;
    private long generation;
    public DesignSession(OperationGate gate) { this.gate = gate; }
    @Override public void setLogger(EventLogger logger) { auditLog = logger; }

    @OnEventHandler public boolean requested(DesignEvents.ReadRequested e) {
        generation++;
        auditLog.info("designRequest", e.kind()).info("generation", generation);
        return true;
    }

    @OnEventHandler public boolean read(DesignEvents.ReadCompleted e) {
        if (e.generation() != generation) { auditLog.info("designRead", "stale result ignored"); return false; }
        if (e.sessionOpen()) {
            if (!Objects.equals(path, e.file())) { document = null; result = null; resultError = ""; }
            path = e.file();
        } else if (!Objects.equals(path, e.file())) return false;
        generation++; // a newer explicit read also supersedes an older background completion
        error = e.error() == null ? "" : e.error();
        if (e.document() != null) document = e.document(); // an intermediate parse error retains the last good revision
        auditLog.info("designRead", error.isEmpty() ? "loaded" : "unavailable").info("generation", generation);
        return true;
    }
    @OnEventHandler public boolean result(DesignEvents.ResultReadCompleted e) {
        if (e.generation() != generation) return false;
        result = e.result(); // refusal clears the previous result
        resultError = e.error() == null ? "" : e.error();
        auditLog.info("producerResult", result == null ? "cleared" : result.stage());
        return true;
    }
    @OnEventHandler public boolean cleared(DesignEvents.Cleared e) { clear(); return true; }
    @OnEventHandler public boolean project(SessionEvents.ProfileApplied e) { if (!gate.accepted()) return false; clear(); return true; }
    @OnEventHandler public boolean restored(SessionEvents.SettingsRestored e) { if (!gate.accepted()) return false; clear(); return true; }
    private void clear() { generation++; document = null; path = null; result = null; error = ""; resultError = ""; auditLog.info("designSession", "cleared"); }
    public DesignDocument document() { return document; }
    public String path() { return path; }
    public String error() { return error; }
    public ProducerResult result() { return result; }
    public String resultError() { return resultError; }
    public long generation() { return generation; }
    public Map<String, Object> echo() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("relationship", "unverified"); m.put("note", "working copy; matched by name; relationship to this run unknown");
        if (path != null) m.put("file", path);
        if (document != null) { m.put("revision", document.revision()); m.put("beans", document.beanIds()); }
        if (!error.isEmpty()) m.put("error", error);
        if (!resultError.isEmpty()) m.put("diagnosticsError", resultError);
        if (result != null) { m.put("diagnosticsFile", result.file()); m.put("inputs", result.relationship(document)); }
        return m;
    }
}
