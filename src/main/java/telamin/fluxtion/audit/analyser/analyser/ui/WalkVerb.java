package telamin.fluxtion.audit.analyser.analyser.ui;

import telamin.fluxtion.audit.analyser.analyser.config.AppConfig;
import telamin.fluxtion.audit.analyser.analyser.config.WalkBin;
import telamin.fluxtion.audit.analyser.analyser.llm.ActionResult;
import telamin.fluxtion.audit.analyser.analyser.session.SessionEvents;
import telamin.fluxtion.audit.analyser.analyser.session.WalkPlaybackState;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkIdentity;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkSpec;
import telamin.fluxtion.audit.analyser.analyser.walk.WalkSteps;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * M69 S4 — the {@code walk} verb (spec-spotlight-walks.md §3.9) and the {@code context.walks} projection (§3.10).
 *
 * <p>One operation per call. The operation is chosen by the field that names it, and every other field present must
 * belong to that operation, or the call is refused before anything changes — {@code steps} beside {@code delete} is
 * two instructions, and guessing which one was meant would destroy a walk either way.
 *
 * <p>Saving goes through {@link WalkAuthoring}, the path the right-click menu uses, so a walk the assistant saved and
 * one a person saved are bound to their evidence the same way. Playing and ending REPORT a request to the session;
 * the {@code walkPlayback} node decides, and the reply reads the snapshot it published (rule 9).
 */
final class WalkVerb {

    interface Frame {
        AppConfig config();

        WalkAuthoring authoring();

        boolean logOpen();

        List<String> runBasisNow();

        /** Store the edited walks through the profile's edit funnel. */
        void persist();

        /** Report a fact to the session (a play or an end request). */
        void post(Object fact);

        WalkPlaybackState state();
    }

    /** Each operation's naming field → every field it may carry. */
    static final Map<String, Set<String>> OPERATIONS = Map.of(
            "steps", Set.of("steps", "name", "title"),
            "delete", Set.of("delete", "name"),
            "rename", Set.of("rename", "name"),
            "restore", Set.of("restore"),
            "play", Set.of("play", "name", "step"),
            "end", Set.of("end"));

    private final Frame frame;

    WalkVerb(Frame frame) {
        this.frame = frame;
    }

    /** The verb, from the assistant. */
    ActionResult run(Map<String, Object> params) {
        return run(params, ORIGIN_ASSISTANT);
    }

    static final String ORIGIN_ASSISTANT = "assistant", ORIGIN_REPORTS_TAB = "the Reports tab";

    /**
     * One operation, from {@code origin} — the verb and the Reports tab both come through here, so a walk deleted
     * from either place ends its own showing the same way.
     */
    ActionResult run(Map<String, Object> params, String origin) {
        List<String> named = OPERATIONS.keySet().stream().filter(params::containsKey).sorted().toList();
        if (named.isEmpty()) {
            return ActionResult.error("walk needs one operation: steps (save), delete, rename, restore, play or end"
                    + " — walks: " + names());
        }
        if (named.size() > 1) {
            return ActionResult.error("walk takes ONE operation per call — " + named + " were given; nothing was changed");
        }
        String op = named.get(0);
        Set<String> alien = new TreeSet<>(params.keySet());
        alien.removeAll(OPERATIONS.get(op));
        if (!alien.isEmpty()) {
            return ActionResult.error("walk " + op + " does not take " + alien + " — nothing was changed");
        }
        String name = params.get("name") == null ? null : String.valueOf(params.get("name"));
        return switch (op) {
            case "steps" -> save(name, params.get("title") == null ? "" : String.valueOf(params.get("title")), params.get("steps"));
            case "delete" -> Boolean.TRUE.equals(params.get("delete")) ? delete(name)
                    : ActionResult.error("walk 'delete' must be true");
            case "rename" -> rename(name, params.get("rename") == null ? null : String.valueOf(params.get("rename")));
            case "restore" -> restore(params.get("restore"));
            case "play" -> Boolean.TRUE.equals(params.get("play")) ? play(name, params.get("step"), origin)
                    : ActionResult.error("walk 'play' must be true");
            default -> Boolean.TRUE.equals(params.get("end")) ? end(origin) : ActionResult.error("walk 'end' must be true");
        };
    }

    private ActionResult save(String name, String title, Object rawSteps) {
        if (name == null || name.isBlank()) return ActionResult.error("saving a walk needs 'name'");
        WalkSteps.Parsed parsed = WalkSteps.parse(rawSteps);
        if (!parsed.ok()) return ActionResult.error(parsed.error() + " — nothing was saved");
        if (!frame.logOpen()) {
            for (int i = 0; i < parsed.steps().size(); i++) {
                for (WalkSpec.Target t : parsed.steps().get(i).targets()) {
                    String kind = WalkSteps.basisKind(t.target());
                    if ("record".equals(kind) || "chart".equals(kind)) {
                        return ActionResult.error("step " + (i + 1) + " points at '" + t.target() + "', which is bound to "
                                + "the log it is read from — open the log first. A walk of tabs, panels and graph nodes "
                                + "saves without one");
                    }
                }
            }
        }
        long generation = frame.authoring().generationNow();
        List<WalkSpec.Step> bound = new ArrayList<>();
        for (WalkSpec.Step s : parsed.steps()) bound.add(frame.authoring().bind(s));
        boolean replaced = WalkBin.find(frame.config().walks, name.trim()) != null;
        String refused = frame.authoring().save(name, title, bound, WalkSpec.AUTHOR_ASSISTANT, generation);
        if (refused != null) return ActionResult.error(refused);
        WalkSpec saved = WalkBin.find(frame.config().walks, name.trim());
        Map<String, Object> echo = new LinkedHashMap<>();
        echo.put("saved", saved.name());
        echo.put("replaced", replaced);
        echo.put("steps", saved.steps().size());
        echo.put("author", saved.author());
        List<String> unbound = new ArrayList<>();
        for (int i = 0; i < saved.steps().size(); i++) {
            for (WalkSpec.Target t : saved.steps().get(i).targets()) {
                if (!"none".equals(t.basis().kind()) && !t.basis().known()) {
                    unbound.add("step " + (i + 1) + " '" + t.target() + "': no " + t.basis().kind()
                            + " identity to bind to, so it will play as unresolved");
                }
            }
        }
        if (!unbound.isEmpty()) echo.put("warnings", unbound);
        echo.put("next", "walk {name: \"" + saved.name() + "\", play: true} presents it to the person");
        return ActionResult.ok("walk", "walk", echo);
    }

    private ActionResult delete(String name) {
        if (name == null) return ActionResult.error("delete needs 'name' — walks: " + names());
        WalkSpec doomed = WalkBin.delete(frame.config(), name, java.time.Instant.now().toString());
        if (doomed == null) return ActionResult.error("no walk called '" + name + "' — walks: " + names());
        // review PR57 R6: reported, never decided here — the node decides what a delete means for a showing walk
        frame.post(new SessionEvents.WalkDefinitionChanged(name, null, null));
        frame.persist();
        Map<String, Object> echo = new LinkedHashMap<>();
        echo.put("deleted", name);
        echo.put("remaining", names());
        echo.put("restorable", "walk {restore: \"" + name + "\"} brings it back into this project; the last "
                + WalkBin.CAPACITY + " deletions are kept on this machine");
        return ActionResult.ok("walk", "deleted", echo);
    }

    private ActionResult rename(String name, String to) {
        if (name == null) return ActionResult.error("rename needs 'name' (the walk to rename) — walks: " + names());
        String refused = WalkBin.rename(frame.config(), name, to);
        if (refused != null) return ActionResult.error(refused);
        frame.post(new SessionEvents.WalkDefinitionChanged(name, null, to.trim()));   // reported; the node decides
        frame.persist();
        return ActionResult.ok("walk", "renamed", new LinkedHashMap<>(Map.of("from", name, "to", to.trim())));
    }

    private ActionResult restore(Object restore) {
        if (Boolean.TRUE.equals(restore)) {
            return ActionResult.ok("walk", "restorable", new LinkedHashMap<>(Map.of("restorable", WalkBin.restorable(frame.config()))));
        }
        if (!(restore instanceof String name) || name.isBlank()) {
            return ActionResult.error("walk 'restore' needs true to list or a deleted walk's name");
        }
        String refused = WalkBin.restore(frame.config(), name);
        if (refused != null) return ActionResult.error(refused);
        frame.persist();
        return ActionResult.ok("walk", "restored", new LinkedHashMap<>(Map.of("restored", name, "walks", names())));
    }

    private ActionResult play(String name, Object step, String origin) {
        if (name == null) return ActionResult.error("play needs 'name' — walks: " + names());
        WalkSpec walk = WalkBin.find(frame.config().walks, name);
        if (walk == null) return ActionResult.error("no walk called '" + name + "' — walks: " + names());
        int from = 0;
        if (step != null) {
            // review PR57 R9: range-checked before narrowing, as the step parser is
            Long n = telamin.fluxtion.audit.analyser.analyser.walk.WalkSteps.integral(step, 1, WalkSpec.MAX_STEPS);
            if (n == null) {
                return ActionResult.error("'step' is a step number, counted from 1 (at most " + WalkSpec.MAX_STEPS + ")");
            }
            from = n.intValue() - 1;
        }
        frame.post(new SessionEvents.WalkPlayRequested(walk, from, origin));
        WalkPlaybackState s = frame.state();              // the node decided; this reports what it published
        if (!s.showing() || !s.walk().equals(walk.name())) {
            return ActionResult.error(s.reason().isBlank() ? "the walk was not started" : s.reason());
        }
        Map<String, Object> echo = new LinkedHashMap<>(showing(s));
        echo.put("note", "the person steps through it with the strip's ◀ ▶ (or ← →); context.walks.showing says what "
                + "each step's targets resolved to");
        return ActionResult.ok("walk", "playing", echo);
    }

    private ActionResult end(String origin) {
        WalkPlaybackState before = frame.state();
        if (!before.showing()) return ActionResult.ok("walk", "ended", new LinkedHashMap<>(Map.of("ended", false, "note", "no walk was showing")));
        frame.post(new SessionEvents.WalkEndRequested(ORIGIN_ASSISTANT.equals(origin) ? "ended by the assistant" : "ended from " + origin));
        return ActionResult.ok("walk", "ended", new LinkedHashMap<>(Map.of("ended", true, "walk", before.walk())));
    }

    private List<String> names() {
        return frame.config().walks.stream().map(WalkSpec::name).toList();
    }

    // ---- context.walks ------------------------------------------------------------------------------------------

    /** {@code context.walks}, or null when there is nothing to say. It reports the states the strip shows. */
    static Map<String, Object> context(AppConfig config, WalkPlaybackState state, boolean logOpen, List<String> runNow,
                                       String from) {
        List<Map<String, Object>> saved = new ArrayList<>();
        for (WalkSpec w : config.walks) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("name", w.name());
            if (!w.title().isBlank()) one.put("title", w.title());
            one.put("author", w.author());
            one.put("steps", w.steps().size());
            one.put("from", from);
            String warning = warning(w, logOpen, runNow);
            if (warning != null) one.put("warning", warning);
            saved.add(one);
        }
        List<String> deleted = WalkBin.restorable(config);
        if (saved.isEmpty() && deleted.isEmpty() && !state.showing()) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("saved", saved);
        if (!deleted.isEmpty()) out.put("restorable", deleted);
        if (state.showing()) out.put("showing", showing(state));
        return out;
    }

    private static String warning(WalkSpec w, boolean logOpen, List<String> runNow) {
        boolean observational = w.allTargets().stream().anyMatch(t -> "record".equals(t.basis().kind()) || "chart".equals(t.basis().kind()));
        if (!observational) return null;
        if (!logOpen) return "points at records or charts, and no log is open";
        return switch (WalkIdentity.compareRuns(w.runBasis(), runNow)) {
            case CURRENT -> null;
            case HISTORICAL -> "saved against a different run: its record and chart targets are historical or not shown";
            case UNRESOLVED -> "the run it was saved against cannot be compared with this one";
        };
    }

    static Map<String, Object> showing(WalkPlaybackState s) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("walk", s.walk());
        out.put("step", s.step() + 1);
        out.put("of", s.count());
        out.put("phase", s.phase());
        if (!s.reason().isBlank()) out.put("reason", s.reason());
        List<Map<String, Object>> targets = new ArrayList<>();
        for (SessionEvents.WalkTargetState t : s.targets()) {
            Map<String, Object> one = new LinkedHashMap<>();
            one.put("n", t.n());
            one.put("target", t.target());
            one.put("state", t.state());
            one.put("available", t.available());
            if (t.reason() != null && !t.reason().isBlank()) one.put("reason", t.reason());
            targets.add(one);
        }
        out.put("targets", targets);
        return out;
    }
}
