package telamin.fluxtion.audit.analyser.analyser.llm;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Spring-authoring spec §H feedback 17 — {@code context {sections: [...]}}, an opt-in projection of the one
 * context payload. Omitting {@code sections} is the full response, byte for byte what it always was.
 *
 * <p>A projection is a FILTER over the full payload, never a second assembly: every selected key is the
 * value the full context holds for the same captured state, so a caller cannot get two answers to one
 * question depending on how much it asked for. The builder may skip what nobody asked for — that is the
 * saving — but it never computes a selected key differently.
 *
 * <p>A verdict travels with its qualification. The keys in {@link #QUALIFIERS} belong to one section but
 * qualify facts in others (an in-flight load, a partial dispatch order, time disorder, producer faults);
 * whenever one is present and qualifies a selected section it is carried, and the scope says why. Dropping
 * one would turn "5/5 logged" into a clean-looking fact the full payload would have warned about.
 */
public final class ContextSections {

    private ContextSections() {
    }

    /** Section name → the top-level context keys it covers, in the order the schema publishes them. */
    public static final Map<String, List<String>> SECTIONS;
    static {
        Map<String, List<String>> s = new LinkedHashMap<>();
        s.put("log", List.of("log", "provenance", "provenanceSource", "files", "inFlight", "dispatchOrder",
                "timeOrder", "producer", "rolledSetOffer"));
        s.put("project", List.of("project", "skills", "projectOffer", "fluxtionKey", "environments", "runbooks",
                "vocabulary", "analyses", "reportDestinations", "exports", "reports"));
        s.put("pairing", List.of("graphPairing"));
        s.put("processors", List.of("processors", "processorDeclarations"));
        s.put("source", List.of("source"));
        s.put("topology", List.of("topology"));
        s.put("view", List.of("filter", "showing", "selection", "flags", "spotlight"));
        s.put("charts", List.of("graphs", "graphScopes", "savedGraphs"));
        s.put("menus", List.of("menus", "menuChanges"));
        s.put("design", List.of("design", "restoration", "designSpotlights"));
        s.put("handoff", List.of("handoff"));
        SECTIONS = java.util.Collections.unmodifiableMap(s);
    }

    /** The published names — the schema's enum and the refusal's list are this one list. */
    public static final List<String> NAMES = List.copyOf(SECTIONS.keySet());

    /** Key → the sections whose facts it qualifies. Carried with those sections whenever it is present. */
    public static final Map<String, List<String>> QUALIFIERS = Map.of(
            "inFlight", List.of("pairing", "topology", "view", "charts"),
            "dispatchOrder", List.of("topology", "view"),
            "timeOrder", List.of("view", "charts"),
            "producer", List.of("pairing", "view", "charts"));

    /** Either a selection, the full default ({@code selection == null, error == null}), or a refusal. */
    public record Parsed(Selection selection, String error) {
        public boolean ok() { return error == null; }
    }

    /**
     * Validate the raw {@code sections} param. Refuses BEFORE anything is read: an unknown name or an empty
     * list is a caller's mistake, and answering it with a guessed subset would hide the mistake.
     */
    public static Parsed parse(Object raw) {
        if (raw == null) return new Parsed(null, null);
        if (!(raw instanceof List<?> list)) {
            return new Parsed(null, "sections is a list of section names: " + NAMES);
        }
        if (list.isEmpty()) {
            return new Parsed(null, "sections: [] selects nothing — omit it for the full context, or name "
                    + "sections from " + NAMES);
        }
        Set<String> picked = new LinkedHashSet<>();
        List<String> unknown = new ArrayList<>();
        for (Object o : list) {
            String name = o == null ? "null" : o.toString();
            if (SECTIONS.containsKey(name)) picked.add(name);
            else unknown.add(name);
        }
        if (!unknown.isEmpty()) {
            return new Parsed(null, "unknown context section(s) " + unknown + "; sections are " + NAMES);
        }
        return new Parsed(new Selection(List.copyOf(picked)), null);
    }

    /** A validated, non-empty set of section names. */
    public record Selection(List<String> names) {

        /** Whether the builder must compute {@code key}: it is selected, or it qualifies a selection. */
        public boolean needs(String key) {
            if (selects(key)) return true;
            List<String> qualifies = QUALIFIERS.get(key);
            if (qualifies != null) for (String n : names) if (qualifies.contains(n)) return true;
            return false;
        }

        /**
         * The selected keys of {@code full}, in its order, each value the same object, plus the qualifiers
         * that apply and a {@code scope} saying what this is and what was carried.
         */
        public Map<String, Object> project(Map<String, Object> full) {
            Map<String, Object> out = new LinkedHashMap<>();
            Map<String, Object> carried = new LinkedHashMap<>();
            for (Map.Entry<String, Object> e : full.entrySet()) {
                String key = e.getKey();
                if (selects(key)) {
                    out.put(key, e.getValue());
                    continue;
                }
                List<String> qualifies = QUALIFIERS.get(key);
                if (qualifies == null) continue;
                List<String> hit = qualifies.stream().filter(names::contains).toList();
                if (!hit.isEmpty()) {
                    out.put(key, e.getValue());
                    carried.put(key, "qualifies " + String.join(", ", hit));
                }
            }
            Map<String, Object> scope = new LinkedHashMap<>();
            scope.put("sections", names);
            scope.put("available", NAMES);
            if (!carried.isEmpty()) scope.put("carried", carried);
            scope.put("note", "a projection of the full context: each key here equals the same key of an "
                    + "unprojected call on the same state; absent sections were not read. Omit 'sections' "
                    + "for everything");
            out.put("scope", scope);
            return out;
        }

        private boolean selects(String key) {
            for (String n : names) if (SECTIONS.get(n).contains(key)) return true;
            return false;
        }
    }
}
