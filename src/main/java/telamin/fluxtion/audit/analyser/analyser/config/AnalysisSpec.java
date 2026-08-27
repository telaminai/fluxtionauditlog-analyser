package telamin.fluxtion.audit.analyser.analyser.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * M38.4 (spec-portable-context D-C5) — a repeatable analysis: <i>"the analysis we want to run every
 * time"</i>, saved by name with its <b>rationale</b>, as a sequence of ANALYSER verbs whose parameters are
 * declared and bound at run time ({@code {log}}, {@code {provenance}}), so one analysis serves every
 * incident of a kind.
 *
 * <p>Tier 2 by construction (D-C1): a step can only be a verb on the analyser's action socket, and that
 * surface never carries a server verb — so a saved analysis can drive a viewer and nothing else, which is
 * what makes it safe to share. Two things the gate refuses even though they are analyser verbs, because
 * each is a human's act rather than an analysis step: opening or closing a <b>project</b> (a session
 * boundary — M35.5), and a step naming a verb this build does not know.
 *
 * <p>Recalling one is an OFFER, never automatic: {@code context.analyses} lists them; {@code open
 * {analysis: name, bind: {…}}} or <i>File ▸ Run analysis</i> runs one — exactly as recalling a named
 * focus is an act.
 *
 * @param name       short handle
 * @param rationale  why this sequence exists — a saved analysis without its reason is an unexplained one
 * @param parameters declared names; a step may reference one as {@code {name}} inside any string value
 * @param defaults   optional default per parameter
 * @param steps      the verbs, in order
 */
public record AnalysisSpec(String name, String rationale, List<String> parameters, Map<String, String> defaults,
                           List<Step> steps) {

    /** One verb call: the action name and its params, exactly as the socket would receive them. */
    public record Step(String action, Map<String, Object> params) {
        public Step {
            action = action == null ? "" : action.trim();
            params = params == null ? Map.of() : Map.copyOf(params);
        }
    }

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9][A-Za-z0-9_ -]{0,59}");
    /**
     * Review F1 — the params of the shipped verbs that name a PATH. "step.action is an analyser verb" is
     * necessary and not sufficient for "a saved analysis can only drive this viewer": report and screenshot
     * write files, source_root mutates the recipient's config, open reads any file. So every value here is
     * gated like every other path in the profile ({@link Runbooks#refusePointer}: project-relative, no
     * {@code ..}, no command shapes) — or is exactly a declared {@code {parameter}}, bound at run time by the
     * person recalling the analysis, who sees the value. The recipient of a shared analysis sees a name and a
     * rationale, not the paths; this is what makes that acceptable.
     */
    static final Map<String, List<String>> PATH_PARAMS = Map.of(
            "open", List.of("log", "logs", "graphml"),
            "source_root", List.of("add", "remove"),
            "report", List.of("path"),
            "screenshot", List.of("path"));
    /** Path params resolved against the PROJECT ROOT at run time. report/screenshot are not: the export policy confines them to the exchange directory, relative to it. */
    static final Set<String> PROJECT_RELATIVE_ACTIONS = Set.of("open", "source_root");
    private static final Pattern PARAM = Pattern.compile("[A-Za-z][A-Za-z0-9_]{0,39}");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([A-Za-z][A-Za-z0-9_]*)}");

    public AnalysisSpec {
        name = name == null ? "" : name.trim();
        rationale = rationale == null ? "" : rationale.trim();
        parameters = List.copyOf(parameters == null ? List.of() : parameters);
        defaults = Map.copyOf(defaults == null ? Map.of() : defaults);
        steps = List.copyOf(steps == null ? List.of() : steps);
    }

    /**
     * Why this analysis may NOT be stored, or empty when it is sound. {@code knownVerbs} MUST be the shipped
     * verb surface, {@code VerbSchemas.all().keySet()}, and nothing wider (review N1) — the whole tier-2
     * argument rests on it; the parameter exists for testability, not for choice.
     */
    public static Optional<String> refuse(AnalysisSpec a, Set<String> knownVerbs) {
        if (a == null) return Optional.of("no analysis");
        if (!NAME.matcher(a.name()).matches()) {
            return Optional.of("analysis name must be 1–60 letters, digits, spaces, '-' or '_' (got '" + a.name() + "')");
        }
        if (a.steps().isEmpty()) return Optional.of("analysis '" + a.name() + "': no steps");
        for (String p : a.parameters()) {
            if (!PARAM.matcher(p).matches()) return Optional.of("analysis '" + a.name() + "': parameter name '" + p + "' is not an identifier");
        }
        for (String d : a.defaults().keySet()) {
            if (!a.parameters().contains(d)) return Optional.of("analysis '" + a.name() + "': default for undeclared parameter '" + d + "'");
        }
        int i = 0;
        for (Step s : a.steps()) {
            i++;
            if (!knownVerbs.contains(s.action())) {
                return Optional.of("analysis '" + a.name() + "' step " + i + ": '" + s.action() + "' is not an analyser verb — a saved "
                        + "analysis can only drive this viewer (" + String.join(", ", knownVerbs.stream().sorted().toList()) + ")");
            }
            if (s.action().equals("open") && (s.params().get("project") != null || "project".equals(s.params().get("close")))) {
                return Optional.of("analysis '" + a.name() + "' step " + i + ": opening or closing a PROJECT is a session boundary "
                        + "and a person's act — it cannot be a step");
            }
            for (String ph : placeholders(s.params())) {
                if (!a.parameters().contains(ph)) {
                    return Optional.of("analysis '" + a.name() + "' step " + i + ": {" + ph + "} is not a declared parameter");
                }
            }
            for (String param : PATH_PARAMS.getOrDefault(s.action(), List.of())) {
                Object v = s.params().get(param);
                if (v == null) continue;
                List<Object> values = v instanceof List<?> l ? new ArrayList<>(l) : List.of(v);
                for (Object o : values) {
                    String path = String.valueOf(o);
                    if (PLACEHOLDER.matcher(path).matches()) continue;      // a declared parameter, bound by the recaller
                    Optional<String> r = Runbooks.refusePointer("analysis '" + a.name() + "' step " + i + " " + s.action() + "." + param, path);
                    if (r.isPresent()) {
                        return Optional.of(r.get() + " — a shared analysis may only reach inside the project; "
                                + "a path elsewhere must be a {parameter} bound when it is run");
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Run-time: a project-relative path in an {@code open} or {@code source_root} step resolves against the
     * project root, so the same analysis works from any checkout. {@code report}/{@code screenshot} paths are
     * left alone — the export policy confines them to the exchange directory, relative to it. A value bound at
     * run time that is already absolute is left as it is: the person recalling chose it, and sees it.
     */
    public static List<Step> resolvePaths(List<Step> bound, java.nio.file.Path projectRoot) {
        if (projectRoot == null) return bound;
        List<Step> out = new ArrayList<>();
        for (Step s : bound) {
            if (!PROJECT_RELATIVE_ACTIONS.contains(s.action())) { out.add(s); continue; }
            Map<String, Object> params = new LinkedHashMap<>(s.params());
            for (String param : PATH_PARAMS.getOrDefault(s.action(), List.of())) {
                Object v = params.get(param);
                if (v == null) continue;
                if (v instanceof List<?> l) {
                    List<Object> resolved = new ArrayList<>();
                    for (Object o : l) resolved.add(resolveOne(String.valueOf(o), projectRoot));
                    params.put(param, resolved);
                } else {
                    params.put(param, resolveOne(String.valueOf(v), projectRoot));
                }
            }
            out.add(new Step(s.action(), params));
        }
        return out;
    }

    private static String resolveOne(String path, java.nio.file.Path root) {
        if (path.isBlank() || path.contains("://") || path.startsWith("~") || java.nio.file.Path.of(path).isAbsolute()) return path;
        return root.resolve(path).normalize().toString();
    }

    /** Every {@code {name}} referenced anywhere in a step's params. */
    static List<String> placeholders(Object value) {
        List<String> out = new ArrayList<>();
        collect(value, out);
        return out;
    }

    private static void collect(Object v, List<String> out) {
        if (v instanceof String s) {
            var m = PLACEHOLDER.matcher(s);
            while (m.find()) if (!out.contains(m.group(1))) out.add(m.group(1));
        } else if (v instanceof Map<?, ?> m) {
            m.values().forEach(x -> collect(x, out));
        } else if (v instanceof List<?> l) {
            l.forEach(x -> collect(x, out));
        }
    }

    /** The parameters a run still needs — declared, no default, not supplied. Empty means bindable. */
    public List<String> unbound(Map<String, String> supplied) {
        List<String> missing = new ArrayList<>();
        for (String p : parameters) {
            boolean have = supplied != null && supplied.get(p) != null && !supplied.get(p).isBlank();
            if (!have && !defaults.containsKey(p)) missing.add(p);
        }
        return missing;
    }

    /** The steps with every {@code {name}} replaced — a whole-string placeholder keeps the bound value's type as text. */
    public List<Step> bind(Map<String, String> supplied) {
        Map<String, String> values = new LinkedHashMap<>(defaults);
        if (supplied != null) supplied.forEach((k, v) -> { if (v != null && !v.isBlank()) values.put(k, v); });
        List<Step> bound = new ArrayList<>();
        for (Step s : steps) bound.add(new Step(s.action(), asMap(substitute(s.params(), values))));
        return bound;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> asMap(Object o) {
        return (Map<String, Object>) o;
    }

    private static Object substitute(Object v, Map<String, String> values) {
        if (v instanceof String s) {
            var m = PLACEHOLDER.matcher(s);
            StringBuilder sb = new StringBuilder();
            while (m.find()) m.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(values.getOrDefault(m.group(1), m.group(0))));
            m.appendTail(sb);
            return sb.toString();
        }
        if (v instanceof Map<?, ?> m) {
            Map<String, Object> out = new LinkedHashMap<>();
            m.forEach((k, x) -> out.put(String.valueOf(k), substitute(x, values)));
            return out;
        }
        if (v instanceof List<?> l) {
            List<Object> out = new ArrayList<>();
            l.forEach(x -> out.add(substitute(x, values)));
            return out;
        }
        return v;
    }

    /** One step's outcome, for the echo and the status bar. */
    public record StepResult(int index, String action, boolean ok, String error) { }

    /** A run: what happened to each step, in order, and where it stopped if it did. */
    public record RunResult(List<StepResult> steps, Integer stoppedAt) {
        public boolean completed() { return stoppedAt == null; }
    }

    /**
     * Execute the bound steps in order through {@code exec} — the same dispatcher the socket uses, so
     * every guard a verb has applies to it here. Stops at the first failure: an analysis is a sequence,
     * and step 4 on a log step 2 failed to open answers a question nobody asked.
     */
    public static RunResult run(List<Step> bound, Function<Step, telamin.fluxtion.audit.analyser.analyser.llm.ActionResult> exec) {
        List<StepResult> out = new ArrayList<>();
        for (int i = 0; i < bound.size(); i++) {
            Step s = bound.get(i);
            telamin.fluxtion.audit.analyser.analyser.llm.ActionResult r;
            try {
                r = exec.apply(s);
            } catch (RuntimeException e) {
                r = telamin.fluxtion.audit.analyser.analyser.llm.ActionResult.error(e.getMessage() == null ? e.toString() : e.getMessage());
            }
            boolean ok = r != null && r.ok();
            out.add(new StepResult(i + 1, s.action(), ok, ok ? null : (r == null ? "no result" : r.error())));
            if (!ok) return new RunResult(List.copyOf(out), i + 1);
        }
        return new RunResult(List.copyOf(out), null);
    }
}
