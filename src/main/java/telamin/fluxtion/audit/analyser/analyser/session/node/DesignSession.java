package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.audit.*;
import telamin.fluxtion.audit.analyser.analyser.design.*;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import java.util.*;

/** Authoritative design/result lifecycle; replayed through the same generated processor as log/project state. */
public class DesignSession implements EventLogSource, DesignWorkspace.State {
    private final OperationGate gate;
    private final EffectQueue effects;
    private EventLogger auditLog = NullEventLogger.INSTANCE;
    private DesignDocument document;
    private BuilderDocument builder;
    private DesignDiscovery discovery = DesignDiscovery.none();
    private long discoveryGeneration;
    private List<String> observedRoots = List.of();
    private String path, error = "", resultError = "";
    private ProducerResult result;
    private long generation;
    public DesignSession(OperationGate gate, EffectQueue effects) { this.gate = gate; this.effects = effects; }
    @Override public void setLogger(EventLogger logger) { auditLog = logger; }

    @OnEventHandler public boolean requested(DesignEvents.ReadRequested e) {
        generation++;
        auditLog.info("designRequest", e.kind()).info("generation", generation);
        return true;
    }

    @OnEventHandler public boolean read(DesignEvents.ReadCompleted e) {
        if (e.generation() != generation) { auditLog.info("designRead", "stale result ignored"); return false; }
        if (e.sessionOpen()) {
            if (!Objects.equals(path, e.file())) { document = null; builder = null; result = null; resultError = ""; }
            path = e.file();
        } else if (!Objects.equals(path, e.file())) return false;
        generation++; // a newer explicit read also supersedes an older background completion
        error = e.error() == null ? "" : e.error();
        if (e.document() != null) document = e.document(); // an intermediate parse error retains the last good revision
        if (e.builder() != null) { builder = e.builder(); document = null; }
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
    @OnEventHandler public boolean project(SessionEvents.ProfileApplied e) { if (!gate.accepted()) return false; clear(); requestDiscovery(); return true; }
    @OnEventHandler public boolean restored(SessionEvents.SettingsRestored e) { if (!gate.accepted()) return false; clear(); requestDiscovery(); return true; }
    @OnEventHandler public boolean roots(SessionEvents.SourceRootsObserved e) {
        if (observedRoots.equals(e.roots())) return false;
        observedRoots = e.roots(); requestDiscovery(); return true;
    }
    @OnEventHandler public boolean discover(DesignEvents.DiscoveryRequested e) { requestDiscovery(); return true; }
    private void requestDiscovery() {
        discovery = DesignDiscovery.none();
        effects.request(new SessionEffects.DiscoverDesignEffect(0, ++discoveryGeneration));
    }
    @OnEventHandler public boolean discovered(DesignEvents.DiscoveryCompleted e) {
        if (e.generation() != discoveryGeneration) return false;
        discovery = e.discovery();
        effects.request(new SessionEffects.PresentDesignDiscoveryEffect(0, discoveryGeneration,
                discovery.files().size() == 1 && path == null ? discovery.files().getFirst().path() : null));
        return true;
    }
    private void clear() { generation++; discoveryGeneration++; document = null; builder = null; path = null; result = null;
        discovery = DesignDiscovery.none(); observedRoots = List.of(); error = ""; resultError = ""; auditLog.info("designSession", "cleared"); }
    public DesignDocument document() { return document; }
    public BuilderDocument builder() { return builder; }
    public DesignDiscovery discovery() { return discovery; }
    public long discoveryGeneration() { return discoveryGeneration; }
    public String path() { return path; }
    public String error() { return error; }
    public ProducerResult result() { return result; }
    public String resultError() { return resultError; }
    public long generation() { return generation; }
    public String discoveryNote() {
        String note = discovery.source().startsWith("unavailable")
                ? "Definition discovery " + discovery.source() + ". No partial list is shown."
                : discovery.files().isEmpty()
                ? (discovery.unreadableFiles().isEmpty() ? "No design found. Add a source folder or open a design."
                        : "No readable design found.")
                : discovery.files().size() == 1
                ? "Definition discovered under authorised roots · relationship to this run unverified"
                : "Several definitions found. Open a design to inspect it.";
        return discovery.warning().isEmpty() ? note : note + "\n" + discovery.warning();
    }
    public Map<String, Object> echo() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("relationship", "unverified"); m.put("note", "working copy; matched by name; relationship to this run unknown");
        if (!discovery.warning().isEmpty()) m.put("note", m.get("note") + "\n" + discovery.warning());
        if (path != null) m.put("file", path);
        if (document != null) { m.put("revision", document.revision()); m.put("beans", document.beanIds()); }
        else if (builder != null) { m.put("revision", builder.revision()); m.put("beans", List.of()); }
        if (!error.isEmpty()) m.put("error", error);
        if (!resultError.isEmpty()) m.put("diagnosticsError", resultError);
        if (result != null) { m.put("diagnosticsFile", result.file()); m.put("inputs", result.relationship(document)); }
        // A failed first read provides no loaded form. Never infer Spring merely from the absence of a builder.
        if (path == null || document != null || builder != null)
            m.put("kind", path != null && discovery.files().isEmpty() ? (builder == null ? "spring" : "builder") : discovery.kind());
        if (!discovery.files().isEmpty()) m.put("files", discovery.files().stream().map(f -> Map.of("path", f.path(), "role", f.role())).toList());
        m.put("nodes", List.of());
        m.put("source", path != null && discovery.files().isEmpty() ? "explicit" : discovery.source());
        return m;
    }
}
