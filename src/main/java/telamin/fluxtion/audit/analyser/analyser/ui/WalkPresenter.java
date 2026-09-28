package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ConfigStore;
import telamin.fluxtion.audit.analyser.analyser.config.WalkBin;
import telamin.fluxtion.audit.analyser.analyser.config.GraphSpec;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEffects;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkIdentity;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkResolver;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkSteps;

import javax.swing.Timer;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * M69 — PERFORMS what the {@code walkPlayback} session node asks (spec-spotlight-walks.md §3.4, §3.8). It decides
 * nothing about the walk: which step, when to light and when to end are the node's. It applies a step's view through
 * transient primitives (never a persisting chart path — review R2), waits for the charts that step involves on a
 * non-blocking timer (never the saved-analysis busy-wait — R3), resolves each target against what is on screen, and
 * REPORTS: {@link SessionEvents.WalkViewApplied} at once, {@link SessionEvents.WalkStepPrepared} when ready.
 *
 * <p>Its one piece of local state is the ticket of the preparation in flight, so a superseded timer stops rather than
 * reporting; the node refuses a stale report anyway, so this is economy, not the guard.
 */
final class WalkPresenter {

    /** How long a step may take to prepare before its targets are resolved as they stand. */
    static final int PREPARE_BOUND_MS = 5_000;
    static final int POLL_MS = 50;

    /** What the presenter needs from the frame — every call a primitive, none a decision. */
    interface Frame {
        AppConfig config();

        LogStore store();

        FilterState filter();

        GraphTabs graphs();

        TopologyPanel topology();

        void selectTab(String tab);

        /** Select a record the current filter shows; false when it is hidden or out of range. */
        boolean selectRecord(int modelRow);

        /** Whether the record selected NOW is exactly {@code modelRow} — what the detail pane shows (review PR57 R5). */
        boolean recordSelected(int modelRow);

        List<String> runBasisNow();

        SpotlightTarget.Resolution resolve(String target);

        /**
         * Light a set through the ordinary spotlight path, as the walk's own act; the lit count, or -1 and why. Each
         * request carries the number the session assigned it (review PR57 R3), which the overlay must draw.
         */
        LitResult light(List<Numbered> requests);

        void clearWalkSpotlight();

        void post(Object fact);
    }

    record LitResult(int lit, String reason) { }

    /** A spotlight request with the number the session gave its target — not its position in the lit subset. */
    record Numbered(SpotlightTarget.Request request, int n) { }

    private final Frame frame;
    private long preparing = -1;
    private Timer timer;

    WalkPresenter(Frame frame) {
        this.frame = frame;
    }

    // ---- effects ----------------------------------------------------------------------------------------------

    SessionEvents.Result applyView(SessionEffects.ApplyWalkViewEffect e) {
        WalkSpec walk = e.walk();                            // review PR57 R6: the node's frozen definition, not config
        if (e.step() < 0 || e.step() >= walk.steps().size()) {
            return refused(e, "walk '" + walk.name() + "' has " + walk.steps().size() + " step(s)");
        }
        WalkSpec.Step step = walk.steps().get(e.step());
        String problem = WalkSteps.problem(step);            // validate the whole view before applying any of it
        if (problem != null) return refused(e, problem);
        if (step.view().filter() != null && !Set.of("DIMENSION", "RAW_EVENT").contains(step.view().filter().groupMode())) {
            return refused(e, "the step's filter grouping is not DIMENSION or RAW_EVENT");
        }
        // review PR57 R5 (§3.4): a record that is not in this log refuses the WHOLE view, before anything changes
        Integer record = step.view().record();
        LogStore store = frame.store();
        if (record != null && (store == null || record >= store.size())) {
            return refused(e, "record " + record + " is not in this log (" + (store == null ? 0 : store.size()) + " records)");
        }
        // only now does the previous step's spotlight go out: a refused view leaves the previous step on screen
        frame.clearWalkSpotlight();
        List<String> notes = apply(step.view());
        prepare(e.ticket(), e.generation(), walk, e.step(), notes, e.recordsTrusted());
        return new SessionEvents.WalkViewApplied(e.opId(), e.ticket(), true, "");
    }

    SessionEvents.Result resolveAgain(SessionEffects.ResolveWalkTargetsEffect e) {
        WalkSpec walk = e.walk();
        if (e.step() >= 0 && e.step() < walk.steps().size()) {
            prepare(e.ticket(), e.generation(), walk, e.step(), List.of(e.why()), e.recordsTrusted());
        }
        return new SessionEvents.WalkAcknowledged(e.opId(), e.ticket(), "resolve");
    }

    SessionEvents.Result light(SessionEffects.LightWalkTargetsEffect e) {
        if (e.targets().isEmpty()) {                       // the node says nothing is lit now: take the walk's light down
            frame.clearWalkSpotlight();
            return new SessionEvents.WalkTargetsLit(e.opId(), e.ticket(), 0, "");
        }
        List<Numbered> requests = new ArrayList<>();
        for (SessionEvents.WalkTargetState t : e.targets()) {
            requests.add(new Numbered(new SpotlightTarget.Request(t.target(), t.caption()), t.n()));
        }
        LitResult r = frame.light(requests);
        return new SessionEvents.WalkTargetsLit(e.opId(), e.ticket(), Math.max(0, r.lit()), r.reason());
    }

    SessionEvents.Result end(SessionEffects.EndWalkEffect e) {
        cancel();
        frame.clearWalkSpotlight();
        return new SessionEvents.WalkAcknowledged(e.opId(), e.ticket(), "end");
    }

    void cancel() {
        preparing = -1;
        if (timer != null) timer.stop();
        timer = null;
    }

    // ---- the view: transient primitives only (§3.3) -----------------------------------------------------------

    /** Apply the view in the documented order; return the notes on what could not be applied, and why. */
    List<String> apply(WalkSpec.View view) {
        List<String> notes = new ArrayList<>();
        // review PR57 R8 (§3.3): a stored step without a filter takes EVERY default — never the person's current
        // selection. Applied here, at playback, so older and imported definitions are covered as well as new ones.
        WalkSpec.Filter f = view.filter() == null ? WalkSpec.Filter.ALL : view.filter();
        if (frame.filter() != null) {
            frame.filter().setAll(f.from(), f.to(), FilterState.GroupMode.valueOf(f.groupMode()),
                    f.dimensions() == null ? null : new HashSet<>(f.dimensions()), f.text());
        }
        if (view.tab() != null) frame.selectTab(view.tab());
        if (view.record() != null && !frame.selectRecord(view.record())) {
            notes.add("record " + view.record() + " is hidden by this step's filter, or not in this log");
        }
        if (view.graph() != null) {
            GraphTabs g = frame.graphs();
            if (g.graphNamed(view.graph()) != null) {
                frame.selectTab("graph");
                g.selectGraph(view.graph());                 // transient: selecting persists nothing
            } else if (g.hasDefinition(view.graph())) {
                notes.add("chart '" + view.graph() + "' is closed — open it to see this step");   // never opened by the walk
            } else {
                notes.add("no chart named '" + view.graph() + "'");
            }
        }
        if (view.focus() != null) {
            var spec = frame.config().namedFocuses.stream().filter(x -> x.name().equals(view.focus().name())).findFirst();
            if (spec.isEmpty()) {
                notes.add("focus '" + view.focus().name() + "' is not saved — not applied");
            } else if (view.focus().digest().isBlank()
                    || !ConfigStore.focusDefinitionDigest(spec.get()).equals(view.focus().digest())) {
                notes.add("focus '" + view.focus().name() + "' now has a different definition — not applied");
            } else {
                String problem = frame.topology().recallFocus(view.focus().name());
                if (problem != null) notes.add(problem);
            }
        }
        return notes;
    }

    // ---- preparation: bounded, cancellable, non-blocking (§3.4) ---------------------------------------------

    private void prepare(long ticket, long generation, WalkSpec walk, int stepIndex, List<String> notes, boolean trusted) {
        cancel();
        preparing = ticket;
        WalkSpec.Step step = walk.steps().get(stepIndex);
        Set<String> charts = chartsOf(step);
        long deadline = System.currentTimeMillis() + PREPARE_BOUND_MS;
        timer = new Timer(POLL_MS, null);
        timer.addActionListener(ev -> {
            if (preparing != ticket) { ((Timer) ev.getSource()).stop(); return; }
            boolean timedOut = System.currentTimeMillis() >= deadline;
            if (!timedOut && !ready(charts)) return;
            ((Timer) ev.getSource()).stop();
            preparing = -1;
            List<String> all = new ArrayList<>(notes);
            if (timedOut && !ready(charts)) all.add("a chart did not finish drawing in " + (PREPARE_BOUND_MS / 1000) + " s");
            frame.post(new SessionEvents.WalkStepPrepared(ticket, generation, states(walk, step, trusted), String.join("; ", all)));
        });
        timer.setInitialDelay(0);
        timer.start();
    }

    private boolean ready(Set<String> charts) {
        for (String name : charts) {
            GraphPanel g = frame.graphs().graphNamed(name);
            if (g == null) continue;                          // not open: nothing to wait for; resolution says so
            if ("pending".equals(g.scopeFacts().get("extraction"))) return false;
            if (!g.chartPanel().drawnFact().settled()) return false;
        }
        return true;
    }

    private Set<String> chartsOf(WalkSpec.Step step) {
        Set<String> out = new LinkedHashSet<>();
        if (step.view().graph() != null) out.add(step.view().graph());
        for (WalkSpec.Target t : step.targets()) {
            SpotlightTarget.Parsed p = SpotlightTarget.parse(t.target());
            if (p.ok() && p.target().graph() != null) out.add(p.target().graph());
        }
        return out;
    }

    /** Every target's state, in step order and numbered from 1, each with its identity verdict and on-screen check. */
    List<SessionEvents.WalkTargetState> states(WalkSpec walk, WalkSpec.Step step, boolean trusted) {
        WalkResolver.Facts facts = facts(trusted);
        List<SessionEvents.WalkTargetState> out = new ArrayList<>();
        int n = 1;
        for (WalkSpec.Target t : step.targets()) {
            WalkResolver.Verdict v = WalkResolver.verdict(t, step.view(), walk.runBasis(), facts);
            boolean available = v.available();
            String reason = v.reason();
            // review PR57 R5: a target that shows the SELECTED record is available only if the selection is the
            // requested record. A visible detail pane is not proof that the step's record is in it.
            if (available && step.view().record() != null && dependsOnSelection(t) && !frame.recordSelected(step.view().record())) {
                available = false;
                reason = "record " + step.view().record() + " is not shown — this step's filter hides it, so "
                        + whatItWouldDescribe(t) + " does not describe it";
            }
            if (available) {
                SpotlightTarget.Resolution r = frame.resolve(t.target());
                if (!r.lit()) { available = false; reason = r.reason(); }
            }
            out.add(new SessionEvents.WalkTargetState(n++, t.target(), WalkResolver.caption(t.caption(), v.state()),
                    v.state().name(), available, reason));
        }
        return out;
    }

    /** The surface a selection-dependent target draws, for a refusal that names what the person is looking at. */
    private static String whatItWouldDescribe(WalkSpec.Target t) {
        SpotlightTarget.Parsed p = SpotlightTarget.parse(t.target());
        return p.ok() && p.target().family() == SpotlightTarget.Family.TOPOLOGY
                ? "the topology's step cursor" : "the detail pane";
    }

    /**
     * Targets whose CONTENT is whichever record is selected, rather than a subject they name themselves.
     *
     * <p>The detail pane and its node blocks describe the selection. So does the topology CANVAS (review PR57 R5,
     * second round): it carries a step cursor bound to a record and kept in step with the table, so lighting it
     * under a step whose record could not be selected points at a canvas describing a different record — the same
     * defect as the detail pane, one surface over.
     *
     * <p>Deliberately NOT here: {@code records:row:<n>} names its own index, and its bounds are empty when the
     * filter hides that row, so it is already refused; {@code topology:node:<id>} names a node that is there
     * whatever is selected; {@code topology:verdict} states the pairing, which is not record-scoped.
     */
    private static boolean dependsOnSelection(WalkSpec.Target t) {
        SpotlightTarget.Parsed p = SpotlightTarget.parse(t.target());
        return p.ok() && (p.target().family() == SpotlightTarget.Family.DETAIL
                || p.target().family() == SpotlightTarget.Family.DETAIL_NODE
                || p.target().family() == SpotlightTarget.Family.TOPOLOGY);
    }

    /**
     * The frame's current facts, read on the EDT at resolution time. Review PR57 R1: when the session says record text
     * cannot be trusted, NO record text is read and no run basis is claimed, so the resolver can only say unresolved.
     */
    WalkResolver.Facts facts(boolean trusted) {
        LogStore store = trusted ? frame.store() : null;
        LogStore counted = frame.store();
        List<String> runNow = trusted ? WalkIdentity.runBasis(frame.runBasisNow()) : List.of();
        return new WalkResolver.Facts() {
            public int recordCount() { return counted == null ? 0 : counted.size(); }
            public String recordRepresentation() { return store == null ? null : store.getClass().getSimpleName(); }
            public String recordDigest(int index) { return store == null ? null : WalkIdentity.recordDigest(store.rawText(index)); }
            public List<String> runBasis() { return runNow; }
            public String chartDefinitionDigest(String chart) {
                GraphSpec spec = specOf(chart);
                return spec == null ? null : ConfigStore.chartDefinitionDigest(spec);
            }
            public boolean chartHasExternalSeries(String chart) {
                GraphSpec spec = specOf(chart);
                return spec != null && !spec.external().isEmpty();
            }
            public WalkResolver.Drawn chartDrawn(String chart) {
                GraphPanel g = frame.graphs().graphNamed(chart);
                if (g == null) return null;
                ChartPanel.DrawnFact d = g.chartPanel().drawnFact();
                return new WalkResolver.Drawn(d.drawn(), d.reason());
            }
            public String graphDigest() { return frame.topology().loadedGraphSha256(); }
            public boolean nodeExists(String id) { return frame.topology().hasNode(id); }
        };
    }

    /** A chart's definition: the open tab's live spec if it is open, else the saved one. */
    private GraphSpec specOf(String chart) {
        if (chart == null) return null;
        for (GraphSpec s : frame.graphs().specs()) if (s.name().equals(chart)) return s;
        for (GraphSpec s : frame.config().savedGraphs) if (s.name().equals(chart)) return s;
        return null;
    }

    private SessionEvents.WalkViewApplied refused(SessionEffects.ApplyWalkViewEffect e, String why) {
        return new SessionEvents.WalkViewApplied(e.opId(), e.ticket(), false, why);
    }

    /** For tests: the ticket of the preparation in flight, or -1. */
    long preparingTicket() {
        return preparing;
    }
}
