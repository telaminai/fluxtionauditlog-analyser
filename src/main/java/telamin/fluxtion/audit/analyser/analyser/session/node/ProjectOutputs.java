package telamin.fluxtion.audit.analyser.analyser.session.node;

import com.telamin.fluxtion.runtime.annotations.OnEventHandler;
import com.telamin.fluxtion.runtime.annotations.PushReference;
import com.telamin.fluxtion.runtime.audit.*;
import telamin.fluxtion.audit.analyser.analyser.session.*;
import java.util.*;

/** M81 D: lifetime, stability, offers, supersession and one-use person acceptance belong to this node. */
public class ProjectOutputs implements EventLogSource {
    public static final int POLL_MILLIS = 4000;
    private final ActiveProject project;
    private final OpenBundle bundle;
    private final LocalAgentSession assistant;
    private final OperationGate gate;
    private final SessionBoundary boundary;
    @PushReference private final EffectQueue effects;
    private EventLogger auditLog = NullEventLogger.INSTANCE;
    private long counter;
    private long watch;
    private long projectGeneration;
    private String profile;
    private boolean watching;
    private boolean baseline;
    private long lastScan = Long.MIN_VALUE;
    private Map<String, Candidate> candidates = new LinkedHashMap<>();
    private Map<String, ProjectOutputsState.Revision> consumed = new HashMap<>();
    private Map<Long, Candidate> announced = new HashMap<>();
    private Map<Long, Pending> pending = new LinkedHashMap<>();
    private Map<Long, Opening> opening = new HashMap<>();
    private Set<Long> shown = new HashSet<>();
    private Set<Opened> opened = new LinkedHashSet<>();
    private Set<Turn> turns = new LinkedHashSet<>();
    private ProjectOutputsState.File latestGraph;
    private record Turn(long generation, long ticket) { }
    private record Candidate(ProjectOutputsState.File file, long since, Turn owner) { }
    private record Pending(ProjectOutputsState.Offer offer, Turn owner) { }
    private record Opening(Pending pending, long operationBasis) { }
    private record Opened(Turn owner, String key, ProjectOutputsState.Revision revision) { }

    public ProjectOutputs(ActiveProject project, OpenBundle bundle, LocalAgentSession assistant, OperationGate gate,
                          SessionBoundary boundary, EffectQueue effects) {
        this.project = project; this.bundle = bundle; this.assistant = assistant;
        this.gate = gate; this.boundary = boundary; this.effects = effects;
    }
    @Override public void setLogger(EventLogger logger) { auditLog = logger; }
    @OnEventHandler public boolean onProfileApplied(SessionEvents.ProfileApplied e) {
        return gate.accepted() && begin(e.profilePath());
    }
    @OnEventHandler public boolean onStartup(SessionEvents.ProjectReopenRequested e) {
        return !(watching && Objects.equals(profile, e.profilePath())) && begin(e.profilePath());
    }
    private boolean begin(String path) {
        clear(); watch = ++counter; projectGeneration = project.generation(); profile = path;
        if (path != null && !bundle.provenance().fromBundle())
            effects.request(new SessionEffects.ObserveProjectOutputsEffect(0, watch, projectGeneration, path));
        auditLog.info("projectOutputs", "OBSERVING").info("watch", watch).info("projectGeneration", projectGeneration);
        return true;
    }
    @OnEventHandler public boolean onClosed(SessionEvents.SettingsRestored e) {
        if (!gate.accepted()) return false;
        clear(); watch = ++counter; profile = null; return true;
    }
    private void clear() {
        if (watch != 0) effects.request(new SessionEffects.StopProjectOutputsWatchEffect(0, watch));
        watching = false; baseline = false; lastScan = Long.MIN_VALUE;
        candidates.clear(); consumed.clear(); announced.clear(); pending.clear(); opening.clear();
        shown.clear(); opened.clear(); latestGraph = null;
    }
    @OnEventHandler public boolean onBasis(SessionEvents.ProjectOutputsBasisObserved e) {
        if (e.watch() != watch || e.projectGeneration() != projectGeneration || watching) return false;
        if (e.rootRef() == 0 || e.analyserOwned() || bundle.provenance().fromBundle()) {
            auditLog.info("projectOutputs", "NOT_WATCHED"); return true;
        }
        watching = true;
        effects.request(new SessionEffects.WatchProjectOutputsEffect(0, watch, projectGeneration, e.rootRef(), e.logDirRefs(), POLL_MILLIS));
        auditLog.info("projectOutputs", "WATCHING").info("watch", watch); return true;
    }
    @OnEventHandler public boolean onScan(SessionEvents.ProjectOutputsScanned e) {
        if (!current(e.watch(), e.projectGeneration()) || !e.complete() || e.atMillis() < lastScan) return false;
        lastScan = e.atMillis();
        Set<String> present = new HashSet<>();
        for (var file : e.files()) {
            if (!valid(file)) continue;
            String key = key(file); present.add(key);
            Candidate before = candidates.get(key);
            if (before == null || !before.file().revision().equals(file.revision())) {
                invalidate(key);
                before = new Candidate(file, e.atMillis(), observedOwner(file)); candidates.put(key, before);
            } else if (!before.file().equals(file)) {
                // Reference-store eviction can replace an opaque handle without changing this file revision.
                before = new Candidate(file, before.since(), before.owner()); candidates.put(key, before);
                refreshReferences(file);
            }
            if (!baseline) consumed.put(key, file.revision());
            if (baseline && !file.revision().equals(consumed.get(key)) && e.atMillis() - before.since() >= POLL_MILLIS) {
                consumed.put(key, file.revision()); long change = ++counter; announced.put(change, before);
                effects.request(new SessionEffects.PublishProjectOutputChangedEffect(0, watch, projectGeneration, change, before.file()));
                auditLog.info("projectOutput", "STABLE_METADATA").info("change", change).info("kind", file.kind());
            }
        }
        for (String missing : new ArrayList<>(candidates.keySet())) if (!present.contains(missing)) {
            candidates.remove(missing); consumed.remove(missing); invalidate(missing);
        }
        // A complete observation retires receipts for absent/superseded revisions, not arbitrary live candidates.
        opened.removeIf(o -> !present.contains(o.key()) || !candidates.get(o.key()).file().revision().equals(o.revision()));
        baseline = true; show(); return true;
    }
    private void invalidate(String key) {
        pending.values().removeIf(p -> key(p.offer().file()).equals(key));
        opening.values().removeIf(p -> key(p.pending().offer().file()).equals(key));
        if (latestGraph != null && key(latestGraph).equals(key)) { latestGraph = null; pairPendingLogs(null); }
    }
    private void refreshReferences(ProjectOutputsState.File file) {
        if (latestGraph != null && key(latestGraph).equals(key(file))) latestGraph = file;
        pending.replaceAll((id, p) -> new Pending(new ProjectOutputsState.Offer(id,
                key(p.offer().file()).equals(key(file)) ? file : p.offer().file(),
                p.offer().graph() != null && key(p.offer().graph()).equals(key(file)) ? file : p.offer().graph()), p.owner()));
    }
    @OnEventHandler public boolean onChange(SessionEvents.ProjectOutputChanged e) {
        if (!current(e.watch(), e.projectGeneration())) return false;
        Candidate candidate = announced.remove(e.change());
        Candidate latest = valid(e.file()) ? candidates.get(key(e.file())) : null;
        if (candidate == null || !key(candidate.file()).equals(key(e.file()))
                || !candidate.file().revision().equals(e.file().revision()) || latest == null
                || !latest.file().revision().equals(e.file().revision())) return false;
        var file = latest.file();
        if (file.kind() == ProjectOutputsState.Kind.GRAPHML) { latestGraph = file; pairPendingLogs(latestGraph); }
        if (!opened.contains(new Opened(candidate.owner(), key(file), file.revision())))
            pending.put(e.change(), new Pending(new ProjectOutputsState.Offer(e.change(), file,
                    file.kind() == ProjectOutputsState.Kind.LOG ? latestGraph : null), candidate.owner()));
        show(); return true;
    }
    private void pairPendingLogs(ProjectOutputsState.File graph) {
        List<Pending> replacements = new ArrayList<>();
        pending.values().removeIf(p -> {
            if (p.offer().file().kind() != ProjectOutputsState.Kind.LOG || Objects.equals(p.offer().graph(), graph)) return false;
            replacements.add(new Pending(new ProjectOutputsState.Offer(++counter, p.offer().file(), graph), p.owner())); return true;
        });
        for (Pending p : replacements) pending.put(p.offer().id(), p);
    }
    private void show() {
        shown.retainAll(pending.keySet());
        if (!watching || assistant.state().openTicket() != 0) return;
        for (Pending p : pending.values()) if (shown.add(p.offer().id()))
            effects.request(new SessionEffects.OfferProjectOutputEffect(0, watch, p.offer().id()));
    }
    @OnEventHandler public boolean onTurnStarted(SessionEvents.LocalAgentTurnStarted e) {
        var state = assistant.state();
        if (state.generation() != e.generation() || state.openTicket() != e.ticket()) return false;
        turns.add(new Turn(e.generation(), e.ticket()));
        while (turns.size() > 8) turns.remove(turns.iterator().next()); return true;
    }
    @OnEventHandler public boolean onTurnEnded(SessionEvents.LocalAgentTurnEnded e) { show(); return true; }
    @OnEventHandler public boolean onExited(SessionEvents.LocalAgentExited e) { show(); return true; }
    @OnEventHandler public boolean onAnswered(SessionEvents.ProjectOutputOfferAnswered e) {
        if (e.watch() != watch || !watching || !pending.containsKey(e.offer())) return false;
        if (e.open() && boundary.projectTransitionPending()) {
            effects.request(new SessionEffects.ShowStatusEffect(0, "The project change is still pending — finish it before opening this output")); return true;
        }
        Pending accepted = pending.remove(e.offer());
        if (e.open()) {
            opening.put(e.offer(), new Opening(accepted, gate.expectedOpId()));
            effects.request(new SessionEffects.OpenProjectOutputEffect(0, watch, projectGeneration, e.offer(), accepted.offer().file(), accepted.offer().graph()));
        }
        auditLog.info("projectOutputAnswer", e.open() ? "OPEN" : "NOT_NOW").info("offer", e.offer()); return true;
    }
    /** Revocable node permission for a deferred EDT effect; a later request cannot lend its context. */
    public boolean permitsOpen(long epoch, long offer, long generation) {
        Opening own = opening.get(offer);
        return current(epoch, generation) && own != null && !boundary.projectTransitionPending()
                && own.operationBasis() == gate.expectedOpId();
    }
    public boolean permitsCompletion(long epoch, long offer, long generation) { return current(epoch, generation) && opening.containsKey(offer); }
    public boolean permitsWatch(long epoch, long generation) { return current(epoch, generation); }
    @OnEventHandler public boolean onOpened(SessionEvents.ProjectOutputOpenObserved e) {
        if (e.watch() != watch) return false;
        Opening own = opening.remove(e.offer()); if (own == null) return false;
        if (!e.opened()) {
            effects.request(new SessionEffects.ShowStatusEffect(0, "The output was not opened — check the load status before trying again"));
            // Failed switches retain the old project, but never resurrect an OLD button or queued act.
            Candidate latest = candidates.get(key(own.pending().offer().file()));
            if (latest != null && latest.file().revision().equals(own.pending().offer().file().revision())) {
                long id = ++counter;
                pending.put(id, new Pending(new ProjectOutputsState.Offer(id, latest.file(),
                        latest.file().kind() == ProjectOutputsState.Kind.LOG ? latestGraph : null), own.pending().owner())); show();
            }
        }
        return true;
    }
    @OnEventHandler public boolean onHostedOpened(SessionEvents.HostedProjectOutputOpened e) {
        if (!current(e.watch(), e.projectGeneration()) || !valid(e.file())) return false;
        if (assistant.state().posture() == null || !assistant.state().posture().isFull()) return false;
        Turn owner = new Turn(e.generation(), e.ticket());
        if (e.ticket() == 0 || !turns.contains(owner)) return false;
        opened.add(new Opened(owner, key(e.file()), e.file().revision()));
        // Eight known turns × one hundred bounded scan candidates; don't truncate a supported hundred-file burst.
        while (opened.size() > 800) opened.remove(opened.iterator().next());
        return pending.values().removeIf(p -> p.owner().equals(owner) && key(p.offer().file()).equals(key(e.file()))
                && p.offer().file().revision().equals(e.file().revision()));
    }
    private Turn currentTurn() {
        var state = assistant.state(); return state.openTicket() == 0 ? new Turn(0, 0) : new Turn(state.generation(), state.openTicket());
    }
    private Turn observedOwner(ProjectOutputsState.File file) {
        Turn active = currentTurn();
        if (active.ticket() != 0) return active;
        // A short turn can finish between polls. Only a completed, scope-checked open of this EXACT revision
        // supplies its original ticket here; neither the current session nor the newest receipt is substituted.
        return opened.stream().filter(o -> o.key().equals(key(file)) && o.revision().equals(file.revision()))
                .map(Opened::owner).findFirst().orElse(active);
    }
    private boolean current(long epoch, long generation) { return watching && epoch == watch && generation == projectGeneration && !bundle.provenance().fromBundle(); }
    private static String key(ProjectOutputsState.File file) { return file.kind() + ":" + file.pathKey(); }
    private static boolean valid(ProjectOutputsState.File f) {
        return f != null && f.kind() != null && f.pathRef() != 0 && f.pathKey() != null && f.revision() != null && f.revision().size() > 0;
    }
    public ProjectOutputsState state() {
        return new ProjectOutputsState(watch, projectGeneration, watching,
                watching && assistant.state().openTicket() == 0 ? pending.values().stream().map(Pending::offer).toList() : List.of());
    }
}
