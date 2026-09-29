package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.ChartNames;
import telamin.fluxtion.audit.analyser.analyser.config.ConfigStore;
import telamin.fluxtion.audit.analyser.analyser.config.FocusSpec;
import telamin.fluxtion.audit.analyser.analyser.config.GraphSpec;
import telamin.fluxtion.audit.analyser.analyser.config.WalkBin;
import telamin.fluxtion.audit.analyser.analyser.filter.FilterState;
import telamin.fluxtion.audit.analyser.analyser.parse.LogStore;
import telamin.fluxtion.audit.analyser.analyser.report.LogFingerprint;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkIdentity;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkConversation;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkSteps;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * M69 — the ONE way a walk is written (spec-spotlight-walks.md §3.5, §3.7, §3.9): the right-click save menu and the
 * {@code walk} verb both come through here, so a walk saved by a person and one saved by the assistant are bound to
 * their evidence the same way.
 *
 * <p>It captures what is on screen (the lit targets, their captions, and the allow-listed view), or binds steps a
 * client supplied, computing every basis from what is loaded NOW — a client points, the analyser digests. A save is
 * coherent: the log generation read at capture must still be current when the walk is stored, or the save is refused
 * rather than binding old captions to new data.
 */
final class WalkAuthoring {

    interface Frame {
        AppConfig config();

        LogStore store();

        FilterState filter();

        GraphTabs graphs();

        TopologyPanel topology();

        /** The side tab showing now, as a spotlight tab word. */
        String selectedTabWord();

        /** The selected record, when exactly one is selected; else -1. */
        int selectedRecord();

        List<SpotlightOverlay.Lit> lit();

        List<String> runBasisNow();

        LogFingerprint fingerprint();

        long generation();

        /** Store the edited walks the way reports are stored: through the profile's edit funnel. */
        void persist();

        /** Report a fact to the session (review PR57 R6: every saved definition is reported; the node decides). */
        void post(Object fact);

        /** The session's published file-identity verdict (review PR57 R1), or null when none. */
        String sessionIdentity();
    }

    /** A captured step, what could not be saved in it, and the generation it was read under. */
    record Capture(WalkSpec.Step step, List<String> notSaved, long generation) { }

    private final Frame frame;

    WalkAuthoring(Frame frame) {
        this.frame = frame;
    }

    /** The log generation now: a caller binding steps reads it first, and hands it to {@link #save}. */
    long generationNow() {
        return frame.generation();
    }

    // ---- capture: what is on screen ---------------------------------------------------------------------------

    Capture capture() {
        List<String> notSaved = new ArrayList<>();
        List<WalkSpec.Target> targets = new ArrayList<>();
        for (SpotlightOverlay.Lit l : frame.lit()) {
            SpotlightTarget.Parsed p = SpotlightTarget.parse(l.target());
            if (!p.ok() || !isWalkable(p.target().family())) {
                notSaved.add("'" + l.target() + "' (a walk cannot point at source, toolbar or menu targets yet)");
                continue;
            }
            targets.add(new WalkSpec.Target(l.target(), l.caption() == null ? "" : l.caption(), null));
        }
        String tab = frame.selectedTabWord();
        FilterState f = frame.filter();
        WalkSpec.Filter filter = f == null ? null : new WalkSpec.Filter(f.fromMillis(), f.toMillis(), f.groupMode().name(),
                f.dimensions() == null ? null : new ArrayList<>(f.dimensions()), f.text());
        int selected = frame.selectedRecord();
        boolean chartTargeted = targets.stream().anyMatch(t -> "chart".equals(WalkSteps.basisKind(t.target())));
        String graph = "graph".equals(tab) || chartTargeted ? frame.graphs().selectedGraphName() : null;
        if (graph != null) {
            GraphPanel g = frame.graphs().graphNamed(graph);
            if (g != null && g.isPinned()) notSaved.add("chart '" + graph + "''s pinned window (a walk does not restore a chart's window)");
        }
        WalkSpec.FocusRef focus = null;
        String applied = frame.topology().appliedFocusLabel();
        if (applied != null) {
            FocusSpec spec = frame.config().namedFocuses.stream().filter(x -> x.name().equals(applied)).findFirst().orElse(null);
            if (spec != null) focus = new WalkSpec.FocusRef(applied, ConfigStore.focusDefinitionDigest(spec));
            else notSaved.add("the topology's current focus (it is not a saved focus)");
        }
        // the selected record is part of the view the author saw — the detail pane shows it
        Integer record = selected >= 0 ? selected : null;
        WalkSpec.View view = new WalkSpec.View(tab, filter, record, graph, focus);
        return new Capture(bind(new WalkSpec.Step("", view, targets)), List.copyOf(notSaved), frame.generation());
    }

    private static boolean isWalkable(SpotlightTarget.Family f) {
        return switch (f) {
            case DESIGN, DESIGN_BEAN, DESIGN_LINE, JAVA, JAVA_LINE, TOOLBAR, MENU, MENU_ITEM -> false;
            default -> true;
        };
    }

    // ---- bind: compute every basis from what is loaded now (§3.5) --------------------------------------------

    /** The step with each target's basis, and its focus's digest, computed now. A client can never supply these. */
    WalkSpec.Step bind(WalkSpec.Step step) {
        LogStore store = frame.store();
        List<WalkSpec.Target> bound = new ArrayList<>();
        for (WalkSpec.Target t : step.targets()) bound.add(new WalkSpec.Target(t.target(), t.caption(), basis(t, step.view(), store)));
        final WalkSpec.FocusRef asked = step.view().focus();
        WalkSpec.FocusRef focus = asked;
        if (asked != null && asked.digest().isBlank()) {
            FocusSpec spec = frame.config().namedFocuses.stream().filter(x -> x.name().equals(asked.name())).findFirst().orElse(null);
            focus = new WalkSpec.FocusRef(asked.name(), spec == null ? "" : ConfigStore.focusDefinitionDigest(spec));
        }
        WalkSpec.View v = step.view();
        return new WalkSpec.Step(step.caption(), new WalkSpec.View(v.tab(), v.filter(), v.record(), v.graph(), focus), bound,
                step.id(), step.through());   // OA-3: binding evidence never moves a step's dialogue binding
    }

    private WalkSpec.Basis basis(WalkSpec.Target t, WalkSpec.View view, LogStore store) {
        SpotlightTarget.Parsed p = SpotlightTarget.parse(t.target());
        if (!p.ok()) return WalkSpec.Basis.NONE;
        return switch (WalkSteps.basisKind(t.target())) {
            case "record" -> {
                int index = p.target().family() == SpotlightTarget.Family.RECORDS_ROW ? p.target().number()
                        : view.record() == null ? -1 : view.record();
                // review PR57 R1: no record text is read while the session says the file changed after it was read
                String digest = store == null || index < 0 || index >= store.size()
                        || !WalkIdentity.recordsTrusted(frame.sessionIdentity()) ? ""
                        : WalkIdentity.recordDigest(store.rawText(index));
                yield new WalkSpec.Basis("record", digest, store == null ? "" : store.getClass().getSimpleName());
            }
            case "chart" -> {
                String chart = p.target().graph() != null ? p.target().graph() : view.graph();
                GraphSpec spec = specOf(chart);
                yield new WalkSpec.Basis("chart", spec == null ? "" : ConfigStore.chartDefinitionDigest(spec), "");
            }
            case "graph" -> {
                String digest = frame.topology().loadedGraphSha256();
                yield new WalkSpec.Basis("graph", digest == null ? "" : digest, "");
            }
            default -> WalkSpec.Basis.NONE;
        };
    }

    private GraphSpec specOf(String chart) {
        if (chart == null) return null;
        for (GraphSpec s : frame.graphs().specs()) if (s.name().equals(chart)) return s;
        for (GraphSpec s : frame.config().savedGraphs) if (s.name().equals(chart)) return s;
        return null;
    }

    // ---- save ---------------------------------------------------------------------------------------------------

    /** Save {@code steps} as walk {@code name} — create, or replace by name. Returns null, or why it was refused. */
    String save(String name, String title, List<WalkSpec.Step> steps, String author, long capturedGeneration) {
        return save(name, title, steps, author, capturedGeneration, null, false);
    }

    /**
     * As {@link #save(String, String, List, String, long)}, with dialogue (OA-3). {@code conversation} null keeps the
     * existing walk's dialogue unless {@code replaceConversation}; a walk with dialogue gives every step a stable id, and
     * the binding is validated BEFORE anything is stored.
     */
    String save(String name, String title, List<WalkSpec.Step> steps, String author, long capturedGeneration,
                WalkSpec.Conversation conversation, boolean replaceConversation) {
        String refused = nameProblem(name);
        if (refused != null) return refused;
        if (steps.isEmpty()) return "a walk needs at least one step";
        if (steps.size() > WalkSpec.MAX_STEPS) return "at most " + WalkSpec.MAX_STEPS + " steps";
        for (int i = 0; i < steps.size(); i++) {
            String p = WalkSteps.problem(steps.get(i));
            if (p != null) return "step " + (i + 1) + ": " + p;
        }
        if (!WalkIdentity.recordsTrusted(frame.sessionIdentity()) && steps.stream().flatMap(s -> s.targets().stream())
                .anyMatch(t -> "record".equals(t.basis().kind()))) {
            return "the file behind this log changed after it was read (" + frame.sessionIdentity() + "), so a step "
                    + "pointing at its records cannot be bound to them — reopen the log, then save";
        }
        if (capturedGeneration != frame.generation()) {
            return "another log was opened while this walk was being saved — nothing was saved, because its steps "
                    + "were read against the previous log";
        }
        String now = java.time.Instant.now().toString();
        WalkSpec existing = WalkBin.find(frame.config().walks, name.trim());
        WalkSpec.Conversation dialogue = replaceConversation || existing == null ? conversation
                : conversation != null ? conversation : existing.conversation();
        String dialogueProblem = WalkConversation.problem(dialogue);
        if (dialogue != null && !dialogue.supported()) dialogueProblem = null;   // kept as it came; never played here
        if (dialogueProblem != null) return dialogueProblem;
        if (dialogue != null) steps = WalkConversation.withIds(steps);
        String binding = dialogue == null || dialogue.supported() ? WalkConversation.bindingProblem(dialogue, steps) : null;
        if (binding != null) return binding;
        List<String> run = WalkIdentity.runBasis(frame.runBasisNow());
        final List<WalkSpec.Step> saved = steps;
        WalkSpec walk = existing == null
                ? new WalkSpec(name.trim(), title, author, now, now, frame.fingerprint(), run, saved, Map.of(), dialogue)
                : new WalkSpec(name.trim(), title == null || title.isBlank() ? existing.title() : title, existing.author(),
                        existing.createdAt(), now, frame.fingerprint(), run, saved, existing.extras(), dialogue);
        frame.config().walks.removeIf(w -> w.name().equals(walk.name()));
        frame.config().walks.add(walk);
        frame.persist();
        frame.post(new telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkDefinitionChanged(walk.name(), walk, null));
        return null;
    }

    /**
     * Review PR57 R2: an edit that keeps a chart step saved on another run is refused. A walk has ONE run basis, and
     * saving rebinds it, so appending or replacing on run B would silently turn a chart step recorded on run A from
     * historical into current. Record targets carry their own digests and structural targets none, so neither is at
     * risk; only a kept chart target is.
     */
    private String mixedRun(WalkSpec w, List<WalkSpec.Step> kept) {
        boolean keepsChart = kept.stream().flatMap(s -> s.targets().stream())
                .anyMatch(t -> "chart".equals(t.basis().kind()));
        if (!keepsChart) return null;
        if (WalkIdentity.compareRuns(w.runBasis(), WalkIdentity.runBasis(frame.runBasisNow())) == WalkIdentity.State.CURRENT) {
            return null;
        }
        return "walk '" + w.name() + "' was saved against another run, and it has chart steps that describe that run — "
                + "editing it here would rebind them to this one. Nothing was saved: save the new step as a new walk, "
                + "or reopen the run it was saved on";
    }

    /** Append a captured step to an existing walk. */
    String append(String name, Capture c) {
        WalkSpec w = WalkBin.find(frame.config().walks, name);
        if (w == null) return "no walk called '" + name + "'";
        String mixed = mixedRun(w, w.steps());
        if (mixed != null) return mixed;
        List<WalkSpec.Step> steps = new ArrayList<>(w.steps());
        steps.add(c.step());
        return save(name, w.title(), steps, w.author(), c.generation());
    }

    /** Replace step {@code index} (0-based) of an existing walk with a captured one. */
    String replace(String name, int index, Capture c) {
        WalkSpec w = WalkBin.find(frame.config().walks, name);
        if (w == null) return "no walk called '" + name + "'";
        if (index < 0 || index >= w.steps().size()) return "walk '" + name + "' has no step " + (index + 1);
        List<WalkSpec.Step> kept = new ArrayList<>(w.steps());
        kept.remove(index);
        String mixed = mixedRun(w, kept);
        if (mixed != null) return mixed;
        List<WalkSpec.Step> steps = new ArrayList<>(w.steps());
        WalkSpec.Step was = w.steps().get(index);
        steps.set(index, c.step().withBinding(was.id(), was.through()));   // OA-3: the replaced step keeps its place in the dialogue
        return save(name, w.title(), steps, w.author(), c.generation());
    }

    /**
     * OA-3: attach, replace or remove a walk's dialogue and its step bindings ({@code through}, one per step, null for
     * none) WITHOUT rebinding its evidence: the steps' views, targets, bases and run basis are untouched, so dialogue can be
     * written for a walk saved on another run. Returns null, or why nothing was stored.
     */
    String setConversation(String name, WalkSpec.Conversation dialogue, List<String> through) {
        WalkSpec w = WalkBin.find(frame.config().walks, name);
        if (w == null) return "no walk called '" + name + "'";
        if (through != null && through.size() != w.steps().size()) {
            return "walk '" + name + "' has " + w.steps().size() + " steps; " + through.size() + " bindings were given";
        }
        String problem = WalkConversation.problem(dialogue);
        if (problem != null) return problem;
        List<WalkSpec.Step> steps = new ArrayList<>();
        for (int i = 0; i < w.steps().size(); i++) {
            WalkSpec.Step s = w.steps().get(i);
            steps.add(s.withBinding(s.id(), dialogue == null ? null : through == null ? s.through() : through.get(i)));
        }
        if (dialogue != null) steps = WalkConversation.withIds(steps);
        String binding = WalkConversation.bindingProblem(dialogue, steps);
        if (binding != null) return binding;
        WalkSpec updated = w.withConversation(dialogue, steps, java.time.Instant.now().toString());
        frame.config().walks.replaceAll(x -> x.name().equals(w.name()) ? updated : x);
        frame.persist();
        frame.post(new telamin.fluxtion.audit.analyser.analyser.session.SessionEvents.WalkDefinitionChanged(updated.name(), updated, null));
        return null;
    }

    static String nameProblem(String name) {
        if (name == null || name.isBlank()) return "a walk needs a name";
        String p = ChartNames.problem(name);
        return p == null ? null : p.replace("a chart name", "a walk name").replace("chart's", "walk's");
    }
}
