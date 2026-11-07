package telamin.fluxtion.audit.analyser.analyser.template;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Option 2 ("start with a template, then load the skills"): the analyser's starter templates as the onboard assistant and
 * the review row see them. Owner decision 2026-11-07 (replacing 2026-10-18's "Spring is our preferred route"): the
 * Java-authoring starter is how most developers are onboarded, so it is listed first and recommended; the catalogue's
 * other onboarding starters (Spring among them) stay recommended, after it. Pure — the catalogue and the
 * template files are read elsewhere ({@link TemplateClient}); this only names, describes and orders what they said.
 *
 * <p>A starter's <b>id</b> is its template file's stem ({@code fluxtion-spring.starter.json} → {@code fluxtion-spring}):
 * the catalogue's own identifier, never a name invented here.
 */
public final class Starters {

    private Starters() {
    }

    /** The file suffix every catalogue template file carries ({@link TemplateCatalogue.Entry} refuses any other). */
    public static final String SUFFIX = ".starter.json";

    /** The graph declared in Spring XML (the template file's {@code fluxtion.definition}). */
    public static final String SPRING = "spring";

    /**
     * The Java-authoring starter (owner, 2026-11-07: the first and recommended starter): the template file declares
     * {@code javaAuthoring: true} — the graph is built in plain Java ({@code FluxtionGraphBuilder}), no processor ships,
     * and the first build generates it with compiler sign-in. {@link TemplateClient.Template#parse} reports it as this
     * authoring word.
     */
    public static final String JAVA = "java";

    /**
     * One starter: the catalogue's id, name, description (as shown: {@link TemplateCatalogue.Entry#shownDescription}),
     * how its graph is authored (the template file's {@code fluxtion.definition}, "" when it names none), where it runs
     * ({@code embedded} or {@code mongoose}), its compile mode ({@code aot}, {@code interpreted} or ""), and its build-key
     * declaration ({@code build}, {@code run}, {@code none} or "").
     */
    public record Entry(String id, String name, String description, String authoring, String host, String mode,
                        String keyNeed, TemplateCatalogue.Entry catalogue) {
        public Entry {
            authoring = authoring == null ? "" : authoring;
            host = host == null ? "" : host;
            mode = mode == null ? "" : mode;
            keyNeed = keyNeed == null ? "" : keyNeed;
        }

        /** The Java-authoring starter: its template file says so, or (unread) the catalogue's tag does. */
        public boolean javaAuthoring() {
            return JAVA.equals(authoring) || (catalogue != null && catalogue.javaAuthoring());
        }

        /**
         * Recommended (owner, 2026-11-07): the Java-authoring starter, and every entry the catalogue tags for
         * onboarding — whether or not a key is present.
         */
        public boolean recommended() {
            return javaAuthoring() || (catalogue != null && catalogue.recommended());
        }
    }

    /** The id of a catalogue entry: its file's stem. */
    public static String idOf(TemplateCatalogue.Entry e) {
        String f = e.file();
        return f.endsWith(SUFFIX) ? f.substring(0, f.length() - SUFFIX.length()) : f;
    }

    /**
     * Where a starter's processor runs: {@code mongoose} when the catalogue says the template is Mongoose ({@code type}
     * {@code mongoose} or {@code hosted}) or the template file says {@code type.mongoose}, else {@code embedded}.
     */
    public static String hostOf(TemplateCatalogue.Entry e, boolean templateSaysMongoose) {
        String t = e.type().toLowerCase(Locale.ROOT);
        return templateSaysMongoose || t.equals("mongoose") || t.equals("hosted") ? "mongoose" : "embedded";
    }

    /** A catalogue entry and its template file's facts, as one starter. */
    public static Entry of(TemplateCatalogue.Entry e, TemplateClient.Template template) {
        return new Entry(idOf(e), e.name(), e.shownDescription(), template == null ? "" : template.authoring(),
                hostOf(e, template != null && template.mongoose()), word(e.mode(), "aot", "interpreted"),
                word(e.keyNeed(), "build", "sign-in-build", "run", "none"), e);
    }

    /** A catalogue declaration as one of its known words, "" when absent, {@code other} for anything else. */
    static String word(String value, String... known) {
        String v = value == null ? "" : value.strip().toLowerCase(Locale.ROOT);
        if (v.isEmpty()) return "";
        for (String k : known) if (k.equals(v)) return v;
        return "other";
    }

    /**
     * The order every starter list shows (owner, 2026-11-07), the same rule as {@link TemplateCatalogue#ordered}: the
     * Java-authoring starter first, then the other recommended entries, then the rest — within each group the
     * catalogue's own order.
     */
    public static List<Entry> ordered(List<Entry> entries) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : entries) if (e.javaAuthoring()) out.add(e);
        for (Entry e : entries) if (!e.javaAuthoring() && e.recommended()) out.add(e);
        for (Entry e : entries) if (!e.recommended()) out.add(e);
        return List.copyOf(out);
    }

    /**
     * What a starter list shows (owner, 2026-11-07), the same rule as {@link TemplateCatalogue#shown}: only the
     * recommended entries unless "Show all templates" is ticked, in {@link #ordered} order; everything when the
     * catalogue recommends nothing.
     */
    public static List<Entry> shown(List<Entry> entries, boolean showAll) {
        List<Entry> ordered = ordered(entries);
        if (showAll || ordered.stream().noneMatch(Entry::recommended)) return ordered;
        return ordered.stream().filter(Entry::recommended).toList();
    }

    /** How the graph is authored, in words: "Spring XML", or "Java" for any other declaration. */
    public static String authoringWords(String authoring) {
        return SPRING.equals(authoring) ? "Spring XML" : "Java";
    }

    /** The review row reports the catalogue's authentication contract; legacy key presence cannot prove Maven sign-in. */
    public static String facts(String authoring, String host, String mode, String keyNeed, boolean keyPresent) {
        List<String> parts = new ArrayList<>();
        parts.add(authoringWords(authoring));
        parts.add("mongoose".equals(host) ? "in a Mongoose server" : "embedded");
        if ("aot".equals(mode)) parts.add("AOT");
        else if ("interpreted".equals(mode)) parts.add("interpreted");
        // the Java-authoring starter ships no processor: its first build generates one (compiler sign-in)
        if (JAVA.equals(authoring)) parts.add("no processor ships; the first build generates it");
        String key = switch (keyNeed) {
            case "build" -> "build needs a Fluxtion key";
            case "sign-in-build" -> "build needs compiler sign-in (see README)";
            case "run" -> "running needs a Fluxtion key";
            case "none" -> "no Fluxtion key needed";
            default -> "";
        };
        if (!key.isEmpty() && !"none".equals(keyNeed) && !"sign-in-build".equals(keyNeed)) {
            key += keyPresent ? "; one is on this machine" : "; none found on this machine";
        }
        if (!key.isEmpty()) parts.add(key);
        return String.join(" · ", parts);
    }
}
