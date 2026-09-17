package telamin.fluxtion.audit.analyser.analyser.llm;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * M48.7 — the authoring handoff as SHARED CANVAS STATE: one object, exposed to an LLM through
 * {@code context.handoff}, rendered for the person in the Project panel, and settable by either.
 *
 * <h2>What it is, and what it is not</h2>
 * The mode selector is a headless CLI ({@code spec-authoring-mode-selector.md} R6). Its {@code --json}
 * record says which authoring mode each figure is in, what the catalogue resolved, and what is left to
 * author. R7 first asked the analyser to RENDER that record as a view; the owner's correction
 * (2026-09-03) is that the analyser is <i>"a shared canvas … the LLM / human is the intelligence, the
 * analyser is the tool/renderer"</i>. So <b>the analyser never runs the selector</b>. Whoever ran it — an
 * agent in its own loop, or a person — PLACES the record here, and both then act on the same object.
 *
 * <h2>Posture is SET; derivation is only the default (R10)</h2>
 * Whether the session is research/support or authoring/deploy can be guessed from what is open, and the
 * guess lags intent: when someone says <i>"let's build something new"</i> no artefact has changed yet.
 * So either party may set it, and what {@code context} reports always says WHICH it is reporting — a
 * setting, with who set it, or a derivation, labelled as a starting guess.
 *
 * <h2>The write rules this follows</h2>
 * {@code spec-shared-evidence-canvas.md} ▸ <i>Who writes, and what a write means</i>: every write is
 * VISIBLE (one state, two renderings), TYPED (a closed record shape, refused otherwise), ATTRIBUTED
 * (who set it and when), SCOPED (it ends with the session — a project transition clears it),
 * REVERSIBLE ({@code clear}), FAIL-CLOSED (a malformed record is refused whole, with the reason — never
 * half-applied) and BOUNDED (every list and string has a cap, named when hit).
 *
 * <p><b>Deliberately NOT here:</b> writing a chosen convention back to the project profile (R9). That is
 * a new profile key family, which grows only by review; this is session state and persists nothing.
 */
public final class CanvasHandoff {

    private CanvasHandoff() {
    }

    /** The two postures R10 names. */
    public enum Posture {
        RESEARCH("research/support"), AUTHORING("authoring/deploy");

        private final String label;

        Posture(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }

        /** Accepts either half of the label, either enum spelling, any case — and nothing else. */
        public static Optional<Posture> parse(String raw) {
            if (raw == null) return Optional.empty();
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "research", "support", "research/support" -> Optional.of(RESEARCH);
                case "authoring", "deploy", "authoring/deploy" -> Optional.of(AUTHORING);
                default -> Optional.empty();
            };
        }
    }

    /** Who wrote to the canvas. The words are the ones {@code log.openedBy} already uses. */
    public enum Author {
        HUMAN("you"), AGENT("action socket");

        private final String label;

        Author(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /** A posture somebody SET, as opposed to one derived from what is open. */
    public record PostureSetting(Posture posture, Author setBy, Instant at) {
    }

    /**
     * The selector's record, as placed on the canvas. {@code skills} may hold nulls: modes 0 and 0+ load
     * nothing, and the contract must be able to say so (spec ▸ <i>The handoff contract</i>).
     */
    public record Record(String branch, List<String> modes, List<String> skills,
                         List<String> resolvedFigures, List<String> authoringRequired,
                         Map<String, List<String>> selectionCandidates, Author setBy, Instant at) {
    }

    // ---- bounds: a canvas entry is a projection, not a dump -----------------------------------------
    static final int MAX_ITEMS = 200;
    static final int MAX_TEXT = 200;

    /** R10's fallback: a project open reads as authoring; otherwise research. A starting guess only. */
    public static Posture derive(boolean projectOpen) {
        return projectOpen ? Posture.AUTHORING : Posture.RESEARCH;
    }

    /** The outcome of validating a raw record: exactly one of the two is present. */
    public record Parsed(Record record, String refusal) {
        public boolean ok() {
            return record != null;
        }
    }

    /**
     * Validate the selector's {@code --json} record. Refused WHOLE with a reason on any defect — a
     * half-applied handoff would show a person a mode list that does not match its figures.
     */
    public static Parsed parse(Object raw, Author setBy, Instant at) {
        if (!(raw instanceof Map<?, ?> m)) return refuse("'record' must be an object — the selector's --json output");
        for (Object key : m.keySet()) {
            if (!KNOWN.contains(String.valueOf(key))) {
                return refuse("unknown field '" + key + "' — the handoff record is " + KNOWN
                        + "; an unrecognised field is refused rather than silently dropped");
            }
        }
        // a TYPED record (review F2): text() stringifies anything, so {"branch": {"instructions": "…"}} used to be
        // ACCEPTED as the branch "{instructions=…}". The list members were already held to String; so is this.
        Object rawBranch = m.get("branch");
        if (rawBranch != null && !(rawBranch instanceof String)) {
            return refuse("'branch' must be a string (the selector's branch, e.g. \"catalogue\") — got "
                    + jsonKind(rawBranch));
        }
        String branch = text(rawBranch);
        if (branch == null) return refuse("'branch' is required (the selector's branch, e.g. \"catalogue\")");
        if (invalidText(branch)) return refuse("'branch' must be one line of at most " + MAX_TEXT + " characters");

        List<String> modes = new ArrayList<>();
        List<String> skills = new ArrayList<>();
        List<String> resolved = new ArrayList<>();
        List<String> required = new ArrayList<>();
        String bad = strings(m.get("modes"), "modes", false, modes);
        if (bad == null) bad = strings(m.get("skills"), "skills", true, skills);
        if (bad == null) bad = strings(m.get("resolved_figures"), "resolved_figures", false, resolved);
        if (bad == null) bad = strings(m.get("authoring_required"), "authoring_required", false, required);
        if (bad != null) return refuse(bad);
        if (modes.isEmpty()) return refuse("'modes' must name at least one mode — mode is per FIGURE, so it is a list");
        if (!skills.isEmpty() && skills.size() != modes.size()) {
            return refuse("'skills' has " + skills.size() + " entries for " + modes.size()
                    + " modes — they are parallel lists (a mode that loads nothing is null, not absent)");
        }

        Map<String, List<String>> candidates = new LinkedHashMap<>();
        Object sc = m.get("selection_candidates");
        if (sc != null) {
            if (!(sc instanceof Map<?, ?> scm)) return refuse("'selection_candidates' must be an object of lists");
            if (scm.size() > MAX_ITEMS) return refuse("'selection_candidates' has more than " + MAX_ITEMS + " entries");
            for (var e : scm.entrySet()) {
                String name = text(e.getKey());
                if (name == null || invalidText(name)) return refuse("a 'selection_candidates' name must be one short line");
                List<String> values = new ArrayList<>();
                String problem = strings(e.getValue(), "selection_candidates." + name, false, values);
                if (problem != null) return refuse(problem);
                candidates.put(name, List.copyOf(values));
            }
        }
        return new Parsed(new Record(branch, List.copyOf(modes), java.util.Collections.unmodifiableList(skills),
                List.copyOf(resolved), List.copyOf(required), java.util.Collections.unmodifiableMap(candidates),
                setBy, at), null);
    }

    private static final java.util.Set<String> KNOWN = java.util.Set.of(
            "branch", "modes", "skills", "resolved_figures", "authoring_required", "selection_candidates");

    /**
     * The {@code context.handoff} section. Always present, because posture always has an answer; the
     * record appears only when somebody placed one. Attribution is in the data, not implied.
     */
    public static Map<String, Object> toContext(PostureSetting set, Record record, boolean projectOpen) {
        Map<String, Object> out = new LinkedHashMap<>();
        Map<String, Object> posture = new LinkedHashMap<>();
        Posture derived = derive(projectOpen);
        if (set != null) {
            posture.put("value", set.posture().label());
            posture.put("source", "set");
            posture.put("setBy", set.setBy().label());
            posture.put("at", set.at().toString());
            if (derived != set.posture()) posture.put("derivedWouldBe", derived.label());
        } else {
            posture.put("value", derived.label());
            posture.put("source", "derived");
            posture.put("note", "a starting guess from what is open (" + (projectOpen ? "a project is open" : "no project is open")
                    + ") — derivation lags intent; set it with open {posture} or AI ▸ Posture");
        }
        out.put("posture", posture);
        if (record != null) {
            Map<String, Object> r = new LinkedHashMap<>();
            r.put("branch", record.branch());
            r.put("modes", record.modes());
            if (!record.skills().isEmpty()) r.put("skills", record.skills());
            r.put("resolvedFigures", record.resolvedFigures());
            r.put("authoringRequired", record.authoringRequired());
            if (!record.selectionCandidates().isEmpty()) r.put("selectionCandidates", record.selectionCandidates());
            r.put("setBy", record.setBy().label());
            r.put("at", record.at().toString());
            r.put("note", "the mode selector's record as PLACED here — the analyser did not run the selector and "
                    + "does not verify it; it is whoever-set-it's statement about the catalogue");
            out.put("record", r);
        }
        return out;
    }

    /** One line for the person: what the Project panel row says under the posture. */
    public static String summary(Record record) {
        if (record == null) return null;
        String gap = record.authoringRequired().isEmpty()
                ? "nothing left to author"
                : record.authoringRequired().size() + " to author: " + String.join(", ", record.authoringRequired());
        return "modes " + String.join(" + ", record.modes()) + " · " + record.resolvedFigures().size()
                + " figure(s) resolved · " + gap
                + (record.selectionCandidates().isEmpty() ? "" : " · " + record.selectionCandidates().size() + " selection question(s)");
    }

    /**
     * The session's handoff state. One instance, held by the frame; both the socket and the menu write
     * through {@link #apply}, so there is one set of rules and the attribution is the only difference.
     */
    public static final class State {
        private PostureSetting posture;
        private Record record;

        public PostureSetting posture() {
            return posture;
        }

        public Record record() {
            return record;
        }

        /**
         * Apply a {@code handoff} request. EVERYTHING is validated before ANYTHING changes: a request
         * that sets a posture and places a malformed record changes neither.
         *
         * @return the reason it was refused, or empty when it was applied (or asked for nothing)
         */
        public Optional<String> apply(Map<String, Object> params, Author by, Instant at) {
            Object rawPosture = params.get("posture");
            Object rawRecord = params.get("record");
            Object rawClear = params.get("clear");
            if (rawClear != null && (rawPosture != null || rawRecord != null)) {
                return Optional.of("'clear' cannot be combined with 'posture' or 'record' — which half of "
                        + "\"set it and remove it\" was meant is not something to guess");
            }
            PostureSetting newPosture = posture;
            if (rawPosture != null && !(rawPosture instanceof String)) {
                return Optional.of("'posture' must be the string research, authoring or derived — got " + jsonKind(rawPosture));
            }
            if (rawPosture != null) {
                String p = rawPosture.toString().trim().toLowerCase(Locale.ROOT);
                if (p.equals("derived")) {
                    newPosture = null;
                } else {
                    Optional<Posture> parsed = Posture.parse(p);
                    if (parsed.isEmpty()) {
                        return Optional.of("'posture' must be research, authoring or derived — got '" + rawPosture + "'");
                    }
                    newPosture = new PostureSetting(parsed.get(), by, at);
                }
            }
            Record newRecord = record;
            if (rawRecord != null) {
                Parsed parsed = parse(rawRecord, by, at);
                if (!parsed.ok()) return Optional.of(parsed.refusal());
                newRecord = parsed.record();
            }
            if (rawClear != null) {
                switch (rawClear.toString().trim().toLowerCase(Locale.ROOT)) {
                    case "record" -> newRecord = null;
                    case "posture" -> newPosture = null;
                    case "all" -> { newRecord = null; newPosture = null; }
                    default -> { return Optional.of("'clear' must be record, posture or all — got '" + rawClear + "'"); }
                }
            }
            posture = newPosture;
            record = newRecord;
            return Optional.empty();
        }

        /** A project transition is a session boundary (M35.5): what was placed belonged to the last one. */
        public void clear() {
            posture = null;
            record = null;
        }

        public Map<String, Object> toContext(boolean projectOpen) {
            return CanvasHandoff.toContext(posture, record, projectOpen);
        }
    }

    // ---- helpers ------------------------------------------------------------------------------------

    private static Parsed refuse(String why) {
        return new Parsed(null, why);
    }

    private static String jsonKind(Object o) {
        if (o instanceof Map<?, ?>) return "an object";
        if (o instanceof List<?>) return "a list";
        if (o instanceof Boolean) return "a boolean";
        if (o instanceof Number) return "a number";
        return o.getClass().getSimpleName();
    }

    private static String text(Object o) {
        if (o == null) return null;
        String s = o.toString().trim();
        return s.isEmpty() ? null : s;
    }

    private static boolean invalidText(String s) {
        return s.length() > MAX_TEXT || s.chars().anyMatch(c -> c == '\n' || c == '\r' || c == '\t' || c < 0x20);
    }

    /** @return the reason the list is refused, or null when {@code out} holds it */
    private static String strings(Object raw, String field, boolean nullsAllowed, List<String> out) {
        if (raw == null) return null;
        if (!(raw instanceof List<?> list)) return "'" + field + "' must be a list of strings";
        if (list.size() > MAX_ITEMS) {
            return "'" + field + "' has " + list.size() + " entries; the canvas holds at most " + MAX_ITEMS
                    + " — it is a projection, and the selector's own output is the full record";
        }
        for (Object o : list) {
            if (o == null) {
                if (!nullsAllowed) return "'" + field + "' may not contain null";
                out.add(null);
                continue;
            }
            if (!(o instanceof String s) || s.isBlank()) return "'" + field + "' must contain only non-blank strings";
            if (invalidText(s.trim())) return "an entry of '" + field + "' is not one line of at most " + MAX_TEXT + " characters";
            out.add(s.trim());
        }
        return null;
    }
}
